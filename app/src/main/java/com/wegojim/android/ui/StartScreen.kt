package com.wegojim.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.wegojim.android.app
import com.wegojim.android.data.TemplateEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartScreen(nav: NavHostController) {
    val context = LocalContext.current
    val repo = context.app.repository
    val store = context.app.activeWorkout
    val scope = rememberCoroutineScope()
    val templates by repo.templates.collectAsStateWithLifecycle(emptyList())
    val active by store.state.collectAsStateWithLifecycle()

    var preview by remember { mutableStateOf<TemplateEntity?>(null) }
    var pendingStart by remember { mutableStateOf<(() -> Unit)?>(null) }
    var deleting by remember { mutableStateOf<TemplateEntity?>(null) }

    // Starting a new workout replaces any in-progress one, so confirm first.
    fun begin(action: () -> Unit) {
        if (store.isActive) pendingStart = action else {
            action()
            nav.navigate(Routes.WORKOUT)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Start Workout", fontWeight = FontWeight.Bold) }) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Quick Start", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        if (active != null) "You have a workout in progress." else "Start an empty session and add exercises as you go.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (active != null) {
                        Button(onClick = { nav.navigate(Routes.WORKOUT) }, Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Resume ${active!!.name}")
                        }
                        OutlinedButton(onClick = { begin { store.startEmpty() } }, Modifier.fillMaxWidth()) { Text("Start Empty Workout") }
                    } else {
                        Button(onClick = { begin { store.startEmpty() } }, Modifier.fillMaxWidth()) { Text("Start Empty Workout") }
                    }
                }
            }
            item {
                SectionHeader("Templates") {
                    TextButton(onClick = { nav.navigate(Routes.template("new")) }) {
                        Icon(Icons.Default.Add, null); Text("New")
                    }
                }
            }
            if (templates.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Default.ViewList,
                        "No templates yet",
                        "Templates hold your planned exercises, sets, reps and rest times so you can start a session in one tap.",
                    )
                }
            }
            items(templates, key = { it.id }) { template ->
                TemplateCard(
                    template = template,
                    onClick = { preview = template },
                    onEdit = { nav.navigate(Routes.template(template.id)) },
                    onDuplicate = { scope.launch { repo.duplicateTemplate(template.id) } },
                    onDelete = { deleting = template },
                )
            }
        }
    }

    preview?.let { template ->
        ModalBottomSheet(onDismissRequest = { preview = null }) {
            TemplatePreview(
                template = template,
                onStart = {
                    preview = null
                    begin { store.startFromTemplate(template) }
                },
                onEdit = {
                    preview = null
                    nav.navigate(Routes.template(template.id))
                },
            )
        }
    }

    pendingStart?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingStart = null },
            title = { Text("Replace current workout?") },
            text = { Text("Your in-progress workout will be discarded. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingStart = null
                    store.discard()
                    action()
                    nav.navigate(Routes.WORKOUT)
                }) { Text("Discard & Start", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingStart = null }) { Text("Keep Current") } },
        )
    }

    deleting?.let { template ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${template.name}\"?") },
            text = { Text("Workouts already logged from this template stay in your history.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch { repo.deleteTemplate(template.id) }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun TemplateCard(
    template: TemplateEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    SectionCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(template.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val sets = template.exercises.sumOf { it.workingSets.size }
                Text(
                    "${template.exercises.size} exercises · $sets sets",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Template actions") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
                }
            }
        }
        if (template.exercises.isNotEmpty()) {
            Text(
                template.exercises.joinToString(" · ") { it.name },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TemplatePreview(template: TemplateEntity, onStart: () -> Unit, onEdit: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(template.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (template.notes.isNotBlank()) Text(template.notes, color = MaterialTheme.colorScheme.onSurfaceVariant)
        template.exercises.forEach { e ->
            Row {
                Text(e.name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                val warmups = e.sets.count { it.isWarmup }
                val reps = when {
                    e.targetRepMin != null && e.targetRepMax != null && e.targetRepMin != e.targetRepMax -> " × ${e.targetRepMin}-${e.targetRepMax}"
                    e.targetRepMin != null -> " × ${e.targetRepMin}"
                    else -> ""
                }
                Text(
                    (if (warmups > 0) "${warmups}W + " else "") + "${e.workingSets.size} sets$reps",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onStart, Modifier.fillMaxWidth(), enabled = template.exercises.isNotEmpty()) {
            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Start Workout")
        }
        FilledTonalButton(onClick = onEdit, Modifier.fillMaxWidth()) { Text("Edit Template") }
    }
}
