package com.novofitclub.dashboard.feature.reception

import androidx.activity.compose.BackHandler
import com.novofitclub.dashboard.feature.sales.SearchField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novofitclub.dashboard.NovoFitClubApp
import com.novofitclub.dashboard.core.net.WPApiClient
import com.novofitclub.dashboard.core.net.arr
import com.novofitclub.dashboard.core.net.bool
import com.novofitclub.dashboard.core.net.num
import com.novofitclub.dashboard.core.net.obj
import com.novofitclub.dashboard.core.net.objects
import com.novofitclub.dashboard.core.ui.EmptyState
import com.novofitclub.dashboard.core.ui.ErrorState
import com.novofitclub.dashboard.core.ui.LoadingState
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.SectionHeader
import com.novofitclub.dashboard.core.ui.SectionLabel
import com.novofitclub.dashboard.core.ui.cardBackground
import com.novofitclub.dashboard.core.ui.tintedCard
import com.novofitclub.dashboard.core.util.Formatters
import com.novofitclub.dashboard.feature.sales.OverlayLayer
import com.novofitclub.dashboard.feature.sales.SaleTag
import com.novofitclub.dashboard.feature.sales.SaleTagsView
import com.novofitclub.dashboard.feature.sales.ScopedViewModels
import com.novofitclub.dashboard.feature.sales.edit.SaleEditScreen
import com.novofitclub.dashboard.feature.sales.saleRowBackground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The receptionist's Ventas tab: her own sales for one day and that day's
 * total — never the month, never anyone else's (both enforced by
 * `novo-staff/v1/sales`, which ignores any other author it's asked for).
 */
class MySalesViewModel(private val api: WPApiClient) : ViewModel() {
    var day: LocalDate by mutableStateOf(LocalDate.now())
        private set
    var sales by mutableStateOf<List<ReceptionSale>>(emptyList())
        private set
    var totals by mutableStateOf(SaleAmounts())
        private set
    var count by mutableIntStateOf(0)
        private set
    var closed by mutableStateOf<Boolean?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var selected by mutableStateOf<ReceptionSale?>(null)
    var showingNewSale by mutableStateOf(false)
    private var job: Job? = null

    /** Search over all her sales (any day), from `novo-staff/v1/sales/list`. */
    var search by mutableStateOf("")
        private set
    var results by mutableStateOf<List<ReceptionSale>>(emptyList())
        private set
    var isSearchLoading by mutableStateOf(false)
        private set
    var searchError by mutableStateOf<String?>(null)
        private set
    private var searchPage = 1
    private var searchHasMore = false
    private var searchJob: Job? = null

    /** The search sent to the server: 2+ characters, else the day view. */
    private val activeSearch: String get() = search.trim().takeIf { it.length >= 2 }.orEmpty()
    val isSearching: Boolean get() = activeSearch.isNotEmpty()

    init {
        load()
    }

    val isToday: Boolean get() = day == LocalDate.now()

    fun select(day: LocalDate) {
        this.day = day
        load()
    }

    fun updateSearch(text: String) {
        val before = activeSearch
        search = text
        if (activeSearch == before) return
        searchJob?.cancel()
        results = emptyList()
        searchError = null
        if (activeSearch.isEmpty()) {
            isSearchLoading = false
            return
        }
        isSearchLoading = true
        searchJob = viewModelScope.launch {
            delay(300)
            searchPage = 1
            searchHasMore = true
            fetchResults()
        }
    }

    /** The next page of results, once the last one is on screen. */
    fun loadMoreResults() {
        if (!isSearching || !searchHasMore || isSearchLoading) return
        searchJob = viewModelScope.launch { fetchResults() }
    }

    private suspend fun fetchResults() {
        val query = activeSearch
        isSearchLoading = true
        try {
            val response = api.getObject(
                "novo-staff/v1/sales/list",
                mapOf("search" to query, "page" to searchPage.toString(), "per_page" to "25"),
            )
            if (query != activeSearch) return
            val page = response.arr("items")?.objects().orEmpty().mapNotNull { ReceptionSale.from(it) }
            results = results + page.filter { sale -> results.none { it.id == sale.id } }
            searchHasMore = response.bool("has_more") == true && page.isNotEmpty()
            searchPage += 1
            searchError = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            searchError = e.localizedMessage ?: "Error desconocido."
        } finally {
            isSearchLoading = false
        }
    }

    fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val response = api.getObject("novo-staff/v1/sales", mapOf("date" to day.toString()))
                sales = response.arr("sales")?.objects().orEmpty().mapNotNull { ReceptionSale.from(it) }
                totals = SaleAmounts.from(response.obj("totals"))
                count = response.obj("totals")?.num("count")?.toInt() ?: 0
                closed = response.bool("closed")
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Error desconocido."
            }
            isLoading = false
        }
    }
}

private val dayTitle = DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("es"))
private val dayChip = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es"))
private val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.forLanguageTag("es"))
private val dayAndClock = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.forLanguageTag("es"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MySalesScreen(app: NovoFitClubApp) {
    val vm: MySalesViewModel = viewModel(key = "my-sales") { MySalesViewModel(app.api) }
    var pickingDay by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = vm.isLoading && vm.sales.isNotEmpty(), onRefresh = { vm.load() }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 96.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "header") { SectionHeader("Mis ventas", if (vm.isToday) "Hoy" else vm.day.format(dayTitle)) }
                item(key = "search") {
                    SearchField(vm.search, vm::updateSearch, "Buscar por nombre, cédula o recibo", Modifier.padding(horizontal = 16.dp), showsClear = true)
                }
                if (vm.isSearching) {
                    searchResults(vm)
                } else {
                    item(key = "day") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().cardBackground().padding(16.dp),
                        ) {
                            SectionLabel("Día", Modifier.weight(1f))
                            Text(
                                vm.day.format(dayChip),
                                style = NovoFonts.medium(15).copy(color = NovoColors.accent),
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(NovoColors.panel).clickable { pickingDay = true }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }
                    item(key = "totals") { Box(Modifier.padding(horizontal = 16.dp)) { TotalsCard(vm) } }
                    val error = vm.errorMessage
                    when {
                        vm.isLoading && vm.sales.isEmpty() -> item(key = "loading") { LoadingState() }
                        error != null -> item(key = "error") { ErrorState(error, onRetry = { vm.load() }) }
                        vm.sales.isEmpty() -> item(key = "empty") {
                            EmptyState(if (vm.isToday) "Todavía no registraste ventas hoy." else "No registraste ventas ese día.")
                        }
                        else -> items(vm.sales, key = { it.id }) { sale ->
                            Box(Modifier.padding(horizontal = 16.dp)) { SaleRow(sale) { vm.selected = sale } }
                        }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { vm.showingNewSale = true },
            containerColor = NovoColors.accent,
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "Nueva venta") }
    }

    if (pickingDay) {
        DayPickerDialog(vm.day, onDismiss = { pickingDay = false }) {
            pickingDay = false
            vm.select(it)
        }
    }
    vm.selected?.let { sale ->
        BackHandler { vm.selected = null }
        OverlayLayer { ReceptionSaleDetail(sale) { vm.selected = null } }
    }
    if (vm.showingNewSale) {
        BackHandler { vm.showingNewSale = false }
        OverlayLayer {
            ScopedViewModels {
                SaleEditScreen(
                    app = app,
                    saleId = null,
                    onDismiss = { vm.showingNewSale = false },
                    onSaved = { vm.select(LocalDate.now()) },
                )
            }
        }
    }
}

@Composable
private fun TotalsCard(vm: MySalesViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().cardBackground().padding(16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionLabel("Total del día")
                Text(Formatters.colones(vm.totals.total), style = NovoFonts.heading(30))
            }
            Text(if (vm.count == 1) "1 venta" else "${vm.count} ventas", style = NovoFonts.medium(14).copy(color = NovoColors.textSecondary))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MethodTile("Efectivo", vm.totals.cash, Modifier.weight(1f))
            MethodTile("Tarjeta", vm.totals.card, Modifier.weight(1f))
            MethodTile("SINPE", vm.totals.sinpe, Modifier.weight(1f))
        }
        vm.closed?.let { closed ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val color = if (closed) NovoColors.statusActive else NovoColors.statusExpiring
                Icon(if (closed) Icons.Outlined.CheckCircle else Icons.Outlined.Schedule, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (closed) "Cierre de caja hecho" else if (vm.isToday) "Cierre de caja pendiente" else "Sin cierre de caja ese día",
                    style = NovoFonts.medium(13).copy(color = color),
                )
            }
        }
    }
}

@Composable
private fun MethodTile(title: String, amount: Double, modifier: Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier.clip(RoundedCornerShape(12.dp)).background(NovoColors.panel).padding(10.dp)) {
        Text(title, style = NovoFonts.body(12).copy(color = NovoColors.textSecondary))
        Text(Formatters.colones(amount), style = NovoFonts.semibold(14), maxLines = 1)
    }
}

