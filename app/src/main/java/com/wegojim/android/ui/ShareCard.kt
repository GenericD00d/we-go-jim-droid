package com.wegojim.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wegojim.android.data.LoadUnit
import com.wegojim.android.data.PerformanceMath
import com.wegojim.android.data.WorkoutEntity

/**
 * Fixed dark-brand card summarising a workout (port of WorkoutShareCard).
 * Records itself into [layer] so it can be exported as an image.
 */
@Composable
fun WorkoutShareCard(workout: WorkoutEntity, unit: LoadUnit, layer: GraphicsLayer, modifier: Modifier = Modifier) {
    val white = Color(0xFFF5F7FA)
    val muted = Color(0xFFA4AFBC)
    val accent = Color(0xFF6DB5FF)
    val gold = Color(0xFFE0C56B)
    Column(
        modifier
            .fillMaxWidth()
            .drawWithContent {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            }
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0A1016), Color(0xFF16304D))))
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("WE GO JIM", color = accent, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 3.sp)
        Text(workout.name, color = white, fontWeight = FontWeight.Bold, fontSize = 26.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(formatDate(workout.startedAt), color = muted, fontSize = 14.sp)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            ShareStat("Time", formatDuration(workout.durationSeconds), white, muted)
            ShareStat("Volume", formatVolume(workout.totalVolumeKg, unit), white, muted)
            ShareStat("Sets", workout.completedSetCount.toString(), white, muted)
            ShareStat("PRs", workout.prCount.toString(), if (workout.prCount > 0) gold else white, muted)
        }
        workout.cardio.forEach { c ->
            Row {
                Text(c.name, Modifier.weight(1f), color = Color(0xFF7CE3EF), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(cardioResultSummary(c).substringBefore(" · ", cardioResultSummary(c)), color = muted, fontSize = 15.sp)
            }
        }
        workout.exercises.take(6).forEach { e ->
            val best = e.sets.filter { !it.isWarmup }.maxByOrNull { s ->
                s.weight?.let { PerformanceMath.estimatedOneRepMax(PerformanceMath.toKg(it, s.unit), s.reps ?: 0) } ?: (s.reps ?: 0).toDouble()
            }
            Row {
                Text("${e.sets.size}× ${e.name}", Modifier.weight(1f), color = white, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (best != null) Text(formatSet(best) + if (e.sets.any { it.isPr }) "  ★" else "", color = if (e.sets.any { it.isPr }) gold else muted, fontSize = 15.sp)
            }
        }
        if (workout.exercises.size > 6) Text("+${workout.exercises.size - 6} more", color = muted, fontSize = 13.sp)
    }
}

@Composable
private fun ShareStat(label: String, value: String, valueColor: Color, labelColor: Color) {
    Column {
        Text(label.uppercase(), color = labelColor, fontSize = 11.sp, letterSpacing = 1.sp)
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}
