package com.novofitclub.dashboard.feature.sales.dashboard

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novofitclub.dashboard.NovoFitClubApp
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.cardBackground
import com.novofitclub.dashboard.core.util.Formatters
import com.novofitclub.dashboard.feature.sales.AutoShrinkText
import com.novofitclub.dashboard.feature.sales.CircleIconButton
import com.novofitclub.dashboard.feature.sales.RowDivider
import com.novofitclub.dashboard.feature.sales.SegmentedPicker
import com.novofitclub.dashboard.feature.sales.StatusPill
import com.novofitclub.dashboard.feature.sales.UnavailableState
import java.util.Locale
import kotlin.math.abs

private enum class TopSoldTab(val label: String) { Memberships("Membresías"), Products("Productos") }

private enum class ExpiringFilter(val label: String) { All("Todos"), Today("Vencen hoy") }

/**
 * Mirrors wp-admin's Sales → 📊 Dashboard screen, using
 * `sale-creator/v1/dashboard` (aggregate KPIs) and
 * `sale-creator/v1/dashboard/expiring` (paginated "expiring soon" list).
 * Pushed from the Ventas list's chart button. Must be hosted inside
 * ScopedViewModels so each visit reloads fresh numbers.
 */
@Composable
internal fun SalesDashboardScreen(app: NovoFitClubApp, onBack: () -> Unit) {
    val vm: SalesDashboardViewModel = viewModel { SalesDashboardViewModel(app.api) }
    var topSoldTab by rememberSaveable { mutableStateOf(TopSoldTab.Memberships) }
    var expiringFilter by rememberSaveable { mutableStateOf(ExpiringFilter.All) }

    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(NovoColors.background)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
        ) {
            CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", onBack)
            Text("DASHBOARD", style = NovoFonts.semibold(13).copy(color = NovoColors.textSecondary, letterSpacing = 0.4.sp))
        }

        val stats = vm.stats
        when {
            vm.isLoading && stats == null -> DashboardSkeleton()
            stats != null -> DashboardContent(
                stats = stats,
                vm = vm,
                topSoldTab = topSoldTab,
                onTopSoldTab = { topSoldTab = it },
                expiringFilter = expiringFilter,
                onExpiringFilter = { expiringFilter = it },
            )
            else -> UnavailableState(vm.errorMessage ?: "Intenta de nuevo.", onRetry = vm::load)
        }
    }
}

@Composable
private fun DashboardContent(
    stats: DashboardStats,
    vm: SalesDashboardViewModel,
    topSoldTab: TopSoldTab,
    onTopSoldTab: (TopSoldTab) -> Unit,
    expiringFilter: ExpiringFilter,
    onExpiringFilter: (ExpiringFilter) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        KpiGrid(stats)

        ChartCard("Ingresos mensuales (12 meses)", Icons.Filled.BarChart) {
            MonthBarChart(stats.monthlyRevenue12mo, NovoColors.accent, 180.dp)
        }
        ChartCard("Volumen de ventas (12 meses)", Icons.AutoMirrored.Filled.ShowChart) {
            MonthLineChart(stats.salesVolume12mo, NovoColors.accent, 180.dp)
        }
        ChartCard("Nuevos miembros (6 meses)", Icons.Filled.PersonAddAlt1) {
            MonthBarChart(stats.newMembersTrend6mo, NovoColors.statusNeutral, 160.dp)
        }

        PaymentMethodCard(stats)
        ClientsPerPlanCard(stats)
        RevenueSplitCard(stats)
        TopSoldCard(stats, topSoldTab, onTopSoldTab)
        ExpiringCard(vm, expiringFilter, onExpiringFilter)
    }
}

// region KPIs

