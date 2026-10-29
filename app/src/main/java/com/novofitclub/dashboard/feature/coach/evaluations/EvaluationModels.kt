package com.novofitclub.dashboard.feature.coach.evaluations

import androidx.compose.ui.graphics.Color
import com.novofitclub.dashboard.core.net.WPApiClient
import com.novofitclub.dashboard.core.net.arr
import com.novofitclub.dashboard.core.net.bool
import com.novofitclub.dashboard.core.net.num
import com.novofitclub.dashboard.core.net.obj
import com.novofitclub.dashboard.core.net.objects
import com.novofitclub.dashboard.core.net.str
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.feature.coach.CoachApi
import com.novofitclub.dashboard.feature.coach.CoachDates
import com.novofitclub.dashboard.feature.coach.CoachMemberRef
import com.novofitclub.dashboard.feature.coach.ProgramStatus
import com.novofitclub.dashboard.feature.coach.hexColor
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.round

/*
 * Evaluaciones for coaches: member-evaluations' "Evaluaciones" and
 * "Expediente del Miembro" pages (novo-staff/v1/coach/evaluations,
 * coach/members/{id}/record) and its 4-step form (member-evaluations/v1).
 */

/** Every 6 weeks, as member-evaluations' next_due. */
const val EVALUATION_EVERY_DAYS = 42L

/** A tinted chip colour. */
object EvalColors {
    val first = Color(0xFF5AC8FA)
    val overdue = Color(0xFFFF6961)
    val soon = Color(0xFFFF9F0A)
    val good = Color(0xFF34C759)
    val pink = Color(0xFFFF5C80)
    val light = Color(0xFFC7C7CC)
}

fun JSONArray?.strings(): List<String> =
    this?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).ifEmpty { null } } }.orEmpty()

/** "84,5": at most [decimals] decimals, trailing zeros dropped, Spanish comma. */
fun evalNumber(value: Double, decimals: Int = 1): String {
    var factor = 1.0
    repeat(decimals) { factor *= 10 }
    val rounded = round(value * factor) / factor
    val text = if (rounded == kotlin.math.floor(rounded)) rounded.toLong().toString() else rounded.toBigDecimal().stripTrailingZeros().toPlainString()
    return text.replace('.', ',')
}

/** "75,5" or "75.5" → 75.5; empty → null. */
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()

object EvalDates {
    private val spanish = Locale.forLanguageTag("es")
    private val slash = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val withYear = DateTimeFormatter.ofPattern("d MMM yyyy", spanish)

    /** "20/10/2026" */
    fun slashed(date: LocalDate?): String = date?.format(slash) ?: "—"

    /** "8 sep", or "8 sep 2025" when it's not this year. */
    fun short(date: LocalDate?): String {
        date ?: return "—"
        return if (date.year == CoachDates.today().year) CoachDates.shortLabel(date) else date.format(withYear).replace(".", "")
    }

    fun days(from: LocalDate, to: LocalDate): Int = ChronoUnit.DAYS.between(from, to).toInt()

    /** [days] business days (Mon–Fri) after [date], as novo_staff_add_business_days. */
    fun addBusinessDays(date: LocalDate, days: Int): LocalDate {
        var d = date
        var added = 0
        while (added < days) {
            d = d.plusDays(1)
            if (d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY) added++
        }
        return d
    }
}

