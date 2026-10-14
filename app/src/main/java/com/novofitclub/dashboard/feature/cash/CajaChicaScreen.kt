package com.novofitclub.dashboard.feature.cash

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novofitclub.dashboard.core.net.WPApiClient
import com.novofitclub.dashboard.core.net.firstNumber
import com.novofitclub.dashboard.core.net.firstString
import com.novofitclub.dashboard.core.ui.ListSectionHeader
import com.novofitclub.dashboard.core.ui.LoadingState
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoDimens
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.SectionHeader
import com.novofitclub.dashboard.core.ui.cardBackground
import com.novofitclub.dashboard.core.util.Formatters
import com.novofitclub.dashboard.feature.cash.common.CardDivider
import com.novofitclub.dashboard.feature.cash.common.LoadFailed
import com.novofitclub.dashboard.feature.cash.common.pluralCount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.abs

/** Caja Chica: `cash-tracking/v1/summary` + `cash-tracking/v1/movements`. */
class CashViewModel(private val client: WPApiClient) : ViewModel() {
    data class State(
        val summary: JSONObject = JSONObject(),
        val movements: List<IdentifiableRecord> = emptyList(),
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val errorMessage: String? = null,
    ) {
        /**
         * The summary also returns bookkeeping flags (e.g. `has_movements`)
         * alongside its real totals — explicit allowlist of the monetary
         * fields so the Resumen card never shows a raw, untranslated key.
         */
        val summaryRows: List<Pair<String, String>>
            get() = SUMMARY_FIELDS.mapNotNull { (keys, label) ->
                val amount = summary.firstNumber(*keys.toTypedArray()) ?: return@mapNotNull null
                label to (Formatters.money(amount) ?: "—")
            }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var job: Job? = null

    /** Fresh state for a new push from the hub (iOS builds a new view model each time). */
    fun reset() {
        job?.cancel()
        _state.value = State()
    }

    fun load(refreshing: Boolean = false) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, isRefreshing = refreshing, errorMessage = null) }
            coroutineScope {
                val summaryTask = async { runCatchingNonCancel { client.getObject("cash-tracking/v1/summary") } }
                val movementsTask = async { runCatchingNonCancel { client.getArray("cash-tracking/v1/movements") } }
                // Summary is a bonus card; a failure here shouldn't hide the movement list.
                val summary = summaryTask.await().getOrElse { JSONObject() }
                _state.update { it.copy(summary = summary) }
                movementsTask.await()
                    .onSuccess { raw -> _state.update { it.copy(movements = IdentifiableRecord.from(raw)) } }
                    .onFailure { e -> _state.update { it.copy(errorMessage = e.localizedMessage ?: e.toString()) } }
            }
            _state.update { it.copy(isLoading = false, isRefreshing = false) }
        }
    }

    private companion object {
        val SUMMARY_FIELDS: List<Pair<List<String>, String>> = listOf(
            listOf("deposits", "depositos", "depósitos") to "Depósitos",
            listOf("withdrawals", "retiros") to "Retiros",
            listOf("vendor_payments", "pagos_proveedores") to "Pagos a proveedores",
            listOf("carried_forward", "saldo_anterior", "saldo_inicial") to "Saldo anterior",
        )
    }
}

/** Like runCatching, but never swallows coroutine cancellation. */
internal inline fun <T> runCatchingNonCancel(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

/** The original "Caja" screen — one of three sections under the Caja hub. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CajaChicaScreen(
    viewModel: CashViewModel,
    onBack: () -> Unit,
    onOpenMovement: (IdentifiableRecord) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { viewModel.load() }

    Column(Modifier.fillMaxSize().background(NovoColors.background)) {
        SectionHeader(
            title = "Caja Chica",
            subtitle = if (state.movements.isEmpty()) null else pluralCount(state.movements.size, "movimiento"),
            breadcrumb = "CAJA",
            onBack = onBack,
        )
        val base = Modifier.fillMaxSize()
        when {
            state.isLoading && state.movements.isEmpty() -> LoadingState(base)
            state.errorMessage != null -> LoadFailed(state.errorMessage!!, { viewModel.load() }, base)
            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.load(refreshing = true) },
                modifier = base,
            ) {
                val summaryRows = state.summaryRows
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (summaryRows.isNotEmpty()) {
                        item { ListSectionHeader("Resumen", Modifier.padding(top = 4.dp)) }
                        item { SummaryCard(summaryRows) }
                    }
                    item { ListSectionHeader("Movimientos", Modifier.padding(top = 4.dp)) }
                    if (state.movements.isEmpty()) {
                        item {
                            Text(
                                "Sin movimientos",
                                style = NovoFonts.body(15).copy(color = NovoColors.textSecondary),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                            )
                        }
                    } else {
                        itemsIndexed(state.movements) { _, record ->
                            MovementRow(record, onClick = { onOpenMovement(record) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(rows: List<Pair<String, String>>) {
    Column(Modifier.fillMaxWidth().cardBackground()) {
        rows.forEachIndexed { index, (label, value) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(label, style = NovoFonts.medium(13).copy(color = NovoColors.textSecondary))
                Spacer(Modifier.weight(1f))
                Text(value, style = NovoFonts.semibold(15))
            }
            if (index < rows.size - 1) CardDivider()
        }
    }
}

/**
 * cash-tracking always stores `amount` as a positive number; whether money
 * came in or went out is in `type` (deposit / sale vs. withdrawal /
 * vendor_payment). A negative amount is still treated as money out.
 */
@Composable
private fun MovementRow(record: IdentifiableRecord, onClick: () -> Unit) {
    val fields = record.fields
    val amount = fields.firstNumber("amount", "monto", "total")
    val type = fields.firstString("type", "tipo")
    val isExpense = type in EXPENSE_TYPES || (amount ?: 0.0) < 0
    val tint = if (isExpense) NovoColors.accent else NovoColors.statusActive
    val title = fields.firstString("description", "descripcion", "concepto", "notes")
        ?: type?.let { MOVEMENT_TYPE_LABELS[it] ?: it }
        ?: "Movimiento #${fields.firstString("id") ?: "?"}"
    val subtitle = Formatters.friendlyDate(fields.firstString("date", "fecha", "created_at"))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(NovoDimens.cornerRadius))
            .clickable(onClick = onClick)
            .cardBackground()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(
            if (isExpense) Icons.Filled.ArrowCircleDown else Icons.Filled.ArrowCircleUp,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = NovoFonts.semibold(15), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = NovoFonts.body(12).copy(color = NovoColors.textSecondary))
            }
        }
        Spacer(Modifier.width(0.dp))
        Text(
            amount?.let { Formatters.money(abs(it)) } ?: "—",
            style = NovoFonts.semibold(15).copy(color = tint),
        )
    }
}

private val EXPENSE_TYPES = setOf("withdrawal", "vendor_payment")

private val MOVEMENT_TYPE_LABELS = mapOf(
    "deposit" to "Depósito",
    "withdrawal" to "Retiro",
    "vendor_payment" to "Pago a proveedor",
    "sale" to "Venta",
)
