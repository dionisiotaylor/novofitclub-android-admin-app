package com.novofitclub.dashboard.feature.cash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novofitclub.dashboard.core.net.WPApiClient
import com.novofitclub.dashboard.core.ui.LoadingState
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.SectionHeader
import com.novofitclub.dashboard.core.ui.cardBackground
import com.novofitclub.dashboard.core.util.Formatters
import com.novofitclub.dashboard.feature.cash.common.CardDivider
import com.novofitclub.dashboard.feature.cash.common.ContentUnavailable
import com.novofitclub.dashboard.feature.cash.common.LoadFailed
import com.novofitclub.dashboard.feature.cash.common.pluralCount
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** One calendar day of register closes (several receptionists can close the same day). */
class CierresDayGroup(val day: Date, val cierres: List<RegisterBalance>) {
    val totalAmount = cierres.sumOf { it.totalAmount }
    val totalCash = cierres.sumOf { it.cashAmount }
    val totalSinpe = cierres.sumOf { it.sinpeAmount }
    val totalCard = cierres.sumOf { it.cardAmount }
}

/** Cierres: `wp/v2/register_balance`, one calendar month at a time. */
class CierresViewModel(private val client: WPApiClient) : ViewModel() {
    data class State(
        val cierres: List<RegisterBalance> = emptyList(),
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val errorMessage: String? = null,
        val selectedMonth: Date = Date(),
        /** Expanded day cards, keyed by the day's start-of-day epoch millis. */
        val expandedDays: Set<Long> = emptySet(),
    ) {
        val dayGroups: List<CierresDayGroup>
            get() = cierres
                .groupBy { startOfDay(it.date ?: DISTANT_PAST) }
                .map { (day, list) -> CierresDayGroup(Date(day), list.sortedByDescending { it.date?.time ?: Long.MIN_VALUE }) }
                .sortedByDescending { it.day.time }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var job: Job? = null

    /** Fresh state for a new push from the hub (iOS builds a new view model each time). */
    fun reset() {
        job?.cancel()
        _state.value = State()
    }

    /**
     * Initial/appearance load: keeps whatever days are expanded, but opens
     * the most recent one when nothing is expanded yet.
     */
    fun load(refreshing: Boolean = false, expandMostRecent: Boolean = !refreshing) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, isRefreshing = refreshing, errorMessage = null) }
            try {
                val (after, before) = monthRange(_state.value.selectedMonth)
                val raw = client.getArray(
                    "wp/v2/register_balance",
                    mapOf(
                        "per_page" to "100",
                        "orderby" to "date",
                        "order" to "desc",
                        "after" to after,
                        "before" to before,
                    ),
                )
                _state.update { it.copy(cierres = raw.mapNotNull(RegisterBalance::from)) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.localizedMessage ?: e.toString()) }
            }
            _state.update { s ->
                val mostRecent = s.dayGroups.firstOrNull()?.day?.time
                val expanded = if (expandMostRecent && s.expandedDays.isEmpty() && mostRecent != null) {
                    setOf(mostRecent)
                } else {
                    s.expandedDays
                }
                s.copy(isLoading = false, isRefreshing = false, expandedDays = expanded)
            }
        }
    }

    fun changeMonth(by: Int) {
        val cal = Calendar.getInstance().apply {
            time = _state.value.selectedMonth
            add(Calendar.MONTH, by)
        }
        _state.update { it.copy(selectedMonth = cal.time, expandedDays = emptySet()) }
        load()
    }

    fun toggle(day: Date) {
        _state.update {
            val key = day.time
            it.copy(expandedDays = if (key in it.expandedDays) it.expandedDays - key else it.expandedDays + key)
        }
    }

    companion object {
        /** Stand-in for a close with no parseable date (iOS `Date.distantPast`). */
        private val DISTANT_PAST = Date(-62135596800000L)

        private fun startOfDay(date: Date): Long = Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        /**
         * Naive ISO8601, no timezone suffix — matches how the `wp/v2` dates
         * already come back unqualified.
         */
        private fun monthRange(month: Date): Pair<String, String> {
            val cal = Calendar.getInstance().apply {
                time = month
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = cal.time
            cal.add(Calendar.MONTH, 1)
            val next = cal.time
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            return fmt.format(start) to fmt.format(next)
        }
    }
}

