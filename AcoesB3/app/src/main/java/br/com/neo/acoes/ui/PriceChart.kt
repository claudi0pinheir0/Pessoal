package br.com.neo.acoes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import br.com.neo.acoes.core.format.Br
import br.com.neo.acoes.core.model.PricePoint
import kotlin.math.roundToInt

/**
 * Gráfico de linha da cotação, com área preenchida, eixo de preços à direita,
 * linha tracejada de referência (fechamento anterior) e seleção por toque/arraste.
 */
@Composable
fun PriceChart(
    points: List<PricePoint>,
    baseline: Double?,
    lineColor: Color,
    modifier: Modifier = Modifier,
    onSelect: (Int?) -> Unit = {},
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val guideColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    var selected by remember(points) { mutableStateOf<Int?>(null) }

    Canvas(
        modifier.pointerInput(points) {
            if (points.size < 2) return@pointerInput
            fun indexAt(x: Float, plotWidth: Float) =
                ((x / plotWidth) * points.lastIndex).roundToInt().coerceIn(0, points.lastIndex)
            awaitEachGesture {
                val plotWidth = size.width - AXIS_WIDTH_DP.dp.toPx()
                val down = awaitFirstDown()
                selected = indexAt(down.position.x, plotWidth).also(onSelect)
                down.consume()
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    selected = indexAt(change.position.x, plotWidth).also(onSelect)
                    change.consume()
                }
                selected = null
                onSelect(null)
            }
        },
    ) {
        if (points.size < 2) return@Canvas
        val closes = points.map { it.close }
        var min = closes.min()
        var max = closes.max()
        if (baseline != null) {
            min = minOf(min, baseline)
            max = maxOf(max, baseline)
        }
        if (max - min < 1e-9) { max += 0.5; min -= 0.5 }
        val span = max - min

        val axisWidth = AXIS_WIDTH_DP.dp.toPx()
        val plotWidth = size.width - axisWidth
        val pad = 10.dp.toPx()
        val plotHeight = size.height - pad * 2
        fun x(i: Int) = i * plotWidth / closes.lastIndex
        fun y(v: Double) = pad + ((max - v) / span * plotHeight).toFloat()

        // Grade + rótulos do eixo de preços
        for (k in 0..3) {
            val value = max - span * k / 3
            val yy = y(value)
            drawLine(gridColor, Offset(0f, yy), Offset(plotWidth, yy), strokeWidth = 1f)
            val layout = measurer.measure(Br.number(value), labelStyle)
            drawText(layout, topLeft = Offset(plotWidth + 6.dp.toPx(), yy - layout.size.height / 2f))
        }

        val line = Path().apply {
            moveTo(x(0), y(closes[0]))
            for (i in 1..closes.lastIndex) lineTo(x(i), y(closes[i]))
        }
        val area = Path().apply {
            addPath(line)
            lineTo(x(closes.lastIndex), size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.28f), Color.Transparent)))

        if (baseline != null) {
            drawLine(
                guideColor, Offset(0f, y(baseline)), Offset(plotWidth, y(baseline)),
                strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
            )
        }
        drawPath(line, lineColor, style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round))

        selected?.let { i ->
            drawLine(guideColor, Offset(x(i), 0f), Offset(x(i), size.height), strokeWidth = 1.dp.toPx())
            drawCircle(lineColor, radius = 5.dp.toPx(), center = Offset(x(i), y(closes[i])))
        }
    }
}

private const val AXIS_WIDTH_DP = 52
