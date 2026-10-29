package com.novofitclub.dashboard.feature.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novofitclub.dashboard.NovoFitClubApp
import com.novofitclub.dashboard.core.net.WPApiClient
import com.novofitclub.dashboard.core.ui.ErrorState
import com.novofitclub.dashboard.core.ui.LoadingState
import com.novofitclub.dashboard.core.ui.MemberAvatar
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.SectionHeader
import com.novofitclub.dashboard.core.ui.cardBackground
import com.novofitclub.dashboard.core.ui.initials
import com.novofitclub.dashboard.feature.coach.CoachDates
import com.novofitclub.dashboard.feature.coach.CoachDay
import com.novofitclub.dashboard.feature.coach.CoachSchedule
import com.novofitclub.dashboard.feature.coach.ScheduleItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Empleados (admins only): one day of the coaches' schedule —
 * `gym-scheduler/v1/schedule?scope=team`, the same call as wp-admin's
 * Horario "Todos los coaches". An admin gets the clients' names on the Citas
 * bookings; the endpoint hides them from everyone else.
 */
class EmployeesViewModel(private val api: WPApiClient) : ViewModel() {
    var date: LocalDate by mutableStateOf(CoachDates.today())
        private set
    /** null = every coach. */
    var coachFilter by mutableStateOf<Int?>(null)
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

    val isToday: Boolean get() = date == CoachDates.today()

    /** "Hoy", "Mañana", "Ayer" or "Martes 28 de octubre". */
    val dateTitle: String
        get() = when (date) {
            CoachDates.today() -> "Hoy"
            CoachDates.today().plusDays(1) -> "Mañana"
            CoachDates.today().minusDays(1) -> "Ayer"
            else -> CoachDates.longLabel(date)
        }

    val dateSubtitle: String get() = CoachDates.longLabel(date)

    /** Every coach's day, those with nothing that day last. */
    val days: List<CoachDay>
        get() {
            val all = schedule.employees.map { schedule.day(it, date.toString()) }
            val shown = coachFilter?.let { id -> all.filter { it.employee.id == id } } ?: all
            return shown.filterNot { it.isEmpty } + shown.filter { it.isEmpty }
        }

    fun shift(days: Long) {
        date = date.plusDays(days)
        load()
    }

    fun goToToday() {
        if (isToday) return
        date = CoachDates.today()
        load()
    }

    fun load() {
        // Tapping ‹ › quickly sends several loads; only the last one may land.
        job?.cancel()
        job = viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                schedule = CoachSchedule(
                    api.getObject(
                        "gym-scheduler/v1/schedule",
                        mapOf("start" to "$date 00:00:00", "end" to "${date.plusDays(1)} 00:00:00", "scope" to "team"),
                    ),
                )
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Error desconocido."
                schedule = CoachSchedule(null)
            }
            isLoading = false
        }
    }
}

/** "Esteban Calderon Bonilla" → "Esteban Calderon" — enough to tell people apart in a chip. */
private fun shortName(name: String) = name.split(" ").take(2).joinToString(" ")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeesTab(app: NovoFitClubApp) {
    val vm: EmployeesViewModel = viewModel(key = "employees") { EmployeesViewModel(app.api) }
    // Redrawn every minute, so "Ahora" and the dimmed past rows keep up.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            tick++
        }
    }
    val minute = remember(tick) { CoachDates.nowMinute() }
    val now = if (vm.isToday) minute else null

    PullToRefreshBox(isRefreshing = vm.isLoading && vm.schedule.employees.isNotEmpty(), onRefresh = { vm.load() }, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionHeader("Empleados", "Horario de los coaches")
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                DateBar(vm)
                CoachChips(vm)
                val days = vm.days
                val error = vm.errorMessage
                when {
                    vm.isLoading && vm.schedule.employees.isEmpty() -> LoadingState()
                    error != null -> ErrorState(error, onRetry = { vm.load() })
                    days.all { it.isEmpty } -> Text(
                        "Ningún coach tiene turno ni nada programado este día.",
                        style = NovoFonts.medium(14).copy(color = NovoColors.textSecondary),
                        modifier = Modifier.fillMaxWidth().cardBackground().padding(16.dp),
                    )
                    else -> {
                        if (now != null && vm.coachFilter == null) NowCard(days, now)
                        days.forEach { CoachDayCard(it, now) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateBar(vm: EmployeesViewModel) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().cardBackground().padding(8.dp),
    ) {
        DayButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Día anterior") { vm.shift(-1) }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Text(vm.dateTitle, style = NovoFonts.title(17))
            if (vm.dateTitle != vm.dateSubtitle) Text(vm.dateSubtitle, style = NovoFonts.medium(12).copy(color = NovoColors.textSecondary))
        }
        if (!vm.isToday) {
            Text(
                "Hoy",
                style = NovoFonts.semibold(13).copy(color = Color.White),
                modifier = Modifier.clip(CircleShape).background(NovoColors.accent).clickable { vm.goToToday() }.padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
        DayButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Día siguiente") { vm.shift(1) }
    }
}

@Composable
private fun DayButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.06f)), modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = label, tint = NovoColors.textPrimary)
    }
}