/** One row of `coach/evaluations`: a member and their latest evaluation. */
data class EvaluationListItem(
    val member: CoachMemberRef,
    val evaluationId: Int?,
    val lastDate: LocalDate?,
    val goal: String,
    val coach: String,
    val count: Int,
    val hasSignature: Boolean,
    val nextDue: LocalDate?,
    val daysToNext: Int?,
    /** Today's Medición cita, "Y-m-d H:i:s". */
    val cita: String?,
) {
    val isFirst: Boolean get() = evaluationId == null
    val needsSignature: Boolean get() = evaluationId != null && !hasSignature
    val isOverdue: Boolean get() = !isFirst && (daysToNext ?: 0) < 0
    val isSoon: Boolean get() = !isFirst && (daysToNext ?: Int.MAX_VALUE) in 0..7

    val subtitle: String
        get() {
            CoachDates.minutes(cita)?.let { return "Cita de medición hoy, ${CoachDates.clock(it)}" }
            if (lastDate == null) return "Sin evaluaciones"
            val first = goal.split(", ").firstOrNull { it.isNotBlank() }
            return listOfNotNull("Última: ${EvalDates.short(lastDate)}", first).joinToString(" · ")
        }

    /** The right-hand chip: text, colour, and whether it's tinted (a plain date isn't). */
    val due: Triple<String, Color, Boolean>
        get() {
            if (isFirst) return Triple("Primera", EvalColors.first, true)
            val d = daysToNext ?: return Triple("—", NovoColors.textSecondary, false)
            return when {
                d < 0 -> Triple("Vencida hace ${-d}d", EvalColors.overdue, true)
                d == 0 -> Triple("Vence hoy", EvalColors.soon, true)
                d <= 7 -> Triple("Vence en ${d}d", EvalColors.soon, true)
                else -> Triple(EvalDates.short(nextDue), Color(0xFF9A9A9E), false)
            }
        }

    companion object {
        fun from(json: JSONObject): EvaluationListItem? {
            val member = CoachMemberRef.from(json.obj("member")) ?: return null
            return EvaluationListItem(
                member = member,
                evaluationId = json.num("evaluation_id")?.toInt(),
                lastDate = CoachDates.parse(json.str("last_date")),
                goal = json.str("goal").orEmpty().trim(),
                coach = json.str("coach").orEmpty(),
                count = json.num("count")?.toInt() ?: 0,
                hasSignature = json.bool("has_signature") ?: false,
                nextDue = CoachDates.parse(json.str("next_due")),
                daysToNext = json.num("days_to_next")?.toInt(),
                cita = json.str("cita"),
            )
        }
    }
}

data class EvalDocument(val id: Int, val url: String, val thumb: String?, val type: String, val name: String) {
    val isImage: Boolean get() = type.startsWith("image/")
    val extension: String
        get() = (name.substringAfterLast('.', "").ifEmpty { url.substringAfterLast('.', "doc") }).take(4).uppercase()

    companion object {
        fun from(json: JSONObject): EvalDocument? {
            val id = json.num("id")?.toInt() ?: return null
            return EvalDocument(id, json.str("url").orEmpty(), json.str("thumb"), json.str("type").orEmpty(), json.str("name") ?: "Documento")
        }
    }
}

/** A full evaluation, as member-evaluations' enrich_evaluation() returns it. */
data class Evaluation(
    val id: Int,
    val memberId: Int,
    val coachId: Int?,
    val routineCoachId: Int?,
    val date: LocalDate?,
    val goal: String,
    val days: Int?,
    val hours: Double?,
    val injuries: String,
    val surgeries: String,
    val medications: String,
    val cardiac: String,
    val gymExperience: String,
    val lastTraining: LocalDate?,
    val lifestyle: String?,
    val comments: String,
    val pain: List<String>,
    val height: Double?,
    val weight: Double?,
    val visceral: Double?,
    val muscle: Double?,
    val fatPct: Double?,
    val fatKg: Double?,
    val documents: List<EvalDocument>,
    val signedAt: LocalDate?,
    val hasSignature: Boolean,
    val coachName: String,
    val routineCoachName: String,
    val deliveryDays: Int,
    val programId: Int?,
    val hasRoutine: Boolean,
) {
    val goals: List<String> get() = goal.split(", ").map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        fun from(json: JSONObject): Evaluation? {
            val id = json.num("id")?.toInt() ?: return null
            fun text(key: String) = json.str(key).orEmpty().trim()
            val coach = json.num("coach_id")?.toInt()?.takeIf { it > 0 }
            val routine = json.num("routine_coach_id")?.toInt()?.takeIf { it > 0 }
            return Evaluation(
                id = id,
                memberId = json.num("member_id")?.toInt() ?: 0,
                coachId = coach,
                routineCoachId = routine,
                date = CoachDates.parse(json.str("evaluation_date")),
                goal = text("program_goal"),
                days = json.num("training_days_per_week")?.toInt()?.takeIf { it > 0 },
                hours = json.num("max_hours_per_day")?.takeIf { it > 0 },
                injuries = text("injuries"),
                surgeries = text("previous_surgeries"),
                medications = text("medications"),
                cardiac = text("cardiac_condition"),
                gymExperience = text("previous_gym_experience"),
                lastTraining = CoachDates.parse(json.str("last_training_date")),
                lifestyle = json.str("lifestyle"),
                comments = text("additional_comments"),
                pain = json.arr("pain_areas").strings(),
                height = json.num("height_cm")?.takeIf { it > 0 },
                weight = json.num("weight_kg")?.takeIf { it > 0 },
                visceral = json.num("visceral_fat")?.takeIf { it > 0 },
                muscle = json.num("muscle_kg")?.takeIf { it > 0 },
                fatPct = json.num("fat_percentage")?.takeIf { it > 0 },
                fatKg = json.num("fat_kg")?.takeIf { it > 0 },
                documents = json.arr("documents")?.objects().orEmpty().mapNotNull { EvalDocument.from(it) },
                signedAt = CoachDates.parse(json.str("signed_at")),
                hasSignature = json.bool("has_signature") ?: false,
                coachName = json.str("coach_name").orEmpty().takeIf { it != "—" }.orEmpty(),
                routineCoachName = json.str("routine_coach_name").orEmpty().takeIf { it != "—" }.orEmpty(),
                deliveryDays = json.num("delivery_days")?.toInt()
                    ?: if (routine != null && routine != coach) 8 else 4,
                programId = json.num("program_id")?.toInt()?.takeIf { it > 0 },
                hasRoutine = json.bool("has_routine") ?: false,
            )
        }
    }
}