/**
 * Register close history ("Register Balances" in wp-admin) — grouped by
 * calendar day, each day expandable to reveal its per-person rows with the
 * Total/SINPE/Efectivo/Tarjeta breakdown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CierresScreen(viewModel: CierresViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { viewModel.load() }

    Column(Modifier.fillMaxSize().background(NovoColors.background)) {
        SectionHeader(
            title = "Cierres",
            subtitle = if (state.cierres.isEmpty()) null else pluralCount(state.cierres.size, "cierre"),
            breadcrumb = "CAJA",
            onBack = onBack,
        )
        MonthSwitcher(
            title = Formatters.monthTitle(state.selectedMonth),
            onPrevious = { viewModel.changeMonth(-1) },
            onNext = { viewModel.changeMonth(1) },
        )
        val base = Modifier.fillMaxSize()
        when {
            state.isLoading && state.cierres.isEmpty() -> LoadingState(base)
            state.errorMessage != null && state.cierres.isEmpty() ->
                LoadFailed(state.errorMessage!!, { viewModel.load() }, base)
            state.cierres.isEmpty() -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.load(refreshing = true) },
                modifier = base,
            ) {
                ContentUnavailable("Sin cierres", Icons.Outlined.Inbox)
            }
            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.load(refreshing = true) },
                modifier = base,
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.dayGroups, key = { it.day.time }) { group ->
                        DayCard(
                            group = group,
                            isExpanded = group.day.time in state.expandedDays,
                            onToggle = { viewModel.toggle(group.day) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthSwitcher(title: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
    ) {
        CircleIconButton(Icons.Filled.ChevronLeft, "Mes anterior", onPrevious)
        Spacer(Modifier.weight(1f))
        Text(title, style = NovoFonts.semibold(15))
        Spacer(Modifier.weight(1f))
        CircleIconButton(Icons.Filled.ChevronRight, "Mes siguiente", onNext)
    }
}

@Composable
private fun CircleIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(containerColor = NovoColors.card),
        modifier = Modifier.size(32.dp),
    ) {
        Icon(icon, contentDescription = description, tint = NovoColors.textPrimary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun DayCard(group: CierresDayGroup, isExpanded: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (isExpanded) 90f else 0f, tween(200), label = "chevron")
    Column(Modifier.fillMaxWidth().cardBackground()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(Formatters.dayTitle(group.day), style = NovoFonts.semibold(16))
                    Text(
                        pluralCount(group.cierres.size, "cierre"),
                        style = NovoFonts.body(13).copy(color = NovoColors.textSecondary),
                    )
                }
                Text(
                    Formatters.money(group.totalAmount) ?: "—",
                    style = NovoFonts.semibold(16).copy(color = NovoColors.accent),
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (isExpanded) "Contraer" else "Expandir",
                    tint = NovoColors.textSecondary.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp).rotate(rotation),
                )
            }
            BreakdownRow(cash = group.totalCash, sinpe = group.totalSinpe, card = group.totalCard)
        }
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(tween(200), expandFrom = Alignment.Top) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(200), shrinkTowards = Alignment.Top) + fadeOut(tween(200)),
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                group.cierres.forEachIndexed { index, cierre ->
                    if (index > 0) CardDivider()
                    PersonRow(cierre)
                }
            }
        }
    }
}

@Composable
private fun PersonRow(cierre: RegisterBalance) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                Icons.Filled.AccountCircle,
                contentDescription = null,
                tint = NovoColors.textSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(26.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(cierre.receptionistName, style = NovoFonts.medium(14))
                cierre.date?.let {
                    Text(Formatters.timeOnly(it), style = NovoFonts.body(12).copy(color = NovoColors.textSecondary))
                }
            }
            Text(Formatters.money(cierre.totalAmount) ?: "—", style = NovoFonts.semibold(14))
        }
        BreakdownRow(cash = cierre.cashAmount, sinpe = cierre.sinpeAmount, card = cierre.cardAmount)
    }
}

/**
 * Efectivo/SINPE/Tarjeta as icon+amount: a bill for cash, a phone for SINPE
 * (Costa Rica's phone-linked transfer system), a card for card payments —
 * each tinted, spread across the full row width.
 */
@Composable
private fun BreakdownRow(cash: Double, sinpe: Double, card: Double) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BreakdownItem(Icons.Filled.Payments, "Efectivo", NovoColors.statusActive, cash)
        Spacer(Modifier.weight(1f))
        BreakdownItem(Icons.Filled.PhoneIphone, "SINPE", NovoColors.statusNeutral, sinpe)
        Spacer(Modifier.weight(1f))
        BreakdownItem(Icons.Filled.CreditCard, "Tarjeta", NovoColors.statusExpiring, card)
    }
}

@Composable
private fun BreakdownItem(icon: ImageVector, description: String, tint: Color, amount: Double) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(12.dp))
        Text(Formatters.money(amount) ?: "—", style = NovoFonts.body(11).copy(color = NovoColors.textSecondary))
    }
}
