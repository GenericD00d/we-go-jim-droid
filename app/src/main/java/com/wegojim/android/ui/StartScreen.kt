package com.wegojim.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.ViewList
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.wegojim.android.data.TemplateTransfer
import com.wegojim.android.data.TemplateFolderEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartScreen(nav: NavHostController) {
    val context = LocalContext.current
    val repo = context.app.repository
    val store = context.app.activeWorkout
    val scope = rememberCoroutineScope()
    val templates by repo.templates.collectAsStateWithLifecycle(emptyList())
    val folders by repo.folders.collectAsStateWithLifecycle(emptyList())
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { importTemplateUri(context, uri) }
    }
    var collapsed by rememberSaveable { mutableStateOf(setOf<String>()) }
    var creatingFolder by remember { mutableStateOf(false) }
    var renamingFolder by remember { mutableStateOf<TemplateFolderEntity?>(null) }
    var deletingFolder by remember { mutableStateOf<TemplateFolderEntity?>(null) }
    val active by store.state.collectAsStateWithLifecycle()

    var preview by remember { mutableStateOf<TemplateEntity?>(null) }
    var pendingStart by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    var deleting by remember { mutableStateOf<TemplateEntity?>(null) }

    // Starting a new workout replaces any in-progress one, so confirm first.
    fun begin(action: suspend () -> Unit) {
        if (store.isActive) pendingStart = action else scope.launch {
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
                    IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Default.FileDownload, "Import template")
                    }
                    TextButton(onClick = { creatingFolder = true }) {
                        Icon(Icons.Default.CreateNewFolder, null); Text(" Folder")
                    }
                    TextButton(onClick = { nav.navigate(Routes.template("new")) }) {
                        Icon(Icons.Default.Add, null); Text("New")
                    }
                }
            }
            if (templates.isEmpty()) {
                item {
                    EmptyState(
                        Icons.AutoMirrored.Filled.ViewList,
                        "No templates yet",
                        "Templates hold your planned exercises, sets, reps and rest times so you can start a session in one tap.",
                    )
                }
            }
            val folderIds = folders.map { it.id }.toSet()
            val sections = folders.map { it to templates.filter { t -> t.folderId == it.id } } +
                listOf(null to templates.filter { it.folderId !in folderIds })
            sections.forEach { (folder, list) ->
                val key = folder?.id ?: "none"
                if (folders.isNotEmpty() && (folder != null || list.isNotEmpty())) {
                    item(key = "folder-$key") {
                        FolderHeader(
                            name = folder?.name ?: "No folder",
                            count = list.size,
                            expanded = key !in collapsed,
                            onToggle = { collapsed = if (key in collapsed) collapsed - key else collapsed + key },
                            onNewTemplate = folder?.let { { nav.navigate(Routes.template("new", it.id)) } },
                            onRename = folder?.let { { renamingFolder = it } },
                            onDelete = folder?.let { { deletingFolder = it } },
                            onShare = folder?.let { f ->
                                {
                                    shareTextFile(context, f.name, TemplateTransfer.EXTENSION, TemplateTransfer.exportFolder(f.name, list), "Share folder")
                                }
                            },
                        )
                    }
                }
                if (key !in collapsed) {
                    items(list, key = { it.id }) { template ->
                        TemplateCard(
                            template = template,
                            onClick = { preview = template },
                            onEdit = { nav.navigate(Routes.template(template.id)) },
                            onDuplicate = { scope.launch { repo.duplicateTemplate(template.id) } },
                            onShare = {
                                shareTextFile(context, template.name, TemplateTransfer.EXTENSION, TemplateTransfer.exportTemplate(template), "Share template")
                            },
                            onDelete = { deleting = template },
                        )
                    }
                }
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
                    scope.launch {
                        action()
                        nav.navigate(Routes.WORKOUT)
                    }
                }) { Text("Discard & Start", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingStart = null }) { Text("Keep Current") } },
        )
    }

    if (creatingFolder) {
        NameDialog("New folder", "", confirm = "Create", onDismiss = { creatingFolder = false }) { name ->
            creatingFolder = false
            scope.launch { repo.createFolder(name) }
        }
    }
    renamingFolder?.let { folder ->
        NameDialog("Rename folder", folder.name, onDismiss = { renamingFolder = null }) { name ->
            renamingFolder = null
            scope.launch { repo.renameFolder(folder, name) }
        }
    }
    deletingFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            title = { Text("Delete folder \"${folder.name}\"?") },
            text = { Text("Templates in this folder are kept and moved to \"No folder\".") },
            confirmButton = {
                TextButton(onClick = {
                    deletingFolder = null
                    scope.launch { repo.deleteFolder(folder.id) }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingFolder = null }) { Text("Cancel") } },
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
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    SectionCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(template.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val sets = template.exercises.sumOf { it.workingSets.size }
                Text(
                    listOfNotNull(
                        plural(template.exercises.size, "exercise"),
                        plural(sets, "set"),
                        template.cardio.takeIf { it.isNotEmpty() }?.let { "${it.size} cardio" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Template actions") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
                    DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; onShare() })
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
        template.cardio.forEach { c ->
            Row {
                Text(c.name, Modifier.weight(1f), fontWeight = FontWeight.Medium, color = Wgj.colors.cyan)
                Text("${c.role.label} · ${cardioGoalSummary(c)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
        Button(onClick = onStart, Modifier.fillMaxWidth(), enabled = template.exercises.isNotEmpty() || template.cardio.isNotEmpty()) {
            Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Start Workout")
        }
        FilledTonalButton(onClick = onEdit, Modifier.fillMaxWidth()) { Text("Edit Template") }
    }
}

@Composable
private fun FolderHeader(
    name: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onNewTemplate: (() -> Unit)?,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onShare: (() -> Unit)?,
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clickableNoRipple(onToggle), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (expanded) Icons.Default.FolderOpen else Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
        Text(" $name", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text("$count", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "Collapse" else "Expand")
        if (onRename != null) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Folder actions") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    onNewTemplate?.let { DropdownMenuItem(text = { Text("New template here") }, onClick = { menu = false; it() }) }
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                    onShare?.let { DropdownMenuItem(text = { Text("Share folder") }, onClick = { menu = false; it() }) }
                    onDelete?.let { DropdownMenuItem(text = { Text("Delete folder", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; it() }) }
                }
            }
        }
    }
}