data class RecordProgram(val id: Int, val title: String, val date: LocalDate?, val status: ProgramStatus, val evaluationId: Int?) {
    companion object {
        fun from(json: JSONObject): RecordProgram? {
            val id = json.num("id")?.toInt() ?: return null
            return RecordProgram(
                id, json.str("title") ?: "Programa", CoachDates.parse(json.str("date")),
                ProgramStatus.from(json.str("delivery_status")), json.num("evaluation_id")?.toInt(),
            )
        }
    }
}

/** `coach/members/{id}/record`: the member's expediente. */
data class MemberRecord(
    val member: CoachMemberRef,
    val status: String,
    val statusLabel: String,
    val typeLabel: String?,
    val typeColor: Color?,
    val expiration: LocalDate?,
    val paymentDay: Int?,
    /** Newest first. */
    val evaluations: List<Evaluation>,
    val programs: List<RecordProgram>,
) {
    val latest: Evaluation? get() = evaluations.firstOrNull()
    val chronological: List<Evaluation> get() = evaluations.reversed()

    companion object {
        fun from(json: JSONObject): MemberRecord? {
            val m = json.obj("member") ?: return null
            val member = CoachMemberRef.from(m) ?: return null
            val type = m.obj("membership_type")
            val dates = m.obj("dates")
            return MemberRecord(
                member = member,
                status = m.str("status").orEmpty(),
                statusLabel = m.str("status_label").orEmpty(),
                typeLabel = type?.str("label"),
                typeColor = hexColor(type?.str("color")),
                expiration = CoachDates.parse(dates?.str("expiration")),
                paymentDay = CoachDates.parse(dates?.str("payment_day"))?.dayOfMonth,
                evaluations = json.arr("evaluations")?.objects().orEmpty().mapNotNull { Evaluation.from(it) },
                programs = json.arr("programs")?.objects().orEmpty().mapNotNull { RecordProgram.from(it) },
            )
        }
    }
}

data class EvalCoach(val id: Int, val name: String)

/** The "IMC" categories (WHO adults) and their colours. */
object Bmi {
    /** Height typed in meters (1,6) instead of centimeters is read as meters. */
    fun value(heightCm: Double?, weight: Double?): Double? {
        val h = heightCm?.takeIf { it > 0 } ?: return null
        val w = weight?.takeIf { it > 0 } ?: return null
        val meters = if (h < 3) h else h / 100
        return w / (meters * meters)
    }

    fun category(bmi: Double): Pair<String, Color> = when {
        bmi < 18.5 -> "Bajo peso" to EvalColors.first
        bmi < 25 -> "Normal" to EvalColors.good
        bmi < 30 -> "Sobrepeso" to EvalColors.soon
        else -> "Obesidad" to EvalColors.overdue
    }
}

/** "¿Condición cardíaca?": the web stores free text; the app asks No / Sí. */
object Cardiac {
    private val NO = setOf("no", "ninguna", "ninguno", "n/a", "na", "-", "nada")

    private fun fold(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase().trim()

    /** null = unset, false = No, true = Sí (with its text). */
    fun decode(raw: String): Pair<Boolean?, String> {
        val text = raw.trim()
        if (text.isEmpty()) return null to ""
        val folded = fold(text).trimEnd('.')
        if (folded in NO) return false to ""
        if (folded == "si") return true to ""
        return true to text
    }

    fun encode(answer: Boolean?, text: String): String = when (answer) {
        null -> ""
        false -> "No"
        true -> text.trim().ifEmpty { "Sí" }
    }

    /** "No", "Sí: …", or null when unset. */
    fun label(raw: String): String? {
        val (answer, text) = decode(raw)
        return when (answer) {
            null -> null
            false -> "No"
            true -> if (text.isEmpty()) "Sí" else "Sí: $text"
        }
    }
}

object Goals {
    val PILLS = listOf(
        "Pérdida de Peso", "Ganancia Muscular", "Rehabilitación",
        "Cardio y Resistencia", "Flexibilidad", "Rendimiento Deportivo", "Condición Física General",
    )

