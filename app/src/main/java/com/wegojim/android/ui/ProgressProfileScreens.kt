package com.wegojim.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.wegojim.android.app
import com.wegojim.android.data.CalorieEstimator
import com.wegojim.android.data.Consistency
import com.wegojim.android.data.DashboardWidget
import com.wegojim.android.data.Direction
import com.wegojim.android.data.ExerciseEntity
import com.wegojim.android.data.ExerciseTrend
import com.wegojim.android.data.LoadUnit
import com.wegojim.android.data.MuscleHeatmap
import com.wegojim.android.data.SetFactEntity
import com.wegojim.android.data.TrendMetric
import com.wegojim.android.data.UserSettings
import com.wegojim.android.data.WeeklyCoach
import com.wegojim.android.data.WidgetKind
import com.wegojim.android.data.WorkoutCompare
import com.wegojim.android.data.WorkoutEntity
import com.wegojim.android.data.weekStartOf
import java.time.LocalDate

@Composable
fun directionColor(d: Direction): Color = when (d) {
    Direction.Up -> Wgj.colors.success
    Direction.Down -> MaterialTheme.colorScheme.error
    Direction.Flat -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun DirectionIcon(d: Direction) = Icon(
    when (d) {
        Direction.Up -> Icons.AutoMirrored.Filled.TrendingUp
        Direction.Down -> Icons.AutoMirrored.Filled.TrendingDown
        Direction.Flat -> Icons.AutoMirrored.Filled.TrendingFlat
    },
    null,
    tint = directionColor(d),
    modifier = Modifier.size(18.dp),
)

/** Formats a trend value: kg-based metrics in the user's unit, reps as-is. */
fun formatTrend(metric: TrendMetric, value: Double, unit: LoadUnit): String = when (metric) {
    TrendMetric.maxReps -> "${value.toInt()} reps"
    TrendMetric.volume -> formatVolume(value, unit)
    else -> formatKg(value, unit)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(nav: NavHostController) {
    val app = LocalContext.current.app
    val workouts by app.repository.workouts.collectAsStateWithLifecycle(emptyList())
    val bests by app.repository.exerciseBests.collectAsStateWithLifecycle(null)
    val unit = app.settings.state.collectAsStateWithLifecycle().value.weightUnit
    var tab by rememberSaveable { mutableStateOf(0) }
    var previousId by rememberSaveable { mutableStateOf<String?>(null) }
    var currentId by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Progress", fontWeight = FontWeight.Bold) }) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Segmented(listOf(0, 1), tab, { if (it == 0) "Compare" else "Exercises" }) { tab = it } }
            if (tab == 0) {
                val a = workouts.firstOrNull { it.id == previousId }
                val b = workouts.firstOrNull { it.id == currentId }
                val pair = if (a != null && b != null && a.id != b.id) {
                    if (a.startedAt <= b.startedAt) a to b else b to a
                } else WorkoutCompare.defaultPair(workouts)
                if (pair == null) {
                    item { EmptyState(Icons.AutoMirrored.Filled.ShowChart, "Log two workouts to compare progress", "Compare any two completed workouts and see what moved.") }
                } else {
                    val comparison = WorkoutCompare.build(pair.first, pair.second) { formatVolume(it, unit) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WorkoutPicker("Earlier", comparison.previous, workouts, Modifier.weight(1f)) { previousId = it.id; currentId = comparison.current.id }
                            WorkoutPicker("Later", comparison.current, workouts, Modifier.weight(1f)) { currentId = it.id; previousId = comparison.previous.id }
                        }
                    }
                    item {
                        Text(
                            if (comparison.sameTemplate) "Same template · like-for-like comparison" else "Different sessions · general comparison",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    item {
                        SectionCard {
                            comparison.metrics.forEach { m ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(m.title, Modifier.weight(1f))
                                    Text("${m.previous} → ${m.current}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(8.dp))
                                    Text(m.delta, color = directionColor(m.direction), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                    item { SectionHeader("Highlights") }
                    items(comparison.highlights) { h ->
                        SectionCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(h.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(h.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(h.detail, style = MaterialTheme.typography.bodySmall)
                                }
                                DirectionIcon(h.direction)
                            }
                        }
                    }
                    if (comparison.exercises.isNotEmpty()) {
                        item { SectionHeader("Exercise progress") }
                        item {
                            Text(
                                "Best set by estimated 1RM. Volume excludes bodyweight sets; e1RM is an estimate.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(comparison.exercises, key = { it.exerciseUuid }) { e ->
                            SectionCard(onClick = { nav.navigate(Routes.exercise(e.exerciseUuid)) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(e.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                    DirectionIcon(e.direction)
                                }
                                Text("${e.previousBest} → ${e.currentBest}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(e.delta, color = directionColor(e.direction), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            } else {
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
}

@Composable
private fun WorkoutPicker(label: String, selected: WorkoutEntity, all: List<WorkoutEntity>, modifier: Modifier, onPick: (WorkoutEntity) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Surface(onClick = { open = true }, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(selected.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${formatDayShort(selected.startedAt)} · ${formatTime(selected.startedAt)}", style = MaterialTheme.typography.bodySmall)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            all.take(60).forEach { w ->
                DropdownMenuItem(text = { Text("${w.name} · ${formatDayShort(w.startedAt)}") }, onClick = { open = false; onPick(w) })
            }
        }
    }
}

@Composable
fun Labeled(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(nav: NavHostController) {
    val app = LocalContext.current.app
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val workouts by app.repository.workouts.collectAsStateWithLifecycle(emptyList())
    val facts by app.repository.facts.collectAsStateWithLifecycle(emptyList())
    val exercises by app.repository.exercises.collectAsStateWithLifecycle(emptyList())
    var customizing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(settings.displayName.ifBlank { "Profile" }, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { customizing = true }) { Icon(Icons.Default.Tune, "Customize dashboard") }
                    IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        },
    ) { padding ->
        val catalog = remember(exercises) { exercises.associateBy { it.uuid } }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ProfileHeader() }
            items(settings.dashboard, key = { it.id }) { widget ->
                DashboardCard(widget, settings, workouts, facts, catalog, nav)
            }
        }
    }

    if (customizing) DashboardEditor(onDismiss = { customizing = false })
}

@Composable
private fun DashboardCard(
    widget: DashboardWidget,
    settings: UserSettings,
    workouts: List<WorkoutEntity>,
    facts: List<SetFactEntity>,
    catalog: Map<String, ExerciseEntity>,
    nav: NavHostController,
) {
    val unit = settings.weightUnit
    val weekStart = weekStartOf(LocalDate.now())
    val thisWeek = remember(workouts) { workouts.filter { !it.startedAt.toLocalDate().isBefore(weekStart) } }
    when (widget.kind) {
        WidgetKind.weeklyGoal -> SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { (thisWeek.size.toFloat() / settings.weeklyGoal).coerceAtMost(1f) },
                        modifier = Modifier.size(72.dp),
                        strokeWidth = 8.dp,
                        color = if (thisWeek.size >= settings.weeklyGoal) Wgj.colors.success else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    Text("${thisWeek.size}/${settings.weeklyGoal}", fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("Weekly goal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (thisWeek.size >= settings.weeklyGoal) "Goal reached this week" else "${settings.weeklyGoal - thisWeek.size} to go this week",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        WidgetKind.coachBrief -> {
            val brief = remember(facts) { WeeklyCoach.build(facts) }
            var follow by remember { mutableStateOf<String?>(null) }
            SectionCard {
                Text("COACH BRIEF", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Text(brief.headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(follow ?: brief.body)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = { follow = brief.whatImproved }, label = { Text("Improved") })
                    AssistChip(onClick = { follow = brief.whatChanged }, label = { Text("Changed") })
                    AssistChip(onClick = { follow = brief.whyFlat }, label = { Text("Flat?") })
                }
            }
        }
        WidgetKind.streaks -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Goal streak", plural(Consistency.goalStreakWeeks(workouts, settings.weeklyGoal), "wk"), Modifier.weight(1f))
            StatTile("Active weeks", Consistency.activeWeekStreak(workouts).toString(), Modifier.weight(1f))
            StatTile("Workouts", workouts.size.toString(), Modifier.weight(1f))
        }
        WidgetKind.prs -> SectionCard {
            Text("Recent PRs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val prs = workouts.flatMap { w -> w.exercises.flatMap { e -> e.sets.filter { it.isPr }.map { Triple(w, e, it) } } }.take(5)
            if (prs.isEmpty()) Text("No PRs yet. They appear when you beat a previous estimated 1RM.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            prs.forEach { (w, e, s) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.name, fontWeight = FontWeight.Medium)
                        Text(formatDayShort(w.startedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(formatSet(s))
                    Spacer(Modifier.width(8.dp))
                    PrBadge()
                }
            }
        }
        WidgetKind.muscleHeatmap -> SectionCard {
            Text("This week's muscles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            MuscleHeatmapGrid(remember(thisWeek, catalog) { MuscleHeatmap.build(thisWeek, catalog) })
        }
        WidgetKind.consistencyCalendar -> SectionCard {
            Text("Consistency · last 16 weeks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ConsistencyCalendar(remember(workouts) { Consistency.workoutDays(workouts) }, ::weekStartOf)
        }
        WidgetKind.topExercises -> SectionCard {
            Text("Top exercises", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val top = remember(facts) {
                facts.filter { !it.isWarmup }.groupBy { it.exerciseUuid }
                    .map { (uuid, list) -> Triple(uuid, list.last().exerciseName, list.size) }
                    .sortedByDescending { it.third }.take(5)
            }
            if (top.isEmpty()) Text("Log a few workouts to see your most trained lifts.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            top.forEach { (uuid, name, sets) ->
                Row(Modifier.fillMaxWidth().clickableNoRipple { nav.navigate(Routes.exercise(uuid)) }) {
                    Text(name, Modifier.weight(1f))
                    Text(plural(sets, "set"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        WidgetKind.exerciseTrend -> SectionCard(onClick = widget.exerciseUuid?.let { { nav.navigate(Routes.exercise(it)) } }) {
            Text("${widget.exerciseName ?: "Exercise"} · ${widget.metric.title}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val points = remember(facts, widget) {
                ExerciseTrend.series(facts.filter { it.exerciseUuid == widget.exerciseUuid }, widget.metric)
            }
            LineChart(points, { formatTrend(widget.metric, it, unit) })
        }
        WidgetKind.calories -> SectionCard {
            Text("Active calories this week", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (!settings.calorieProfile.isComplete) {
                Text("Add sex, birth date, height and weight in Settings to see estimates.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Text("Open Settings") }
            } else {
                val kcal = thisWeek.sumOf { CalorieEstimator.estimate(it, settings.calorieProfile) ?: 0 }
                Text("≈ $kcal kcal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Estimated from duration, sets and cardio. Not a medical measurement.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
