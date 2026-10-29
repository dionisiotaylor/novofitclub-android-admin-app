package com.novofitclub.dashboard.feature.coach.evaluations

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.novofitclub.dashboard.NovoFitClubApp
import com.novofitclub.dashboard.core.net.ViewedCoach
import com.novofitclub.dashboard.core.ui.MemberAvatar
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.PrimaryButton
import com.novofitclub.dashboard.core.ui.cardBackground
import com.novofitclub.dashboard.core.ui.rememberPhotoCapture
import com.novofitclub.dashboard.feature.coach.CoachMemberRef
import com.novofitclub.dashboard.feature.sales.OverlayLayer
import com.novofitclub.dashboard.feature.sales.ScopedViewModels
import com.novofitclub.dashboard.feature.sales.TextAction
import com.novofitclub.dashboard.feature.sales.pickers.MemberPickerScreen
import com.novofitclub.dashboard.feature.sales.pickers.SaleMemberSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val gray = Color(0xFF9A9A9E)
private val fieldGray = Color(0xFF8E8E93)

/**
 * Nueva / Editar evaluación, and for a new one the Firma right after saving.
 * [onClose] (changed) closes the sheet; [onCreateRoutine] asks the host to
 * open (or create) the evaluation's program. Host inside ScopedViewModels.
 */