@Composable
private fun CoachChips(vm: EmployeesViewModel) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Chip("Todos", null, vm.coachFilter == null) { vm.coachFilter = null } }
        items(vm.schedule.employees, key = { it.id }) { employee ->
            Chip(shortName(employee.name), employee.color, vm.coachFilter == employee.id) {
                vm.coachFilter = if (vm.coachFilter == employee.id) null else employee.id
            }
        }
    }
}

@Composable
private fun Chip(title: String, color: Color?, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(if (selected) NovoColors.accent else NovoColors.card)
            .border(1.dp, if (selected) NovoColors.accent else NovoColors.separator, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
    ) {
        if (color != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
        }
        Text(title, style = NovoFonts.semibold(13).copy(color = if (selected) Color.White else Color(0xFFB0B0B0)))
    }
}

@Composable
private fun CoachAvatar(employee: CoachSchedule.Employee, size: Dp) {
    MemberAvatar(initials(employee.name), employee.color, employee.photoUrl, size, if (employee.isLight) Color(0xFF16181D) else Color.White)
}

/** Today only: one line per coach with what they should be doing right now. */
@Composable
private fun NowCard(days: List<CoachDay>, now: Int) {
    Column(Modifier.fillMaxWidth().cardBackground().padding(bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(NovoColors.accent))
            Spacer(Modifier.width(8.dp))
            Text("Ahora · ${CoachDates.clock(now)}", style = NovoFonts.semibold(13).copy(color = NovoColors.textSecondary))
        }
        days.filterNot { it.isEmpty }.forEachIndexed { index, day ->
            if (index > 0) HorizontalDivider(color = NovoColors.separator, modifier = Modifier.padding(start = 64.dp))
            val current = day.current(now)
            val next = day.next(now)
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                CoachAvatar(day.employee, 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(shortName(day.employee.name), style = NovoFonts.semibold(15))
                    if (current != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(4.dp).height(14.dp).clip(RoundedCornerShape(2.dp)).background(current.color))
                            Spacer(Modifier.width(6.dp))
                            Text(current.title, style = NovoFonts.medium(14), maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(6.dp))
                            Text("hasta ${CoachDates.clock(current.to)}", style = NovoFonts.body(12).copy(color = NovoColors.textSecondary), maxLines = 1)
                        }
                    } else {
                        Text(day.off ?: "Fuera de turno", style = NovoFonts.medium(14).copy(color = NovoColors.textSecondary))
                    }
                    next?.let {
                        Text("Después: ${it.title} · ${CoachDates.clock(it.from)}", style = NovoFonts.body(12).copy(color = NovoColors.textSecondary), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun CoachDayCard(day: CoachDay, now: Int?) {
    Column(Modifier.fillMaxWidth().cardBackground().alpha(if (day.isEmpty) 0.6f else 1f)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            CoachAvatar(day.employee, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(day.employee.name, style = NovoFonts.semibold(16), maxLines = 1)
                Text(day.shiftText, style = NovoFonts.body(13).copy(color = NovoColors.textSecondary), maxLines = 2)
            }
        }
        if (day.items.isEmpty()) {
            Text(
                if (day.off == null) "Nada programado este día." else "Sin turno este día.",
                style = NovoFonts.body(13).copy(color = NovoColors.textSecondary),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
        } else {
            // Only the one thing the coach should be doing now is marked "Ahora" — not also the shift or Citas hours it sits in.
            val currentId = now?.let { day.current(it)?.id }
            Column(Modifier.padding(bottom = 8.dp)) {
                day.items.forEach { item ->
                    val past = now != null && item.to <= now
                    ItemRow(item, isCurrent = item.id == currentId && !past, isPast = past)
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: ScheduleItem, isCurrent: Boolean, isPast: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(if (isCurrent) NovoColors.accent.copy(alpha = 0.10f) else Color.Transparent)
            .alpha(if (isPast) 0.45f else 1f)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(item.timeLabel, style = NovoFonts.medium(12).copy(color = NovoColors.textSecondary), modifier = Modifier.width(96.dp).padding(top = 2.dp))
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(item.color.copy(alpha = if (item.kind == ScheduleItem.Kind.Window || item.kind == ScheduleItem.Kind.Shift) 0.55f else 1f)),
        )
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, style = NovoFonts.semibold(14), maxLines = 2, modifier = Modifier.weight(1f, fill = false))
                if (item.blocksBookings) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Lock, contentDescription = "Bloquea citas", tint = NovoColors.textSecondary, modifier = Modifier.size(11.dp))
                }
                if (isCurrent) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Ahora",
                        style = NovoFonts.semibold(10).copy(color = Color.White),
                        modifier = Modifier.clip(CircleShape).background(NovoColors.accent).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(item.subtitle, style = NovoFonts.body(12).copy(color = NovoColors.textSecondary), maxLines = 2)
        }
    }
}
