package com.novofitclub.dashboard.feature.sales.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novofitclub.dashboard.core.ui.NovoColors
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/*
 * Minimal Canvas stand-ins for the Swift Charts BarMark / LineMark+PointMark
 * charts on the iOS dashboard: category x-axis of month labels, value axis
 * on the leading edge with light grid lines.
 */

private const val Y_TICKS = 4

private val axisStyle = TextStyle(fontSize = 10.sp, color = NovoColors.textSecondary)

@Composable
internal fun MonthBarChart(points: List<DashboardStats.MonthPoint>, color: Color, height: Dp) {
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().height(height)) {
        val plot = drawAxes(points, measurer) ?: return@Canvas
        val band = plot.width / points.size
        val barWidth = band * 0.6f
        points.forEachIndexed { index, point ->
            val barHeight = (point.value / plot.maxValue).toFloat() * plot.height
            if (barHeight <= 0f) return@forEachIndexed
            drawRoundRectTop(
                color = color,
                topLeft = Offset(plot.left + band * index + (band - barWidth) / 2, plot.bottom - barHeight),
                size = Size(barWidth, barHeight),
            )
        }
    }
}

@Composable
internal fun MonthLineChart(points: List<DashboardStats.MonthPoint>, color: Color, height: Dp) {
    val measurer = rememberTextMeasurer()
    Canvas(Modifier.fillMaxWidth().height(height)) {
        val plot = drawAxes(points, measurer) ?: return@Canvas
        val band = plot.width / points.size
        val coords = points.mapIndexed { index, point ->
            Offset(
                plot.left + band * index + band / 2,
                plot.bottom - (point.value / plot.maxValue).toFloat() * plot.height,
            )
        }
        // Catmull-Rom spline through the points (iOS `.interpolationMethod(.catmullRom)`).
        val path = Path().apply {
            moveTo(coords.first().x, coords.first().y)
            for (i in 0 until coords.size - 1) {
                val p0 = coords[maxOf(i - 1, 0)]
                val p1 = coords[i]
                val p2 = coords[i + 1]
                val p3 = coords[minOf(i + 2, coords.size - 1)]
                cubicTo(
                    p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
                    p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
                    p2.x, p2.y,
                )
            }
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
        coords.forEach { drawCircle(color, radius = 3.5.dp.toPx(), center = it) }
    }
}

private class PlotArea(
    val left: Float,
    val bottom: Float,
    val width: Float,
    val height: Float,
    val maxValue: Double,
)

/** Draws grid lines + both axes' labels and returns the plot rectangle, or null with no data. */
private fun DrawScope.drawAxes(points: List<DashboardStats.MonthPoint>, measurer: TextMeasurer): PlotArea? {
    if (points.isEmpty()) return null
    val maxValue = niceMax(points.maxOf { it.value })
    val tickLabels = (0..Y_TICKS).map { compactNumber(maxValue * it / Y_TICKS) }
    val measuredTicks = tickLabels.map { measurer.measure(it, axisStyle) }
    val axisWidth = measuredTicks.maxOf { it.size.width } + 6.dp.toPx()
    val xLabelHeight = measurer.measure("Ene 25", axisStyle).size.height + 6.dp.toPx()

    val top = measuredTicks.first().size.height / 2f
    val left = axisWidth
    val bottom = size.height - xLabelHeight
    val plotHeight = bottom - top
    val plotWidth = size.width - left

    measuredTicks.forEachIndexed { index, layout ->
        val y = bottom - plotHeight * index / Y_TICKS
        drawLine(NovoColors.separator, Offset(left, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        drawText(layout, topLeft = Offset(axisWidth - 6.dp.toPx() - layout.size.width, y - layout.size.height / 2f))
    }

    // Thin out month labels so they don't collide (Swift Charts does this automatically).
    val band = plotWidth / points.size
    val stride = max(1, ceil(points.size / 6.0).toInt())
    points.forEachIndexed { index, point ->
        if (index % stride != 0) return@forEachIndexed
        val layout = measurer.measure(point.shortLabel, axisStyle)
        val x = left + band * index + band / 2 - layout.size.width / 2f
        drawText(layout, topLeft = Offset(x.coerceIn(left, size.width - layout.size.width), bottom + 4.dp.toPx()))
    }
    return PlotArea(left, bottom, plotWidth, plotHeight, maxValue)
}

private fun DrawScope.drawRoundRectTop(color: Color, topLeft: Offset, size: Size) {
    val radius = minOf(3.dp.toPx(), size.width / 2, size.height)
    val path = Path().apply {
        moveTo(topLeft.x, topLeft.y + size.height)
        lineTo(topLeft.x, topLeft.y + radius)
        quadraticTo(topLeft.x, topLeft.y, topLeft.x + radius, topLeft.y)
        lineTo(topLeft.x + size.width - radius, topLeft.y)
        quadraticTo(topLeft.x + size.width, topLeft.y, topLeft.x + size.width, topLeft.y + radius)
        lineTo(topLeft.x + size.width, topLeft.y + size.height)
        close()
    }
    drawPath(path, color)
}

/** Rounds the axis maximum up to 1/2/2.5/5 × 10ⁿ. */
private fun niceMax(value: Double): Double {
    if (value <= 0) return 1.0
    val magnitude = 10.0.pow(floor(log10(value)))
    val normalized = value / magnitude
    val nice = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it >= normalized - 1e-9 }
    return nice * magnitude
}

private fun compactNumber(value: Double): String = when {
    value >= 1_000_000 -> trimmed(value / 1_000_000) + "M"
    value >= 1_000 -> trimmed(value / 1_000) + "k"
    else -> trimmed(value)
}

private fun trimmed(value: Double): String =
    if (value == floor(value)) value.toLong().toString() else String.format(java.util.Locale.US, "%.1f", value)