@Composable
fun EvaluationFlow(
    app: NovoFitClubApp,
    member: CoachMemberRef,
    existing: Evaluation?,
    viewing: ViewedCoach?,
    onClose: (Boolean) -> Unit,
    onCreateRoutine: (SignTarget) -> Unit,
) {
    var signing by remember { mutableStateOf<SignTarget?>(null) }
    val target = signing
    if (target != null) {
        SignatureScreen(
            app = app,
            target = target,
            onClose = { onClose(true) },
            onCreateRoutine = { onCreateRoutine(target) },
        )
        return
    }
    EvaluationFormScreen(app, member, existing, viewing, onCancel = { onClose(false) }) { saved ->
        if (saved == null) onClose(true) else signing = saved
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EvaluationFormScreen(
    app: NovoFitClubApp,
    member: CoachMemberRef,
    existing: Evaluation?,
    viewing: ViewedCoach?,
    onCancel: () -> Unit,
    onSaved: (SignTarget?) -> Unit,
) {
    val vm: EvaluationFormViewModel = viewModel { EvaluationFormViewModel(app.api, member, existing, viewing?.id) }
    var pickingMember by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val focusManager = LocalFocusManager.current

    BackHandler(onBack = onCancel)
    LaunchedEffect(vm.step) { scroll.scrollTo(0) }
    LaunchedEffect(scroll.isScrollInProgress) { if (scroll.isScrollInProgress) focusManager.clearFocus() }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp)) {
            TextAction("Cancelar", onClick = onCancel)
            Text(
                if (vm.isNew) "Nueva evaluación" else "Editar evaluación",
                style = NovoFonts.semibold(17).copy(fontWeight = FontWeight.Bold),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${vm.step.ordinal + 1} de 4",
                style = NovoFonts.semibold(13).copy(color = gray),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.width(88.dp).padding(end = 12.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(NovoColors.separator))

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            MemberCard(vm) { pickingMember = true }
            vm.prefilledFrom?.let { from ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(EvalColors.first.copy(alpha = 0.12f)).padding(12.dp),
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = EvalColors.first, modifier = Modifier.size(18.dp))
                    Text(
                        "Precargamos salud y objetivos de la evaluación del ${EvalDates.slashed(from)}. Revisá que sigan vigentes; las medidas quedan vacías para tomarlas de nuevo.",
                        style = NovoFonts.body(13).copy(color = EvalColors.light),
                    )
                }
            }
            StepTabs(vm.step) { vm.step = it }
            vm.errorMessage?.let {
                Text(
                    it,
                    style = NovoFonts.medium(13).copy(color = EvalColors.overdue),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(EvalColors.overdue.copy(alpha = 0.12f)).padding(12.dp),
                )
            }
            when (vm.step) {
                EvalStep.Personal -> PersonalStep(vm)
                EvalStep.Health -> HealthStep(vm)
                EvalStep.Training -> TrainingStep(vm)
                EvalStep.Measures -> MeasuresStep(vm)
            }
            if (!vm.isNew && vm.step == EvalStep.Measures) {
                TextButton(onClick = { confirmingDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Eliminar evaluación", style = NovoFonts.semibold(15).copy(color = EvalColors.overdue))
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().background(NovoColors.background).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            val first = vm.step == EvalStep.Personal
            OutlinedButton(
                onClick = { vm.step = EvalStep.entries[vm.step.ordinal - 1] },
                enabled = !first,
                shape = CircleShape,
                border = BorderStroke(1.5.dp, if (first) Color(0xFF3A3A3C) else NovoColors.accent),
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Text("Anterior", style = NovoFonts.semibold(16).copy(color = if (first) Color(0xFF6E6E73) else EvalColors.pink))
            }
            val last = vm.step == EvalStep.Measures
            PrimaryButton(
                text = if (!last) "Siguiente" else if (vm.isNew) "Guardar y firmar" else "Guardar",
                onClick = {
                    focusManager.clearFocus()
                    if (last) vm.save(onSaved) else vm.step = EvalStep.entries[vm.step.ordinal + 1]
                },
                loading = vm.isSaving,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Eliminar evaluación") },
            text = { Text("¿Eliminar esta evaluación? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    vm.delete { onSaved(null) }
                }) { Text("Eliminar", color = EvalColors.overdue) }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancelar") } },
        )
    }
    if (pickingMember) {
        OverlayLayer {
            ScopedViewModels {
                BackHandler { pickingMember = false }
                val source: SaleMemberSource = viewModel { SaleMemberSource(app.api) }
                MemberPickerScreen(
                    source = source,
                    alreadySelected = emptyList(),
                    singleSelection = true,
                    onDone = { picked ->
                        pickingMember = false
                        picked.firstOrNull()?.let { vm.changeMember(CoachMemberRef(it.memberPostId, it.displayName, null)) }
                    },
                    onCancel = { pickingMember = false },
                )
            }
        }
    }
}

@Composable
private fun MemberCard(vm: EvaluationFormViewModel, onChange: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().cardBackground().padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        MemberAvatar(vm.member.initials, vm.member.avatarColor, vm.member.photoUrl, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(vm.member.name, style = NovoFonts.semibold(16), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(vm.routineCoachName?.let { "Coach asignado: $it" }, EvalDates.slashed(vm.date)).joinToString(" · "),
                style = NovoFonts.body(13).copy(color = gray),
                maxLines = 2,
            )
        }
        if (vm.isNew) TextAction("Cambiar", onClick = onChange, bold = true)
    }
}

@Composable
private fun StepTabs(step: EvalStep, onPick: (EvalStep) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NovoColors.card).padding(4.dp),
    ) {
        EvalStep.entries.forEach { s ->
            val on = s == step
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (on) NovoColors.accent else Color.Transparent)
                    .clickable { onPick(s) },
            ) {
                Text(s.label, style = NovoFonts.semibold(12).copy(color = if (on) Color.White else gray, fontWeight = FontWeight.Bold), maxLines = 1)
            }
        }
    }
}

// ---- Small form pieces ----

@Composable
private fun FieldLabel(text: String) {
    Text(text.uppercase(), style = NovoFonts.semibold(13).copy(color = gray, letterSpacing = 0.4.sp))
}

@Composable
private fun Labeled(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), content = { FieldLabel(label); content() })
}

@Composable
private fun FormInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    lines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    textSize: Int = 16,
    bold: Boolean = false,
) {
    val style = NovoFonts.body(textSize).copy(fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = lines == 1,
        minLines = lines,
        textStyle = style,
        cursorBrush = SolidColor(NovoColors.accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(contentAlignment = if (lines == 1) Alignment.CenterStart else Alignment.TopStart) {
                if (value.isEmpty()) Text(placeholder, style = style.copy(color = fieldGray, fontWeight = FontWeight.Normal))
                inner()
            }
        },
    )
}

