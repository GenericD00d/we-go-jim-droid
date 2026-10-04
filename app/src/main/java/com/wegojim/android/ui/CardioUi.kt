package com.wegojim.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wegojim.android.data.CardioBlock
import com.wegojim.android.data.CardioGoal
import com.wegojim.android.data.CardioMetrics
import com.wegojim.android.data.CardioPhase
import com.wegojim.android.data.CardioRole
import com.wegojim.android.data.DistanceUnit
import com.wegojim.android.data.WorkoutEditor
import kotlinx.coroutines.delay

fun formatDistance(meters: Double, unit: DistanceUnit) = "${formatNumber(unit.fromMeters(meters))} ${unit.symbol}"

fun cardioGoalSummary(c: CardioBlock): String = when (c.goal) {
    CardioGoal.time -> formatDuration(c.targetDurationSeconds)
    CardioGoal.distance -> c.targetDistanceMeters?.let { formatDistance(it, c.distanceUnit) } ?: "Distance"
    CardioGoal.openEnded -> "Open"
}

fun cardioResultSummary(c: CardioBlock): String = buildList {
    c.durationSeconds?.let { add(formatClock(it.toLong())) }
    c.distanceMeters?.let { add(formatDistance(it, c.distanceUnit)) }
    val m = CardioMetrics.calculate(c.durationSeconds, c.distanceMeters, c.distanceUnit, c.profile)
    m.paceSecondsPerUnit?.let { add("${formatClock(it.toLong())} /${c.distanceUnit.symbol}") }
    m.rowSplitSecondsPer500m?.let { add("${formatClock(it.toLong())} /500m") }
    if (m.paceSecondsPerUnit == null) m.speedPerHour?.let { add("${formatNumber(Math.round(it * 10) / 10.0)} ${c.distanceUnit.symbol}/h") }
    c.inclinePercent?.let { add("${formatNumber(it)}% incline") }
    c.resistanceLevel?.let { add("level ${formatNumber(it)}") }
}.joinToString(" · ").ifEmpty { "Not logged" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Segmented(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label(option), maxLines = 1) }
        }
    }
}

