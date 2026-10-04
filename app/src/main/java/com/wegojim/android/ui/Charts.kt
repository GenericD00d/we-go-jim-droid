package com.wegojim.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wegojim.android.data.MuscleHeatmap
import com.wegojim.android.data.TrendPoint
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max

private val shortDate = DateTimeFormatter.ofPattern("d MMM")

/**
 * Minimal line chart: one series, light min/max gridlines, tap a point to
 * read its value. Values are already converted to display units.
 */
@Composable
fun LineChart(points: List<TrendPoint>, format: (Double) -> String, modifier: Modifier = Modifier) {
    if (points.isEmpty()) {
        Text("No data yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainer
    var selected by remember(points) { mutableStateOf(points.lastIndex) }
    val minV = points.minOf { it.valueKgOrReps }
    val maxV = points.maxOf { it.valueKgOrReps }
    val span = if (maxV - minV < 1e-6) max(1.0, maxV * 0.1) else maxV - minV
    val lo = minV - span * 0.1
    val hi = maxV + span * 0.1
    val pick = points[selected]
    val description = "Trend from ${format(points.first().valueKgOrReps)} to ${format(points.last().valueKgOrReps)} over ${points.size} sessions"

    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(format(pick.valueKgOrReps), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("  ${pick.date.format(shortDate)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(vertical = 8.dp)
                .semantics { contentDescription = description }
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        if (points.size == 1) return@detectTapGestures
                        val step = size.width / (points.size - 1).toFloat()
                        selected = (offset.x / step).let { Math.round(it) }.coerceIn(0, points.lastIndex)
                    }
                },
        ) {
            fun x(i: Int) = if (points.size == 1) size.width / 2 else i * size.width / (points.size - 1)
            fun y(v: Double) = (size.height - ((v - lo) / (hi - lo)) * size.height).toFloat()
            listOf(maxV, minV).forEach { v ->
                drawLine(grid, Offset(0f, y(v)), Offset(size.width, y(v)), strokeWidth = 1.dp.toPx())
            }
            val path = Path()
            points.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(i), y(p.valueKgOrReps)) else path.lineTo(x(i), y(p.valueKgOrReps)) }
            drawPath(path, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            points.forEachIndexed { i, p ->
                val r = if (i == selected) 6.dp.toPx() else 4.dp.toPx()
                drawCircle(line, r, Offset(x(i), y(p.valueKgOrReps)))
                drawCircle(surface, r - 2.dp.toPx(), Offset(x(i), y(p.valueKgOrReps)))
            }
        }
        Row {
            Text(points.first().date.format(shortDate), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("max ${format(maxV)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** GitHub-style grid of the last [weeks] weeks; columns are weeks, rows weekdays. */
@Composable
fun ConsistencyCalendar(days: Map<LocalDate, Int>, weekStart: (LocalDate) -> LocalDate, weeks: Int = 16) {
    val on = Wgj.colors.success
    val off = MaterialTheme.colorScheme.surfaceContainerHighest
    val today = LocalDate.now()
    val firstWeek = weekStart(today).minusWeeks((weeks - 1).toLong())
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        repeat(weeks) { w ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(7) { d ->
                    val date = firstWeek.plusWeeks(w.toLong()).plusDays(d.toLong())
                    val count = days[date] ?: 0
                    Box(
                        Modifier
                            .size(14.dp)
                            .aspectRatio(1f)
                            .background(
                                when {
                                    date.isAfter(today) -> Color.Transparent
                                    count > 0 -> on.copy(alpha = if (count > 1) 1f else 0.75f)
                                    else -> off
                                },
                                RoundedCornerShape(3.dp),
                            ),
                    )
                }
            }
        }
    }
}
