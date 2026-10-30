package com.novofitclub.dashboard.feature.coach

import androidx.compose.ui.graphics.Color
import com.novofitclub.dashboard.core.net.WPApiClient
import com.novofitclub.dashboard.core.net.WPApiException
import com.novofitclub.dashboard.core.net.arr
import com.novofitclub.dashboard.core.net.bool
import com.novofitclub.dashboard.core.net.num
import com.novofitclub.dashboard.core.net.obj
import com.novofitclub.dashboard.core.net.objects
import com.novofitclub.dashboard.core.net.str
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.initials
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

/** Dates of the coach endpoints, in the gym's clock (Costa Rica) whatever the phone is set to. */
object CoachDates {
    val zone: ZoneId = ZoneId.of("America/Costa_Rica")
    private val spanish = Locale.forLanguageTag("es")
    private val short = DateTimeFormatter.ofPattern("d MMM", spanish)
    private val long = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", spanish)

    fun today(): LocalDate = LocalDate.now(zone)

    fun parse(raw: String?): LocalDate? =
        raw?.takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

    /** "12 oct" */
    fun shortLabel(date: LocalDate?): String = date?.format(short)?.replace(".", "") ?: "—"

    /** "Martes 20 de octubre" */
    fun longLabel(date: LocalDate): String = date.format(long).replaceFirstChar { it.uppercase() }

    /** Minutes from midnight of a "Y-m-d H:i:s", read from the string itself. */
    fun minutes(raw: String?): Int? {
        if (raw == null || raw.length < 16) return null
        val h = raw.substring(11, 13).toIntOrNull() ?: return null
        val m = raw.substring(14, 16).toIntOrNull() ?: return null
        return h * 60 + m
    }

    /** "6:00 a. m." — the 12-hour format of Citas and Horario on the web. */
    fun clock(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        val hour12 = if (h % 12 == 0) 12 else h % 12
        return "$hour12:${m.toString().padStart(2, '0')} ${if (h < 12) "a. m." else "p. m."}"
    }

    fun nowMinute(): Int {
        val now = java.time.LocalTime.now(zone)
        return now.hour * 60 + now.minute
    }
}

/** "#d06cf4" (employee, activity type and service colours). */
fun hexColor(raw: String?): Color? {
    val hex = raw?.trim()?.removePrefix("#") ?: return null
    if (hex.length != 6) return null
    return hex.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
}

/** A member as the coach endpoints send it (`novo_staff_member_ref`). */
data class CoachMemberRef(val id: Int, val name: String, val photoUrl: String?) {
    val initials: String get() = initials(name)
    val avatarColor: Color get() = NovoColors.avatarPalette[abs(id) % NovoColors.avatarPalette.size]

    companion object {
        fun from(json: JSONObject?): CoachMemberRef? {
            val id = json?.num("id")?.toInt() ?: return null
            return CoachMemberRef(id, json.str("name") ?: "Miembro", json.str("photo_url"))
        }
    }
}

/** A program's `delivery_status`, as the web's selects name it. */
enum class ProgramStatus(val raw: String, val label: String, val color: Color) {
    New("new", "Nuevo", NovoColors.statusNeutral),
    InProgress("in_progress", "En progreso", Color(0xFFB9A3FF)),
    Done("done", "Listo", NovoColors.statusActive),
    Delivered("delivered", "Entregado", Color(0xFFC7C7CC));

    val isPending: Boolean get() = this == New || this == InProgress

    companion object {
        fun from(raw: String?): ProgramStatus = entries.firstOrNull { it.raw == raw } ?: New
    }
}

/** The delivery deadline: 4 business days from the evaluation, 8 when another coach builds it. */
data class ProgramDeadline(val dueDate: LocalDate?, val daysRemaining: Int?, val isExpired: Boolean) {
    /** "4 días vencido", "Vence hoy", "1 día restante". */
    val label: String?
        get() {
            val days = daysRemaining ?: return null
            if (isExpired) return if (days == -1) "1 día vencido" else "${-days} días vencido"
            return when (days) {
                0 -> "Vence hoy"
                1 -> "1 día restante"
                else -> "$days días restantes"
            }
        }