/** Phase, role and goal for a cardio block (templates and live workouts). */
@Composable
fun CardioSetupDialog(initial: CardioBlock, onDismiss: () -> Unit, onSave: (CardioBlock) -> Unit) {
    var block by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(block.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("When", style = MaterialTheme.typography.labelLarge)
                Segmented(CardioPhase.entries, block.phase, { it.label }) { block = block.copy(phase = it) }
                Text("Role", style = MaterialTheme.typography.labelLarge)
                Segmented(CardioRole.entries, block.role, { it.label }) { block = block.copy(role = it) }
                Text("Goal", style = MaterialTheme.typography.labelLarge)
                Segmented(CardioGoal.entries.filter { it != CardioGoal.distance || block.profile.tracksDistance }, block.goal, { it.label }) {
                    block = block.copy(goal = it)
                }
                when (block.goal) {
                    CardioGoal.time -> NumberField(
                        block.targetDurationSeconds / 60.0,
                        { v -> block = block.copy(targetDurationSeconds = ((v ?: 0.0) * 60).toInt()) },
                        Modifier.fillMaxWidth(),
                        placeholder = "Minutes",
                        suffix = "min",
                    )
                    CardioGoal.distance -> {
                        NumberField(
                            block.targetDistanceMeters?.let { block.distanceUnit.fromMeters(it) },
                            { v -> block = block.copy(targetDistanceMeters = v?.let { block.distanceUnit.toMeters(it) }) },
                            Modifier.fillMaxWidth(),
                            placeholder = "Distance",
                            suffix = block.distanceUnit.symbol,
                        )
                        Segmented(DistanceUnit.entries, block.distanceUnit, { it.symbol }) { block = block.copy(distanceUnit = it) }
                    }
                    CardioGoal.openEnded -> Text("No target, just log what you do.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(block) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CardioHeader(block: CardioBlock, onEdit: () -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.DirectionsRun, null, tint = Wgj.colors.cyan)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(block.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Wgj.colors.cyan)
            Text(
                "${block.role.label} · ${block.phase.label} · Goal ${cardioGoalSummary(block)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Cardio actions") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Edit goal") }, onClick = { menu = false; onEdit() })
                DropdownMenuItem(text = { Text("Remove", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onRemove() })
            }
        }
    }
}

/** Planned cardio in the template editor. */
@Composable
fun CardioPlanCard(block: CardioBlock, onChange: (CardioBlock) -> Unit, onRemove: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    SectionCard { CardioHeader(block, onEdit = { editing = true }, onRemove = onRemove) }
    if (editing) CardioSetupDialog(block, onDismiss = { editing = false }, onSave = { onChange(it); editing = false })
}

/** Live cardio block with a timer, result inputs and derived pace/speed. */
@Composable
fun ActiveCardioCard(block: CardioBlock, editor: WorkoutEditor) {
    var editing by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(block.isRunning) {
        while (block.isRunning) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val elapsed = block.elapsedSeconds(now)

    SectionCard {
        CardioHeader(block, onEdit = { editing = true }, onRemove = { editor.removeCardio(block.id) })
        if (editor.isLive && !block.completed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatClock(elapsed.toLong()), fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (block.isRunning) {
                    FilledTonalButton(onClick = { editor.pauseCardioTimer(block.id) }) { Icon(Icons.Default.Pause, null); Text("Pause") }
                } else {
                    Button(onClick = { editor.startCardioTimer(block.id) }) { Icon(Icons.Default.PlayArrow, null); Text(if (elapsed > 0) "Resume" else "Start") }
                }
                if (elapsed > 0 && !block.isRunning) IconButton(onClick = { editor.resetCardioTimer(block.id) }) { Icon(Icons.Default.Replay, "Reset timer") }
            }
            if (block.goal == CardioGoal.time && block.targetDurationSeconds > 0) {
                LinearProgressIndicator(
                    progress = { (elapsed.toFloat() / block.targetDurationSeconds).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Wgj.colors.cyan,
                )
            }
        }
        // Results: typed values win over the timer.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val duration = block.durationSeconds
            NumberField(
                duration?.let { (it / 60).toDouble() },
                { v -> editor.updateCardio(block.id) { it.copy(durationSeconds = combine(v?.toInt(), (it.durationSeconds ?: 0) % 60)) } },
                Modifier.weight(1f),
                placeholder = "0",
                suffix = "min",
                integer = true,
            )
            NumberField(
                duration?.let { (it % 60).toDouble() },
                { v -> editor.updateCardio(block.id) { it.copy(durationSeconds = combine((it.durationSeconds ?: 0) / 60, v?.toInt()?.coerceIn(0, 59))) } },
                Modifier.weight(1f),
                placeholder = "00",
                suffix = "s",
                integer = true,
            )
            if (block.profile.tracksDistance) {
                NumberField(
                    block.distanceMeters?.let { block.distanceUnit.fromMeters(it) },
                    { v -> editor.updateCardio(block.id) { it.copy(distanceMeters = v?.let { d -> it.distanceUnit.toMeters(d) }) } },
                    Modifier.weight(1.3f),
                    placeholder = "0",
                    suffix = block.distanceUnit.symbol,
                )
            }
        }
        if (block.profile.tracksIncline || block.profile.tracksResistance) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (block.profile.tracksIncline) NumberField(
                    block.inclinePercent, { v -> editor.updateCardio(block.id) { it.copy(inclinePercent = v) } },
                    Modifier.weight(1f), placeholder = "Incline", suffix = "%",
                )
                if (block.profile.tracksResistance) NumberField(
                    block.resistanceLevel, { v -> editor.updateCardio(block.id) { it.copy(resistanceLevel = v) } },
                    Modifier.weight(1f), placeholder = "Level",
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                cardioResultSummary(block.copy(durationSeconds = block.durationSeconds ?: elapsed.takeIf { it > 0 })),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
            )
            Surface(
                onClick = { editor.toggleCardioCompleted(block.id) },
                shape = MaterialTheme.shapes.medium,
                color = if (block.completed) Wgj.colors.success else MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, null, tint = if (block.completed) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface)
                    Text(
                        if (block.completed) " Done" else " Complete",
                        color = if (block.completed) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
    if (editing) {
        CardioSetupDialog(block, onDismiss = { editing = false }, onSave = { updated ->
            editor.updateCardio(block.id) { updated }
            editing = false
        })
    }
}

private fun combine(minutes: Int?, seconds: Int?): Int? =
    if (minutes == null && seconds == null) null else (minutes ?: 0) * 60 + (seconds ?: 0)
