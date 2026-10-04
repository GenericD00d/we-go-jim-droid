package com.wegojim.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.wegojim.android.app
import com.wegojim.android.data.ExerciseTrend
import com.wegojim.android.data.GymEasterEgg
import com.wegojim.android.data.SetEntry
import com.wegojim.android.data.TrendMetric
import kotlinx.coroutines.launch

private val customCategories = listOf("Arms", "Back", "Chest", "Core", "Legs", "Shoulders", "Conditioning", "Cardio")
private val muscleOptions = listOf(
    "Abs", "Abductors", "Adductors", "Back", "Biceps", "Calves", "Chest", "Forearms",
    "Glutes", "Hamstrings", "Quadriceps", "Shoulders", "Traps", "Triceps",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen(nav: NavHostController) {
    val repo = LocalContext.current.app.repository
    val all by repo.exercises.collectAsStateWithLifecycle(emptyList())
    val categories = remember(all) { all.map { it.category }.distinct().sorted() }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var muscle by rememberSaveable { mutableStateOf<String?>(null) }
    val muscles = remember(all) { all.primaryMuscleOptions() }
    val filtered = remember(all, query, category, muscle) { all.filterCatalog(query, category, muscle) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Exercises", fontWeight = FontWeight.Bold) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Custom") })
        },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 88.dp)) {
            item { CatalogFilters(query, { query = it }, categories, category, { category = it }, muscles, muscle, { muscle = it }) }
            if (filtered.isEmpty() && GymEasterEgg.searchMatches(query)) {
                item { Text(GymEasterEgg.SEARCH_MESSAGE, Modifier.padding(16.dp), color = Wgj.colors.gold, fontWeight = FontWeight.Bold) }
            }
            item {
                Text(
                    "${filtered.size} exercises",
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(filtered, key = { it.uuid }) { exercise ->
                ListItem(
                    headlineContent = { Text(exercise.name) },
                    supportingContent = {
                        Text(listOf(exercise.catalogSubtitle(), exercise.equipment.replace(",", ", ")).filter { it.isNotBlank() }.joinToString(" · "))
                    },
                    trailingContent = { if (exercise.isCustom) Text("Custom", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier.clickable { nav.navigate(Routes.exercise(exercise.uuid)) },
                )
            }
        }
    }

    if (creating) CreateExerciseDialog(onDismiss = { creating = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateExerciseDialog(onDismiss: () -> Unit) {
    val repo = LocalContext.current.app.repository
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(customCategories.first()) }
    var equipment by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Exercise") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Dropdown("Category", category, customCategories) { category = it }
                Dropdown("Primary muscle", muscle ?: "None", listOf("None") + muscleOptions) { muscle = it.takeIf { m -> m != "None" } }
                OutlinedTextField(equipment, { equipment = it }, label = { Text("Equipment (optional)") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                scope.launch { repo.createCustomExercise(name, category, equipment, muscle ?: "") }
                onDismiss()
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dropdown(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseDetailScreen(nav: NavHostController, uuid: String) {
    val app = LocalContext.current.app
    val exercise by app.repository.exercise(uuid).collectAsStateWithLifecycle(null)
    val facts by app.repository.factsForExercise(uuid).collectAsStateWithLifecycle(emptyList())
    val unit = app.settings.state.collectAsStateWithLifecycle().value.weightUnit
    val scope = rememberCoroutineScope()
    val sessions = remember(facts) { facts.groupBy { it.workoutId }.values.toList() }
    var metric by rememberSaveable { mutableStateOf(TrendMetric.oneRepMax) }
    val workouts by app.repository.workouts.collectAsStateWithLifecycle(emptyList())
    val cardio = remember(workouts) {
        workouts.flatMap { w -> w.cardio.filter { it.exerciseUuid == uuid }.map { w to it } }
    }
    val working = remember(facts) { facts.filter { !it.isWarmup } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(exercise?.name ?: facts.firstOrNull()?.exerciseName ?: "") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (exercise?.isCustom == true) {
                        IconButton(onClick = { scope.launch { app.repository.deleteCustomExercise(uuid); nav.popBackStack() } }) {
                            Icon(Icons.Default.Delete, "Delete custom exercise")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            exercise?.let { e ->
                item {
                    SectionCard {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AssistChip(onClick = {}, label = { Text(e.category) })
                            e.equipment.split(",").filter { it.isNotBlank() }.forEach { AssistChip(onClick = {}, label = { Text(it.trim()) }) }
                        }
                        if (e.primaryList.isNotEmpty()) LabeledValue("Primary", e.primaryList.joinToString())
                        if (e.secondaryList.isNotEmpty()) LabeledValue("Secondary", e.secondaryList.joinToString())
                        if (e.instructions.isNotBlank()) Text(e.instructions)
                        if (e.primaryList.isNotEmpty() || e.secondaryList.isNotEmpty()) {
                            Text("Muscle map", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            ExerciseBodyMap(e.primaryList, e.secondaryList)
                        }
                    }
                }
            }
            if (cardio.isNotEmpty()) {
                item { SectionHeader("Cardio history") }
                items(cardio, key = { it.second.id }) { (w, block) ->
                    SectionCard(onClick = { nav.navigate(Routes.workoutDetail(w.id)) }) {
                        Text(formatDate(w.startedAt), fontWeight = FontWeight.SemiBold)
                        Text(cardioResultSummary(block))
                    }
                }
            }
            if (working.isNotEmpty()) {
                item {
                    SectionCard {
                        val available = TrendMetric.entries.filter { m -> m != TrendMetric.oneRepMax && m != TrendMetric.maxWeight || working.any { it.weightKg != null } }
                        Segmented(available, if (metric in available) metric else available.first(), { it.title.replace("Est. ", "") }) { metric = it }
                        LineChart(
                            ExerciseTrend.series(facts, if (metric in available) metric else available.first()),
                            { formatTrend(if (metric in available) metric else available.first(), it, unit) },
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("Est. 1RM", working.mapNotNull { it.e1rmKg }.maxOrNull()?.let { formatKg(it, unit) } ?: "–", Modifier.weight(1f))
                        StatTile("Heaviest", working.mapNotNull { it.weightKg }.maxOrNull()?.let { formatKg(it, unit) } ?: "–", Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("Most reps", working.maxOf { it.reps }.toString(), Modifier.weight(1f))
                        StatTile("Sessions", sessions.size.toString(), Modifier.weight(1f))
                    }
                }
                item { SectionHeader("History") }
                items(sessions, key = { it.first().workoutId }) { sets ->
                    SectionCard(onClick = { nav.navigate(Routes.workoutDetail(sets.first().workoutId)) }) {
                        Text(formatDate(sets.first().completedAt), fontWeight = FontWeight.SemiBold)
                        var n = 0
                        sets.forEach { f ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (f.isWarmup) "W" else (++n).toString(),
                                    Modifier.width(32.dp),
                                    color = if (f.isWarmup) Wgj.colors.gold else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(formatSet(SetEntry(reps = f.reps, weight = f.weight, unit = f.unit)), Modifier.weight(1f))
                                f.e1rmKg?.takeIf { !f.isWarmup && f.reps > 1 }?.let {
                                    Text("e1RM ${formatKg(it, unit)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            } else {
                item { Text("No logged sets yet.", Modifier.fillMaxWidth().padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row {
        Text("$label: ", fontWeight = FontWeight.SemiBold)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
