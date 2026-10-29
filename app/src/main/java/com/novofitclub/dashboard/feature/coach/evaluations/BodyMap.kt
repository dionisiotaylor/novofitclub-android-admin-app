package com.novofitclub.dashboard.feature.coach.evaluations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The pain map: a figure from the front or the back with the zones of
 * member-evaluations (ids, centres and radii from body-map.js, viewBox
 * 205×452), so the ids saved in pain_areas keep working.
 */
enum class BodyView { Front, Back }

data class BodyZone(val id: String, val label: String, val cx: Float, val cy: Float, val rx: Float, val ry: Float, val front: Boolean, val back: Boolean) {
    fun shows(view: BodyView) = if (view == BodyView.Front) front else back
    fun contains(x: Float, y: Float): Boolean {
        val dx = (x - cx) / rx
        val dy = (y - cy) / ry
        return dx * dx + dy * dy <= 1f
    }
}

object BodyZones {
    private fun z(id: String, label: String, cx: Int, cy: Int, rx: Int, ry: Int, view: String) =
        BodyZone(id, label, cx.toFloat(), cy.toFloat(), rx.toFloat(), ry.toFloat(), view != "back", view != "front")

    val all = listOf(
        z("head", "Cabeza", 102, 35, 21, 25, "both"),
        z("left_shoulder", "Hombro Izquierdo", 66, 86, 13, 13, "both"),
        z("right_shoulder", "Hombro Derecho", 136, 86, 13, 13, "both"),
        z("left_arm", "Brazo Izquierdo", 59, 120, 11, 20, "both"),
        z("right_arm", "Brazo Derecho", 146, 120, 11, 20, "both"),
        z("left_elbow", "Codo Izquierdo", 49, 151, 10, 10, "back"),
        z("right_elbow", "Codo Derecho", 156, 151, 10, 10, "back"),
        z("left_forearm", "Antebrazo Izquierdo", 44, 167, 10, 25, "both"),
        z("right_forearm", "Antebrazo Derecho", 160, 167, 10, 25, "both"),
        z("left_wrist", "Muñeca Izquierda", 32, 205, 10, 10, "both"),
        z("right_wrist", "Muñeca Derecha", 172, 205, 10, 10, "both"),
        z("left_hand", "Mano Izquierda", 26, 224, 12, 14, "both"),
        z("right_hand", "Mano Derecha", 179, 224, 12, 14, "both"),
        z("chest", "Pecho", 101, 102, 29, 15, "front"),
        z("upper_back", "Espalda Alta", 101, 102, 29, 15, "back"),
        z("abdomen", "Abdomen", 102, 156, 29, 15, "front"),
        z("lower_back", "Espalda Baja", 102, 156, 29, 15, "back"),
        z("left_hip", "Cadera Izquierda", 78, 198, 19, 19, "front"),
        z("right_hip", "Cadera Derecha", 127, 198, 19, 19, "front"),
        z("left_glute", "Glúteo Izquierdo", 78, 198, 19, 19, "back"),
        z("right_glute", "Glúteo Derecho", 127, 198, 19, 19, "back"),
        z("left_thigh", "Muslo Izquierdo", 79, 254, 17, 30, "both"),
        z("right_thigh", "Muslo Derecho", 125, 254, 17, 30, "both"),
        z("left_knee", "Rodilla Izquierda", 80, 318, 14, 14, "both"),
        z("right_knee", "Rodilla Derecha", 124, 318, 14, 14, "both"),
        z("left_calf", "Pantorrilla Izquierda", 80, 360, 12, 22, "both"),
        z("right_calf", "Pantorrilla Derecha", 124, 360, 12, 22, "both"),
        z("left_ankle", "Tobillo Izquierdo", 85, 401, 9, 10, "both"),
        z("right_ankle", "Tobillo Derecho", 120, 401, 9, 10, "both"),
        z("left_foot", "Pie Izquierdo", 80, 434, 13, 12, "both"),
        z("right_foot", "Pie Derecho", 125, 434, 13, 12, "both"),
    )

    private val byId = all.associateBy { it.id }

    fun label(id: String): String = byId[id]?.label ?: id