/** A dark rounded box around a [FormInput]. */
@Composable
private fun BoxedInput(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, lines: Int = 1) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(NovoColors.card).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        FormInput(value, onValueChange, placeholder, lines = lines, textSize = if (lines == 1) 16 else 15)
    }
}

/** A row of equal segments; tapping the selected one clears it when [clearable]. */
@Composable
private fun <T> Segments(options: List<Pair<T, String>>, selected: T?, onPick: (T?) -> Unit, clearable: Boolean = true, pill: Boolean = true) {
    val shape = if (pill) CircleShape else RoundedCornerShape(12.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().clip(shape).background(NovoColors.card).padding(4.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.weight(1f).height(36.dp).clip(shape)
                    .background(if (on) NovoColors.accent else Color.Transparent)
                    .clickable { onPick(if (on && clearable) null else value) },
            ) {
                Text(label, style = NovoFonts.semibold(13).copy(color = if (on) Color.White else gray), maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(date: LocalDate?, placeholder: String, onChange: (LocalDate?) -> Unit, clearable: Boolean) {
    var showPicker by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(NovoColors.card)
            .clickable { showPicker = true }.padding(start = 14.dp, end = 6.dp),
    ) {
        Text(
            date?.let { EvalDates.slashed(it) } ?: placeholder,
            style = NovoFonts.body(16).copy(color = if (date == null) fieldGray else NovoColors.textPrimary),
            modifier = Modifier.weight(1f),
        )
        if (clearable && date != null) {
            Icon(
                Icons.Filled.Close, contentDescription = "Borrar fecha", tint = gray,
                modifier = Modifier.size(36.dp).clip(CircleShape).clickable { onChange(null) }.padding(8.dp),
            )
        }
    }
    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        val colors = DatePickerDefaults.colors(
            containerColor = NovoColors.card,
            selectedDayContainerColor = NovoColors.accent,
            todayDateBorderColor = NovoColors.accent,
            todayContentColor = NovoColors.accent,
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    showPicker = false
                }) { Text("Aceptar", color = NovoColors.accent) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancelar", color = NovoColors.textSecondary) } },
            colors = colors,
        ) { DatePicker(state = state, colors = colors) }
    }
}

// ---- Steps ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonalStep(vm: EvaluationFormViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Labeled("Coach para rutina") {
            var open by remember { mutableStateOf(false) }
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(NovoColors.card)
                        .clickable { open = true }.padding(horizontal = 14.dp),
                ) {
                    Text(
                        vm.routineCoachName ?: "Elegí un coach",
                        style = NovoFonts.body(16).copy(color = if (vm.routineCoachName == null) fieldGray else NovoColors.textPrimary),
                        modifier = Modifier.weight(1f),
                    )
                    Text("Cambiar", style = NovoFonts.semibold(13).copy(color = EvalColors.pink))
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    vm.coaches.forEach { coach ->
                        DropdownMenuItem(text = { Text(coach.name) }, onClick = {
                            vm.routineCoachId = coach.id
                            open = false
                        })
                    }
                }
            }
            Text(vm.deadlineHint, style = NovoFonts.body(12).copy(color = gray))
        }
        Labeled("Fecha de evaluación") {
            DateField(vm.date, "dd/mm/aaaa", { it?.let { d -> vm.date = d } }, clearable = false)
        }
        Labeled("Estilo de vida") {
            Segments(LIFESTYLES, vm.lifestyle, { vm.lifestyle = it })
        }
        Labeled("Objetivo del programa") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Goals.PILLS.forEach { goal -> GoalPill(goal, goal in vm.goals) { vm.toggleGoal(goal) } }
                GoalPill("Otro…", vm.otherGoal) {
                    vm.otherGoal = !vm.otherGoal
                    if (!vm.otherGoal) vm.otherText = ""
                }
            }
            if (vm.otherGoal) BoxedInput(vm.otherText, { vm.otherText = it }, "Describí el objetivo…", lines = 2)
        }
        Labeled("Última fecha de entrenamiento") {
            DateField(vm.lastTraining, "dd/mm/aaaa", { vm.lastTraining = it }, clearable = true)
        }
        Labeled("Experiencia previa en gimnasio") {
            BoxedInput(vm.gymExperience, { vm.gymExperience = it }, "Ej. 2 años CrossFit, ninguna…", lines = 3)
        }
    }
}