@Composable
private fun KpiGrid(stats: DashboardStats) {
    val count = stats.revenue.todaySalesCount
    val percent = stats.revenue.monthVsLastMonthPercent
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiTile("Clientes totales", "${stats.clients.total}", Modifier.weight(1f))
            KpiTile("Activos", "${stats.clients.active}", Modifier.weight(1f), subtitle = "${stats.clients.activePercent}% del total")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiTile("Inactivos", "${stats.clients.inactive}", Modifier.weight(1f))
            KpiTile(
                "Ingresos hoy",
                Formatters.money(stats.revenue.today) ?: "—",
                Modifier.weight(1f),
                subtitle = "$count venta${if (count == 1) "" else "s"}",
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiTile(
                "Ingresos del mes",
                Formatters.money(stats.revenue.monthTotal) ?: "—",
                Modifier.weight(1f),
                subtitle = monthChangeLabel(percent),
                subtitleColor = if (percent < 0) NovoColors.statusExpired else NovoColors.statusActive,
            )
            KpiTile("Vencen en 7 días", "${stats.expiringIn7Days}", Modifier.weight(1f))
        }
    }
}

private fun monthChangeLabel(percent: Double): String {
    val arrow = if (percent < 0) "▼" else "▲"
    return "$arrow ${String.format(Locale.US, "%.1f", abs(percent))}% vs mes anterior"
}

/** Always renders a subtitle line (blank when none) so tiles in a row share a height. */
@Composable
private fun KpiTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color = NovoColors.textSecondary,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.cardBackground().padding(14.dp),
    ) {
        Text(title.uppercase(), style = NovoFonts.semibold(11).copy(color = NovoColors.textSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
        AutoShrinkText(value, maxSize = 20, minSize = 12)
        Text(
            subtitle ?: " ",
            style = NovoFonts.body(12).copy(color = if (subtitle == null) Color.Transparent else subtitleColor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// endregion

// region Card chrome

@Composable
private fun ChartCard(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().cardBackground().padding(20.dp),
    ) {
        CardHeader(title, icon)
        content()
    }
}

@Composable
private fun CardHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = NovoColors.accent, modifier = Modifier.size(15.dp))
        Text(title.uppercase(), style = NovoFonts.semibold(12).copy(color = NovoColors.textSecondary, letterSpacing = 0.3.sp))
    }
}

@Composable
private fun BreakdownCard(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth().cardBackground().padding(20.dp),
    ) {
        CardHeader(title, icon)
        content()
    }
}

/** A labeled proportional bar — shared by every breakdown section. */
@Composable
private fun BreakdownRow(label: String, value: String, fraction: Double, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = NovoFonts.medium(14), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(value, style = NovoFonts.semibold(13).copy(color = NovoColors.textSecondary))
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(NovoColors.separator)) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0.0, 1.0).toFloat())
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

// endregion

// region Breakdown cards

@Composable
private fun PaymentMethodCard(stats: DashboardStats) {
    val b = stats.revenueByPaymentMethod
    val total = maxOf(b.cash + b.card + b.sinpe, 1.0)
    BreakdownCard("Ingresos por método de pago", Icons.Filled.CreditCard) {
        BreakdownRow("Efectivo", Formatters.money(b.cash) ?: "—", b.cash / total, NovoColors.statusActive)
        BreakdownRow("Tarjeta", Formatters.money(b.card) ?: "—", b.card / total, NovoColors.accent)
        BreakdownRow("SINPE", Formatters.money(b.sinpe) ?: "—", b.sinpe / total, NovoColors.statusNeutral)
    }
}

@Composable
private fun ClientsPerPlanCard(stats: DashboardStats) {
    val maxTotal = maxOf(stats.clientsPerPlan.maxOfOrNull { it.total } ?: 1, 1)
    BreakdownCard("Clientes por plan", Icons.Filled.Sell) {
        stats.clientsPerPlan.forEachIndexed { index, plan ->
            BreakdownRow(
                label = plan.plan,
                value = "${plan.total} · ${plan.active} activos",
                fraction = plan.total.toDouble() / maxTotal,
                color = NovoColors.planPalette[index % NovoColors.planPalette.size],
            )
        }
    }
}