    /** In the order the web lists them. */
    fun sorted(ids: Collection<String>): List<String> = all.map { it.id }.filter { it in ids } + ids.filter { it !in byId }
}

private const val VIEW_W = 205f
private const val VIEW_H = 452f
private val silhouette = Color(0xFF3A3A3C)
private val zoneOff = Color(0xFF48484A)
private val zoneOffBorder = Color(0xFF5A5A5E)
private val zoneOn = Color(0xFFE00E46)
private val zoneOnBorder = Color(0xFFFF8AA4)

/** [width] wide (the height follows the viewBox). [onToggle] null = read only: only the marked zones are drawn. */
@Composable
fun BodyFigure(view: BodyView, selected: Set<String>, width: Dp, onToggle: ((String) -> Unit)? = null) {
    val zones = BodyZones.all.filter { it.shows(view) }
    val toggle by rememberUpdatedState(onToggle)
    val marked = zones.filter { it.id in selected }.joinToString(", ") { it.label }.ifEmpty { "sin áreas marcadas" }
    var modifier = Modifier
        .size(width, width * (VIEW_H / VIEW_W))
        .semantics { contentDescription = "${if (view == BodyView.Front) "Frente" else "Espalda"}: $marked" }
    if (onToggle != null) {
        modifier = modifier.pointerInput(view) {
            detectTapGestures { tap ->
                val s = size.width / VIEW_W
                val x = tap.x / s
                val y = tap.y / s
                zones.lastOrNull { it.contains(x, y) }?.let { toggle?.invoke(it.id) }
            }
        }
    }
    Canvas(modifier) {
        val s = size.width / VIEW_W
        scale(s, s, pivot = Offset.Zero) {
            drawSilhouette()
            zones.forEach { zone ->
                val on = zone.id in selected
                if (onToggle == null && !on) return@forEach
                val topLeft = Offset(zone.cx - zone.rx, zone.cy - zone.ry)
                val oval = Size(zone.rx * 2, zone.ry * 2)
                drawOval(if (on) zoneOn else zoneOff, topLeft, oval)
                val border = if (on) 3f else 1.5f
                drawOval(
                    if (on) zoneOnBorder else zoneOffBorder,
                    Offset(topLeft.x + border / 2, topLeft.y + border / 2),
                    Size(oval.width - border, oval.height - border),
                    style = Stroke(border),
                )
            }
        }
    }
}

private fun DrawScope.drawSilhouette() {
    fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        drawRoundRect(silhouette, Offset(x, y), Size(w, h), CornerRadius(r, r))
    fun oval(cx: Float, cy: Float, rx: Float, ry: Float) =
        drawOval(silhouette, Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2))

    oval(102f, 35f, 21f, 25f)
    rect(93f, 56f, 18f, 18f, 6f)
    rect(64f, 70f, 76f, 150f, 28f)
    rotate(12f, Offset(58f, 118f)) { rect(46f, 78f, 24f, 80f, 12f) }
    rotate(-12f, Offset(147f, 118f)) { rect(135f, 78f, 24f, 80f, 12f) }
    rotate(14f, Offset(43f, 181f)) { rect(33f, 145f, 20f, 72f, 10f) }
    rotate(-14f, Offset(161f, 181f)) { rect(151f, 145f, 20f, 72f, 10f) }
    oval(26f, 226f, 11f, 14f)
    oval(179f, 226f, 11f, 14f)
    rect(63f, 200f, 35f, 120f, 17f)
    rect(107f, 200f, 35f, 120f, 17f)
    rect(68f, 310f, 25f, 100f, 12f)
    rect(112f, 310f, 25f, 100f, 12f)
    oval(80f, 432f, 14f, 10f)
    oval(125f, 432f, 14f, 10f)
}

/** Read-only pair, for the expediente. */
@Composable
fun BodyFigurePair(selected: Set<String>, width: Dp = 82.dp, caption: @Composable (String) -> Unit) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
        BodyView.entries.forEach { view ->
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
            ) {
                BodyFigure(view, selected, width)
                caption(if (view == BodyView.Front) "Frente" else "Espalda")
            }
        }
    }
}
