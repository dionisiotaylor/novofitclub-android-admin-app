package com.novofitclub.dashboard.feature.sales

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.RemoveShoppingCart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.SaleThumbnail
import com.novofitclub.dashboard.core.ui.cardBackground
import com.novofitclub.dashboard.core.util.Formatters
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val chipInactiveText = Color(0xFFB0B0B0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SalesListScreen(
    vm: SalesViewModel,
    listState: LazyListState,
    onOpenSale: (String) -> Unit,
    onOpenDashboard: () -> Unit,
    onNewSale: () -> Unit,
) {
    val error = vm.errorMessage
    if (error != null && vm.rows.isEmpty() && !vm.isLoadingFirstPage) {
        UnavailableState(error, onRetry = vm::retry)
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(vm, onOpenDashboard)
            PullToRefreshBox(
                isRefreshing = vm.isRefreshing,
                onRefresh = { vm.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                SaleList(vm, listState, onOpenSale)
            }
        }

        // Floating "+" — new sale.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 16.dp)
                .shadow(12.dp, CircleShape, ambientColor = NovoColors.accent, spotColor = NovoColors.accent)
                .size(56.dp)
                .clip(CircleShape)
                .background(NovoColors.accent)
                .clickable(onClick = onNewSale),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Nueva venta", tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}

/** Title, search field and filter chips — kept outside the list so only the rows scroll. */
@Composable
private fun TopBar(vm: SalesViewModel, onOpenDashboard: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(NovoColors.background)
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Ventas", style = NovoFonts.heading(32))
                Text(summaryLabel(vm), style = NovoFonts.medium(14).copy(color = NovoColors.textSecondary))
            }
            Box(Modifier.padding(top = 6.dp)) {
                CircleIconButton(Icons.Filled.BarChart, "Dashboard", onOpenDashboard, size = 36.dp, iconSize = 18.dp)
            }
        }
        SearchField(
            value = vm.searchText,
            onValueChange = vm::updateSearch,
            placeholder = "Buscar por nombre, cédula o recibo",
            showsClear = true,
        )
        FilterChips(vm)
    }
}

private fun summaryLabel(vm: SalesViewModel): String {
    val month = vm.monthTotals
    if (month != null) {
        val count = month.count
        return "$count venta${if (count == 1) "" else "s"} este mes · ${Formatters.money(month.total) ?: "—"}"
    }
    return if (vm.isLoadingSummary) "Cargando resumen…" else "—"
}

@Composable
private fun FilterChips(vm: SalesViewModel) {
    var showingDatePicker by remember { mutableStateOf(false) }
    var showingReceptionists by remember { mutableStateOf(false) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        SalesFilterChip("Todas", isActive = vm.selectedDate == null, onClick = { vm.setDate(null) })

        // Any day, not just today — neutral until a date is applied (crimson once it is).
        SalesFilterChip(
            title = chipDateFormat().format(vm.selectedDate ?: Date()),
            isActive = vm.selectedDate != null,
            onClick = { showingDatePicker = true },
        )

        Box {
            SalesFilterChip(
                title = vm.selectedReceptionist?.name ?: "Recepcionista",
                isActive = vm.selectedReceptionist != null,
                showsCaret = true,
                onClick = { showingReceptionists = true },
            )
            DropdownMenu(
                expanded = showingReceptionists,
                onDismissRequest = { showingReceptionists = false },
                containerColor = NovoColors.card,
            ) {
                DropdownMenuItem(
                    text = { Text("Todas", style = NovoFonts.medium(15)) },
                    onClick = {
                        showingReceptionists = false
                        vm.setReceptionist(null)
                    },
                )
                if (vm.receptionists.isNotEmpty()) {
                    HorizontalDivider(color = NovoColors.separator)
                    vm.receptionists.forEach { receptionist ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    receptionist.name,
                                    style = NovoFonts.medium(15).copy(
                                        color = if (receptionist.id == vm.selectedReceptionist?.id) NovoColors.accent else NovoColors.textPrimary,
                                    ),
                                )
                            },
                            onClick = {
                                showingReceptionists = false
                                vm.setReceptionist(receptionist)
                            },
                        )
                    }
                }
            }
        }
    }

    if (showingDatePicker) {
        SaleDatePickerDialog(
            initial = vm.selectedDate ?: Date(),
            onDismiss = { showingDatePicker = false },
            onConfirm = { date ->
                showingDatePicker = false
                vm.setDate(date)
            },
        )
    }
}

private fun chipDateFormat() = SimpleDateFormat("d MMM yyyy", Locale("es"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaleDatePickerDialog(initial: Date, onDismiss: () -> Unit, onConfirm: (Date) -> Unit) {
    // Material's DatePicker works in UTC midnights; convert to/from the local day.
    val state = rememberDatePickerState(initialSelectedDateMillis = localDayToUtcMillis(initial))
    val colors = DatePickerDefaults.colors(
        containerColor = NovoColors.card,
        selectedDayContainerColor = NovoColors.accent,
        todayDateBorderColor = NovoColors.accent,
        todayContentColor = NovoColors.accent,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = colors,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis == null) onDismiss() else onConfirm(utcMillisToLocalDay(millis))
            }) { Text("Aplicar", style = NovoFonts.semibold(15).copy(color = NovoColors.accent)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", style = NovoFonts.body(15).copy(color = NovoColors.accent))
            }
        },
    ) {
        DatePicker(state = state, colors = colors)
    }
}