    /** Saved text → (selected pills, other text). */
    fun decode(text: String): Pair<Set<String>, String> {
        val pills = mutableSetOf<String>()
        val custom = mutableListOf<String>()
        text.split(", ").forEach { part ->
            if (part in PILLS) pills.add(part) else if (part.isNotBlank()) custom.add(part.trim())
        }
        return pills to custom.joinToString(", ")
    }

    fun encode(pills: Set<String>, other: Boolean, otherText: String): String {
        val parts = PILLS.filter { it in pills }.toMutableList()
        if (other && otherText.isNotBlank()) parts.add(otherText.trim())
        return parts.joinToString(", ")
    }
}

val LIFESTYLES = listOf("sedentary" to "Sedentario", "moderately_active" to "Mod. activo", "active" to "Activo")

/** The "Medidas" fields, with which way is good for the delta. */
enum class Measure(val label: String, val unit: String, val goodUp: Boolean?) {
    Height("Altura (cm)", "cm", null),
    Weight("Peso (kg)", "kg", false),
    Muscle("Músculo (kg)", "kg", true),
    FatPct("Grasa corporal (%)", "%", false),
    FatKg("Grasa corporal (kg)", "kg", false),
    Visceral("Grasa visceral", "", false);

    fun of(evaluation: Evaluation): Double? = when (this) {
        Height -> evaluation.height
        Weight -> evaluation.weight
        Muscle -> evaluation.muscle
        FatPct -> evaluation.fatPct
        FatKg -> evaluation.fatKg
        Visceral -> evaluation.visceral
    }
}

/** What the Firma screen shows and needs. */
data class SignTarget(
    val evaluationId: Int,
    val member: CoachMemberRef,
    val date: LocalDate?,
    val coachName: String,
    val goals: List<String>,
    val pain: List<String>,
    val days: Int?,
    val signedAt: LocalDate?,
    val deliveryDays: Int,
    val programId: Int?,
) {
    val deadline: LocalDate? get() = date?.let { EvalDates.addBusinessDays(it, deliveryDays) }

    companion object {
        fun from(evaluation: Evaluation, member: CoachMemberRef) = SignTarget(
            evaluationId = evaluation.id,
            member = member,
            date = evaluation.date,
            coachName = evaluation.coachName,
            goals = evaluation.goals,
            pain = evaluation.pain,
            days = evaluation.days,
            signedAt = if (evaluation.hasSignature) evaluation.signedAt ?: evaluation.date else null,
            deliveryDays = evaluation.deliveryDays,
            programId = evaluation.programId,
        )
    }
}

object EvaluationApi {
    suspend fun list(api: WPApiClient, coachId: Int?, search: String): List<EvaluationListItem> {
        val query = CoachApi.coachQuery(coachId) + (if (search.isNotEmpty()) mapOf("search" to search) else emptyMap())
        return api.getObject("novo-staff/v1/coach/evaluations", query).arr("items")?.objects().orEmpty().mapNotNull { EvaluationListItem.from(it) }
    }

    suspend fun record(api: WPApiClient, memberId: Int): MemberRecord =
        MemberRecord.from(api.getObject("novo-staff/v1/coach/members/$memberId/record"))
            ?: throw com.novofitclub.dashboard.core.net.WPApiException.Decoding()

    /** (my coach id or 0, active coaches). */
    suspend fun coaches(api: WPApiClient): Pair<Int, List<EvalCoach>> {
        val json = api.getObject("novo-staff/v1/coach/evaluation-coaches")
        val coaches = json.arr("coaches")?.objects().orEmpty().mapNotNull { c ->
            c.num("id")?.toInt()?.let { EvalCoach(it, c.str("name").orEmpty()) }
        }
        return (json.num("me")?.toInt() ?: 0) to coaches
    }

    suspend fun upload(api: WPApiClient, bytes: ByteArray, contentType: String, name: String): EvalDocument =
        EvalDocument.from(api.uploadBytes("novo-staff/v1/coach/evaluations/documents", bytes, contentType, mapOf("name" to name)))
            ?: throw com.novofitclub.dashboard.core.net.WPApiException.Decoding()

    suspend fun sign(api: WPApiClient, evaluationId: Int, pngBase64: String) {
        api.postObject("member-evaluations/v1/evaluations/$evaluationId/sign", JSONObject().put("signature_data", "data:image/png;base64,$pngBase64"))
    }
}
