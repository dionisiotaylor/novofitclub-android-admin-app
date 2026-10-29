package com.novofitclub.dashboard.feature.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novofitclub.dashboard.NovoFitClubApp
import com.novofitclub.dashboard.core.net.ViewedCoach
import com.novofitclub.dashboard.core.net.WPApiClient
import com.novofitclub.dashboard.core.ui.EmptyState
import com.novofitclub.dashboard.core.ui.ErrorState
import com.novofitclub.dashboard.core.ui.LoadingState
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.SectionHeader
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Horario: my week, or every coach's (`gym-scheduler/v1/schedule`, scope
 * me / team). With an admin's "Ver como", that coach's employee id stands in
 * for "me". Other coaches' citas come without the client's name.
 */
class CoachScheduleViewModel(private val api: WPApiClient, private val viewing: ViewedCoach?) : ViewModel() {
    var mineOnly by mutableStateOf(true)
        private set
    var weekStart: LocalDate by mutableStateOf(CoachDates.today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
        private set
    var selectedDay: LocalDate by mutableStateOf(CoachDates.today())
    var schedule by mutableStateOf(CoachSchedule(null))
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    private var job: Job? = null

    init {
        load()
    }

    val days: List<LocalDate> get() = (0L until 7L).map { weekStart.plusDays(it) }

    fun showMine(mine: Boolean) {
        if (mine == mineOnly) return
        mineOnly = mine
        load()
    }

    fun moveWeek(offset: Long) {
        weekStart = weekStart.plusWeeks(offset)
        selectedDay = if (days.contains(CoachDates.today())) CoachDates.today() else weekStart
        load()
    }

    fun load() {
        // Tapping ‹ › quickly sends several loads; only the last one may land.
        job?.cancel()
        job = viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val query = mutableMapOf(
                    "start" to "$weekStart 00:00:00",
                    "end" to "${weekStart.plusDays(7)} 00:00:00",
                    "scope" to if (mineOnly) "me" else "team",
                )
                if (mineOnly && viewing != null) query["employee_id"] = viewing.id.toString()
                schedule = CoachSchedule(api.getObject("gym-scheduler/v1/schedule", query))
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Error desconocido."
                schedule = CoachSchedule(null)
            }
            isLoading = false
        }
    }

    data class Entry(val item: ScheduleItem, val employee: CoachSchedule.Employee?)

    /** The day's items in time order; with every coach, each carries whose it is. */
    fun entries(day: LocalDate): List<Entry> {
        val me = viewing?.id ?: schedule.me
        val employees = if (mineOnly) schedule.employees.filter { it.id == me } else schedule.employees
        return employees.flatMap { e -> schedule.items(e.id, day.toString()).map { Entry(it, if (mineOnly) null else e) } }
            .sortedWith(compareBy<Entry> { it.item.from }.thenBy { it.item.kind.ordinal })
    }
}

private val WEEKDAYS = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachScheduleScreen(app: NovoFitClubApp, viewing: ViewedCoach?) {
    val vm: CoachScheduleViewModel = viewModel(key = "coach-schedule-${viewing?.id}") { CoachScheduleViewModel(app.api, viewing) }
    val entries = vm.entries(vm.selectedDay)

    PullToRefreshBox(isRefreshing = vm.isLoading && entries.isNotEmpty(), onRefresh = { vm.load() }, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionHeader("Horario", "Tus horas de planta, citas, programación, comidas y demás actividades.")
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                Row(Modifier.fillMaxWidth().clip(CircleShape).background(NovoColors.card).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ScopeButton("Mi horario", vm.mineOnly, Modifier.weight(1f)) { vm.showMine(true) }
                    ScopeButton("Todos los coaches", !vm.mineOnly, Modifier.weight(1f)) { vm.showMine(false) }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    WeekButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Semana anterior") { vm.moveWeek(-1) }
                    Text(
                        "Semana del ${CoachDates.shortLabel(vm.weekStart)}",
                        style = NovoFonts.semibold(14),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    WeekButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Semana siguiente") { vm.moveWeek(1) }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    vm.days.forEachIndexed { index, day ->
                        val selected = day == vm.selectedDay
                        val isToday = day == CoachDates.today()
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                            modifier = Modifier
                                .weight(1f)
                                .height(62.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) NovoColors.accent else NovoColors.card)
                                .clickable { vm.selectedDay = day },
                        ) {
                            Text(WEEKDAYS[index], style = NovoFonts.semibold(11).copy(color = if (selected) Color.White else NovoColors.textSecondary))
                            Text("${day.dayOfMonth}", style = NovoFonts.title(17))
                            Box(Modifier.size(4.dp).clip(CircleShape).background(if (isToday) (if (selected) Color.White else NovoColors.accent) else Color.Transparent))
                        }
                    }
                }

                var title = CoachDates.longLabel(vm.selectedDay)
                if (vm.selectedDay == CoachDates.today()) title += " · Hoy"
                val citas = if (vm.mineOnly) entries.count { it.item.kind == ScheduleItem.Kind.Booking } else 0
                if (citas > 0) title += if (citas == 1) " · 1 cita" else " · $citas citas"
                Text(title.uppercase(), style = NovoFonts.semibold(13).copy(color = NovoColors.textSecondary, letterSpacing = 0.4.sp))

                val error = vm.errorMessage
                when {
                    vm.isLoading && entries.isEmpty() -> LoadingState()
                    error != null -> ErrorState(error, onRetry = { vm.load() })
                    entries.isEmpty() -> EmptyState(if (vm.mineOnly) "Libre: no tenés turnos ni citas este día." else "Ningún coach tiene turnos este día.")
                    else -> entries.forEach { EntryRow(it) }
                }
            }
        }
    }
}

@Composable
private fun ScopeButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.height(36.dp).clip(CircleShape).background(if (selected) NovoColors.accent else Color.Transparent).clickable(onClick = onClick),
    ) {
        Text(text, style = NovoFonts.semibold(14).copy(color = if (selected) Color.White else NovoColors.textSecondary))
    }
}

@Composable
private fun WeekButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, colors = IconButtonDefaults.iconButtonColors(containerColor = NovoColors.card), modifier = Modifier.size(32.dp)) {
        Icon(icon, contentDescription = label, tint = NovoColors.textPrimary)
    }
}

@Composable
private fun EntryRow(entry: CoachScheduleViewModel.Entry) {
    val item = entry.item
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp)).background(NovoColors.card).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(Modifier.width(76.dp)) {
            Text(CoachDates.clock(item.from), style = NovoFonts.semibold(14))
            Text(CoachDates.clock(item.to), style = NovoFonts.body(12).copy(color = NovoColors.textSecondary))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                entry.employee?.let {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(it.color))
                    Spacer(Modifier.width(6.dp))
                }
                val who = entry.employee?.name?.split(' ')?.take(1)?.joinToString(" ")
                Text(if (who != null) "$who · ${item.title}" else item.title, style = NovoFonts.semibold(15), maxLines = 1)
            }
            Text(item.subtitle, style = NovoFonts.body(12).copy(color = NovoColors.textSecondary), maxLines = 2)
        }
        Box(Modifier.width(4.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(item.color))
    }
}
