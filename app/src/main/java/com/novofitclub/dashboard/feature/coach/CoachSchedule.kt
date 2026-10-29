package com.novofitclub.dashboard.feature.coach

import androidx.compose.ui.graphics.Color
import com.novofitclub.dashboard.core.net.arr
import com.novofitclub.dashboard.core.net.num
import com.novofitclub.dashboard.core.net.obj
import com.novofitclub.dashboard.core.net.objects
import com.novofitclub.dashboard.core.net.str
import com.novofitclub.dashboard.core.ui.NovoColors
import org.json.JSONObject

/**
 * The coaches' schedule as wp-admin's Horario draws it
 * (`gym-scheduler/v1/schedule`): each coach's shifts (Planta, Elite,
 * Clase…), their Citas and their activities (comida, programación…). Same
 * day-building rules as the iOS app's StaffSchedule and the web's agenda.js.
 * Times are site-time "Y-m-d H:i:s", read as minutes straight from the string.
 */
class CoachSchedule(json: JSONObject?) {
    data class Employee(val id: Int, val name: String, val color: Color, val photoUrl: String? = null) {
        /** Employee colours can be pale — dark initials on those. */
        val isLight: Boolean get() = (0.299f * color.red + 0.587f * color.green + 0.114f * color.blue) * 255 > 150
    }

    val me: Int? = json?.num("me")?.toInt()?.takeIf { it > 0 }
    val employees: List<Employee> = json?.arr("employees")?.objects().orEmpty().mapNotNull {
        val id = it.num("id")?.toInt() ?: return@mapNotNull null
        Employee(id, it.str("name").orEmpty(), hexColor(it.str("color")) ?: NovoColors.textSecondary, it.str("avatar_url")?.ifEmpty { null })
    }.sortedBy { it.name }
    private val shifts = json?.arr("shifts")?.objects().orEmpty()
    private val activities = json?.arr("activities")?.objects().orEmpty()
    private val citas = json?.arr("citas")?.objects().orEmpty()

    /** Everything one coach has on [date] ("Y-m-d"), in time order. */
    fun items(employeeId: Int, date: String): List<ScheduleItem> {
        fun mine(list: List<JSONObject>) = list.filter {
            it.props().num("employee_id")?.toInt() == employeeId && it.str("start").orEmpty().startsWith(date)
        }
        val items = mutableListOf<ScheduleItem>()

        // Consecutive shifts of the same kind read as one block.
        val segments = mine(shifts)
            .filter { it.props().str("status") in setOf("scheduled", "completed") }
            .mapNotNull { s ->
                val from = CoachDates.minutes(s.str("start")) ?: return@mapNotNull null
                val to = CoachDates.minutes(s.str("end")) ?: return@mapNotNull null
                Triple(from, to, shiftKind(s.props()))
            }
            .sortedBy { it.first }
        val merged = mutableListOf<Triple<Int, Int, Pair<String, Color>>>()
        segments.forEach { s ->
            val last = merged.lastOrNull()
            if (last != null && last.third.first == s.third.first && last.second == s.first) {
                merged[merged.size - 1] = Triple(last.first, s.second, last.third)
            } else {
                merged.add(s)
            }
        }
        merged.forEachIndexed { i, s -> items.add(ScheduleItem("shift-$i", ScheduleItem.Kind.Shift, s.first, s.second, s.third.first, "Turno", s.third.second)) }

        mine(citas).forEachIndexed { i, e ->
            val from = CoachDates.minutes(e.str("start")) ?: return@forEachIndexed
            val to = CoachDates.minutes(e.str("end")) ?: return@forEachIndexed
            val p = e.props()
            val service = p.str("service_name") ?: "Citas"
            val color = hexColor(e.str("backgroundColor")) ?: NovoColors.statusNeutral
            if (p.str("kind") == "citas_booking") {
                val client = p.str("client_name").orEmpty()
                items.add(
                    ScheduleItem(
                        e.str("id") ?: "cita-$i", ScheduleItem.Kind.Booking, from, to,
                        if (client.isEmpty()) service else "$service: $client",
                        if (p.str("status") == "pending") "Cita por confirmar" else "Cita confirmada", color,
                    ),
                )
            } else {
                items.add(ScheduleItem(e.str("id") ?: "window-$i", ScheduleItem.Kind.Window, from, to, "Horario de $service", "Disponible para citas", color))
            }
        }

        mine(activities).forEachIndexed { i, a ->
            val from = CoachDates.minutes(a.str("start")) ?: return@forEachIndexed
            val to = CoachDates.minutes(a.str("end")) ?: return@forEachIndexed
            val p = a.props()
            val blocks = p.num("blocks_bookings")?.toInt() == 1
            val notes = p.str("notes").orEmpty().trim()
            items.add(
                ScheduleItem(
                    a.str("id") ?: "act-$i", ScheduleItem.Kind.Activity, from, to, p.str("type_name") ?: "Actividad",
                    notes.ifEmpty { if (blocks) "No se pueden reservar citas" else "Actividad" },
                    hexColor(a.str("backgroundColor")) ?: NovoColors.textSecondary,
                    blocksBookings = blocks,
                ),
            )
        }
        return items.sortedWith(compareBy<ScheduleItem> { it.from }.thenBy { if (it.kind == ScheduleItem.Kind.Shift) 0 else 1 })
    }