    val color: Color
        get() = when {
            isExpired -> Color(0xFFFF6961)
            daysRemaining == 0 -> NovoColors.statusExpiring
            else -> Color(0xFFC7C7CC)
        }

    companion object {
        fun from(json: JSONObject) = ProgramDeadline(
            CoachDates.parse(json.str("due_date")),
            json.num("days_remaining")?.toInt(),
            json.bool("is_expired") ?: false,
        )
    }
}

/** One row of `novo-staff/v1/coach/programs`: an evaluation whose routine this coach builds. */
data class CoachProgram(
    val evaluationId: Int,
    val programId: Int?,
    val member: CoachMemberRef?,
    val goal: String,
    val evaluationDate: LocalDate?,
    val status: ProgramStatus,
    val measuredBy: String,
    val deadline: ProgramDeadline,
) {
    val memberName: String get() = member?.name ?: "Miembro"

    /** "Eval. 12 oct · Límite 16 oct" */
    val datesLabel: String
        get() = buildList {
            add("Eval. ${CoachDates.shortLabel(evaluationDate)}")
            if (status.isPending && deadline.dueDate != null) add("Límite ${CoachDates.shortLabel(deadline.dueDate)}")
            if (measuredBy.isNotEmpty()) add("Midió $measuredBy")
        }.joinToString(" · ")

    companion object {
        fun from(json: JSONObject): CoachProgram? {
            val evaluationId = json.num("evaluation_id")?.toInt() ?: return null
            return CoachProgram(
                evaluationId = evaluationId,
                programId = json.num("program_id")?.toInt(),
                member = CoachMemberRef.from(json.obj("member")),
                goal = json.str("goal").orEmpty().trim(),
                evaluationDate = CoachDates.parse(json.str("evaluation_date")),
                status = ProgramStatus.from(json.str("delivery_status")),
                measuredBy = json.str("measured_by").orEmpty(),
                deadline = ProgramDeadline.from(json),
            )
        }
    }
}

/** One of my bookings from `novo-staff/v1/coach/today`, with the client's note. Reception contacts the client, not the coach. */
data class CoachCita(
    val id: Int,
    val startMinute: Int,
    val endMinute: Int,
    val service: String,
    val status: String,
    val client: String,
    val notes: String,
    val member: CoachMemberRef?,
) {
    val name: String get() = member?.name ?: client.ifEmpty { service }
    val statusLabel: String
        get() = when (status) {
            "pending" -> "Por confirmar"
            "completed" -> "Completada"
            else -> "Confirmada"
        }
    val durationLabel: String get() = "${endMinute - startMinute} min"

    companion object {
        fun from(json: JSONObject): CoachCita? {
            val id = json.num("id")?.toInt() ?: return null
            val start = CoachDates.minutes(json.str("start")) ?: return null
            val end = CoachDates.minutes(json.str("end")) ?: return null
            return CoachCita(
                id, start, end,
                service = json.str("service") ?: "Cita",
                status = json.str("status").orEmpty(),
                client = json.str("client").orEmpty(),
                notes = json.str("notes").orEmpty(),
                member = CoachMemberRef.from(json.obj("member")),
            )
        }
    }
}

