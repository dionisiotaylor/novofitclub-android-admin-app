package com.novofitclub.dashboard.feature.coach.evaluations

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.novofitclub.dashboard.NovoFitClubApp
import com.novofitclub.dashboard.core.net.ViewedCoach
import com.novofitclub.dashboard.core.net.WPApiClient
import com.novofitclub.dashboard.core.ui.ErrorState
import com.novofitclub.dashboard.core.ui.LoadingState
import com.novofitclub.dashboard.core.ui.MemberAvatar
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.cardBackground
import com.novofitclub.dashboard.feature.coach.CoachApi
import com.novofitclub.dashboard.feature.coach.CoachChip
import com.novofitclub.dashboard.feature.coach.CoachDates
import com.novofitclub.dashboard.feature.coach.CoachMemberRef
import com.novofitclub.dashboard.feature.coach.ProgramDetailScreen
import com.novofitclub.dashboard.feature.members.FullPhotoViewer
import com.novofitclub.dashboard.feature.sales.CircleIconButton
import com.novofitclub.dashboard.feature.sales.OverlayLayer
import com.novofitclub.dashboard.feature.sales.ScopedViewModels
import kotlinx.coroutines.launch

class MemberRecordViewModel(private val api: WPApiClient, private val memberId: Int) : ViewModel() {
    var record by mutableStateOf<MemberRecord?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var openProgramId by mutableStateOf<Int?>(null)
    var creatingProgram by mutableStateOf(false)
        private set
    var programError by mutableStateOf<String?>(null)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            isLoading = true
            try {
                record = EvaluationApi.record(api, memberId)
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Error desconocido."
            }
            isLoading = false
        }
    }

    /** The evaluation's program: open it, or create it first (member-evaluations links it to the evaluation). */
    fun openRoutine(evaluationId: Int, programId: Int?) {
        if (programId != null) {
            openProgramId = programId
            return
        }
        if (creatingProgram) return
        viewModelScope.launch {
            creatingProgram = true
            try {
                openProgramId = CoachApi.createProgram(api, evaluationId)
                load()
            } catch (e: Exception) {
                programError = e.localizedMessage ?: "Error desconocido."
            }
            creatingProgram = false
        }
    }
}

/** What the form sheet is doing. */
private sealed interface FormTarget {
    data object New : FormTarget
    data class Edit(val evaluation: Evaluation) : FormTarget
}

