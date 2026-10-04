package com.wegojim.android.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.wegojim.android.app
import com.wegojim.android.data.LoadUnit
import com.wegojim.android.data.SetEntry
import com.wegojim.android.data.WorkoutEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(nav: NavHostController) {
    val app = LocalContext.current.app
    val workouts by app.repository.workouts.collectAsStateWithLifecycle(null)
    val unit = app.settings.state.collectAsStateWithLifecycle().value.weightUnit
    val byMonth = remember(workouts) { workouts.orEmpty().groupBy { formatMonth(it.startedAt) } }

    Scaffold(topBar = { TopAppBar(title = { Text("History", fontWeight = FontWeight.Bold) }) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (workouts?.isEmpty() == true) {
                item { EmptyState(Icons.Default.History, "No workouts yet", "Finished workouts show up here with their sets, volume and PRs.") }
            }
            byMonth.forEach { (month, list) ->
                item(key = month) { SectionHeader("$month · ${list.size}") }
                items(list, key = { it.id }) { workout ->
                    WorkoutCard(workout, unit) { nav.navigate(Routes.workoutDetail(workout.id)) }
                }
            }
        }
    }
}

@Composable
private fun WorkoutCard(workout: WorkoutEntity, unit: LoadUnit, onClick: () -> Unit) {
    SectionCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(workout.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${formatDayShort(workout.startedAt)} · ${formatTime(workout.startedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (workout.prCount > 0) PrBadge()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Meta("Duration", formatDuration(workout.durationSeconds))
            Meta("Sets", workout.completedSetCount.toString())
            Meta("Volume", formatVolume(workout.totalVolumeKg, unit))
        }
        Text(
            (workout.cardio.map { it.name } + workout.exercises.map { "${it.sets.size}× ${it.name}" }).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

@Composable
private fun Meta(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Completed workout detail; also used as the post-workout summary. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(nav: NavHostController, workoutId: String, isSummary: Boolean) {
    val app = LocalContext.current.app
    val workout by app.repository.workout(workoutId).collectAsStateWithLifecycle(null)
    val unit = app.settings.state.collectAsStateWithLifecycle().value.weightUnit
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var savingTemplate by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isSummary) "Workout Complete" else workout?.name ?: "") },
                navigationIcon = {
                    if (!isSummary) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Workout actions") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Edit workout") }, onClick = { menu = false; nav.navigate(Routes.editWorkout(workoutId)) })
                            DropdownMenuItem(text = { Text("Save as template") }, onClick = { menu = false; savingTemplate = true })
                            if (!isSummary) {
                                DropdownMenuItem(
                                    text = { Text("Delete workout", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { menu = false; confirmDelete = true },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        val w = workout ?: return@Scaffold
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    if (isSummary) Text(w.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${formatDate(w.startedAt)} · ${formatTime(w.startedAt)} – ${formatTime(w.endedAt)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Duration", formatDuration(w.durationSeconds), Modifier.weight(1f))
                    StatTile(if (w.exercises.isEmpty()) "Cardio" else "Sets", if (w.exercises.isEmpty()) formatDuration(w.cardioSeconds) else w.completedSetCount.toString(), Modifier.weight(1f))
                }
                Spacer(Modifier.padding(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Volume", formatVolume(w.totalVolumeKg, unit), Modifier.weight(1f))
                    StatTile("PRs", w.prCount.toString(), Modifier.weight(1f))
                }
            }
            if (isSummary && w.prCount > 0) {
                item {
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EmojiEvents, null, tint = Wgj.colors.gold)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "New estimated 1RM on " + w.exercises.filter { e -> e.sets.any { it.isPr } }.joinToString { it.name },
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            items(w.cardio, key = { it.id }) { block ->
                SectionCard(onClick = { nav.navigate(Routes.exercise(block.exerciseUuid)) }) {
                    Text(block.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Wgj.colors.cyan)
                    Text("${block.role.label} · ${cardioResultSummary(block)}")
                }
            }
            items(w.exercises, key = { it.id }) { exercise ->
                SectionCard(onClick = { nav.navigate(Routes.exercise(exercise.exerciseUuid)) }) {
                    Text(exercise.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    var working = 0
                    exercise.sets.forEach { set ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (set.isWarmup) "W" else (++working).toString(),
                                Modifier.width(32.dp),
                                color = if (set.isWarmup) Wgj.colors.gold else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(formatSet(set), Modifier.weight(1f))
                            if (set.isPr) PrBadge()
                        }
                        set.dropStages.forEach { d ->
                            Text(
                                "      ↳ " + formatSet(SetEntry(reps = d.reps, weight = d.weight, unit = d.unit)),
                                color = Wgj.colors.warning,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            if (w.notes.isNotBlank()) {
                item { SectionCard { Text("Notes", fontWeight = FontWeight.SemiBold); Text(w.notes) } }
            }
            if (isSummary) {
                item {
                    Button(onClick = { nav.popBackStack(Routes.START, inclusive = false) }, Modifier.fillMaxWidth()) { Text("Done") }
                }
            }
        }
    }

    if (savingTemplate) {
        NameDialog("Save as template", workout?.name ?: "", onDismiss = { savingTemplate = false }) { name ->
            savingTemplate = false
            val w = workout ?: return@NameDialog
            scope.launch {
                app.repository.saveAsTemplate(name, w.exercises, w.cardio, w.notes)
                Toast.makeText(context, "Template \"$name\" saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete workout?") },
            text = { Text("This removes the workout and its sets from your history and progress.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        app.repository.deleteWorkout(workoutId)
                        nav.popBackStack()
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
