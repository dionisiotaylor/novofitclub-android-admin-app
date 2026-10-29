package com.novofitclub.dashboard.feature.coach

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novofitclub.dashboard.NovoFitClubApp
import com.novofitclub.dashboard.core.ui.ErrorState
import com.novofitclub.dashboard.core.ui.LoadingState
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.PrimaryButton
import com.novofitclub.dashboard.core.ui.SecondaryButton
import com.novofitclub.dashboard.core.ui.SectionHeader
import com.novofitclub.dashboard.core.ui.cardBackground

private val Purple = Color(0xFFB9A3FF)
private val Pink = Color(0xFFFF5C80)

private sealed interface EditTarget {
    data class Exercise(val exercise: RoutineExercise) : EditTarget
    data class Group(val group: RoutineItem.Group) : EditTarget
    data class Day(val day: RoutineDay) : EditTarget
}

private sealed interface PickTarget {
    data object Day : PickTarget
    data class Group(val id: String) : PickTarget
}

/**
 * A program for the coach who builds it: Estado (Nuevo / En progreso /
 * Listo), the evaluation to keep in mind, and the routine editor — days,
 * exercises and circuits, saved whole with "Guardar rutina".
 */
@Composable
fun ProgramDetailScreen(app: NovoFitClubApp, programId: Int, onBack: () -> Unit) {
    val vm: ProgramDetailViewModel = viewModel(key = "program-$programId") { ProgramDetailViewModel(app.api, programId) }
    val context = LocalContext.current
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    var picking by remember { mutableStateOf<PickTarget?>(null) }
    var importing by remember { mutableStateOf(false) }
    var confirmingLeave by remember { mutableStateOf(false) }

    val leave: () -> Unit = {
        if (vm.isDirty) {
            confirmingLeave = true
        } else {
            onBack()
        }
    }
    BackHandler(onBack = leave)
    val openVideo = { url: String -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    val detail = vm.detail
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when {
                detail == null && vm.isLoading -> {
                    SectionHeader("Programa", breadcrumb = "PROGRAMAS", onBack = leave)
                    LoadingState()
                }
                detail == null -> {
                    SectionHeader("Programa", breadcrumb = "PROGRAMAS", onBack = leave)
                    ErrorState(vm.loadError ?: "No se pudo cargar.", onRetry = { vm.load() })
                }
                else -> {
                    val subtitle = buildList {
                        add(if (detail.evaluation != null) "Eval. ${CoachDates.shortLabel(detail.evaluationDate)}" else detail.title)
                        if (detail.status.isPending && detail.deadline.dueDate != null) add("Límite ${CoachDates.shortLabel(detail.deadline.dueDate)}")
                    }.joinToString(" · ")
                    SectionHeader(detail.member?.name ?: detail.title, subtitle, "PROGRAMAS", leave)
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                        StatusSection(detail.status, vm.isUpdatingStatus) { vm.setStatus(it) {} }
                        detail.evaluation?.let { EvaluationCard(it) }
                        RoutineSection(
                            vm,
                            onEdit = { editing = it },
                            onPick = { picking = it },
                            onImport = { importing = true },
                            openVideo = openVideo,
                        )
                    }
                }
            }
        }
        if (detail != null) {
            BottomBar(
                vm,
                onAddExercise = { picking = PickTarget.Day },
                onAddGroup = { vm.addGroup()?.let { editing = EditTarget.Group(it) } },
            )
        }
    }

    when (val target = editing) {
        is EditTarget.Exercise -> ExerciseEditorDialog(
            app, target.exercise,
            canMoveUp = vm.canMove(target.exercise.id, -1),
            canMoveDown = vm.canMove(target.exercise.id, 1),
            onSave = { vm.updateExercise(it) },
            onMove = { vm.move(target.exercise.id, it) },
            onDelete = { vm.delete(target.exercise.id) },
            onDismiss = { editing = null },
        )
        is EditTarget.Group -> GroupEditorDialog(
            target.group,
            canMoveUp = vm.canMove(target.group.id, -1),
            canMoveDown = vm.canMove(target.group.id, 1),
            onSave = { name, rounds -> vm.updateGroup(target.group.id, name, rounds) },
            onMove = { vm.move(target.group.id, it) },
            onDelete = { vm.delete(target.group.id) },
            onDismiss = { editing = null },
        )
        is EditTarget.Day -> DayEditorDialog(
            target.day,
            canMoveLeft = vm.canMoveDay(target.day.id, -1),
            canMoveRight = vm.canMoveDay(target.day.id, 1),
            onSave = { name, label -> vm.updateDay(target.day.id, name, label) },
            onMove = { vm.moveDay(target.day.id, it) },
            onDuplicate = { vm.duplicateDay(target.day.id) },
            onDelete = { vm.deleteDay(target.day.id) },
            onDismiss = { editing = null },
        )
        null -> Unit
    }
    picking?.let { target ->
        ExercisePickerDialog(app, onDismiss = { picking = null }) { exercise ->
            when (target) {
                PickTarget.Day -> vm.addExercise(exercise)
                is PickTarget.Group -> vm.addExercise(exercise, target.id)
            }
        }
    }
    if (importing) {
        RoutineImportDialog(app, excluding = programId, hasDays = vm.days.isNotEmpty(), onDismiss = { importing = false }) { days, replacing ->
            vm.importDays(days, replacing)
        }
    }
    if (confirmingLeave) {
        AlertDialog(
            onDismissRequest = { confirmingLeave = false },
            title = { Text("Tenés cambios sin guardar") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingLeave = false
                    vm.save(onSaved = onBack)
                }) { Text("Guardar y salir") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmingLeave = false }) { Text("Seguir editando") }
                    TextButton(onClick = {
                        confirmingLeave = false
                        onBack()
                    }) { Text("Salir sin guardar", color = Color(0xFFFF6961)) }
                }
            },
        )
    }
    vm.actionError?.let { message ->
        AlertDialog(
            onDismissRequest = { vm.actionError = null },
            title = { Text("No se pudo guardar") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { vm.actionError = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(), style = NovoFonts.semibold(13).copy(color = NovoColors.textSecondary, letterSpacing = 0.4.sp))
}

@Composable
private fun StatusSection(status: ProgramStatus, updating: Boolean, onSelect: (ProgramStatus) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Estado")
        Row(Modifier.fillMaxWidth().clip(CircleShape).background(NovoColors.card).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(ProgramStatus.New, ProgramStatus.InProgress, ProgramStatus.Done).forEach { option ->
                val selected = option == status
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(CircleShape)
                        .background(if (selected) (if (option == ProgramStatus.Done) Color(0xFF248A3D) else NovoColors.accent) else Color.Transparent)
                        .clickable(enabled = !updating) { onSelect(option) },
                ) {
                    Text(option.label, style = NovoFonts.semibold(14).copy(color = if (selected) Color.White else NovoColors.textSecondary))
                }
            }
        }
        Text(
            when (status) {
                ProgramStatus.New -> "Todavía no empezaste este programa."
                ProgramStatus.InProgress -> "Guardá la rutina cuando termines cada día."
                ProgramStatus.Done -> "Recepción lo entregará al miembro por WhatsApp."
                ProgramStatus.Delivered -> "Recepción ya se lo entregó al miembro."
            },
            style = NovoFonts.body(12).copy(color = NovoColors.textSecondary),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EvaluationCard(evaluation: EvaluationSummary) {
    Column(Modifier.fillMaxWidth().cardBackground().padding(horizontal = 16.dp)) {
        Text("Evaluación del miembro", style = NovoFonts.title(15), modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
        EvaluationRow(Icons.Outlined.GpsFixed, NovoColors.accent, "Objetivo") {
            Text(evaluation.goals.joinToString(", ").ifEmpty { "Sin objetivo anotado" }, style = NovoFonts.medium(14))
        }
        if (evaluation.painAreas.isNotEmpty()) {
            EvaluationRow(Icons.Outlined.Place, NovoColors.statusExpiring, "Mapa de dolor") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    evaluation.painAreas.forEach { CoachChip(it, NovoColors.statusExpiring) }
                }
            }
        }
        if (evaluation.restrictions.isNotEmpty()) {
            EvaluationRow(Icons.Outlined.WarningAmber, Color(0xFFFF6961), "Restricciones de salud") {
                Text(evaluation.restrictions.joinToString("\n"), style = NovoFonts.medium(14))
            }
        }
        evaluation.trainingLabel?.let {
            EvaluationRow(Icons.Filled.FitnessCenter, NovoColors.statusNeutral, "Entrenamiento", divider = false) {
                Text(it, style = NovoFonts.medium(14))
            }
        }
    }
}

@Composable
private fun EvaluationRow(icon: ImageVector, color: Color, title: String, divider: Boolean = true, content: @Composable () -> Unit) {
    Column {
        Row(Modifier.padding(vertical = 10.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = NovoFonts.semibold(12).copy(color = NovoColors.textSecondary))
                content()
            }
        }
        if (divider) HorizontalDivider(color = NovoColors.separator)
    }
}

@Composable
private fun RoutineSection(
    vm: ProgramDetailViewModel,
    onEdit: (EditTarget) -> Unit,
    onPick: (PickTarget) -> Unit,
    onImport: () -> Unit,
    openVideo: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SectionTitle("Rutinas") }
            TextButton(onClick = onImport) { Text("Importar", style = NovoFonts.semibold(13).copy(color = NovoColors.textSecondary)) }
            TextButton(onClick = { vm.addDay() }) { Text("+ Nuevo día", style = NovoFonts.semibold(13).copy(color = NovoColors.accent)) }
        }
        if (vm.days.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().cardBackground().padding(18.dp),
            ) {
                Text("La rutina está vacía", style = NovoFonts.semibold(15))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Nuevo día", { vm.addDay() }, Modifier.weight(1f))
                    PrimaryButton("Importar rutina", onImport, Modifier.weight(1f))
                }
            }
            return@Column
        }

        val selectedIndex = vm.selectedIndex
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.days, key = { it.id }) { day ->
                val selected = selectedIndex != null && vm.days[selectedIndex].id == day.id
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier
                        .widthIn(min = 110.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) NovoColors.accent.copy(alpha = 0.16f) else NovoColors.card)
                        .border(1.5.dp, if (selected) NovoColors.accent else Color.Transparent, RoundedCornerShape(14.dp))
                        .clickable { if (selected) onEdit(EditTarget.Day(day)) else vm.selectedDayId = day.id }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(day.name.ifEmpty { "Día" }, style = NovoFonts.title(15))
                        if (selected) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Filled.Edit, contentDescription = "Editar día", tint = Color(0xFFFF8AA4), modifier = Modifier.size(12.dp))
                        }
                    }
                    Text(
                        day.label.ifEmpty { "${day.exerciseCount} ejercicios" },
                        style = NovoFonts.body(12).copy(color = if (selected) Color(0xFFFF8AA4) else NovoColors.textSecondary),
                        maxLines = 1,
                    )
                }
            }
        }

        val day = selectedIndex?.let { vm.days[it] } ?: return@Column
        if (day.items.isEmpty()) {
            Text(
                "Este día no tiene ejercicios. Agregá uno o un circuito.",
                style = NovoFonts.body(13).copy(color = NovoColors.textSecondary),
                modifier = Modifier.fillMaxWidth().cardBackground().padding(16.dp),
            )
        }
        day.items.forEach { item ->
            when (item) {
                is RoutineItem.Single -> ExerciseRow(
                    item.exercise,
                    openVideo,
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NovoColors.card)
                        .clickable { onEdit(EditTarget.Exercise(item.exercise)) }.padding(horizontal = 14.dp, vertical = 12.dp),
                )
                is RoutineItem.Group -> Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(NovoColors.card).padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onEdit(EditTarget.Group(item)) }) {
                        Text(
                            item.name.ifEmpty { "Circuito" }.uppercase(),
                            style = NovoFonts.semibold(13).copy(color = Purple, letterSpacing = 0.4.sp),
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        if (item.rounds.isNotEmpty()) CoachChip(item.rounds, Purple, Modifier.widthIn(max = 180.dp))
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.Edit, contentDescription = "Editar circuito", tint = NovoColors.textSecondary, modifier = Modifier.size(14.dp))
                    }
                    item.exercises.forEach { exercise ->
                        ExerciseRow(exercise, openVideo, Modifier.fillMaxWidth().clickable { onEdit(EditTarget.Exercise(exercise)) })
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onPick(PickTarget.Group(item.id)) }.padding(vertical = 4.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = NovoColors.accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ejercicio al circuito", style = NovoFonts.semibold(13).copy(color = NovoColors.accent))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseRow(exercise: RoutineExercise, openVideo: (String) -> Unit, modifier: Modifier) {
    Row(modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF3A3A3C))
                .clickable(enabled = exercise.videoUrl != null) { exercise.videoUrl?.let(openVideo) },
        ) {
            Icon(
                if (exercise.videoUrl != null) Icons.Filled.PlayArrow else Icons.Filled.FitnessCenter,
                contentDescription = "Ver video de ${exercise.name}",
                tint = Color(0xFFC7C7CC),
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text(
                exercise.name.ifEmpty { "Ejercicio sin elegir" },
                style = NovoFonts.semibold(15).copy(color = if (exercise.exerciseId == null) NovoColors.statusExpiring else NovoColors.textPrimary),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (exercise.objective.isNotEmpty()) CoachChip(exercise.objective, Pink)
                if (exercise.rest.isNotEmpty()) Text("Descanso ${exercise.rest}", style = NovoFonts.body(12).copy(color = NovoColors.textSecondary))
            }
            if (exercise.instructions.isNotEmpty()) {
                Text(exercise.instructions, style = NovoFonts.body(12).copy(color = Color(0xFFC7C7CC)))
            }
        }
    }
}

@Composable
private fun BottomBar(vm: ProgramDetailViewModel, onAddExercise: () -> Unit, onAddGroup: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().background(NovoColors.background).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Box(Modifier.weight(1f)) {
            OutlinedButton(
                onClick = { menu = true },
                enabled = vm.days.isNotEmpty(),
                shape = CircleShape,
                border = BorderStroke(1.5.dp, if (vm.days.isEmpty()) NovoColors.textSecondary else NovoColors.accent),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("+ Agregar", style = NovoFonts.semibold(15).copy(color = if (vm.days.isEmpty()) NovoColors.textSecondary else Pink))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Ejercicio") }, onClick = {
                    menu = false
                    onAddExercise()
                })
                DropdownMenuItem(text = { Text("Circuito / superserie") }, onClick = {
                    menu = false
                    onAddGroup()
                })
            }
        }
        Box(Modifier.weight(1f)) {
            if (vm.isSaving) {
                Box(Modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(NovoColors.accent), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                }
            } else {
                PrimaryButton(
                    text = if (!vm.isDirty && vm.savedOnce) "Guardada ✓" else "Guardar rutina",
                    onClick = { vm.save() },
                    enabled = vm.isDirty,
                )
            }
        }
    }
}
