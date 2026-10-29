package com.novofitclub.dashboard.feature.coach.evaluations

import android.graphics.Bitmap
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.novofitclub.dashboard.NovoFitClubApp
import com.novofitclub.dashboard.core.ui.MemberAvatar
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.PrimaryButton
import com.novofitclub.dashboard.core.ui.cardBackground
import com.novofitclub.dashboard.feature.coach.CoachDates
import com.novofitclub.dashboard.feature.sales.TextAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private val ink = Color(0xFF141414)
private const val INK_WIDTH_DP = 3f
/** The PNG is kept at most this wide: plenty for a signature, small for the database. */
private const val MAX_PNG_WIDTH = 900

/**
 * Firma del Miembro: the member signs on the phone, saved as the web's
 * signature pad does (a PNG data URL to member-evaluations' /sign).
 * [onClose] (signed) closes it; [onCreateRoutine] asks the host to open the routine.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignatureScreen(app: NovoFitClubApp, target: SignTarget, onClose: (Boolean) -> Unit, onCreateRoutine: () -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var padSize by remember { mutableStateOf(IntSize.Zero) }
    var saving by remember { mutableStateOf(false) }
    var signed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val inkWidth = with(density) { INK_WIDTH_DP.dp.toPx() }
    val gray = Color(0xFF9A9A9E)

    BackHandler { onClose(signed) }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp)) {
            TextAction("Atrás", onClick = { onClose(signed) }, modifier = Modifier.width(88.dp))
            Text(
                "Firma del Miembro",
                style = NovoFonts.semibold(17).copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(88.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(NovoColors.separator))

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState(), enabled = current.isEmpty()).padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().cardBackground().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MemberAvatar(target.member.initials, target.member.avatarColor, target.member.photoUrl, 44.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(target.member.name, style = NovoFonts.semibold(16))
                        Text(
                            listOf("Evaluación ${EvalDates.slashed(target.date)}", target.coachName).filter { it.isNotEmpty() }.joinToString(" · "),
                            style = NovoFonts.body(13).copy(color = gray),
                        )
                    }
                }
                val chips = target.goals.map { it to EvalColors.pink } +
                    target.pain.map { "Dolor: ${BodyZones.label(it)}" to EvalColors.soon } +
                    listOfNotNull(target.days?.let { "$it ${if (it == 1) "día" else "días"} / semana" to EvalColors.light })
                if (chips.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        chips.forEach { (text, color) -> PinkChip(text, color) }
                    }
                }
            }

            target.signedAt?.let {
                if (!signed) {
                    Text(
                        "Firmado previamente el ${EvalDates.slashed(it)} — firmar de nuevo para actualizar.",
                        style = NovoFonts.medium(13).copy(color = EvalColors.good),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(EvalColors.good.copy(alpha = 0.10f)).padding(12.dp),
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFF2F2F7))
                    .onSizeChanged { padSize = it }
                    .pointerInput(signed) {
                        if (signed) return@pointerInput
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            var points = listOf(down.position)
                            current = points
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                change.consume()
                                points = points + change.position
                                current = points
                            }
                            strokes.add(points)
                            current = emptyList()
                        }
                    },
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val baseline = size.height - 40.dp.toPx()
                    drawLine(
                        Color(0xFFAEAEB2), Offset(24.dp.toPx(), baseline), Offset(size.width - 24.dp.toPx(), baseline),
                        strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                    )
                    (strokes + listOf(current)).forEach { stroke ->
                        if (stroke.size == 1) {
                            drawCircle(ink, radius = inkWidth / 2, center = stroke[0])
                        } else if (stroke.size > 1) {
                            drawPath(smoothPath(stroke), ink, style = Stroke(inkWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
                if (strokes.isEmpty() && current.isEmpty()) {
                    Text("Firme aquí", style = NovoFonts.semibold(18).copy(color = Color(0xFF8E8E93)), modifier = Modifier.align(Alignment.Center))
                }
                Text(
                    target.member.name,
                    style = NovoFonts.body(12).copy(color = Color(0xFF6E6E73)),
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 16.dp),
                )
            }

            Text(
                "Al firmar arriba, el miembro reconoce y aprueba su evaluación de salud y programa de ejercicios.",
                style = NovoFonts.body(13).copy(color = EvalColors.light),
            )
            error?.let { Text(it, style = NovoFonts.medium(13).copy(color = EvalColors.overdue)) }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            if (!signed) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { strokes.clear() },
                        shape = CircleShape,
                        border = BorderStroke(1.5.dp, NovoColors.accent),
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text("Limpiar", style = NovoFonts.semibold(16).copy(color = EvalColors.pink)) }
                    PrimaryButton(
                        text = "Confirmar Firma",
                        enabled = strokes.isNotEmpty(),
                        loading = saving,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val lines = strokes.toList()
                            val canvas = padSize
                            scope.launch {
                                saving = true
                                error = null
                                try {
                                    val png = withContext(Dispatchers.Default) { renderPng(lines, canvas, inkWidth) }
                                    EvaluationApi.sign(app.api, target.evaluationId, png)
                                    signed = true
                                } catch (e: Exception) {
                                    error = "No se pudo guardar la firma: ${e.localizedMessage ?: "error desconocido"}"
                                }
                                saving = false
                            }
                        },
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(EvalColors.good.copy(alpha = 0.10f)).padding(14.dp),
                ) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = EvalColors.good, modifier = Modifier.size(22.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Evaluación firmada", style = NovoFonts.semibold(15).copy(color = EvalColors.good, fontWeight = FontWeight.Bold))
                        target.deadline?.let {
                            Text(
                                "Plazo de entrega de la rutina: ${CoachDates.shortLabel(it)} (${target.deliveryDays} días hábiles).",
                                style = NovoFonts.body(13).copy(color = EvalColors.light),
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onClose(true) },
                        shape = CircleShape,
                        border = BorderStroke(1.5.dp, NovoColors.accent),
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text("Listo", style = NovoFonts.semibold(15).copy(color = EvalColors.pink)) }
                    PrimaryButton("Crear rutina", onClick = onCreateRoutine, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Quadratic curves through the midpoints: smoother than straight segments. */