/**
 * Expediente del Miembro: progress, the latest body composition and health,
 * every measurement, and the programs. [startNew] opens a new evaluation
 * straight away (from the list's "Nueva evaluación"); closing that flow
 * comes back to the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberRecordScreen(app: NovoFitClubApp, member: CoachMemberRef, viewing: ViewedCoach?, startNew: Boolean, onBack: () -> Unit) {
    val vm: MemberRecordViewModel = viewModel { MemberRecordViewModel(app.api, member.id) }
    var form by remember { mutableStateOf<FormTarget?>(if (startNew) FormTarget.New else null) }
    var signing by remember { mutableStateOf<SignTarget?>(null) }
    var photo by remember { mutableStateOf<String?>(null) }
    var closesOnFinish by remember { mutableStateOf(startNew) }
    val context = LocalContext.current

    BackHandler(onBack = onBack)
    val record = vm.record
    val shownMember = record?.member ?: member

    PullToRefreshBox(isRefreshing = vm.isLoading && record != null, onRefresh = { vm.load() }, modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Volver a evaluaciones", onBack)
                Text("EXPEDIENTE DEL MIEMBRO", style = NovoFonts.semibold(13).copy(color = Color(0xFF9A9A9E), letterSpacing = 0.4.sp))
            }
            Hero(shownMember, record)

            val error = vm.errorMessage
            when {
                record == null && vm.isLoading -> LoadingState(Modifier.height(240.dp))
                record == null && error != null -> ErrorState(error, onRetry = { vm.load() })
                record != null -> RecordBody(
                    record = record,
                    onEvaluate = {
                        closesOnFinish = false
                        form = FormTarget.New
                    },
                    onEdit = { form = FormTarget.Edit(it) },
                    onSign = { signing = SignTarget.from(it, record.member) },
                    onOpenProgram = { vm.openProgramId = it },
                    onCreateRoutine = { vm.openRoutine(it.id, it.programId) },
                    creatingProgram = vm.creatingProgram,
                    onOpenDocument = { doc ->
                        if (doc.isImage) photo = doc.url else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(doc.url)))
                    },
                )
            }
        }
    }

    vm.programError?.let { message ->
        AlertDialog(
            onDismissRequest = { vm.programError = null },
            title = { Text("No se pudo crear la rutina") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { vm.programError = null }) { Text("OK") } },
        )
    }
    photo?.let { url -> FullPhotoViewer(url) { photo = null } }

    form?.let { target ->
        OverlayLayer {
            ScopedViewModels {
                EvaluationFlow(
                    app = app,
                    member = shownMember,
                    existing = (target as? FormTarget.Edit)?.evaluation,
                    viewing = viewing,
                    onClose = { changed ->
                        form = null
                        if (closesOnFinish) onBack() else if (changed) vm.load()
                    },
                    onCreateRoutine = { target2 ->
                        form = null
                        closesOnFinish = false
                        vm.openRoutine(target2.evaluationId, target2.programId)
                    },
                )
            }
        }
    }
    signing?.let { target ->
        OverlayLayer {
            ScopedViewModels {
                SignatureScreen(
                    app = app,
                    target = target,
                    onClose = { signed ->
                        signing = null
                        if (signed) vm.load()
                    },
                    onCreateRoutine = {
                        signing = null
                        vm.openRoutine(target.evaluationId, target.programId)
                    },
                )
            }
        }
    }
    vm.openProgramId?.let { id ->
        OverlayLayer {
            ScopedViewModels {
                ProgramDetailScreen(app, id) {
                    vm.openProgramId = null
                    vm.load()
                }
            }
        }
    }
}

@Composable
private fun Hero(member: CoachMemberRef, record: MemberRecord?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        MemberAvatar(member.initials, member.avatarColor, member.photoUrl, 60.dp)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(member.name, style = NovoFonts.heading(23), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (record != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    record.typeLabel?.let { CoachChip(it, record.typeColor ?: EvalColors.light) }
                    when (record.status) {
                        "active" -> CoachChip("Activo", NovoColors.statusActive)
                        "expired" -> CoachChip(record.statusLabel.ifEmpty { "Vencido" }, NovoColors.statusExpired)
                        "" -> {}
                        else -> CoachChip(record.statusLabel.ifEmpty { record.status }, EvalColors.light)
                    }
                    val line = listOfNotNull(
                        record.expiration?.let { "Vence ${EvalDates.short(it)}" },
                        record.paymentDay?.let { "Día de pago $it" },
                    ).joinToString(" · ")
                    if (line.isNotEmpty()) Text(line, style = NovoFonts.body(12).copy(color = Color(0xFF9A9A9E)), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun RecordBody(
    record: MemberRecord,
    onEvaluate: () -> Unit,
    onEdit: (Evaluation) -> Unit,
    onSign: (Evaluation) -> Unit,
    onOpenProgram: (Int) -> Unit,
    onCreateRoutine: (Evaluation) -> Unit,
    creatingProgram: Boolean,
    onOpenDocument: (EvalDocument) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Stats(record)
        NextBanner(record, onEvaluate)
        val chrono = record.chronological
        if (chrono.count { e -> Measure.entries.any { it != Measure.Height && it.of(e) != null } } >= 2) ProgressCard(chrono)
        record.latest?.let { latest ->
            CompositionCard(latest)
            HealthCard(latest)
            if (latest.documents.isNotEmpty()) DocumentsCard(latest.documents, onOpenDocument)
        }
        if (record.evaluations.isNotEmpty()) HistoryCard(record.evaluations, onEdit, onSign)
        ProgramsSection(record, onOpenProgram, onCreateRoutine, creatingProgram)
    }
}

@Composable
private fun Stats(record: MemberRecord) {
    val weights = record.chronological.mapNotNull { it.weight }
    val latestWeight = record.evaluations.firstNotNullOfOrNull { it.weight }
    val latestFat = record.evaluations.firstNotNullOfOrNull { it.fatPct }
    val change = if (weights.size >= 2) weights.last() - weights.first() else null
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Peso actual", latestWeight?.let { evalNumber(it) }, " kg", null, Modifier.weight(1f))
            StatTile("Grasa corp. actual", latestFat?.let { evalNumber(it) }, " %", null, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                "Cambio desde la 1ª",
                change?.let { (if (it > 0) "+" else if (it < 0) "−" else "") + evalNumber(kotlin.math.abs(it)) },
                " kg",
                change?.let { if (it < 0) EvalColors.good else if (it > 0) EvalColors.soon else null },
                Modifier.weight(1f),
            )
            StatTile("Evaluaciones", "${record.evaluations.size}", "", null, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String?, unit: String, color: Color?, modifier: Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier.cardBackground().padding(14.dp)) {
        Text(label, style = NovoFonts.semibold(12).copy(color = Color(0xFF9A9A9E)), maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value ?: "—", style = NovoFonts.title(24).copy(color = color ?: NovoColors.textPrimary))
            if (value != null && unit.isNotEmpty()) {
                Text(unit, style = NovoFonts.semibold(13).copy(color = Color(0xFF9A9A9E)), modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

@Composable
private fun NextBanner(record: MemberRecord, onEvaluate: () -> Unit) {
    val last = record.latest?.date
    val title: String
    val subtitle: String
    val tint: Color?
    if (last == null) {
        title = "Sin evaluaciones todavía"
        subtitle = "Hacé la primera medición."
        tint = null
    } else {
        val next = last.plusDays(EVALUATION_EVERY_DAYS)
        val d = EvalDates.days(CoachDates.today(), next)
        title = "Próxima evaluación: " + when {
            d < 0 -> "vencida hace ${-d} ${if (d == -1) "día" else "días"}"
            d == 0 -> "hoy"
            d <= 7 -> "en $d ${if (d == 1) "día" else "días"}"
            else -> EvalDates.short(next)
        }
        subtitle = "Última: ${EvalDates.short(last)} · cada 6 semanas"
        tint = when {
            d < 0 -> EvalColors.overdue
            d <= 7 -> EvalColors.soon
            else -> null
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(tint?.copy(alpha = 0.10f) ?: NovoColors.card)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = NovoFonts.semibold(14).copy(color = tint ?: NovoColors.textPrimary, fontWeight = FontWeight.Bold))
            Text(subtitle, style = NovoFonts.body(12).copy(color = EvalColors.light))
        }
        Text(
            "Evaluar",
            style = NovoFonts.semibold(14).copy(color = Color.White),
            modifier = Modifier.clip(CircleShape).background(NovoColors.accent).clickable(onClick = onEvaluate).padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun RecordCard(title: String, trailing: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().cardBackground().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = NovoFonts.semibold(15).copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
            trailing?.let { Text(it, style = NovoFonts.medium(12).copy(color = Color(0xFF9A9A9E))) }
        }
        content()
    }
}

private enum class ChartMetric(val label: String, val unit: String, val measure: Measure) {
    Weight("Peso", " kg", Measure.Weight),
    Fat("Grasa", " %", Measure.FatPct),
    Muscle("Músculo", " kg", Measure.Muscle),
}

@Composable
private fun ProgressCard(chrono: List<Evaluation>) {
    var metric by remember { mutableStateOf(ChartMetric.Weight) }
    val points = chrono.mapNotNull { e -> e.date?.let { d -> metric.measure.of(e)?.let { d to it } } }
    RecordCard("Progreso", "${chrono.size} evaluaciones") {
        Row(
            Modifier.fillMaxWidth().clip(CircleShape).background(NovoColors.background).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ChartMetric.entries.forEach { m ->
                val on = m == metric
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.weight(1f).height(32.dp).clip(CircleShape)
                        .background(if (on) Color(0xFF3A3A3C) else Color.Transparent)
                        .clickable { metric = m },
                ) {
                    Text(m.label, style = NovoFonts.semibold(13).copy(color = if (on) Color.White else Color(0xFF9A9A9E)))
                }
            }
        }
        if (points.size < 2) {
            Text("Hace falta al menos dos mediciones de ${metric.label.lowercase()}.", style = NovoFonts.body(13).copy(color = Color(0xFF9A9A9E)))
        } else {
            LineChart(points, metric.unit)
        }
    }
}

@Composable
private fun LineChart(points: List<Pair<java.time.LocalDate, Double>>, unit: String) {
    val measurer = rememberTextMeasurer()
    val gray = Color(0xFF9A9A9E)
    val axisStyle = TextStyle(fontSize = 11.sp, color = gray)
    val lastStyle = TextStyle(fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
    val lineColor = EvalColors.pink
    val values = points.map { it.second }
    val lo = kotlin.math.floor(values.min())
    val hi = kotlin.math.ceil(values.max()).let { if (it == lo) lo + 1 else it }
    val description = "De ${evalNumber(values.first())}$unit a ${evalNumber(values.last())}$unit"
    Canvas(Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = description }) {
        val left = 36.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 16.dp.toPx()
        val bottom = size.height - 26.dp.toPx()
        fun y(v: Double) = (bottom - (v - lo) / (hi - lo) * (bottom - top)).toFloat()
        val xs = points.indices.map { i -> if (points.size == 1) (left + right) / 2 else left + 14.dp.toPx() + i * (right - left - 28.dp.toPx()) / (points.size - 1) }

        listOf(hi, (hi + lo) / 2, lo).forEachIndexed { i, v ->
            val gy = y(v)
            drawLine(Color.White.copy(alpha = if (i == 2) 0.14f else 0.08f), Offset(left, gy), Offset(right, gy), strokeWidth = 1.dp.toPx())
            val label = measurer.measure(evalNumber(v), axisStyle)
            drawText(label, topLeft = Offset(0f, gy - label.size.height / 2f))
        }
        val path = Path()
        points.forEachIndexed { i, p -> if (i == 0) path.moveTo(xs[i], y(p.second)) else path.lineTo(xs[i], y(p.second)) }
        drawPath(path, lineColor, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Dates: every point when they fit, else first, last and a couple between.
        val step = maxOf(1, (points.size + 3) / 4)
        points.forEachIndexed { i, p ->
            if (i % step != 0 && i != points.lastIndex) return@forEachIndexed
            if (i != points.lastIndex && points.lastIndex - i < step && i != 0) return@forEachIndexed
            val label = measurer.measure(CoachDates.shortLabel(p.first), axisStyle)
            val x = (xs[i] - label.size.width / 2f).coerceIn(left - 4.dp.toPx(), size.width - label.size.width)
            drawText(label, topLeft = Offset(x, size.height - label.size.height))
        }
        val lastX = xs.last()
        val lastY = y(points.last().second)
        drawCircle(NovoColors.card, radius = 7.dp.toPx(), center = Offset(lastX, lastY))
        drawCircle(lineColor, radius = 5.dp.toPx(), center = Offset(lastX, lastY))
        val text = measurer.measure(evalNumber(points.last().second) + unit, lastStyle)
        val ly = if (lastY - top > 24.dp.toPx()) lastY - 10.dp.toPx() - text.size.height else lastY + 10.dp.toPx()
        drawText(text, topLeft = Offset((lastX - 10.dp.toPx() - text.size.width).coerceAtLeast(left), ly))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompositionCard(latest: Evaluation) {
    val bmi = Bmi.value(latest.height, latest.weight)
    val cells = listOfNotNull(
        latest.height?.let { "Altura" to "${evalNumber(it)} cm" },
        latest.muscle?.let { "Músculo" to "${evalNumber(it)} kg" },
        latest.fatKg?.let { "Grasa" to "${evalNumber(it)} kg" },
        latest.visceral?.let { "Grasa visceral" to evalNumber(it) },
    )
    if (cells.isEmpty() && bmi == null) return
    RecordCard("Composición corporal", EvalDates.short(latest.date)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = 3) {
            cells.forEach { (label, value) ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, style = NovoFonts.body(12).copy(color = Color(0xFF9A9A9E)))
                    Text(value, style = NovoFonts.semibold(16).copy(fontWeight = FontWeight.Bold))
                }
            }
        }
        bmi?.let {
            val (category, color) = Bmi.category(it)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("IMC", style = NovoFonts.body(12).copy(color = Color(0xFF9A9A9E)))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(evalNumber(it), style = NovoFonts.semibold(16).copy(fontWeight = FontWeight.Bold))
                    Text(" · $category", style = NovoFonts.semibold(12).copy(color = color, fontWeight = FontWeight.Bold))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HealthCard(latest: Evaluation) {
    RecordCard("Salud") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BodyFigurePair(latest.pain.toSet()) { Text(it, style = NovoFonts.body(11).copy(color = Color(0xFF9A9A9E))) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Áreas de dolor", style = NovoFonts.semibold(12).copy(color = Color(0xFF9A9A9E)))
                if (latest.pain.isEmpty()) {
                    Text("Sin áreas de dolor", style = NovoFonts.body(13).copy(color = EvalColors.light))
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BodyZones.sorted(latest.pain).forEach { PinkChip(BodyZones.label(it)) }
                    }
                }
                Cardiac.label(latest.cardiac)?.let {
                    Spacer(Modifier.height(4.dp))
                    Text("¿Condición cardíaca?", style = NovoFonts.semibold(12).copy(color = Color(0xFF9A9A9E)))
                    Text(it, style = NovoFonts.semibold(14))
                }
            }
        }
        val rows = listOfNotNull(
            latest.injuries.ifEmpty { null }?.let { "Lesiones activas o recientes" to it },
            "Cirugías previas · Medicamentos" to "${latest.surgeries.ifEmpty { "Ninguna" }} · ${latest.medications.ifEmpty { "Ninguno" }}",
            latest.comments.ifEmpty { null }?.let { "Comentarios adicionales" to it },
        )
        rows.forEach { (label, value) ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = NovoFonts.semibold(12).copy(color = Color(0xFF9A9A9E)))
                Text(value, style = NovoFonts.body(14))
            }
        }
    }
}

@Composable
fun PinkChip(text: String, color: Color = EvalColors.pink) {
    Text(
        text,
        style = NovoFonts.semibold(12).copy(color = color),
        modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun DocumentsCard(documents: List<EvalDocument>, onOpen: (EvalDocument) -> Unit) {
    RecordCard("Documentos") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            documents.forEach { doc -> DocumentThumb(doc, 72.dp) { onOpen(doc) } }
        }
    }
}

@Composable
fun DocumentThumb(doc: EvalDocument, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val image = doc.thumb ?: doc.url.takeIf { doc.isImage }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).clip(shape).background(Color(0xFF3A3A3C)).clickable(onClick = onClick),
    ) {
        if (doc.isImage && image != null) {
            AsyncImage(model = image, contentDescription = doc.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(doc.extension, style = NovoFonts.semibold(12).copy(color = EvalColors.light))
        }
    }
}

@Composable
private fun HistoryCard(evaluations: List<Evaluation>, onEdit: (Evaluation) -> Unit, onSign: (Evaluation) -> Unit) {
    val gray = Color(0xFF9A9A9E)
    Column(Modifier.fillMaxWidth().cardBackground().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp)) {
        Text("Historial de mediciones", style = NovoFonts.semibold(15).copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
        Row(Modifier.padding(vertical = 6.dp)) {
            listOf("Fecha" to 1.2f, "Peso" to 1f, "Grasa" to 1f, "Músculo" to 1f).forEach { (label, w) ->
                Text(label.uppercase(), style = NovoFonts.semibold(11).copy(color = gray, letterSpacing = 0.3.sp), modifier = Modifier.weight(w))
            }
            Text("FIRMA", style = NovoFonts.semibold(11).copy(color = gray, letterSpacing = 0.3.sp), modifier = Modifier.width(64.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        evaluations.forEach { e ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(NovoColors.separator))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { onEdit(e) },
            ) {
                Text(EvalDates.short(e.date), style = NovoFonts.semibold(14), modifier = Modifier.weight(1.2f))
                Text(e.weight?.let { evalNumber(it) } ?: "—", style = NovoFonts.body(14), modifier = Modifier.weight(1f))
                Text(e.fatPct?.let { "${evalNumber(it)} %" } ?: "—", style = NovoFonts.body(14), modifier = Modifier.weight(1f))
                Text(e.muscle?.let { evalNumber(it) } ?: "—", style = NovoFonts.body(14), modifier = Modifier.weight(1f))
                Box(Modifier.width(64.dp), contentAlignment = Alignment.CenterEnd) {
                    if (e.hasSignature) {
                        Icon(Icons.Filled.Check, contentDescription = "Firmado", tint = EvalColors.good, modifier = Modifier.size(18.dp))
                    } else {
                        Text(
                            "Firmar",
                            style = NovoFonts.semibold(12).copy(color = EvalColors.pink),
                            modifier = Modifier.clip(CircleShape).background(NovoColors.accent.copy(alpha = 0.18f)).clickable { onSign(e) }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramsSection(record: MemberRecord, onOpen: (Int) -> Unit, onCreateRoutine: (Evaluation) -> Unit, creating: Boolean) {
    val latest = record.latest
    if (latest != null && (latest.programId == null || !latest.hasRoutine)) {
        ProgramCard(
            title = "Crear rutina",
            subtitle = "La evaluación del ${EvalDates.short(latest.date)} todavía no tiene rutina.",
            chip = null,
            loading = creating,
        ) { onCreateRoutine(latest) }
    }
    record.programs.filter { latest == null || it.id != latest.programId || latest.hasRoutine }.take(3).forEachIndexed { i, program ->
        ProgramCard(
            title = if (i == 0) "Programa actual" else "Programa anterior",
            subtitle = program.title,
            chip = program.status.label to program.status.color,
            loading = false,
        ) { onOpen(program.id) }
    }
}

@Composable
private fun ProgramCard(title: String, subtitle: String, chip: Pair<String, Color>?, loading: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().cardBackground().clickable(enabled = !loading, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(Icons.Outlined.FitnessCenter, contentDescription = null, tint = EvalColors.pink, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = NovoFonts.semibold(15))
            Text(subtitle, style = NovoFonts.body(12).copy(color = Color(0xFF9A9A9E)), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            loading -> CircularProgressIndicator(color = NovoColors.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            chip != null -> CoachChip(chip.first, chip.second)
            else -> Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFF9A9A9E))
        }
    }
}
