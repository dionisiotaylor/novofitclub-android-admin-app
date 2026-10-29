package com.novofitclub.dashboard.feature.reception

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novofitclub.dashboard.NovoFitClubApp
import com.novofitclub.dashboard.core.net.WPApiClient
import com.novofitclub.dashboard.core.net.arr
import com.novofitclub.dashboard.core.net.num
import com.novofitclub.dashboard.core.net.objects
import com.novofitclub.dashboard.core.ui.EmptyState
import com.novofitclub.dashboard.core.ui.ErrorState
import com.novofitclub.dashboard.core.ui.LoadingState
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.SectionHeader
import com.novofitclub.dashboard.core.ui.cardBackground
import com.novofitclub.dashboard.core.util.Formatters
import kotlinx.coroutines.launch

/**
 * The receptionist's own cierres de caja (read-only), newest first, from
 * `novo-staff/v1/closings` — the server only returns closings whose
 * `envelope_author_id` is the signed-in user. Closing the register is still
 * done from the web's "Resumen de ventas" page.
 */
class MyClosingsViewModel(private val api: WPApiClient) : ViewModel() {
    var closings by mutableStateOf<List<ReceptionClosing>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var hasMore by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    private var page by mutableIntStateOf(1)

    init {
        refresh()
    }

    fun refresh() {
        page = 1
        closings = emptyList()
        loadPage()
    }

    fun loadMore() {
        if (!hasMore || isLoading) return
        page += 1
        loadPage()
    }

    private fun loadPage() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val response = api.getObject("novo-staff/v1/closings", mapOf("page" to page.toString(), "per_page" to "30"))
                closings = closings + response.arr("closings")?.objects().orEmpty().mapNotNull { ReceptionClosing.from(it) }
                hasMore = page < (response.num("pages")?.toInt() ?: 1)
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Error desconocido."
            }
            isLoading = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyClosingsScreen(app: NovoFitClubApp) {
    val vm: MyClosingsViewModel = viewModel(key = "my-closings") { MyClosingsViewModel(app.api) }
    PullToRefreshBox(isRefreshing = vm.isLoading && vm.closings.isNotEmpty(), onRefresh = { vm.refresh() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
            item { SectionHeader("Mis cierres", "Tus cierres de caja") }
            val error = vm.errorMessage
            when {
                vm.isLoading && vm.closings.isEmpty() -> item { LoadingState() }
                error != null && vm.closings.isEmpty() -> item { ErrorState(error, onRetry = { vm.refresh() }) }
                vm.closings.isEmpty() -> item { EmptyState("Todavía no hiciste ningún cierre de caja.") }
                else -> items(vm.closings, key = { it.id }) { closing ->
                    if (closing.id == vm.closings.last().id) LaunchedEffect(closing.id) { vm.loadMore() }
                    ClosingRow(closing)
                }
            }
        }
    }
}

@Composable
private fun ClosingRow(closing: ReceptionClosing) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().cardBackground().padding(16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(closing.date?.let { Formatters.dayTitle(it) } ?: "—", style = NovoFonts.semibold(15))
                closing.date?.let { Text(Formatters.timeOnly(it), style = NovoFonts.body(12).copy(color = NovoColors.textSecondary)) }
            }
            Text(Formatters.colones(closing.total), style = NovoFonts.title(18))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Amount("Efectivo", closing.totalCash, Modifier.weight(1f))
            Amount("Tarjeta", closing.totalCard, Modifier.weight(1f))
            Amount("SINPE", closing.totalSinpe, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Amount("Billetes", closing.bills, Modifier.weight(1f))
            Amount("Monedas", closing.coins, Modifier.weight(1f))
            Amount("Al sobre", closing.sobreAdded, Modifier.weight(1f))
        }
        if (closing.notes.isNotEmpty()) Text(closing.notes, style = NovoFonts.body(12).copy(color = NovoColors.textSecondary))
    }
}

@Composable
private fun Amount(title: String, value: Double, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = NovoFonts.body(11).copy(color = NovoColors.textSecondary))
        Text(Formatters.colones(value), style = NovoFonts.medium(13))
    }
}