@Composable
private fun SaleRow(sale: ReceptionSale, showDate: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().saleRowBackground(sale.isCanje, sale.hasAjuste).clickable(onClick = onClick).padding(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(sale.buyers.ifEmpty { listOf("Sin miembro") }.joinToString(", "), style = NovoFonts.semibold(15), maxLines = 1)
            Text(sale.items.joinToString(" · "), style = NovoFonts.body(13).copy(color = NovoColors.textSecondary), maxLines = 2)
            val time = sale.date?.toInstant()?.atZone(ZoneId.systemDefault())?.format(if (showDate) dayAndClock else clock)
            Text(
                listOfNotNull(time, if (sale.isCanje) "Sin pago" else sale.paymentLabel.ifEmpty { null }).joinToString(" · "),
                style = NovoFonts.body(12).copy(color = NovoColors.textSecondary),
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SaleTagsView(SaleTag.of(sale.isCanje, sale.hasAjuste))
            Text(Formatters.colones(sale.amounts.total), style = NovoFonts.semibold(15).copy(color = if (sale.isCanje) NovoColors.canje else NovoColors.textPrimary))
            sale.statusLabel?.let { Text(it, style = NovoFonts.semibold(11).copy(color = NovoColors.statusExpiring)) }
        }
    }
}

/** Her sales matching the search, any day, newest first; the next page loads at the end. */
private fun LazyListScope.searchResults(vm: MySalesViewModel) {
    val error = vm.searchError
    when {
        vm.results.isEmpty() && vm.isSearchLoading -> item(key = "searching") { LoadingState() }
        error != null && vm.results.isEmpty() -> item(key = "search-error") { ErrorState(error, onRetry = { vm.loadMoreResults() }) }
        vm.results.isEmpty() -> item(key = "no-results") { EmptyState("No encontramos ventas tuyas con esa búsqueda.") }
        else -> {
            items(vm.results, key = { "r-${it.id}" }) { sale ->
                if (sale.id == vm.results.last().id) LaunchedEffect(sale.id) { vm.loadMoreResults() }
                Box(Modifier.padding(horizontal = 16.dp)) { SaleRow(sale, showDate = true) { vm.selected = sale } }
            }
            if (vm.isSearchLoading) item(key = "more") { LoadingState() }
        }
    }
}

/** Read-only detail of one of the receptionist's own sales: she can't edit a sale after creating it, same as the web. */
@Composable
private fun ReceptionSaleDetail(sale: ReceptionSale, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionHeader("Venta", breadcrumb = "MIS VENTAS", onBack = onClose)
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(Formatters.colones(sale.amounts.total), style = NovoFonts.heading(30).copy(color = if (sale.isCanje) NovoColors.canje else NovoColors.textPrimary))
                sale.date?.let { Text(Formatters.friendlyDate(it), style = NovoFonts.body(13).copy(color = NovoColors.textSecondary)) }
                sale.statusLabel?.let { Text(it, style = NovoFonts.semibold(13).copy(color = NovoColors.statusExpiring)) }
                SaleTagsView(SaleTag.of(sale.isCanje, sale.hasAjuste))
            }
            DetailBlock("Miembro", sale.buyers.ifEmpty { listOf("—") })
            DetailBlock("Vendido", sale.items.ifEmpty { listOf("—") })
            if (sale.isCanje) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().tintedCard(NovoColors.canje).padding(16.dp)) {
                    SectionLabel("Pago")
                    Text("Venta por canje", style = NovoFonts.body(14))
                    Text("No se cobró nada. No suma a tu total del día.", style = NovoFonts.body(12).copy(color = NovoColors.textSecondary))
                }
            } else {
                DetailBlock(
                    "Pago",
                    listOf(
                        sale.paymentLabel.ifEmpty { "—" },
                        "Efectivo ${Formatters.colones(sale.amounts.cash)} · Tarjeta ${Formatters.colones(sale.amounts.card)} · SINPE ${Formatters.colones(sale.amounts.sinpe)}",
                    ),
                )
            }
            if (sale.notes.isNotEmpty()) DetailBlock("Notas", sale.notes)
        }
    }
}

@Composable
private fun DetailBlock(title: String, lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().cardBackground().padding(16.dp)) {
        SectionLabel(title)
        lines.forEach { Text(it, style = NovoFonts.body(14)) }
    }
}

/** A day up to today (no sales in the future). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPickerDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val todayMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
        },
    )
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
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }) { Text("Aplicar", color = NovoColors.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = NovoColors.accent) } },
    ) { DatePicker(state = state, colors = colors) }
}
