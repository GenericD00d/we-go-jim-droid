package com.wegojim.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wegojim.android.app
import com.wegojim.android.data.ExerciseEntity

/** "Chest · Triceps, Shoulders", without repeating a muscle that matches the category. */
fun ExerciseEntity.catalogSubtitle(): String =
    (listOf(category) + primaryList.filter { it != category }.joinToString(", ")).filter { it.isNotBlank() }.joinToString(" · ")

/** Search filter shared by the picker and the catalog tab. */
fun List<ExerciseEntity>.filterCatalog(query: String, category: String?): List<ExerciseEntity> {
    val q = query.trim()
    return filter { e ->
        (category == null || e.category == category) &&
            (q.isEmpty() || e.name.contains(q, ignoreCase = true) || e.aliases.contains(q, ignoreCase = true) ||
                e.primaryMuscles.contains(q, ignoreCase = true) || e.equipment.contains(q, ignoreCase = true))
    }
}

@Composable
fun CatalogFilters(
    query: String,
    onQueryChange: (String) -> Unit,
    categories: List<String>,
    category: String?,
    onCategoryChange: (String?) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        placeholder = { Text("Search exercises, muscles, equipment") },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Clear, "Clear") } },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
    )
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { FilterChip(selected = category == null, onClick = { onCategoryChange(null) }, label = { Text("All") }) }
        items(categories) { c ->
            FilterChip(selected = category == c, onClick = { onCategoryChange(if (category == c) null else c) }, label = { Text(c) })
        }
    }
}

enum class PickerMode { Strength, Cardio }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (List<ExerciseEntity>) -> Unit,
    mode: PickerMode = PickerMode.Strength,
    single: Boolean = false,
    title: String = if (mode == PickerMode.Cardio) "Add Cardio" else "Add Exercises",
) {
    val repo = LocalContext.current.app.repository
    val all by repo.exercises.collectAsStateWithLifecycle(emptyList())
    val pool = remember(all, mode) { all.filter { it.isCardio == (mode == PickerMode.Cardio) } }
    val categories = remember(pool) { pool.map { it.category }.distinct().sorted() }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = remember { mutableStateListOf<String>() }
    val filtered = remember(pool, query, category) { pool.filterCatalog(query, category) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close") } },
                    actions = {
                        if (!single) TextButton(enabled = selected.isNotEmpty(), onClick = {
                            val byId = pool.associateBy { it.uuid }
                            onConfirm(selected.mapNotNull { byId[it] })
                        }) { Text(if (selected.isEmpty()) "Add" else "Add (${selected.size})") }
                    },
                )
            },
        ) { padding ->
            LazyColumn(contentPadding = padding) {
                item { CatalogFilters(query, { query = it }, categories, category, { category = it }) }
                items(filtered, key = { it.uuid }) { exercise ->
                    val checked = exercise.uuid in selected
                    val toggle = {
                        if (single) onConfirm(listOf(exercise))
                        else if (checked) selected.remove(exercise.uuid) else selected.add(exercise.uuid)
                    }
                    ListItem(
                        headlineContent = { Text(exercise.name) },
                        supportingContent = { Text(exercise.catalogSubtitle()) },
                        trailingContent = { if (!single) Checkbox(checked = checked, onCheckedChange = { toggle() }) },
                        modifier = Modifier.clickable { toggle() },
                    )
                }
            }
        }
    }
}
