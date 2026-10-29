package com.novofitclub.dashboard.feature.coach.evaluations

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novofitclub.dashboard.NovoFitClubApp
import com.novofitclub.dashboard.core.net.ViewedCoach
import com.novofitclub.dashboard.core.net.WPApiClient
import com.novofitclub.dashboard.core.ui.EmptyState
import com.novofitclub.dashboard.core.ui.ErrorState
import com.novofitclub.dashboard.core.ui.LoadingState
import com.novofitclub.dashboard.core.ui.MemberAvatar
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.SectionHeader
import com.novofitclub.dashboard.core.ui.cardBackground
import com.novofitclub.dashboard.feature.coach.CoachMemberRef
import com.novofitclub.dashboard.feature.sales.OverlayLayer
import com.novofitclub.dashboard.feature.sales.ScopedViewModels
import com.novofitclub.dashboard.feature.sales.SearchField
import com.novofitclub.dashboard.feature.sales.pickers.MemberPickerScreen
import com.novofitclub.dashboard.feature.sales.pickers.SaleMemberSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class EvaluationsFilter(val label: String) { All("Todas"), Soon("Por vencer"), Overdue("Vencidas"), Unsigned("Falta firma") }

/** Evaluaciones: my members by their next evaluation (every 6 weeks); a search looks through every evaluated member. */
class EvaluationsViewModel(private val api: WPApiClient, private val coachId: Int?) : ViewModel() {
    var items by mutableStateOf<List<EvaluationListItem>>(emptyList())
        private set
    /** Rows of my own list (without a search), for the header. */
    var myCount by mutableStateOf<Int?>(null)
        private set
    var filter by mutableStateOf(EvaluationsFilter.All)
    var search by mutableStateOf("")
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    private var searchJob: Job? = null

    init {
        load()
    }

    private val activeSearch: String get() = search.trim().takeIf { it.length >= 2 }.orEmpty()

    fun updateSearch(text: String) {
        val before = activeSearch
        search = text
        if (activeSearch == before) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            load()
        }
    }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            val query = activeSearch
            try {
                val loaded = EvaluationApi.list(api, coachId, query)
                if (query == activeSearch) {
                    items = loaded
                    if (query.isEmpty()) myCount = loaded.size
                }
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Error desconocido."
            }
            isLoading = false
        }
    }

    fun count(filter: EvaluationsFilter): Int = items.count { it.matches(filter) }

    val shown: List<EvaluationListItem> get() = items.filter { it.matches(filter) }

    private fun EvaluationListItem.matches(filter: EvaluationsFilter) = when (filter) {
        EvaluationsFilter.All -> true
        EvaluationsFilter.Soon -> isSoon
        EvaluationsFilter.Overdue -> isOverdue
        EvaluationsFilter.Unsigned -> needsSignature
    }
}

/** A member's expediente to show, and whether it was opened to start a new evaluation right away. */
private data class OpenedRecord(val member: CoachMemberRef, val startNew: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvaluationsScreen(app: NovoFitClubApp, viewing: ViewedCoach?) {
    val vm: EvaluationsViewModel = viewModel(key = "coach-evaluations-${viewing?.id}") { EvaluationsViewModel(app.api, viewing?.id) }
    var opened by remember { mutableStateOf<OpenedRecord?>(null) }
    var picking by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(isRefreshing = vm.isLoading && vm.items.isNotEmpty(), onRefresh = { vm.load() }, modifier = Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                item {
                    val count = vm.myCount
                    SectionHeader(
                        "Evaluaciones",
                        if (count == null) "Próxima cada 6 semanas" else "$count ${if (count == 1) "miembro" else "miembros"} · próxima cada 6 semanas",
                    )
                }
                item {
                    SearchField(vm.search, vm::updateSearch, "Buscar por nombre o cédula", Modifier.padding(horizontal = 16.dp), showsClear = true)
                }
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        EvaluationsFilter.entries.forEach { filter ->
                            val selected = vm.filter == filter
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (selected) NovoColors.accent else NovoColors.card)
                                    .clickable { vm.filter = filter }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                            ) {
                                val color = if (selected) Color.White else Color(0xFF9A9A9E)
                                Text(filter.label, style = NovoFonts.semibold(14).copy(color = color))
                                Text("${vm.count(filter)}", style = NovoFonts.semibold(12).copy(color = color.copy(alpha = 0.85f)))
                            }
                        }
                    }
                }
                val error = vm.errorMessage
                when {
                    vm.isLoading && vm.items.isEmpty() -> item { LoadingState(Modifier.height(240.dp)) }
                    error != null && vm.items.isEmpty() -> item { ErrorState(error, onRetry = { vm.load() }) }
                    vm.shown.isEmpty() -> item {
                        EmptyState(if (vm.search.trim().isNotEmpty()) "Sin resultados." else "Todavía no tenés evaluaciones.")
                    }
                    else -> items(vm.shown, key = { it.member.id }) { row ->
                        EvaluationRow(row, Modifier.padding(horizontal = 16.dp)) { opened = OpenedRecord(row.member, startNew = false) }
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .shadow(12.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .height(52.dp)
                .clip(CircleShape)
                .background(NovoColors.accent)
                .clickable { picking = true }
                .padding(start = 16.dp, end = 20.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text("Nueva evaluación", style = NovoFonts.semibold(15).copy(color = Color.White))
        }

        if (picking) {
            OverlayLayer {
                ScopedViewModels {
                    BackHandler { picking = false }
                    val source: SaleMemberSource = viewModel { SaleMemberSource(app.api) }
                    MemberPickerScreen(
                        source = source,
                        alreadySelected = emptyList(),
                        singleSelection = true,
                        onDone = { picked ->
                            picking = false
                            picked.firstOrNull()?.let { opened = OpenedRecord(CoachMemberRef(it.memberPostId, it.displayName, null), startNew = true) }
                        },
                        onCancel = { picking = false },
                    )
                }
            }
        }

        opened?.let { record ->
            OverlayLayer {
                ScopedViewModels {
                    MemberRecordScreen(
                        app = app,
                        member = record.member,
                        viewing = viewing,
                        startNew = record.startNew,
                        onBack = {
                            opened = null
                            vm.load()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EvaluationRow(row: EvaluationListItem, modifier: Modifier, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().cardBackground().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        MemberAvatar(row.member.initials, row.member.avatarColor, row.member.photoUrl, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(row.member.name, style = NovoFonts.semibold(16), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                row.subtitle,
                style = NovoFonts.body(13).copy(color = Color(0xFF9A9A9E)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val (text, color, tinted) = row.due
            Text(
                text,
                style = NovoFonts.semibold(12).copy(color = color),
                maxLines = 1,
                modifier = if (tinted) {
                    Modifier.clip(CircleShape).background(color.copy(alpha = 0.15f)).padding(horizontal = 9.dp, vertical = 4.dp)
                } else {
                    Modifier.padding(vertical = 4.dp)
                },
            )
            if (row.needsSignature) Text("Falta firma", style = NovoFonts.semibold(11).copy(color = EvalColors.pink))
        }
    }
}
