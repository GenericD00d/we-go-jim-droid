package com.wegojim.android.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import androidx.health.connect.client.PermissionController
import com.wegojim.android.HealthConnectExport
import com.wegojim.android.app
import com.wegojim.android.data.CalorieSex
import com.wegojim.android.data.DashboardWidget
import com.wegojim.android.data.DistanceUnit
import com.wegojim.android.data.LoadUnit
import com.wegojim.android.data.PerformanceMath
import com.wegojim.android.data.TrendMetric
import com.wegojim.android.data.WidgetKind
import com.wegojim.android.data.moved
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavHostController) {
    val app = LocalContext.current.app
    val settings by app.settings.state.collectAsStateWithLifecycle()
    val store = app.settings
    var pickingBirthDate by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching {
                val text = app.repository.exportBackup()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }
            }.isSuccess
            Toast.makeText(context, if (ok) "Backup saved" else "Couldn't save the backup", Toast.LENGTH_LONG).show()
        }
    }
    val healthLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        val ok = granted.containsAll(HealthConnectExport.permissions)
        store.update { it.copy(healthConnectExport = ok) }
        if (!ok) Toast.makeText(context, "Health Connect permission wasn't granted", Toast.LENGTH_LONG).show()
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingRestore = uri }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionHeader("Training") }
            item {
                SectionCard {
                    OutlinedTextField(
                        value = settings.displayName,
                        onValueChange = { v -> store.update { it.copy(displayName = v) } },
                        label = { Text("Display name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Weight unit", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Segmented(listOf(LoadUnit.kg, LoadUnit.lb), settings.weightUnit, { it.label }) { u -> store.update { it.copy(weightUnit = u) } }
                    Text("Distance unit", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Segmented(listOf(DistanceUnit.kilometers, DistanceUnit.miles), settings.distanceUnit, { it.symbol }) { u -> store.update { it.copy(distanceUnit = u) } }
                    Stepper(
                        "Weekly goal", "${settings.weeklyGoal}",
                        onDecrement = { store.update { it.copy(weeklyGoal = (it.weeklyGoal - 1).coerceAtLeast(1)) } },
                        onIncrement = { store.update { it.copy(weeklyGoal = (it.weeklyGoal + 1).coerceAtMost(14)) } },
                    )
                    Stepper(
                        "Default rest", formatRest(settings.defaultRestSeconds),
                        onDecrement = { store.update { it.copy(defaultRestSeconds = (it.defaultRestSeconds - 15).coerceAtLeast(0)) } },
                        onIncrement = { store.update { it.copy(defaultRestSeconds = it.defaultRestSeconds + 15) } },
                    )
                    SwitchRow("Start rest timer when a set is completed", settings.autoStartRestTimer) { v -> store.update { it.copy(autoStartRestTimer = v) } }
                    SwitchRow("Keep screen awake during workouts", settings.keepScreenAwake) { v -> store.update { it.copy(keepScreenAwake = v) } }
                }
            }
            item { SectionHeader("Appearance") }
            item {
                SectionCard {
                    AppTheme.entries.filter { it.isAvailable }.forEach { theme ->
                        Row(
                            Modifier.fillMaxWidth().clickableNoRipple { store.update { it.copy(appTheme = theme.name) } },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = AppTheme.of(settings.appTheme) == theme, onClick = { store.update { it.copy(appTheme = theme.name) } })
                            Column(Modifier.weight(1f)) {
                                Text(theme.title, fontWeight = FontWeight.SemiBold)
                                Text(theme.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item {
                SectionCard {
                    SwitchRow("Training guidance while logging", settings.trainingGuidance) { v -> store.update { it.copy(trainingGuidance = v) } }
                    SwitchRow("Gym bro mode", settings.gymBroMode) { v -> store.update { it.copy(gymBroMode = v) } }
                    Text(
                        "Guidance suggests warm-ups, rep targets and when to change the load. Gym bro mode adds more jokes when you finish.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { SectionHeader("Calorie estimates") }
            item {
                SectionCard {
                    SwitchRow("Show calorie estimates", settings.showCalories) { v -> store.update { it.copy(showCalories = v) } }
                    Text(
                        "Estimates use your body data with standard MET values. They stay on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Sex (for resting metabolic rate)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Segmented(CalorieSex.entries, settings.sex, { it.label }) { s -> store.update { it.copy(sex = s) } }
                    OutlinedButton(onClick = { pickingBirthDate = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(settings.birthDate?.let { "Born ${it.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))}" } ?: "Set birth date")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(settings.heightCm, { v -> store.update { it.copy(heightCm = v) } }, Modifier.weight(1f), "Height", suffix = "cm")
                        val unit = settings.weightUnit
                        NumberField(
                            settings.bodyWeightKg?.let { Math.round(PerformanceMath.fromKg(it, unit) * 10) / 10.0 },
                            { v -> store.update { it.copy(bodyWeightKg = v?.let { w -> PerformanceMath.toKg(w, unit) }) } },
                            Modifier.weight(1f), "Weight", suffix = unit.label,
                        )
                    }
                }
            }
            item { SectionHeader("Health Connect") }
            item {
                SectionCard {
                    val available = remember { HealthConnectExport.isAvailable(context) }
                    SwitchRow("Export finished workouts", settings.healthConnectExport && available) { on ->
                        if (!on) store.update { it.copy(healthConnectExport = false) }
                        else scope.launch {
                            if (HealthConnectExport.hasPermissions(context)) store.update { it.copy(healthConnectExport = true) }
                            else healthLauncher.launch(HealthConnectExport.permissions)
                        }
                    }
                    Text(
                        if (available) "Writes each finished workout (and estimated calories) to Health Connect. Nothing is read."
                        else "Health Connect isn't available on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { SectionHeader("Your data") }
            item {
                SectionCard {
                    Text(
                        "Everything is stored on this device and included in Android's device backup. " +
                            "You can also save a backup file and restore it on another phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { exportLauncher.launch("WeGoJim-backup-${LocalDate.now()}.json") }, Modifier.fillMaxWidth()) { Text("Save backup file") }
                    OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/json", "*/*")) }, Modifier.fillMaxWidth()) { Text("Restore from backup") }
                }
            }
            item { SectionHeader("About") }
            item {
                SectionCard {
                    Text("We Go Jim for Android", fontWeight = FontWeight.SemiBold)
                    Text(
                        "An Android port of We Go Jim by Andreas Hortlund (github.com/Hortlund/we-go-jim), MIT License. " +
                            "Muscle map artwork from MuscleMap by Melih Colpan (github.com/melihcolpan/MuscleMap), MIT License. " +
                            "Your data stays on this device and is included in Android's device backup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Replace all data?") },
            text = { Text("Restoring replaces your templates, folders, history and custom exercises with the backup's contents. Settings are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        val message = runCatching {
                            val text = withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            } ?: error("Couldn't read the file.")
                            val backup = app.repository.restoreBackup(text)
                            "Restored ${backup.workouts.size} workouts and ${backup.templates.size} templates"
                        }.getOrElse { it.message ?: "Restore failed" }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }) { Text("Restore", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }

    if (pickingBirthDate) {
        val initial = (settings.birthDate ?: LocalDate.now().minusYears(30)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { pickingBirthDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        store.update { it.copy(birthDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()) }
                    }
                    pickingBirthDate = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { pickingBirthDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Show, hide, reorder and add profile dashboard cards (port of ProfileWidgetManagerView). */
@Composable
fun DashboardEditor(onDismiss: () -> Unit) {
    val app = LocalContext.current.app
    val settings by app.settings.state.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var pickingExercise by remember { mutableStateOf(false) }
    var trendMetric by remember { mutableStateOf(TrendMetric.oneRepMax) }
    fun set(list: List<DashboardWidget>) = app.settings.update { it.copy(dashboard = list) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize dashboard") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                settings.dashboard.forEach { w ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (w.kind == WidgetKind.exerciseTrend) "${w.exerciseName} · ${w.metric.title}" else w.kind.title,
                            Modifier.weight(1f),
                        )
                        IconButton(onClick = { set(settings.dashboard.moved(w.id, -1) { it.id }) }) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                        IconButton(onClick = { set(settings.dashboard.moved(w.id, 1) { it.id }) }) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                        IconButton(onClick = { set(settings.dashboard.filterNot { it.id == w.id }) }) { Icon(Icons.Default.Close, "Remove") }
                    }
                }
                Box {
                    TextButton(onClick = { adding = true }) { Icon(Icons.Default.Add, null); Text("Add card") }
                    DropdownMenu(expanded = adding, onDismissRequest = { adding = false }) {
                        WidgetKind.entries
                            .filter { k -> k == WidgetKind.exerciseTrend || settings.dashboard.none { it.kind == k } }
                            .forEach { k ->
                                if (k == WidgetKind.exerciseTrend) {
                                    TrendMetric.entries.forEach { m ->
                                        DropdownMenuItem(text = { Text("Exercise trend · ${m.title}") }, onClick = {
                                            adding = false
                                            trendMetric = m
                                            pickingExercise = true
                                        })
                                    }
                                } else {
                                    DropdownMenuItem(text = { Text(k.title) }, onClick = {
                                        adding = false
                                        set(settings.dashboard + DashboardWidget(kind = k))
                                    })
                                }
                            }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = { set(DashboardWidget.defaults) }) { Text("Reset") } },
    )

    if (pickingExercise) {
        ExercisePickerDialog(onDismiss = { pickingExercise = false }, single = true, title = "Trend exercise", onConfirm = { picked ->
            val e = picked.first()
            set(settings.dashboard + DashboardWidget(kind = WidgetKind.exerciseTrend, exerciseUuid = e.uuid, exerciseName = e.name, metric = trendMetric))
            pickingExercise = false
        })
    }
}
