package com.wegojim.android

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.wegojim.android.data.weekStartOf
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Home screen widget showing weekly goal progress (port of the iOS WidgetKit extension). */
class WeeklyGoalWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.app
        val goal = app.settings.state.value.weeklyGoal
        val weekStart = weekStartOf(LocalDate.now())
        val done = app.repository.workouts.first().count {
            !Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate().isBefore(weekStart)
        }
        provideContent { Content(done, goal) }
    }

    @Composable
    private fun Content(done: Int, goal: Int) {
        val bg = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF16202A))
        val text = ColorProvider(day = Color(0xFF0D1520), night = Color(0xFFF5F7FA))
        val muted = ColorProvider(day = Color(0xFF566476), night = Color(0xFFA4AFBC))
        val accent = if (done >= goal) ColorProvider(day = Color(0xFF1A9D6F), night = Color(0xFF62D8A6))
        else ColorProvider(day = Color(0xFF1E86FF), night = Color(0xFF6DB5FF))
        Column(
            GlanceModifier.fillMaxSize().background(bg).cornerRadius(20.dp).padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Weekly Goal", style = TextStyle(color = muted, fontSize = 13.sp, fontWeight = FontWeight.Medium))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$done", style = TextStyle(color = text, fontSize = 34.sp, fontWeight = FontWeight.Bold))
                Text("/$goal", style = TextStyle(color = muted, fontSize = 18.sp, fontWeight = FontWeight.Medium))
            }
            Spacer(GlanceModifier.height(6.dp))
            LinearProgressIndicator(
                progress = (done.toFloat() / goal).coerceAtMost(1f),
                modifier = GlanceModifier.fillMaxWidth(),
                color = accent,
                backgroundColor = ColorProvider(day = Color(0xFFE6EDF4), night = Color(0xFF22303D)),
            )
            Spacer(GlanceModifier.height(6.dp))
            Text(
                if (done >= goal) "Goal reached" else "${goal - done} to go",
                style = TextStyle(color = muted, fontSize = 12.sp),
            )
        }
    }

    companion object {
        suspend fun refresh(context: Context) = runCatching { WeeklyGoalWidget().updateAll(context) }
    }
}

class WeeklyGoalWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeeklyGoalWidget()
}
