package com.novofitclub.dashboard.feature.coach

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novofitclub.dashboard.NovoFitClubApp
import com.novofitclub.dashboard.core.net.ViewedCoach
import com.novofitclub.dashboard.core.net.WPApiClient
import com.novofitclub.dashboard.core.net.arr
import com.novofitclub.dashboard.core.net.num
import com.novofitclub.dashboard.core.net.obj
import com.novofitclub.dashboard.core.net.objects
import com.novofitclub.dashboard.core.ui.EmptyState
import com.novofitclub.dashboard.core.ui.ErrorState
import com.novofitclub.dashboard.core.ui.LoadingState
import com.novofitclub.dashboard.core.ui.MemberAvatar
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoDimens
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.cardBackground
import com.novofitclub.dashboard.core.ui.initials
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Hoy: today's citas (the next one up front), the counts, and the programs closest to their deadline. */
class CoachTodayViewModel(private val api: WPApiClient, private val coachId: Int?) : ViewModel() {
    var citas by mutableStateOf<List<CoachCita>>(emptyList())
        private set
    var evaluationsToday by mutableIntStateOf(0)
        private set
    var programs by mutableStateOf<List<CoachProgram>>(emptyList())
        private set
    /** False when the user isn't linked to a gym-scheduler employee: no citas then. */
    var isLinked by mutableStateOf(true)
        private set
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
                coroutineScope {
                    val today = async {
                        api.getObject("novo-staff/v1/coach/today", CoachApi.coachQuery(coachId) + ("date" to CoachDates.today().toString()))
                    }
                    val list = async { CoachApi.programs(api, coachId) }
                    val record = today.await()
                    citas = record.arr("citas")?.objects().orEmpty().mapNotNull { CoachCita.from(it) }
                    evaluationsToday = record.num("evaluations_today")?.toInt() ?: 0
                    isLinked = record.obj("employee") != null
                    programs = list.await()
                }
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Error desconocido."
            }
            isLoading = false
        }
    }

    /** Pending programs, the most overdue first. */
    val pending: List<CoachProgram>
        get() = programs.filter { it.status.isPending }.sortedBy { it.deadline.daysRemaining ?: Int.MAX_VALUE }

    val overdueCount: Int get() = pending.count { it.deadline.isExpired }

    /** The cita on now or the next one today. */
    fun nextCita(minute: Int): CoachCita? = citas.firstOrNull { it.endMinute > minute && it.status != "completed" }
}

/**
 * Opens a program from a list row; an evaluation whose program wasn't
 * created yet asks first, then creates it (member-evaluations links it).
 */
class ProgramOpener(
    private val api: WPApiClient,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val onCreated: () -> Unit,
) {
    var openProgramId by mutableStateOf<Int?>(null)
    var confirming by mutableStateOf<CoachProgram?>(null)
    var error by mutableStateOf<String?>(null)

    fun open(program: CoachProgram) {
        if (program.programId != null) openProgramId = program.programId else confirming = program
    }

    fun create(program: CoachProgram) {
        confirming = null
        scope.launch {
            try {
                openProgramId = CoachApi.createProgram(api, program.evaluationId)
                onCreated()
            } catch (e: Exception) {
                error = e.localizedMessage
            }
        }
    }
}

