package com.novofitclub.dashboard.feature.cash.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novofitclub.dashboard.core.net.WPApiClient
import com.novofitclub.dashboard.core.ui.LoadingState
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoDimens
import com.novofitclub.dashboard.core.ui.RecordRow
import com.novofitclub.dashboard.feature.cash.IdentifiableRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Generic list state for any endpoint that returns a JSON array of records. */
class RecordListViewModel(
    private val client: WPApiClient,
    private val path: String,
    private val query: Map<String, String> = emptyMap(),
) : ViewModel() {
    data class State(
        val records: List<IdentifiableRecord> = emptyList(),
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val errorMessage: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var job: Job? = null

    fun load(refreshing: Boolean = false) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, isRefreshing = refreshing, errorMessage = null) }
            try {
                val raw = client.getArray(path, query)
                _state.update { it.copy(records = IdentifiableRecord.from(raw)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = e.localizedMessage ?: e.toString()) }
            }
            _state.update { it.copy(isLoading = false, isRefreshing = false) }
        }
    }
}

/**
 * Reusable list body: loading / "No se pudo cargar" / "Sin datos" / records,
 * with pull-to-refresh. `rowTitle`/`rowSubtitle`/`rowTrailing` pick fields
 * out of each record; tapping a row calls [onSelect] (the caller pushes the
 * destination, e.g. [RecordDetailScreen]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordListContent(
    viewModel: RecordListViewModel,
    rowTitle: (JSONObject) -> String,
    onSelect: (IdentifiableRecord) -> Unit,
    modifier: Modifier = Modifier,
    rowSubtitle: (JSONObject) -> String? = { null },
    rowTrailing: (JSONObject) -> String? = { null },
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { viewModel.load() }

    val base = modifier.fillMaxSize().background(NovoColors.background)
    when {
        state.isLoading && state.records.isEmpty() -> LoadingState(base)
        state.errorMessage != null -> LoadFailed(state.errorMessage!!, { viewModel.load() }, base)
        state.records.isEmpty() -> ContentUnavailable("Sin datos", Icons.Outlined.Inbox, base)
        else -> PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.load(refreshing = true) },
            modifier = base,
        ) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.records) { record ->
                    RecordRow(
                        title = rowTitle(record.fields),
                        subtitle = rowSubtitle(record.fields),
                        trailing = rowTrailing(record.fields),
                        modifier = Modifier
                            .clip(RoundedCornerShape(NovoDimens.cornerRadius))
                            .clickable { onSelect(record) },
                    )
                }
            }
        }
    }
}
