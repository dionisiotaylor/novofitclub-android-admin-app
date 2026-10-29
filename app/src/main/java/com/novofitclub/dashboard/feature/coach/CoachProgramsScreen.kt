package com.novofitclub.dashboard.feature.coach

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.novofitclub.dashboard.core.net.ViewedCoach
import com.novofitclub.dashboard.core.net.WPApiClient
import com.novofitclub.dashboard.core.ui.EmptyState
import com.novofitclub.dashboard.core.ui.ErrorState
import com.novofitclub.dashboard.core.ui.LoadingState
import com.novofitclub.dashboard.core.ui.MemberAvatar
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.SectionHeader
import com.novofitclub.dashboard.core.ui.ThemedField
import com.novofitclub.dashboard.core.ui.cardBackground
import java.text.Normalizer
import kotlinx.coroutines.launch

enum class ProgramsFilter(val label: String) { Pending("Pendientes"), Done("Listos"), Delivered("Entregados") }

/** Programas: the programs this coach builds, as Pendientes / Listos / Entregados. Reception delivers the done ones. */
class CoachProgramsViewModel(private val api: WPApiClient, private val coachId: Int?) : ViewModel() {
    var programs by mutableStateOf<List<CoachProgram>>(emptyList())
        private set
    var filter by mutableStateOf(ProgramsFilter.Pending)
    var search by mutableStateOf("")
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    val opener = ProgramOpener(api, viewModelScope) { load() }

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            try {
                programs = CoachApi.programs(api, coachId)
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Error desconocido."
            }
            isLoading = false
        }
    }

    fun programs(filter: ProgramsFilter): List<CoachProgram> = when (filter) {
        ProgramsFilter.Pending -> programs.filter { it.status.isPending }.sortedBy { it.deadline.daysRemaining ?: Int.MAX_VALUE }
        ProgramsFilter.Done -> programs.filter { it.status == ProgramStatus.Done }
        ProgramsFilter.Delivered -> programs.filter { it.status == ProgramStatus.Delivered }
    }

    val shown: List<CoachProgram>
        get() {
            val needle = fold(search.trim())
            return programs(filter).filter { needle.isEmpty() || fold(it.memberName).contains(needle) }
        }

    private fun fold(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachProgramsScreen(app: NovoFitClubApp, viewing: ViewedCoach?) {
    val vm: CoachProgramsViewModel = viewModel(key = "coach-programs-${viewing?.id}") { CoachProgramsViewModel(app.api, viewing?.id) }

    vm.opener.openProgramId?.let { id ->
        BackHandler { vm.opener.openProgramId = null }
        ProgramDetailScreen(app, id) {
            vm.opener.openProgramId = null
            vm.load()
        }
        return
    }
    ProgramOpenerDialogs(vm.opener)

    PullToRefreshBox(isRefreshing = vm.isLoading && vm.programs.isNotEmpty(), onRefresh = { vm.load() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            item { SectionHeader("Programas", "Plazo de entrega: 4 días hábiles desde la evaluación") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                    ProgramsFilter.entries.forEach { filter ->
                        val selected = vm.filter == filter
                        Text(
                            "${filter.label}  ${vm.programs(filter).size}",
                            style = NovoFonts.semibold(14).copy(color = if (selected) Color.White else NovoColors.textSecondary),
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (selected) NovoColors.accent else NovoColors.card)
                                .clickable { vm.filter = filter }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                }
            }
            item { ThemedField(vm.search, { vm.search = it }, "Buscar miembro", Modifier.padding(horizontal = 16.dp)) }
            if (vm.filter == ProgramsFilter.Done && vm.shown.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(NovoColors.statusActive.copy(alpha = 0.10f)).padding(12.dp),
                    ) {
                        Icon(Icons.Outlined.Info, contentDescription = null, tint = NovoColors.statusActive)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Recepción entrega los programas listos al miembro por WhatsApp y los marca como entregados.",
                            style = NovoFonts.body(13).copy(color = Color(0xFFC7C7CC)),
                        )
                    }
                }
            }
            val error = vm.errorMessage
            when {
                vm.isLoading && vm.programs.isEmpty() -> item { LoadingState() }
                error != null && vm.programs.isEmpty() -> item { ErrorState(error, onRetry = { vm.load() }) }
                vm.shown.isEmpty() -> item {
                    EmptyState(
                        when {
                            vm.search.isNotEmpty() -> "Sin resultados"
                            vm.filter == ProgramsFilter.Pending -> "Nada pendiente"
                            vm.filter == ProgramsFilter.Done -> "Ningún programa listo"
                            else -> "Nada entregado en los últimos 90 días"
                        },
                    )
                }
                else -> items(vm.shown, key = { it.evaluationId }) { program ->
                    ProgramRow(program, Modifier.padding(horizontal = 16.dp)) { vm.opener.open(program) }
                }
            }
        }
    }
}

@Composable
private fun ProgramRow(program: CoachProgram, modifier: Modifier, onClick: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth().cardBackground().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MemberAvatar(program.member?.initials ?: "?", program.member?.avatarColor ?: NovoColors.avatarPalette[0], program.member?.photoUrl, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(program.memberName, style = NovoFonts.semibold(16), maxLines = 1)
                Text(
                    program.goal.ifEmpty { "Sin objetivo anotado" },
                    style = NovoFonts.body(13).copy(color = NovoColors.textSecondary),
                    maxLines = 1,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = NovoColors.textSecondary)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 56.dp)) {
            if (program.programId == null) CoachChip("Sin programa", NovoColors.textSecondary) else CoachChip(program.status.label, program.status.color)
            if (program.status.isPending) program.deadline.label?.let { CoachChip(it, program.deadline.color) }
            Text(program.datesLabel, style = NovoFonts.body(12).copy(color = NovoColors.textSecondary), maxLines = 1)
        }
    }
}