private fun smoothPath(points: List<Offset>): Path = Path().apply {
    moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val mid = Offset((prev.x + points[i].x) / 2, (prev.y + points[i].y) / 2)
        quadraticTo(prev.x, prev.y, mid.x, mid.y)
    }
    lineTo(points.last().x, points.last().y)
}

/** The strokes on a transparent PNG, base64 (no data: prefix). */
private fun renderPng(strokes: List<List<Offset>>, size: IntSize, inkWidth: Float): String {
    val scale = minOf(1f, MAX_PNG_WIDTH.toFloat() / maxOf(1, size.width))
    val width = maxOf(1, (size.width * scale).toInt())
    val height = maxOf(1, (size.height * scale).toInt())
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        color = ink.toArgb()
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = inkWidth * scale
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    strokes.forEach { stroke ->
        if (stroke.isEmpty()) return@forEach
        if (stroke.size == 1) {
            val dot = android.graphics.Paint(paint).apply { style = android.graphics.Paint.Style.FILL }
            canvas.drawCircle(stroke[0].x * scale, stroke[0].y * scale, inkWidth * scale / 2, dot)
            return@forEach
        }
        val path = android.graphics.Path()
        path.moveTo(stroke[0].x * scale, stroke[0].y * scale)
        for (i in 1 until stroke.size) {
            val prev = stroke[i - 1]
            val cur = stroke[i]
            path.quadTo(prev.x * scale, prev.y * scale, (prev.x + cur.x) / 2 * scale, (prev.y + cur.y) / 2 * scale)
        }
        path.lineTo(stroke.last().x * scale, stroke.last().y * scale)
        canvas.drawPath(path, paint)
    }
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    bitmap.recycle()
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}
