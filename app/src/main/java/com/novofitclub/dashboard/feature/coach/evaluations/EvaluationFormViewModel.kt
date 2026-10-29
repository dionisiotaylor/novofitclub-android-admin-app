package com.novofitclub.dashboard.feature.coach.evaluations

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novofitclub.dashboard.core.net.WPApiClient
import com.novofitclub.dashboard.core.net.num
import com.novofitclub.dashboard.core.ui.prepareUpload
import com.novofitclub.dashboard.feature.coach.CoachDates
import com.novofitclub.dashboard.feature.coach.CoachMemberRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

enum class EvalStep(val label: String) { Personal("Personal"), Health("Salud"), Training("Entrenamiento"), Measures("Medidas") }

/**
 * The 4-step form of member-evaluations (evaluations.js): a new evaluation
 * is prefilled from the member's latest one (health and goals, not the
 * measurements); every measurement shows its change from the one before.
 * [viewingCoachId]: an admin's "Ver como" coach, the default routine coach.
 */
class EvaluationFormViewModel(
    private val api: WPApiClient,
    initialMember: CoachMemberRef,
    val existing: Evaluation?,
    private val viewingCoachId: Int?,
) : ViewModel() {
    val isNew: Boolean get() = existing == null

    var member by mutableStateOf(initialMember)
        private set
    var step by mutableStateOf(EvalStep.Personal)

    var coaches by mutableStateOf<List<EvalCoach>>(emptyList())
        private set
    var routineCoachId by mutableStateOf<Int?>(existing?.let { it.routineCoachId ?: it.coachId })
    /** Who measured: the saved coach, or me on a new evaluation. */
    var measurerId by mutableStateOf(existing?.coachId)
        private set

    var date by mutableStateOf(existing?.date ?: CoachDates.today())
    var lifestyle by mutableStateOf(existing?.lifestyle)
    val goals = mutableStateListOf<String>()
    var otherGoal by mutableStateOf(false)
    var otherText by mutableStateOf("")
    var lastTraining by mutableStateOf(existing?.lastTraining)
    var gymExperience by mutableStateOf(existing?.gymExperience.orEmpty())

    val pain = mutableStateListOf<String>()
    var injuries by mutableStateOf(existing?.injuries.orEmpty())
    var surgeries by mutableStateOf(existing?.surgeries.orEmpty())
    var medications by mutableStateOf(existing?.medications.orEmpty())
    var cardiacAnswer by mutableStateOf<Boolean?>(null)
    var cardiacText by mutableStateOf("")
    var comments by mutableStateOf(existing?.comments.orEmpty())
    val documents = mutableStateListOf<EvalDocument>()
    var uploading by mutableIntStateOf(0)
        private set
    var uploadError by mutableStateOf<String?>(null)

    var days by mutableStateOf(existing?.days)
    var hours by mutableStateOf(existing?.hours)
    val measures = mutableStateMapOf<Measure, String>()

    /** The evaluation before this one, for the deltas. */
    var previous by mutableStateOf<Evaluation?>(null)
        private set
    /** Set when a new evaluation was prefilled from this date's evaluation. */
    var prefilledFrom by mutableStateOf<LocalDate?>(null)
        private set

    var isSaving by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)

    init {
        existing?.let { e ->
            setGoal(e.goal)
            pain.addAll(e.pain)
            val (answer, text) = Cardiac.decode(e.cardiac)
            cardiacAnswer = answer
            cardiacText = text
            documents.addAll(e.documents)
            Measure.entries.forEach { m -> m.of(e)?.let { measures[m] = evalNumber(if (m == Measure.Height) heightCm(it) else it, 2) } }
        }
        viewModelScope.launch {
            runCatching { EvaluationApi.coaches(api) }.getOrNull()?.let { (mine, list) ->
                coaches = list
                if (isNew) {
                    measurerId = mine.takeIf { it > 0 }
                    if (routineCoachId == null) routineCoachId = viewingCoachId ?: mine.takeIf { it > 0 }
                }
            }
        }
        loadPrevious()
    }

    val routineCoachName: String? get() = coaches.firstOrNull { it.id == routineCoachId }?.name
        ?: existing?.let { if (routineCoachId == it.routineCoachId) it.routineCoachName else if (routineCoachId == it.coachId) it.coachName else null }

    val deadlineHint: String
        get() {
            val measurer = measurerId
            val name = coaches.firstOrNull { it.id == measurer }?.name ?: existing?.coachName?.takeIf { measurer != null }
            val handoff = measurer != null && routineCoachId != null && routineCoachId != measurer
            val deadline = if (handoff) "otro coach hace la rutina: plazo de 8 días hábiles." else "plazo de 4 días hábiles."
            return if (!name.isNullOrEmpty()) "Midió $name · $deadline" else deadline.replaceFirstChar { it.uppercase() }
        }

    fun changeMember(ref: CoachMemberRef) {
        if (ref.id == member.id) return
        member = ref
        previous = null
        prefilledFrom = null
        loadPrevious()
    }

    private fun setGoal(text: String) {
        val (pills, custom) = Goals.decode(text)
        goals.clear()
        goals.addAll(Goals.PILLS.filter { it in pills })
        otherGoal = custom.isNotEmpty()
        otherText = custom
    }

    val goalText: String get() = Goals.encode(goals.toSet(), otherGoal, otherText)

    fun toggleGoal(goal: String) {
        if (goal in goals) goals.remove(goal) else goals.add(goal)
    }

    fun togglePain(id: String) {
        if (id in pain) pain.remove(id) else pain.add(id)
    }

    /** The previous evaluation (deltas) and, on a new one, the prefill. Only empty fields are filled. */
    private fun loadPrevious() {
        val memberId = member.id
        viewModelScope.launch {
            val record = runCatching { EvaluationApi.record(api, memberId) }.getOrNull() ?: return@launch
            if (memberId != member.id) return@launch
            val others = record.evaluations.filter { it.id != existing?.id }
            val prev = if (existing == null) others.firstOrNull() else others.firstOrNull { (it.date ?: LocalDate.MIN) <= date }
            previous = prev
            if (existing != null || prev == null) return@launch

            if (days == null) days = prev.days
            if (hours == null) hours = prev.hours
            if (lastTraining == null) lastTraining = prev.lastTraining
            if (gymExperience.isBlank()) gymExperience = prev.gymExperience
            if (injuries.isBlank()) injuries = prev.injuries
            if (surgeries.isBlank()) surgeries = prev.surgeries
            if (medications.isBlank()) medications = prev.medications
            if (cardiacAnswer == null) {
                val (answer, text) = Cardiac.decode(prev.cardiac)
                cardiacAnswer = answer
                cardiacText = text
            }
            if (comments.isBlank()) comments = prev.comments
            if (measures[Measure.Height].isNullOrBlank()) prev.height?.let { measures[Measure.Height] = evalNumber(heightCm(it), 2) }
            if (lifestyle == null) lifestyle = prev.lifestyle
            if (goalText.isEmpty() && prev.goal.isNotEmpty()) setGoal(prev.goal)
            if (pain.isEmpty()) pain.addAll(prev.pain)
            prefilledFrom = prev.date
        }
    }

    fun value(measure: Measure): Double? = measures[measure]?.let { parseDecimal(it) }

    val bmi: Double? get() = Bmi.value(value(Measure.Height), value(Measure.Weight))

    // ---- Documents ----

    fun uploadPhoto(jpeg: ByteArray) = upload { EvaluationApi.upload(api, jpeg, "image/jpeg", "foto-evaluacion.jpg") }

    fun uploadFile(context: Context, uri: Uri) = upload {
        val resolver = context.contentResolver
        val type = resolver.getType(uri).orEmpty()
        val name = withContext(Dispatchers.IO) {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } ?: "documento"
        if (type == "application/pdf") {
            val bytes = withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { it.readBytes() } }
                ?: throw IllegalStateException("No se pudo leer el archivo.")
            EvaluationApi.upload(api, bytes, "application/pdf", name)
        } else {
            val photo = withContext(Dispatchers.IO) { prepareUpload(context, uri) }
                ?: throw IllegalStateException("Ese archivo no es una foto o un PDF.")
            EvaluationApi.upload(api, photo.jpeg, "image/jpeg", name.substringBeforeLast('.') + ".jpg")
        }
    }

    private fun upload(block: suspend () -> EvalDocument) {
        viewModelScope.launch {
            uploading++
            uploadError = null
            try {
                documents.add(block())
            } catch (e: Exception) {
                uploadError = "No se pudo subir: ${e.localizedMessage ?: "error desconocido"}"
            }
            uploading--
        }
    }

    // ---- Save / delete ----

    private fun payload(): JSONObject {
        fun nullable(v: Any?) = v ?: JSONObject.NULL
        return JSONObject()
            .put("member_id", member.id)
            .put("routine_coach_id", routineCoachId ?: 0)
            .put("evaluation_date", date.toString())
            .put("program_goal", goalText)
            .put("training_days_per_week", nullable(days))
            .put("max_hours_per_day", nullable(hours))
            .put("lifestyle", nullable(lifestyle))
            .put("last_training_date", nullable(lastTraining?.toString()))
            .put("previous_gym_experience", gymExperience.trim())
            .put("injuries", injuries.trim())
            .put("previous_surgeries", surgeries.trim())
            .put("medications", medications.trim())
            .put("cardiac_condition", Cardiac.encode(cardiacAnswer, cardiacText))
            .put("additional_comments", comments.trim())
            .put("height_cm", nullable(value(Measure.Height)))
            .put("weight_kg", nullable(value(Measure.Weight)))
            .put("visceral_fat", nullable(value(Measure.Visceral)))
            .put("muscle_kg", nullable(value(Measure.Muscle)))
            .put("fat_percentage", nullable(value(Measure.FatPct)))
            .put("fat_kg", nullable(value(Measure.FatKg)))
            .put("documents", JSONArray(documents.map { it.id }))
            .put("pain_areas", JSONArray(BodyZones.sorted(pain)))
    }

    /** Saves; a new evaluation hands back what the Firma screen needs. */
    fun save(onSaved: (SignTarget?) -> Unit) {
        if (isSaving) return
        if (routineCoachId == null) {
            step = EvalStep.Personal
            errorMessage = "Elegí el coach para la rutina."
            return
        }
        viewModelScope.launch {
            isSaving = true
            errorMessage = null
            try {
                val existing = existing
                if (existing == null) {
                    val saved = api.postObject("member-evaluations/v1/evaluations", payload())
                    val id = saved.num("id")?.toInt() ?: throw com.novofitclub.dashboard.core.net.WPApiException.Decoding()
                    val routine = routineCoachId
                    val measurer = saved.num("coach_id")?.toInt() ?: measurerId
                    onSaved(
                        SignTarget(
                            evaluationId = id,
                            member = member,
                            date = date,
                            coachName = coaches.firstOrNull { it.id == measurer }?.name ?: routineCoachName.orEmpty(),
                            goals = goalText.split(", ").filter { it.isNotBlank() },
                            pain = BodyZones.sorted(pain),
                            days = days,
                            signedAt = null,
                            deliveryDays = saved.num("delivery_days")?.toInt() ?: if (routine != null && measurer != null && routine != measurer) 8 else 4,
                            programId = saved.num("program_id")?.toInt()?.takeIf { it > 0 },
                        ),
                    )
                } else {
                    api.putObject("member-evaluations/v1/evaluations/${existing.id}", payload())
                    onSaved(null)
                }
            } catch (e: Exception) {
                errorMessage = "No se pudo guardar: ${e.localizedMessage ?: "error desconocido"}"
            }
            isSaving = false
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = existing?.id ?: return
        viewModelScope.launch {
            isSaving = true
            try {
                api.deleteObject("member-evaluations/v1/evaluations/$id")
                onDeleted()
            } catch (e: Exception) {
                errorMessage = "No se pudo eliminar: ${e.localizedMessage ?: "error desconocido"}"
            }
            isSaving = false
        }
    }
}

/** Height in cm; some were typed in meters (1,6). */
private fun heightCm(value: Double): Double = if (value < 3) value * 100 else value