/** What the routine's author needs from the evaluation (`novo-staff/v1/programs/{id}`). */
data class EvaluationSummary(
    val goals: List<String>,
    val painAreas: List<String>,
    val restrictions: List<String>,
    val trainingLabel: String?,
) {
    companion object {
        /** Fillers the web form gets instead of leaving a field empty: "NO PRESENTA", "No indica", "Sin comentarios". */
        private val FILLER = Regex("""^(n/?a|-+|\.+|no|ninguna|ninguno|niega|nada|sin comentarios?|no (presenta|indica|refiere|aplica|tiene|registra|hay|hay comentarios))\.?$""")

        fun from(json: JSONObject?): EvaluationSummary? {
            json ?: return null
            fun text(key: String) = json.str(key).orEmpty().trim()
            fun strings(key: String) = json.arr(key)?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).ifEmpty { null } } }.orEmpty()
            val restrictions = buildList {
                val meaningful = { v: String -> v.isNotBlank() && !FILLER.matches(v.trim().lowercase()) }
                text("injuries").takeIf(meaningful)?.let { add(it) }
                text("cardiac").takeIf(meaningful)?.let { add("Condición cardíaca: $it") }
                text("surgeries").takeIf(meaningful)?.let { add("Cirugías: $it") }
                text("medications").takeIf(meaningful)?.let { add("Medicamentos: $it") }
                text("comments").takeIf(meaningful)?.let { add(it) }
            }
            val training = buildList {
                json.num("days")?.toInt()?.takeIf { it > 0 }?.let { add(if (it == 1) "1 día / semana" else "$it días / semana") }
                json.num("hours")?.takeIf { it > 0 }?.let {
                    val value = if (it % 1.0 == 0.0) it.toInt().toString() else it.toString().replace('.', ',')
                    add("máx. $value h / día")
                }
                json.str("lifestyle")?.let { add(it) }
            }
            return EvaluationSummary(strings("goals"), strings("pain_areas"), restrictions, training.joinToString(" · ").ifEmpty { null })
        }
    }
}

/** `novo-staff/v1/programs/{id}`: the program around its routine. */
data class ProgramDetail(
    val id: Int,
    val title: String,
    val status: ProgramStatus,
    val member: CoachMemberRef?,
    val evaluation: EvaluationSummary?,
    val evaluationDate: LocalDate?,
    val deadline: ProgramDeadline,
) {
    companion object {
        fun from(json: JSONObject, id: Int) = ProgramDetail(
            id = id,
            title = json.str("title") ?: "Programa",
            status = ProgramStatus.from(json.str("delivery_status")),
            member = CoachMemberRef.from(json.obj("member")),
            evaluation = EvaluationSummary.from(json.obj("evaluation")),
            evaluationDate = CoachDates.parse(json.obj("evaluation")?.str("date")),
            deadline = ProgramDeadline.from(json),
        )
    }
}

/** An exercise of the library (`novo-staff/v1/exercises`). */
data class LibraryExercise(val id: Int, val name: String, val muscle: String, val videoUrl: String?) {
    companion object {
        fun from(json: JSONObject): LibraryExercise? {
            val id = json.num("id")?.toInt() ?: return null
            return LibraryExercise(id, json.str("name").orEmpty(), json.str("muscle").orEmpty(), json.str("video_url"))
        }
    }
}

// ---- Routine: the web builder's JSON (`gym/v1/program-routines/{id}`) ----

data class RoutineExercise(
    val id: String = UUID.randomUUID().toString(),
    val exerciseId: Int?,
    val name: String,
    val muscle: String,
    val videoUrl: String?,
    /** "4x12 reps", "40 s"… */
    val objective: String,
    /** The builder's "break": "1 min", "30 sec"… */
    val rest: String,
    val instructions: String,
) {
    fun using(exercise: LibraryExercise) = copy(exerciseId = exercise.id, name = exercise.name, muscle = exercise.muscle, videoUrl = exercise.videoUrl)

    fun withNewId() = copy(id = UUID.randomUUID().toString())

    // A missing exercise is sent as the web builder sends it (0).
    fun payload(): JSONObject = JSONObject()
        .put("exercise_id", exerciseId ?: 0)
        .put("objective", objective)
        .put("break", rest)
        .put("instructions", instructions)

    companion object {
        fun from(json: JSONObject) = RoutineExercise(
            exerciseId = json.num("exercise_id")?.toInt(),
            name = json.str("name").orEmpty(),
            muscle = json.str("muscle").orEmpty(),
            videoUrl = json.str("video_url"),
            objective = json.str("objective").orEmpty(),
            rest = json.str("break").orEmpty(),
            instructions = json.str("instructions").orEmpty(),
        )

        fun from(exercise: LibraryExercise, rest: String) = RoutineExercise(
            exerciseId = exercise.id, name = exercise.name, muscle = exercise.muscle, videoUrl = exercise.videoUrl,
            objective = "", rest = rest, instructions = "",
        )
    }
}

