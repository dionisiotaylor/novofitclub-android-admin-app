package com.novofitclub.dashboard.feature.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.novofitclub.dashboard.NovoFitClubApp
import com.novofitclub.dashboard.core.net.bool
import com.novofitclub.dashboard.core.net.num
import com.novofitclub.dashboard.core.net.str
import com.novofitclub.dashboard.core.ui.LoadingState
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.SecondaryButton
import com.novofitclub.dashboard.core.ui.ThemedField
import com.novofitclub.dashboard.core.ui.cardBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DangerRed = Color(0xFFFF6961)

/** A full-screen sheet with Cancelar / Listo and a scrolling form of the app's cards. */
@Composable
fun EditorFrame(title: String, onCancel: () -> Unit, onDone: (() -> Unit)?, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(NovoColors.background).safeDrawingPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                TextButton(onClick = onCancel) { Text("Cancelar", style = NovoFonts.medium(15).copy(color = NovoColors.accent)) }
                Text(title, style = NovoFonts.semibold(17), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                if (onDone != null) {
                    TextButton(onClick = onDone) { Text("Listo", style = NovoFonts.semibold(15).copy(color = NovoColors.accent)) }
                } else {
                    Spacer(Modifier.width(88.dp))
                }
            }
            HorizontalDivider(color = NovoColors.separator)
            content()
        }
    }
}

/** A labeled text field with optional one-tap suggestions. */
@Composable
private fun EditorField(title: String, value: String, onChange: (String) -> Unit, placeholder: String, suggestions: List<String> = emptyList(), multiline: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = NovoFonts.semibold(13).copy(color = NovoColors.textSecondary, letterSpacing = 0.4.sp))
        if (multiline) {
            TextField(
                value = value,
                onValueChange = onChange,
                placeholder = { Text(placeholder, style = NovoFonts.body(16).copy(color = NovoColors.textSecondary)) },
                textStyle = NovoFonts.body(16),
                minLines = 2,
                maxLines = 6,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = NovoColors.card,
                    unfocusedContainerColor = NovoColors.card,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = NovoColors.accent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            ThemedField(value, onChange, placeholder)
        }
        if (suggestions.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(suggestions) { suggestion ->
                    val selected = value == suggestion
                    Text(
                        suggestion,
                        style = NovoFonts.semibold(13).copy(color = if (selected) Color.White else Color(0xFFD1D1D6)),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (selected) NovoColors.accent else NovoColors.card)
                            .clickable { onChange(suggestion) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MoveButtons(firstLabel: String, secondLabel: String, canFirst: Boolean, canSecond: Boolean, onMove: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { if (canFirst) SecondaryButton(firstLabel, { onMove(-1) }) }
        Box(Modifier.weight(1f)) { if (canSecond) SecondaryButton(secondLabel, { onMove(1) }) }
    }
}

@Composable
private fun DangerButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
        Text(text, style = NovoFonts.semibold(15).copy(color = DangerRed))
    }
}

private val OBJECTIVES = listOf("3x10 reps", "3x12 reps", "4x10 reps", "4x12 reps", "3x15 reps", "30 s", "40 s", "1 min")
private val RESTS = listOf("30 sec", "45 sec", "1 min", "1:30 min", "2 min")

/** One exercise of the routine: which exercise, objective, rest and instructions — the web builder's fields. */
@Composable
fun ExerciseEditorDialog(
    app: NovoFitClubApp,
    initial: RoutineExercise,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSave: (RoutineExercise) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var exercise by remember { mutableStateOf(initial) }
    var choosing by remember { mutableStateOf(false) }
    EditorFrame("Ejercicio", onCancel = onDismiss, onDone = {
        onSave(exercise)
        onDismiss()
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().cardBackground().clickable { choosing = true }.padding(14.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(exercise.name.ifEmpty { "Elegir ejercicio" }, style = NovoFonts.semibold(16))
                    if (exercise.muscle.isNotEmpty()) Text(exercise.muscle, style = NovoFonts.body(13).copy(color = NovoColors.textSecondary))
                }
                Text("Cambiar", style = NovoFonts.semibold(14).copy(color = NovoColors.accent))
            }
            EditorField("Objetivo", exercise.objective, { exercise = exercise.copy(objective = it) }, "4x12 reps", OBJECTIVES)
            EditorField("Descanso", exercise.rest, { exercise = exercise.copy(rest = it) }, "1 min", RESTS)
            EditorField("Instrucciones", exercise.instructions, { exercise = exercise.copy(instructions = it) }, "Rango corto, espalda neutra…", multiline = true)
            MoveButtons("↑ Subir", "↓ Bajar", canMoveUp, canMoveDown) {
                onSave(exercise)
                onMove(it)
                onDismiss()
            }
            DangerButton("Quitar de la rutina") {
                onDelete()
                onDismiss()
            }
        }
    }
    if (choosing) {
        ExercisePickerDialog(app, onDismiss = { choosing = false }) { exercise = exercise.using(it) }
    }
}

