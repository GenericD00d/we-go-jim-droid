package com.wegojim.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.wegojim.android.app
import com.wegojim.android.data.DistanceUnit
import com.wegojim.android.data.LoadUnit
import com.wegojim.android.data.WorkoutEntity
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(nav: NavHostController) {
    val app = LocalContext.current.app
    val bests by app.repository.exerciseBests.collectAsStateWithLifecycle(null)
    val workouts by app.repository.workouts.collectAsStateWithLifecycle(emptyList())
    val unit = app.settings.state.collectAsStateWithLifecycle().value.weightUnit
    val last30 = remember(workouts) {
        val cutoff = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
        workouts.filter { it.startedAt >= cutoff }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Progress", fontWeight = FontWeight.Bold) }) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionHeader("Last 30 days") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Workouts", last30.size.toString(), Modifier.weight(1f))
                    StatTile("Volume", formatVolume(last30.sumOf { it.totalVolumeKg }, unit), Modifier.weight(1f))
                    StatTile("PRs", last30.sumOf { it.prCount }.toString(), Modifier.weight(1f))
                }
            }
            item { SectionHeader("Exercises") }
            if (bests?.isEmpty() == true) {
                item { EmptyState(Icons.AutoMirrored.Filled.ShowChart, "No progress yet", "Finish a workout to start tracking estimated 1RM, heaviest sets and reps.") }
            }
            items(bests.orEmpty(), key = { it.exerciseUuid }) { best ->
                SectionCard(onClick = { nav.navigate(Routes.exercise(best.exerciseUuid)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(best.exerciseName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(formatDayShort(best.lastAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Labeled("Est. 1RM", best.bestE1rmKg?.let { formatKg(it, unit) } ?: "–")
                        Labeled("Heaviest", best.maxWeightKg?.let { formatKg(it, unit) } ?: "–")
                        Labeled("Most reps", best.maxReps.toString())
                        Labeled("Sessions", best.sessions.toString())
                    }
                }
            }
        }
    }
}

@Composable
private fun Labeled(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

/** Consecutive weeks (ending this week or last) with at least one workout. */
private fun weekStreak(workouts: List<WorkoutEntity>): Int {
    val weeks = workouts.map { startOfWeek(it.startedAt.toLocalDate()) }.toSet()
    var week = startOfWeek(LocalDate.now())
    if (week !in weeks) week = week.minusWeeks(1)
    var streak = 0
    while (week in weeks) {
        streak++
        week = week.minusWeeks(1)
    }
    return streak
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen() {
    val app = LocalContext.current.app
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val workouts by app.repository.workouts.collectAsStateWithLifecycle(emptyList())
    val thisWeek = remember(workouts) {
        val start = startOfWeek()
        workouts.count { !it.startedAt.toLocalDate().isBefore(start) }
    }
    val streak = remember(workouts) { weekStreak(workouts) }

    Scaffold(topBar = { TopAppBar(title = { Text(settings.displayName.ifBlank { "Profile" }, fontWeight = FontWeight.Bold) }) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { (thisWeek.toFloat() / settings.weeklyGoal).coerceAtMost(1f) },
                                modifier = Modifier.size(72.dp),
                                strokeWidth = 8.dp,
                                color = if (thisWeek >= settings.weeklyGoal) Wgj.colors.success else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                            Text("$thisWeek/${settings.weeklyGoal}", fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Weekly goal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (thisWeek >= settings.weeklyGoal) "Goal reached this week" else "${settings.weeklyGoal - thisWeek} to go this week",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Workouts", workouts.size.toString(), Modifier.weight(1f))
                    StatTile("Week streak", streak.toString(), Modifier.weight(1f))
                    StatTile("PRs", workouts.sumOf { it.prCount }.toString(), Modifier.weight(1f))
                }
            }
            item { SectionHeader("Settings") }
            item {
                SectionCard {
                    OutlinedTextField(
                        value = settings.displayName,
                        onValueChange = { v -> app.settings.update { it.copy(displayName = v) } },
                        label = { Text("Display name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Weight unit", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(LoadUnit.kg, LoadUnit.lb).forEachIndexed { i, unit ->
                            SegmentedButton(
                                selected = settings.weightUnit == unit,
                                onClick = { app.settings.update { it.copy(weightUnit = unit) } },
                                shape = SegmentedButtonDefaults.itemShape(i, 2),
                            ) { Text(unit.label) }
                        }
                    }
                    Text("Distance unit", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Segmented(listOf(DistanceUnit.kilometers, DistanceUnit.miles), settings.distanceUnit, { it.symbol }) { unit ->
                        app.settings.update { it.copy(distanceUnit = unit) }
                    }
                    Stepper(
                        "Weekly goal",
                        "${settings.weeklyGoal}",
                        onDecrement = { app.settings.update { it.copy(weeklyGoal = (it.weeklyGoal - 1).coerceAtLeast(1)) } },
                        onIncrement = { app.settings.update { it.copy(weeklyGoal = (it.weeklyGoal + 1).coerceAtMost(14)) } },
                    )
                    Stepper(
                        "Default rest",
                        formatRest(settings.defaultRestSeconds),
                        onDecrement = { app.settings.update { it.copy(defaultRestSeconds = (it.defaultRestSeconds - 15).coerceAtLeast(0)) } },
                        onIncrement = { app.settings.update { it.copy(defaultRestSeconds = it.defaultRestSeconds + 15) } },
                    )
                    SwitchRow("Start rest timer when a set is completed", settings.autoStartRestTimer) { v -> app.settings.update { it.copy(autoStartRestTimer = v) } }
                    SwitchRow("Keep screen awake during workouts", settings.keepScreenAwake) { v -> app.settings.update { it.copy(keepScreenAwake = v) } }
                }
            }
            item { SectionHeader("About") }
            item {
                SectionCard {
                    Text("We Go Jim for Android", fontWeight = FontWeight.SemiBold)
                    Text(
                        "An Android port of We Go Jim by Andreas Hortlund (github.com/Hortlund/we-go-jim), MIT License. " +
                            "Your data stays on this device and is included in Android's device backup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