    /** One coach's [date]: their shifts merged into work stretches, why there's no shift, and every item. */
    fun day(employee: Employee, date: String): CoachDay {
        val shiftRows = shifts.filter {
            it.props().num("employee_id")?.toInt() == employee.id && it.str("start").orEmpty().startsWith(date)
        }
        // Shifts merged into stretches (6–7 regular + 7–8 elite + 8–1 regular = 6–1).
        val work = mutableListOf<IntRange>()
        shiftRows
            .filter { it.props().str("status") in setOf("scheduled", "completed") }
            .mapNotNull { s -> CoachDates.minutes(s.str("start"))?.let { from -> CoachDates.minutes(s.str("end"))?.let { to -> from..to } } }
            .sortedBy { it.first }
            .forEach { r ->
                val last = work.lastOrNull()
                if (last != null && r.first <= last.last) work[work.size - 1] = last.first..maxOf(last.last, r.last) else work.add(r)
            }
        val off = shiftRows.firstNotNullOfOrNull { OFF_LABELS[it.props().str("status")] }
        return CoachDay(employee, work, off, items(employee.id, date))
    }

    private fun JSONObject.props(): JSONObject = obj("extendedProps") ?: JSONObject()

    private companion object {
        val OFF_LABELS = mapOf("sick" to "Enfermo", "vacation" to "Vacaciones", "feriado" to "Feriado", "cancelled" to "Turno cancelado")
    }

    /** gym-scheduler's kinds of shift — the labels and colours of agenda.js. */
    private fun shiftKind(p: JSONObject): Pair<String, Color> {
        val type = p.str("shift_type") ?: "regular"
        val className = p.str("class_name")
        if (type == "special_class" && !className.isNullOrEmpty()) return className to (hexColor(p.str("class_color")) ?: Color(0xFF6D3FC4))
        return when (type) {
            "elite" -> "Elite" to Color(0xFFB8326F)
            "special_class" -> "Clase" to Color(0xFF6D3FC4)
            "secondary" -> "Secundario" to Color(0xFF4A5160)
            "receptionist" -> "Recepción" to Color(0xFF2457C5)
            "programming" -> "Programación" to Color(0xFFB4540A)
            else -> "Planta" to Color(0xFF0F6E66)
        }
    }
}

data class ScheduleItem(
    val id: String,
    val kind: Kind,
    val from: Int,
    val to: Int,
    val title: String,
    val subtitle: String,
    val color: Color,
    val blocksBookings: Boolean = false,
) {
    /** [priority]: lower wins when several overlap "now". */
    enum class Kind(val priority: Int) { Shift(3), Window(2), Booking(0), Activity(1) }

    fun contains(minute: Int) = minute >= from && minute < to

    /** "8:00 – 9:00 a. m." when both ends share a. m. / p. m.; both suffixes otherwise. */
    val timeLabel: String
        get() = if ((from < 720) == (to < 720)) {
            val h = from / 60
            "${if (h % 12 == 0) 12 else h % 12}:${(from % 60).toString().padStart(2, '0')} – ${CoachDates.clock(to)}"
        } else {
            "${CoachDates.clock(from)} – ${CoachDates.clock(to)}"
        }
}

/** One coach on one day. */
data class CoachDay(
    val employee: CoachSchedule.Employee,
    /** The shifts merged into stretches, in minutes. */
    val work: List<IntRange>,
    /** Why there's no shift (Vacaciones, Enfermo…), when that's the case. */
    val off: String?,
    val items: List<ScheduleItem>,
) {
    val isEmpty: Boolean get() = work.isEmpty() && off == null && items.isEmpty()

    /** "6:00 a. m. – 1:00 p. m., 5:00 – 7:00 p. m." or why there's no shift. */
    val shiftText: String
        get() = if (work.isEmpty()) off ?: "Sin turno" else work.joinToString(", ") {
            ScheduleItem("", ScheduleItem.Kind.Shift, it.first, it.last, "", "", Color.Transparent).timeLabel
        }

    /** What the coach should be doing at [minute]: a measurement before an activity, an activity before the Citas hours, those before the bare shift. */
    fun current(minute: Int): ScheduleItem? = items.filter { it.contains(minute) }.minByOrNull { it.kind.priority }

    /** The next thing that starts after [minute] (the shift itself not counted while it's running). */
    fun next(minute: Int): ScheduleItem? =
        items.firstOrNull { it.from > minute && it.kind != ScheduleItem.Kind.Shift } ?: items.firstOrNull { it.from > minute }
}