sealed interface RoutineItem {
    val id: String

    data class Single(val exercise: RoutineExercise) : RoutineItem {
        override val id: String get() = exercise.id
    }

    data class Group(
        override val id: String = UUID.randomUUID().toString(),
        val name: String,
        /** Free text: "3 rondas", or a timer's instructions. */
        val rounds: String,
        val exercises: List<RoutineExercise>,
    ) : RoutineItem

    fun payload(): JSONObject = when (this) {
        is Single -> exercise.payload().put("type", "single")
        is Group -> JSONObject()
            .put("type", "group")
            .put("groupName", name)
            .put("rounds", rounds)
            .put("items", JSONArray(exercises.map { it.payload() }))
    }

    fun withNewIds(): RoutineItem = when (this) {
        is Single -> Single(exercise.withNewId())
        is Group -> Group(name = name, rounds = rounds, exercises = exercises.map { it.withNewId() })
    }

    companion object {
        fun from(json: JSONObject): RoutineItem? = when (json.str("type")) {
            "single" -> Single(RoutineExercise.from(json))
            "group" -> Group(
                name = json.str("groupName").orEmpty(),
                rounds = json.str("rounds").orEmpty(),
                exercises = json.arr("items")?.objects().orEmpty().map { RoutineExercise.from(it) },
            )
            else -> null
        }
    }
}

data class RoutineDay(
    val id: String = UUID.randomUUID().toString(),
    /** "Día 1" */
    val name: String,
    /** "Tren inferior", "Lunes y jueves"… */
    val label: String = "",
    val items: List<RoutineItem> = emptyList(),
) {
    val exerciseCount: Int get() = items.sumOf { if (it is RoutineItem.Group) it.exercises.size else 1 }

    fun payload(): JSONObject = JSONObject()
        .put("name", name)
        .put("label", label)
        .put("exercises", JSONArray(items.map { it.payload() }))

    fun withNewIds() = RoutineDay(name = name, label = label, items = items.map { it.withNewIds() })

    companion object {
        fun from(json: JSONObject) = RoutineDay(
            name = json.str("name").orEmpty(),
            label = json.str("label").orEmpty(),
            items = json.arr("exercises")?.objects().orEmpty().mapNotNull { RoutineItem.from(it) },
        )
    }
}

/** Calls shared by Hoy and Programas. */
object CoachApi {
    /** `?coach=` for an admin's "Ver como"; the server ignores it for anyone else. */
    fun coachQuery(coachId: Int?): Map<String, String> = coachId?.let { mapOf("coach" to it.toString()) }.orEmpty()

    suspend fun programs(api: WPApiClient, coachId: Int?): List<CoachProgram> =
        api.getObject("novo-staff/v1/coach/programs", coachQuery(coachId)).arr("items")?.objects().orEmpty().mapNotNull { CoachProgram.from(it) }

    /** An evaluation whose program wasn't created yet gets it now, as the web's "Crear rutina" does. */
    suspend fun createProgram(api: WPApiClient, evaluationId: Int): Int =
        api.postObject("member-evaluations/v1/evaluations/$evaluationId/program", JSONObject()).num("program_id")?.toInt()
            ?: throw WPApiException.Decoding()
}