private fun localDayToUtcMillis(date: Date): Long {
    val local = Calendar.getInstance().apply { time = date }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun utcMillisToLocalDay(millis: Long): Date {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
    }.time
}

@Composable
private fun SalesFilterChip(title: String, isActive: Boolean, onClick: () -> Unit, showsCaret: Boolean = false) {
    val textColor = if (isActive) Color.White else chipInactiveText
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .height(34.dp)
            .capsule(
                background = if (isActive) NovoColors.accent else NovoColors.card,
                border = if (isActive) NovoColors.accent else NovoColors.separator,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
    ) {
        Text(title, style = NovoFonts.semibold(13).copy(color = textColor), maxLines = 1)
        if (showsCaret) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = textColor, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun SaleList(vm: SalesViewModel, listState: LazyListState, onOpenSale: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    val rows = vm.filteredRows

    // Infinite scroll: once the last loaded sale is on screen, fetch the next
    // page. Keyed on that row's id so each page is requested once, and only
    // emitted while no fetch is running so it re-fires when one finishes.
    LaunchedEffect(listState, vm) {
        snapshotFlow {
            val lastId = vm.rows.lastOrNull()?.id
            val visible = lastId != null && listState.layoutInfo.visibleItemsInfo.any { it.key == lastId }
            if (visible && !vm.isFetchingPage) lastId else null
        }
            .distinctUntilChanged()
            .filterNotNull()
            .collect { vm.loadNextPage() }
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 96.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "summary") { SummaryTiles(vm) }

        when {
            vm.isLoadingFirstPage && vm.rows.isEmpty() -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NovoColors.accent)
                }
            }
            rows.isEmpty() -> item(key = "empty") { EmptySales(hasRows = vm.isSearching) }
            else -> {
                items(rows, key = { it.id }) { row ->
                    SaleRow(row, onClick = {
                        focusManager.clearFocus()
                        onOpenSale(row.id)
                    })
                }
                if (vm.isLoadingMore) {
                    item(key = "more") {
                        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = NovoColors.accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryTiles(vm: SalesViewModel) {
    // "Hoy" / "Este mes" with no date filter; relabelled to the picked day
    // and "Hasta <día>" once one is applied, since the numbers follow it.
    val date = vm.selectedDate
    val dayTitle = date?.let { Formatters.shortDate(it) } ?: "Hoy"
    val monthTitle = date?.let { "Hasta ${Formatters.shortDate(it)}" } ?: "Este mes"
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        SummaryTile(dayTitle, vm.todayTotals, vm.isLoadingSummary, Modifier.weight(1f))
        SummaryTile(monthTitle, vm.monthTotals, vm.isLoadingSummary, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryTile(title: String, totals: SalesTotals?, isLoading: Boolean, modifier: Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.cardBackground().padding(14.dp),
    ) {
        Text(title.uppercase(), style = NovoFonts.semibold(11).copy(color = NovoColors.textSecondary))
        when {
            totals != null -> {
                AutoShrinkText(Formatters.money(totals.total) ?: "—", maxSize = 20, minSize = 14)
                Text(
                    "${totals.count} venta${if (totals.count == 1) "" else "s"}",
                    style = NovoFonts.body(12).copy(color = NovoColors.textSecondary),
                )
            }
            isLoading -> Box(Modifier.height(32.dp), contentAlignment = Alignment.CenterStart) {
                CircularProgressIndicator(color = NovoColors.accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            }
            else -> Text("—", style = NovoFonts.heading(20).copy(color = NovoColors.textSecondary))
        }
    }
}

@Composable
private fun EmptySales(hasRows: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
    ) {
        Icon(Icons.Outlined.RemoveShoppingCart, contentDescription = null, tint = NovoColors.textSecondary, modifier = Modifier.size(34.dp))
        Text(if (hasRows) "Sin resultados" else "Sin ventas", style = NovoFonts.semibold(15))
        if (hasRows) {
            Text(
                "No encontramos ventas que coincidan con tu búsqueda.",
                style = NovoFonts.body(13).copy(color = NovoColors.textSecondary),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Card row for the Ventas list: thumbnail of what was sold, product/date
 * subtitle, and the sale total in accent crimson.
 */
@Composable
private fun SaleRow(row: SaleSummaryRow, onClick: () -> Unit) {
    val subtitle = listOfNotNull(row.buyer, row.date).joinToString(" · ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .saleRowBackground(row.isCanje, row.hasAjuste)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        SaleThumbnail(row.imageUrl)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(row.product ?: row.receipt ?: "Venta", style = NovoFonts.semibold(16), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = NovoFonts.body(13).copy(color = NovoColors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SaleTagsView(SaleTag.of(row.isCanje, row.hasAjuste))
            Formatters.money(row.amount)?.let {
                Text(it, style = NovoFonts.semibold(15).copy(color = if (row.isCanje) NovoColors.canje else NovoColors.accent))
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = NovoColors.textSecondary.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp),
        )
    }
}