@Composable
private fun RevenueSplitCard(stats: DashboardStats) {
    val s = stats.revenueSplit
    val total = maxOf(s.memberships + s.products + s.services + s.ajustes, 1.0)
    BreakdownCard("Ingresos: membresías vs productos", Icons.Filled.Inventory2) {
        BreakdownRow("Membresías", Formatters.money(s.memberships) ?: "—", s.memberships / total, NovoColors.accent)
        BreakdownRow("Productos físicos", Formatters.money(s.products) ?: "—", s.products / total, NovoColors.statusNeutral)
        BreakdownRow("Servicios", Formatters.money(s.services) ?: "—", s.services / total, NovoColors.statusExpiring)
        if (s.ajustes > 0) BreakdownRow("Diferencias", Formatters.money(s.ajustes) ?: "—", s.ajustes / total, NovoColors.ajuste)
    }
}

@Composable
private fun TopSoldCard(stats: DashboardStats, tab: TopSoldTab, onTab: (TopSoldTab) -> Unit) {
    val items = if (tab == TopSoldTab.Memberships) stats.topSoldMemberships else stats.topSoldProducts
    BreakdownCard("Más vendido (12 meses)", Icons.Filled.EmojiEvents) {
        SegmentedPicker(TopSoldTab.entries, tab, { it.label }, onTab)
        if (items.isEmpty()) {
            Text("Sin datos todavía.", style = NovoFonts.body(13).copy(color = NovoColors.textSecondary))
        } else {
            Column {
                items.forEachIndexed { index, item ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                        Text("#${index + 1}", style = NovoFonts.semibold(13).copy(color = NovoColors.accent), modifier = Modifier.width(28.dp))
                        Text(item.name, style = NovoFonts.medium(14), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Text("${item.count} vendidos", style = NovoFonts.body(12).copy(color = NovoColors.textSecondary))
                    }
                    if (index < items.size - 1) RowDivider()
                }
            }
        }
    }
}

// endregion

// region Expiring soon

@Composable
private fun ExpiringCard(vm: SalesDashboardViewModel, filter: ExpiringFilter, onFilter: (ExpiringFilter) -> Unit) {
    // Sorted ascending by expiration server-side, so "today" rows are always at the front.
    val members = when (filter) {
        ExpiringFilter.All -> vm.expiringMembers
        ExpiringFilter.Today -> vm.expiringMembers.filter { it.daysLeft <= 0 }
    }
    BreakdownCard("Vencen pronto", Icons.Filled.Warning) {
        SegmentedPicker(ExpiringFilter.entries, filter, { it.label }, onFilter)
        if (members.isEmpty() && !vm.isLoading) {
            Text(
                if (filter == ExpiringFilter.Today) "Nadie vence hoy." else "Nadie vence próximamente.",
                style = NovoFonts.body(13).copy(color = NovoColors.textSecondary),
            )
        } else {
            Column {
                members.forEachIndexed { index, member ->
                    ExpiringRow(member)
                    if (index < members.size - 1) RowDivider()
                }
            }
            if (filter == ExpiringFilter.All && vm.canLoadMoreExpiring) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !vm.isLoadingMoreExpiring, onClick = vm::loadMoreExpiring)
                        .padding(8.dp),
                ) {
                    if (vm.isLoadingMoreExpiring) {
                        CircularProgressIndicator(color = NovoColors.accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    } else {
                        Text("Cargar más", style = NovoFonts.semibold(14).copy(color = NovoColors.accent))
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpiringRow(member: ExpiringMember) {
    val color = if (member.daysLeft <= 0) NovoColors.statusExpired else NovoColors.statusExpiring
    val label = if (member.daysLeft <= 0) "Hoy" else "${member.daysLeft} día${if (member.daysLeft == 1) "" else "s"}"
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(member.name, style = NovoFonts.medium(14), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(member.plan, style = NovoFonts.body(12).copy(color = NovoColors.textSecondary))
        }
        Spacer(Modifier.width(8.dp))
        StatusPill(label, color, horizontal = 10.dp, vertical = 4.dp)
    }
}

// endregion