/** The create-program question and its errors, shared by Hoy and Programas. */
@Composable
fun ProgramOpenerDialogs(opener: ProgramOpener) {
    opener.confirming?.let { program ->
        AlertDialog(
            onDismissRequest = { opener.confirming = null },
            title = { Text("Crear programa") },
            text = { Text("La evaluación de ${program.memberName} todavía no tiene programa. Se crea vacío para armar la rutina.") },
            confirmButton = { TextButton(onClick = { opener.create(program) }) { Text("Crear") } },
            dismissButton = { TextButton(onClick = { opener.confirming = null }) { Text("Cancelar") } },
        )
    }
    opener.error?.let { message ->
        AlertDialog(
            onDismissRequest = { opener.error = null },
            title = { Text("No se pudo abrir") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { opener.error = null }) { Text("OK") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachTodayScreen(app: NovoFitClubApp, viewing: ViewedCoach?, showPrograms: () -> Unit) {
    val vm: CoachTodayViewModel = viewModel(key = "coach-today-${viewing?.id}") { CoachTodayViewModel(app.api, viewing?.id) }
    val auth by app.auth.state.collectAsState()
    val context = LocalContext.current

    vm.opener.openProgramId?.let { id ->
        BackHandler { vm.opener.openProgramId = null }
        ProgramDetailScreen(app, id) {
            vm.opener.openProgramId = null
            vm.load()
        }
        return
    }
    ProgramOpenerDialogs(vm.opener)

    val name = viewing?.name ?: auth.displayName
    PullToRefreshBox(isRefreshing = vm.isLoading && vm.programs.isNotEmpty(), onRefresh = { vm.load() }, modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        CoachDates.longLabel(CoachDates.today()).uppercase(),
                        style = NovoFonts.semibold(13).copy(color = NovoColors.textSecondary, letterSpacing = 0.4.sp),
                    )
                    val first = name.substringBefore(' ')
                    Text(if (first.isEmpty()) "Hola" else "Hola, $first", style = NovoFonts.heading(30))
                }
                MemberAvatar(initials(name), hexColor(viewing?.color) ?: NovoColors.avatarPalette[1], null, 44.dp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(vm.evaluationsToday, "Evaluaciones hoy", NovoColors.statusNeutral, Modifier.weight(1f)) {}
                StatTile(vm.pending.size, "Programas pendientes", NovoColors.statusExpiring, Modifier.weight(1f), showPrograms)
                StatTile(vm.overdueCount, "Programas vencidos", Color(0xFFFF6961), Modifier.weight(1f), showPrograms)
            }

            val error = vm.errorMessage
            if (error != null && vm.programs.isEmpty()) {
                ErrorState(error, onRetry = { vm.load() })
                return@Column
            }

            val cita = vm.nextCita(CoachDates.nowMinute())
            if (cita != null) {
                NextCitaCard(cita, vm.citas.count { it.startMinute > cita.startMinute && it.status != "completed" }) { url ->
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            } else if (!vm.isLoading) {
                Text(
                    if (vm.isLinked) "No tenés más citas hoy." else "Tu usuario no está vinculado a un empleado del horario: pedile a administración que lo vincule para ver tus citas.",
                    style = NovoFonts.medium(14).copy(color = NovoColors.textSecondary),
                    modifier = Modifier.fillMaxWidth().cardBackground().padding(16.dp),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "PROGRAMAS POR ENTREGAR",
                    style = NovoFonts.semibold(13).copy(color = NovoColors.textSecondary, letterSpacing = 0.4.sp),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = showPrograms) { Text("Ver todos", style = NovoFonts.semibold(13).copy(color = NovoColors.accent)) }
            }
            when {
                vm.isLoading && vm.programs.isEmpty() -> LoadingState()
                vm.pending.isEmpty() -> EmptyState("No tenés programas pendientes.")
                else -> vm.pending.take(3).forEach { program ->
                    PendingRow(program) { vm.opener.open(program) }
                }
            }
        }
    }
}

@Composable
private fun StatTile(value: Int, label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.heightIn(min = 90.dp).cardBackground().clickable(onClick = onClick).padding(12.dp),
    ) {
        Text("$value", style = NovoFonts.heading(26).copy(color = color))
        Text(label, style = NovoFonts.medium(12).copy(color = NovoColors.textSecondary))
    }
}

@Composable
private fun NextCitaCard(cita: CoachCita, later: Int, openUrl: (String) -> Unit) {
    val isNow = cita.startMinute <= CoachDates.nowMinute()
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(NovoDimens.cornerRadius)).background(NovoColors.accent).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${if (isNow) "Cita ahora" else "Próxima cita"} · ${CoachDates.clock(cita.startMinute)}".uppercase(),
                style = NovoFonts.semibold(12).copy(letterSpacing = 0.6.sp),
                modifier = Modifier.weight(1f),
            )
            Text(
                "${cita.service} · ${cita.durationLabel}",
                style = NovoFonts.semibold(12),
                modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.22f)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(cita.name, style = NovoFonts.title(22))
            Text(cita.statusLabel, style = NovoFonts.medium(14))
        }
        if (cita.notes.isNotEmpty()) {
            Text(
                "“${cita.notes}”",
                style = NovoFonts.body(13),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.18f)).padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
        cita.whatsAppUrl?.let { url ->
            OutlinedButton(
                onClick = { openUrl(url) },
                shape = CircleShape,
                border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            ) { Text("WhatsApp", style = NovoFonts.semibold(14)) }
        }
        if (later > 0) Text(if (later == 1) "1 cita más hoy" else "$later citas más hoy", style = NovoFonts.medium(12))
    }
}

@Composable
private fun PendingRow(program: CoachProgram, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().cardBackground().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        MemberAvatar(program.member?.initials ?: "?", program.member?.avatarColor ?: NovoColors.avatarPalette[0], program.member?.photoUrl, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(program.memberName, style = NovoFonts.semibold(15), maxLines = 1)
            Text(
                "${program.status.label} · límite ${CoachDates.shortLabel(program.deadline.dueDate)}",
                style = NovoFonts.body(13).copy(color = NovoColors.textSecondary),
                maxLines = 1,
            )
        }
        program.deadline.label?.let { CoachChip(it, program.deadline.color) }
    }
}