/** A circuit / superserie: its name and rounds. */
@Composable
fun GroupEditorDialog(
    group: RoutineItem.Group,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSave: (String, String) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(group.name) }
    var rounds by remember { mutableStateOf(group.rounds) }
    EditorFrame("Circuito", onCancel = onDismiss, onDone = {
        onSave(name, rounds)
        onDismiss()
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            EditorField("Nombre", name, { name = it }, "Circuito A", listOf("Calentamiento", "Circuito A", "Circuito B", "Superserie", "Core", "Finisher"))
            EditorField("Rondas", rounds, { rounds = it }, "3 rondas", listOf("2 rondas", "3 rondas", "4 rondas", "5 rondas"), multiline = true)
            MoveButtons("↑ Subir", "↓ Bajar", canMoveUp, canMoveDown) {
                onSave(name, rounds)
                onMove(it)
                onDismiss()
            }
            DangerButton("Eliminar el circuito y sus ejercicios") {
                onDelete()
                onDismiss()
            }
        }
    }
}

/** A day of the routine: name ("Día 1"), what it is ("Tren inferior"), its place. */
@Composable
fun DayEditorDialog(
    day: RoutineDay,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onSave: (String, String) -> Unit,
    onMove: (Int) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(day.name) }
    var label by remember { mutableStateOf(day.label) }
    var confirmingDelete by remember { mutableStateOf(false) }
    EditorFrame("Día", onCancel = onDismiss, onDone = {
        onSave(name, label)
        onDismiss()
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            EditorField("Nombre", name, { name = it }, "Día 1")
            EditorField("Descripción", label, { label = it }, "Tren inferior", listOf("Tren inferior", "Tren superior", "Full body", "Empuje", "Jalón", "Pierna", "Cardio", "METCON"))
            MoveButtons("← Antes", "Después →", canMoveLeft, canMoveRight) {
                onSave(name, label)
                onMove(it)
                onDismiss()
            }
            SecondaryButton("Duplicar día", {
                onSave(name, label)
                onDuplicate()
                onDismiss()
            })
            DangerButton("Eliminar día") { confirmingDelete = true }
        }
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("¿Eliminar ${name.ifEmpty { "este día" }} con todos sus ejercicios?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    onDelete()
                    onDismiss()
                }) { Text("Eliminar día", color = DangerRed) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancelar") } },
        )
    }
}

