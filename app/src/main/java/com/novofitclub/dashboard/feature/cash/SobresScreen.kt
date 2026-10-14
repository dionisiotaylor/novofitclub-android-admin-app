package com.novofitclub.dashboard.feature.cash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.SubcomposeAsyncImage
import com.novofitclub.dashboard.core.net.WPApiClient
import com.novofitclub.dashboard.core.ui.ListSectionHeader
import com.novofitclub.dashboard.core.ui.LoadingState
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.SectionHeader
import com.novofitclub.dashboard.core.ui.cardBackground
import com.novofitclub.dashboard.core.ui.initials
import com.novofitclub.dashboard.core.util.Formatters
import com.novofitclub.dashboard.feature.cash.common.CardDivider
import com.novofitclub.dashboard.feature.cash.common.LoadFailed
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Sobres: `sobre-tracking/v1/balances` + `sobre-tracking/v1/movements`. */
class SobresViewModel(private val client: WPApiClient) : ViewModel() {
    data class State(
        val balances: List<SobreBalance> = emptyList(),
        val movements: List<SobreMovement> = emptyList(),
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val errorMessage: String? = null,
    )

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
                val balancesTask = async { runCatchingNonCancel { client.getArray("sobre-tracking/v1/balances") } }
                val movementsTask = async { runCatchingNonCancel { client.getArray("sobre-tracking/v1/movements") } }
                // A summary card failing shouldn't hide the movement list below it.
                val balances = balancesTask.await()
                    .map { raw -> raw.mapNotNull(SobreBalance::from).sortedByDescending { it.balance } }
                    .getOrElse { emptyList() }
                _state.update { it.copy(balances = balances) }
                movementsTask.await()
                    .onSuccess { raw -> _state.update { it.copy(movements = raw.mapNotNull(SobreMovement::from)) } }
                    .onFailure { e -> _state.update { it.copy(errorMessage = e.localizedMessage ?: e.toString()) } }
            }
            _state.update { it.copy(isLoading = false, isRefreshing = false) }
        }
    }
}

/**
 * Per-receptionist cash envelope tracking: how much each has accumulated
 * from their register closes (Saldos) and the deposit/withdrawal ledger
 * (Movimientos).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SobresScreen(viewModel: SobresViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { viewModel.load() }

    val pending = state.balances.sumOf { it.balance }
    val pendingSubtitle = if (pending > 0) "${Formatters.money(pending) ?: "—"} pendientes de retirar" else null

    Column(Modifier.fillMaxSize().background(NovoColors.background)) {
        SectionHeader(title = "Sobres", subtitle = pendingSubtitle, breadcrumb = "CAJA", onBack = onBack)
        val base = Modifier.fillMaxSize()
        when {
            state.isLoading && state.movements.isEmpty() -> LoadingState(base)
            state.errorMessage != null && state.movements.isEmpty() ->
                LoadFailed(state.errorMessage!!, { viewModel.load() }, base)
            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.load(refreshing = true) },
                modifier = base,
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (state.balances.isNotEmpty()) {
                        item { ListSectionHeader("Saldos", Modifier.padding(top = 4.dp)) }
                        item {
                            Column(Modifier.fillMaxWidth().cardBackground()) {
                                state.balances.forEachIndexed { index, balance ->
                                    BalanceRow(balance)
                                    if (index < state.balances.size - 1) CardDivider()
                                }
                            }
                        }
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
                        items(state.movements) { movement -> MovementRow(movement) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceRow(balance: SobreBalance) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Avatar(balance.avatarUrl, balance.name)
        Text(
            balance.name,
            style = NovoFonts.medium(15),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            Formatters.money(balance.balance) ?: "—",
            style = NovoFonts.semibold(15).copy(
                color = if (balance.balance > 0) NovoColors.accent else NovoColors.textSecondary,
            ),
        )
    }
}

@Composable
private fun MovementRow(movement: SobreMovement) {
    val isDeposito = movement.kind == SobreMovement.Kind.Deposito
    val tint = if (isDeposito) NovoColors.statusActive else NovoColors.accent
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().cardBackground().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(
            if (isDeposito) Icons.Filled.ArrowCircleDown else Icons.Filled.ArrowCircleUp,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(movement.receptionistName, style = NovoFonts.semibold(15))
            Text(
                movement.notes ?: if (isDeposito) "Depósito" else "Retiro",
                style = NovoFonts.body(13).copy(color = NovoColors.textSecondary),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            movement.createdAt?.let {
                Text(Formatters.shortDate(it), style = NovoFonts.body(12).copy(color = NovoColors.textSecondary))
            }
        }
        Text(Formatters.money(movement.amount) ?: "—", style = NovoFonts.semibold(15).copy(color = tint))
    }
}

/** Remote avatar with an initials circle as placeholder/fallback. */
@Composable
private fun Avatar(url: String?, name: String) {
    val placeholder = @Composable {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(36.dp).clip(CircleShape).background(NovoColors.card),
        ) {
            Text(initials(name), style = NovoFonts.semibold(12))
        }
    }
    if (url.isNullOrEmpty()) {
        placeholder()
    } else {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            loading = { placeholder() },
            error = { placeholder() },
            modifier = Modifier.size(36.dp).clip(CircleShape),
        )
    }
}