@Composable
private fun GoalPill(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = NovoFonts.semibold(13).copy(color = if (on) Color.White else Color(0xFFD1D1D6)),
        modifier = Modifier
            .clip(CircleShape)
            .background(if (on) NovoColors.accent else Color.Transparent)
            .border(1.5.dp, if (on) NovoColors.accent else Color(0xFF48484A), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HealthStep(vm: EvaluationFormViewModel) {
    val context = LocalContext.current
    var view by remember { mutableStateOf(BodyView.Front) }
    val camera = rememberPhotoCapture { photos -> photos.forEach { vm.uploadPhoto(it.jpeg) } }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { vm.uploadFile(context, it) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().cardBackground().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Áreas de dolor y problemas", style = NovoFonts.semibold(15).copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                Row(Modifier.clip(CircleShape).background(NovoColors.background).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    BodyView.entries.forEach { v ->
                        val on = v == view
                        Text(
                            if (v == BodyView.Front) "Frente" else "Espalda",
                            style = NovoFonts.semibold(12).copy(color = if (on) Color.White else gray),
                            modifier = Modifier.clip(CircleShape).background(if (on) Color(0xFF3A3A3C) else Color.Transparent)
                                .clickable { view = v }.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                BodyFigure(view, vm.pain.toSet(), 174.dp) { vm.togglePain(it) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tocá una zona para marcarla o quitarla.", style = NovoFonts.body(12).copy(color = gray))
                    if (vm.pain.isEmpty()) {
                        Text("Sin áreas marcadas", style = NovoFonts.body(13).copy(color = EvalColors.light))
                    } else {
                        BodyZones.sorted(vm.pain).forEach { id ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.clip(CircleShape).background(NovoColors.accent.copy(alpha = 0.18f))
                                    .clickable { vm.togglePain(id) }.padding(start = 12.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
                            ) {
                                Text(BodyZones.label(id), style = NovoFonts.semibold(12).copy(color = EvalColors.pink))
                                Icon(Icons.Filled.Close, contentDescription = "Quitar ${BodyZones.label(id)}", tint = EvalColors.pink, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }
        }
        Labeled("Lesiones activas o recientes") {
            BoxedInput(vm.injuries, { vm.injuries = it }, "Ej. molestia en rodilla al correr", lines = 2)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) { Labeled("Cirugías previas") { BoxedInput(vm.surgeries, { vm.surgeries = it }, "Ninguna") } }
            Column(Modifier.weight(1f)) { Labeled("Medicamentos") { BoxedInput(vm.medications, { vm.medications = it }, "Ninguno") } }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(NovoColors.card).padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            ) {
                Text("¿Condición cardíaca?", style = NovoFonts.semibold(15), modifier = Modifier.weight(1f))
                Row(Modifier.clip(CircleShape).background(NovoColors.background).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(false to "No", true to "Sí").forEach { (value, label) ->
                        val on = vm.cardiacAnswer == value
                        Text(
                            label,
                            style = NovoFonts.semibold(13).copy(color = if (on) Color.White else gray),
                            modifier = Modifier.clip(CircleShape)
                                .background(if (!on) Color.Transparent else if (value) NovoColors.accent else Color(0xFF3A3A3C))
                                .clickable { vm.cardiacAnswer = if (on) null else value }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            if (vm.cardiacAnswer == true) BoxedInput(vm.cardiacText, { vm.cardiacText = it }, "Describí la condición", lines = 2)
        }
        Labeled("Comentarios adicionales") {
            BoxedInput(vm.comments, { vm.comments = it }, "Ejercicios contraindicados, notas de fisioterapia…", lines = 2)
        }
        Labeled("Documentos e imágenes") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DashedButton("Subir archivo", { Icon(Icons.Outlined.UploadFile, null, tint = Color.White, modifier = Modifier.size(18.dp)) }, Modifier.weight(1f)) {
                    files.launch(arrayOf("image/*", "application/pdf"))
                }
                DashedButton("Tomar foto", { Icon(Icons.Outlined.PhotoCamera, null, tint = Color.White, modifier = Modifier.size(18.dp)) }, Modifier.weight(1f)) {
                    camera.takePhoto()
                }
            }
            if (vm.uploading > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(color = NovoColors.accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Text("Subiendo…", style = NovoFonts.body(13).copy(color = gray))
                }
            }
            vm.uploadError?.let { Text(it, style = NovoFonts.body(13).copy(color = EvalColors.overdue)) }
            vm.documents.forEach { doc ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().cardBackground().padding(10.dp),
                ) {
                    DocumentThumb(doc, 44.dp) {}
                    Column(Modifier.weight(1f)) {
                        Text(doc.name, style = NovoFonts.medium(14), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(doc.type, style = NovoFonts.body(12).copy(color = gray), maxLines = 1)
                    }
                    Icon(
                        Icons.Filled.Close, contentDescription = "Quitar documento", tint = gray,
                        modifier = Modifier.size(36.dp).clip(CircleShape).clickable { vm.documents.remove(doc) }.padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DashedButton(label: String, icon: @Composable () -> Unit, modifier: Modifier, onClick: () -> Unit) {
    val dash = Color(0xFF5A5A5E)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    dash,
                    topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            }
            .clickable(onClick = onClick),
    ) {
        icon()
        Text(label, style = NovoFonts.semibold(14))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrainingStep(vm: EvaluationFormViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Labeled("Días de entrenamiento / semana") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..7).forEach { d -> Tile("$d", vm.days == d, Modifier.weight(1f)) { vm.days = if (vm.days == d) null else d } }
            }
        }
        Labeled("Máx. horas / día") {
            val hours = (1..8).map { it * 0.5 }
            hours.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { h -> Tile(evalNumber(h), vm.hours == h, Modifier.weight(1f)) { vm.hours = if (vm.hours == h) null else h } }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().cardBackground().padding(14.dp)) {
            Text("Objetivos seleccionados", style = NovoFonts.semibold(13).copy(color = gray))
            val goals = vm.goalText.split(", ").filter { it.isNotBlank() }
            if (goals.isEmpty()) {
                Text("Aún no hay objetivos. Elegilos en Personal.", style = NovoFonts.body(13).copy(color = gray))
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { goals.forEach { PinkChip(it) } }
            }
            val summary = listOfNotNull(
                vm.days?.let { if (it == 1) "1 día por semana" else "$it días por semana" },
                vm.hours?.let { "máx. ${evalNumber(it)} h por día" },
            ).joinToString(" · ")
            if (summary.isNotEmpty()) Text(summary, style = NovoFonts.body(13).copy(color = EvalColors.light))
        }
    }
}

@Composable
private fun Tile(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(if (on) NovoColors.accent else NovoColors.card).clickable(onClick = onClick),
    ) {
        Text(label, style = NovoFonts.semibold(16).copy(color = if (on) Color.White else EvalColors.light, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun MeasuresStep(vm: EvaluationFormViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        vm.previous?.date?.let {
            Text(
                "Composición corporal de hoy. Debajo de cada valor ves el cambio desde la medición del ${EvalDates.slashed(it)}.",
                style = NovoFonts.body(13).copy(color = gray),
            )
        }
        MeasureGrid(vm, listOf(Measure.Height, Measure.Weight))
        FieldLabel("Composición corporal")
        MeasureGrid(vm, listOf(Measure.Muscle, Measure.FatPct, Measure.FatKg, Measure.Visceral))
        BmiCard(vm.bmi)
    }
}

@Composable
private fun MeasureGrid(vm: EvaluationFormViewModel, measures: List<Measure>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        measures.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { m -> MeasureField(vm, m, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MeasureField(vm: EvaluationFormViewModel, measure: Measure, modifier: Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(NovoColors.card).padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(measure.label, style = NovoFonts.semibold(13).copy(color = gray), maxLines = 1, overflow = TextOverflow.Ellipsis)
        FormInput(
            value = vm.measures[measure].orEmpty(),
            onValueChange = { text -> vm.measures[measure] = text.filter { it.isDigit() || it == ',' || it == '.' } },
            placeholder = if (measure == Measure.Height) "165" else "0,0",
            keyboardType = KeyboardType.Decimal,
            textSize = 22,
            bold = true,
            modifier = Modifier.height(34.dp),
        )
        val before = vm.previous?.let { measure.of(it) }
        val good = measure.goodUp
        if (before != null && good != null) {
            val now = vm.value(measure)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Antes ${evalNumber(before, 2)}", style = NovoFonts.body(12).copy(color = gray))
                if (now != null) {
                    val diff = kotlin.math.round((now - before) * 100) / 100
                    if (diff != 0.0) {
                        val isGood = if (good) diff > 0 else diff < 0
                        val sign = if (diff > 0) "+" else "−"
                        Text(
                            "$sign${evalNumber(kotlin.math.abs(diff), 2)}${if (measure.unit.isNotEmpty()) " ${measure.unit}" else ""}",
                            style = NovoFonts.semibold(12).copy(color = if (isGood) EvalColors.good else EvalColors.overdue),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BmiCard(bmi: Double?) {
    val weights = listOf(1f, 1.3f, 1f, 1f)
    val bands = listOf("Bajo peso" to EvalColors.first, "Normal" to EvalColors.good, "Sobrepeso" to EvalColors.soon, "Obesidad" to EvalColors.overdue)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().cardBackground().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("IMC", style = NovoFonts.semibold(15).copy(fontWeight = FontWeight.Bold))
            bmi?.let {
                val (label, color) = Bmi.category(it)
                Text("  ${evalNumber(it)}", style = NovoFonts.semibold(15).copy(fontWeight = FontWeight.Bold))
                Text(" · $label", style = NovoFonts.semibold(13).copy(color = color))
            }
            Spacer(Modifier.weight(1f))
            Text("Se calcula con altura y peso", style = NovoFonts.body(12).copy(color = gray))
        }
        Box(Modifier.fillMaxWidth().height(14.dp)) {
            Row(Modifier.fillMaxWidth().height(6.dp).align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                bands.forEachIndexed { i, (_, color) -> Box(Modifier.weight(weights[i]).fillMaxSize().clip(RoundedCornerShape(3.dp)).background(color)) }
            }
            bmi?.let {
                // 15–18.5–25–30–40 laid over the 1 : 1.3 : 1 : 1 bands.
                val edges = listOf(15.0, 18.5, 25.0, 30.0, 40.0)
                val clamped = it.coerceIn(15.0, 40.0)
                val band = (0 until 4).first { b -> clamped <= edges[b + 1] || b == 3 }
                val within = ((clamped - edges[band]) / (edges[band + 1] - edges[band])).toFloat()
                val total = weights.sum()
                val fraction = (weights.take(band).sum() + weights[band] * within) / total
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.padding(start = (maxWidth - 14.dp) * fraction).size(14.dp).clip(CircleShape)
                            .background(Color.White).border(2.dp, NovoColors.card, CircleShape),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            bands.forEachIndexed { i, (label, _) -> Text(label, style = NovoFonts.body(11).copy(color = gray), modifier = Modifier.weight(weights[i]), maxLines = 1) }
        }
    }
}
