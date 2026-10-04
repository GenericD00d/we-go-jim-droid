package com.wegojim.android.ui

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.wegojim.android.app
import com.wegojim.android.data.CalorieSex
import com.wegojim.android.data.DashboardWidget
import com.wegojim.android.data.DistanceUnit
import com.wegojim.android.data.LoadUnit
import com.wegojim.android.data.PerformanceMath
import com.wegojim.android.data.TrendMetric
import com.wegojim.android.data.WidgetKind
import com.wegojim.android.data.moved
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
                    Segmented(CalorieSex.entries, settings.sex ?: CalorieSex.entries.first(), { it.label }) { s -> store.update { it.copy(sex = s) } }
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