/** The exercise library, searchable by name (`novo-staff/v1/exercises`). */
@Composable
fun ExercisePickerDialog(app: NovoFitClubApp, onDismiss: () -> Unit, onPick: (LibraryExercise) -> Unit) {
    var search by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<LibraryExercise>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(search) {
        // Waits for the coach to stop typing before asking the server.
        if (search.isNotEmpty()) delay(300)
        try {
            results = app.api.getArray("novo-staff/v1/exercises", mapOf("search" to search)).mapNotNull { LibraryExercise.from(it) }
            error = null
        } catch (e: Exception) {
            error = e.localizedMessage
        }
    }
    EditorFrame("Ejercicios", onCancel = onDismiss, onDone = null) {
        ThemedField(search, { search = it }, "Buscar ejercicio", Modifier.padding(16.dp))
        val list = results
        when {
            error != null -> Text(error.orEmpty(), style = NovoFonts.body(14).copy(color = NovoColors.textSecondary), modifier = Modifier.padding(16.dp))
            list == null -> LoadingState()
            list.isEmpty() -> Text(
                if (search.isEmpty()) "Sin ejercicios." else "Ningún ejercicio se llama así. Los nombres están en inglés: probá “squat”, “press”, “row”…",
                style = NovoFonts.body(14).copy(color = NovoColors.textSecondary),
                modifier = Modifier.padding(16.dp),
            )
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(list, key = { it.id }) { exercise ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().cardBackground().clickable {
                            onPick(exercise)
                            onDismiss()
                        }.padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(exercise.name, style = NovoFonts.semibold(15))
                            if (exercise.muscle.isNotEmpty()) Text(exercise.muscle, style = NovoFonts.body(12).copy(color = NovoColors.textSecondary))
                        }
                        if (exercise.videoUrl != null) Icon(Icons.Outlined.PlayCircle, contentDescription = null, tint = NovoColors.textSecondary)
                    }
                }
            }
        }
    }
}

/**
 * Copies another program's routine (templates first, then the recent ones —
 * member-evaluations' `/routines`, as the web's "Importar rutina").
 */
@Composable
fun RoutineImportDialog(
    app: NovoFitClubApp,
    excluding: Int,
    hasDays: Boolean,
    onDismiss: () -> Unit,
    onImport: (List<RoutineDay>, Boolean) -> Unit,
) {
    data class Source(val id: Int, val title: String, val days: Int, val isTemplate: Boolean)

    var search by remember { mutableStateOf("") }
    var sources by remember { mutableStateOf<List<Source>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var chosen by remember { mutableStateOf<Source?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(search) {
        if (search.isNotEmpty()) delay(300)
        try {
            sources = app.api.getArray("member-evaluations/v1/routines", mapOf("search" to search)).mapNotNull {
                val id = it.num("id")?.toInt() ?: return@mapNotNull null
                if (id == excluding) return@mapNotNull null
                Source(id, it.str("title") ?: "Programa", it.num("days")?.toInt() ?: 0, it.bool("is_template") ?: false)
            }
            error = null
        } catch (e: Exception) {
            error = e.localizedMessage
        }
    }

    fun import(source: Source, replacing: Boolean) {
        chosen = null
        loading = true
        scope.launch {
            try {
                val days = app.api.getArray("gym/v1/program-routines/${source.id}").map { RoutineDay.from(it) }
                onImport(days, replacing)
                onDismiss()
            } catch (e: Exception) {
                error = e.localizedMessage
            }
            loading = false
        }
    }

    EditorFrame("Importar rutina", onCancel = onDismiss, onDone = null) {
        ThemedField(search, { search = it }, "Buscar por nombre del programa", Modifier.padding(16.dp))
        val list = sources
        when {
            loading || list == null -> LoadingState()
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                error?.let { message -> item { Text(message, style = NovoFonts.body(14).copy(color = NovoColors.textSecondary)) } }
                items(list, key = { it.id }) { source ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().cardBackground().clickable {
                            if (hasDays) chosen = source else import(source, replacing = true)
                        }.padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(source.title, style = NovoFonts.semibold(15), maxLines = 2)
                            Text(if (source.days == 1) "1 día" else "${source.days} días", style = NovoFonts.body(12).copy(color = NovoColors.textSecondary))
                        }
                        if (source.isTemplate) CoachChip("Plantilla", NovoColors.statusNeutral)
                    }
                }
            }
        }
    }
    chosen?.let { source ->
        AlertDialog(
            onDismissRequest = { chosen = null },
            title = { Text("Importar “${source.title}”") },
            text = {
                Column {
                    TextButton(onClick = { import(source, replacing = false) }) { Text("Agregar sus días al final") }
                    TextButton(onClick = { import(source, replacing = true) }) { Text("Reemplazar la rutina actual", color = DangerRed) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { chosen = null }) { Text("Cancelar") } },
        )
    }
}
