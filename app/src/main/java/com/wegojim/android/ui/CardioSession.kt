package com.wegojim.android.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.wegojim.android.GpsState
import com.wegojim.android.app
import com.wegojim.android.data.CardioBlock
import com.wegojim.android.data.CardioGoal
import com.wegojim.android.data.CardioMetrics
import com.wegojim.android.data.CardioProfile
import com.wegojim.android.data.CardioRecording
import com.wegojim.android.data.CardioResultValidator
import com.wegojim.android.data.CardioRoute
import com.wegojim.android.data.CardioSummary
import com.wegojim.android.data.DistanceUnit
import com.wegojim.android.data.ExerciseEntity
import com.wegojim.android.data.WorkoutEditor
import com.wegojim.android.data.newCardioBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

fun cardioIcon(exerciseUuid: String): ImageVector = when {
    CardioRecording.isCycling(exerciseUuid) -> Icons.AutoMirrored.Filled.DirectionsBike
    "run" in exerciseUuid -> Icons.AutoMirrored.Filled.DirectionsRun
    "walk" in exerciseUuid || "hiking" in exerciseUuid -> Icons.AutoMirrored.Filled.DirectionsWalk
    else -> Icons.AutoMirrored.Filled.DirectionsRun
}

// region Route drawing

/**
 * A recorded route drawn on its own (Web Mercator, fitted to the space). Pauses
 * and GPS gaps stay disconnected. There are no map tiles: the app has no network
 * access, so the route never leaves the device to be drawn.
 */
@Composable
fun RouteDrawing(route: CardioRoute, modifier: Modifier = Modifier, isCompleted: Boolean = true) {
    val line = Color(0xFF6DB5FF)
    val start = Color(0xFF62D8A6)
    val grid = Color.White.copy(alpha = 0.05f)
    Canvas(
        modifier
            .background(Color(0xFF0B1A26), RoundedCornerShape(18.dp))
            .semantics { contentDescription = "Recorded route" },
    ) {
        if (route.points.isEmpty()) return@Canvas
        fun x(lon: Double) = (lon + 180) / 360
        fun y(lat: Double): Double {
            val r = lat.coerceIn(-85.0, 85.0) * PI / 180
            return (1 - ln(tan(r) + 1 / kotlin.math.cos(r)) / PI) / 2
        }
        val xs = route.points.map { x(it.longitude) }
        val ys = route.points.map { y(it.latitude) }
        val minX = xs.min(); val maxX = xs.max(); val minY = ys.min(); val maxY = ys.max()
        val pad = 18.dp.toPx()
        val spanX = max(maxX - minX, 1e-7)
        val spanY = max(maxY - minY, 1e-7)
        val scale = min((size.width - 2 * pad) / spanX, (size.height - 2 * pad) / spanY)
        val offX = (size.width - spanX * scale) / 2
        val offY = (size.height - spanY * scale) / 2
        fun point(i: Int) = Offset((offX + (xs[i] - minX) * scale).toFloat(), (offY + (ys[i] - minY) * scale).toFloat())
        val step = 28.dp.toPx()
        var gx = 0f
        while (gx < size.width) { drawLine(grid, Offset(gx, 0f), Offset(gx, size.height)); gx += step }
        var gy = 0f
        while (gy < size.height) { drawLine(grid, Offset(0f, gy), Offset(size.width, gy)); gy += step }
        val path = Path()
        var segment: Int? = null
        route.points.forEachIndexed { i, p ->
            val o = point(i)
            if (p.segment != segment) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            segment = p.segment
        }
        drawPath(path, Color.Black.copy(alpha = 0.3f), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, line, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(Color.White, 7.dp.toPx(), point(0))
        drawCircle(start, 5.dp.toPx(), point(0))
        if (route.points.size > 1) {
            drawCircle(Color.White, 8.dp.toPx(), point(route.points.lastIndex))
            drawCircle(if (isCompleted) line else Color(0xFFE0C56B), 5.dp.toPx(), point(route.points.lastIndex))
        }
    }
}

/** A saved activity's route, read from disk. Shows nothing when there isn't one. */
@Composable
fun SavedRoute(sessionId: String, activityId: String, modifier: Modifier = Modifier, caption: Boolean = true) {
    val app = LocalContext.current.app
    val route by produceState<CardioRoute?>(null, sessionId, activityId) {
        value = withContext(Dispatchers.IO) {
            app.routes.load(activityId)?.takeIf { it.sessionId == sessionId && !it.isRecording && it.points.isNotEmpty() }
        }
    }
    val r = route ?: return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (caption) Text("Recorded route", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        RouteDrawing(r, Modifier.fillMaxWidth().height(200.dp))
        if (caption) Text("Route stored on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// endregion

// region Activity card and quick picker

/** Entry back into the recording screen; the timer ticks only inside this card (port of CardioSessionCard). */
@Composable
fun CardioSessionCard(block: CardioBlock, editor: WorkoutEditor, onOpen: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf(false) }
    var changing by remember { mutableStateOf(false) }
    val distanceUnit = LocalContext.current.app.settings.state.collectAsStateWithLifecycle().value.distanceUnit
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(block.isRunning) {
        while (block.isRunning) { now = System.currentTimeMillis(); delay(500) }
    }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(cardioIcon(block.exerciseUuid), null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(block.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (CardioRecording.recordsGps(block.exerciseUuid)) "GPS distance & route" else "Timer · Add distance afterward",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Cardio actions") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Set a goal") }, onClick = { menu = false; editingGoal = true })
                    DropdownMenuItem(text = { Text("Change activity") }, onClick = { menu = false; changing = true })
                    DropdownMenuItem(text = { Text("Remove", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; editor.removeCardio(block.id) })
                }
            }
        }
        when {
            block.completed -> Text(cardioResultSummary(block), style = MaterialTheme.typography.titleMedium, color = Wgj.colors.success, fontWeight = FontWeight.SemiBold)
            block.isRunning || block.timerAccumulatedSeconds > 0 ->
                Text(formatClock(block.elapsedSeconds(now).toLong()), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Wgj.colors.cyan)
            block.goal != CardioGoal.openEnded -> Text("Goal ${cardioGoalSummary(block)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = onOpen, Modifier.fillMaxWidth()) {
            Icon(if (block.completed) Icons.Default.Check else Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(6.dp))
            Text(
                when {
                    block.completed -> "View Activity"
                    block.isRunning || block.timerAccumulatedSeconds > 0 -> "Open Recording"
                    else -> "Start Activity"
                },
            )
        }
    }
    if (editingGoal) {
        CardioSetupDialog(block, onDismiss = { editingGoal = false }, onSave = { updated -> editor.updateCardio(block.id) { updated }; editingGoal = false })
    }
    if (changing) {
        CardioQuickPicker(onDismiss = { changing = false }) { exercise ->
            changing = false
            // A different activity starts over: its result and any recorded route go.
            editor.removeCardio(block.id)
            editor.addCardio(newCardioBlock(exercise, block.phase, block.role, distanceUnit).copy(goal = CardioGoal.openEnded, targetDurationSeconds = 0))
        }
    }
}

private data class QuickChoice(val uuid: String, val name: String, val icon: ImageVector)

private val outdoorChoices = listOf(
    QuickChoice("seed-outdoor-walk", "Outdoor Walk", Icons.AutoMirrored.Filled.DirectionsWalk),
    QuickChoice("seed-outdoor-run", "Outdoor Run", Icons.AutoMirrored.Filled.DirectionsRun),
    QuickChoice("seed-outdoor-bike", "Outdoor Bike", Icons.AutoMirrored.Filled.DirectionsBike),
)
private val indoorChoices = listOf(
    QuickChoice("seed-treadmill-walk", "Treadmill Walk", Icons.AutoMirrored.Filled.DirectionsWalk),
    QuickChoice("seed-treadmill-run", "Treadmill Run", Icons.AutoMirrored.Filled.DirectionsRun),
    QuickChoice("seed-bike", "Bike", Icons.AutoMirrored.Filled.DirectionsBike),
    QuickChoice("seed-crosstrainer", "Crosstrainer", Icons.AutoMirrored.Filled.DirectionsRun),
    QuickChoice("seed-row-machine", "Row Machine", Icons.Default.Timer),
    QuickChoice("seed-stair-climber", "Stair Climber", Icons.Default.Timer),
)

/** Port of CardioActivityQuickPicker: outdoor GPS activities, common indoor ones, or the full cardio catalog. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CardioQuickPicker(onDismiss: () -> Unit, onSelect: (ExerciseEntity) -> Unit) {
    val all by LocalContext.current.app.repository.exercises.collectAsStateWithLifecycle(emptyList())
    val byId = remember(all) { all.associateBy { it.uuid } }
    var browsing by remember { mutableStateOf(false) }
    if (browsing) {
        ExercisePickerDialog(onDismiss = { browsing = false }, mode = PickerMode.Cardio, single = true, title = "More Cardio", onConfirm = { onSelect(it.first()) })
        return
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Choose Cardio") },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel") } },
                )
            },
        ) { padding ->
            Column(
                Modifier.padding(padding).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                @Composable
                fun section(title: String, subtitle: String, choices: List<QuickChoice>) {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    choices.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { choice ->
                                val exercise = byId[choice.uuid]
                                Surface(
                                    onClick = { exercise?.let(onSelect) },
                                    enabled = exercise != null,
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.weight(1f).heightIn(min = 104.dp),
                                ) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Icon(choice.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                        Text(choice.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                section("Outdoors", "Record distance and your route with GPS.", outdoorChoices)
                Spacer(Modifier.height(4.dp))
                section("Indoors", "Time your activity and add distance afterward.", indoorChoices)
                Spacer(Modifier.height(4.dp))
                Button(onClick = { browsing = true }, Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.List, null); Spacer(Modifier.width(6.dp)); Text("More Cardio")
                }
            }
        }
    }
}

// endregion

// region Recording screen

/**
 * Port of CardioRecordingView: a big timer, distance and pace, the route as it
 * records, and start/pause/finish. [embedded] when the workout is just this
 * activity, so the screen is the workout itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardioRecordingScreen(
    nav: NavHostController,
    activityId: String,
    embedded: Boolean = false,
    onCancelWorkout: () -> Unit = {},
    onSaveWorkout: () -> Unit = {},
) {
    val app = LocalContext.current.app
    val context = LocalContext.current
    val store = app.activeWorkout
    val workout by store.state.collectAsStateWithLifecycle()
    val liveRoute by app.routeRecorder.route.collectAsStateWithLifecycle()
    val gpsState by app.routeRecorder.gpsState.collectAsStateWithLifecycle()
    val w = workout
    val block = w?.cardio?.firstOrNull { it.id == activityId }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }
    var reviewing by remember { mutableStateOf(false) }
    var reviewFinishesWorkout by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var askedPermission by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (w == null || block == null) {
        if (!embedded) LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val gps = CardioRecording.recordsGps(block.exerciseUuid)
    val savedRoute by produceState<CardioRoute?>(null, block.id, block.completed) {
        value = if (block.completed) withContext(Dispatchers.IO) { app.routeRecorder.routeFor(w.id, block.id) } else null
    }
    val route = if (block.completed) savedRoute else liveRoute?.takeIf { it.activityId == block.id && it.sessionId == w.id }
    val canSaveWorkout = w.exercises.isEmpty() && w.cardio.isNotEmpty() && w.cardio.all { it.completed }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        askedPermission = true
        app.routeRecorder.sync(store.state.value)
    }
    fun requestLocation() = permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))

    fun startOrResume() {
        error = null
        if (!store.startCardioTimer(block.id)) {
            error = "Pause or finish your other cardio activity first."
            return
        }
        if (gps && !app.routeRecorder.hasPermission()) requestLocation()
    }

    fun openReview(finishesWorkout: Boolean) {
        reviewFinishesWorkout = finishesWorkout
        reviewing = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        if (embedded || !block.completed) Icon(Icons.Default.KeyboardArrowDown, "Minimize")
                        else Icon(Icons.AutoMirrored.Filled.ArrowBack, "Done")
                    }
                },
                title = { Text(if (block.completed) "Activity Summary" else "Record Activity") },
                actions = {
                    if (embedded) Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Activity options") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Cancel workout", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; confirmCancel = true })
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (block.completed) {
                        if (canSaveWorkout) {
                            Button(onClick = onSaveWorkout, Modifier.fillMaxWidth()) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(6.dp)); Text("Save Workout") }
                        }
                        OutlinedButton(onClick = { openReview(false) }, Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Edit, null); Spacer(Modifier.width(6.dp)); Text("Edit Result")
                        }
                    } else {
                        Button(
                            onClick = { if (block.isRunning) store.pauseCardioTimer(block.id) else startOrResume() },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Icon(if (block.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (block.isRunning) "Pause" else if (block.timerAccumulatedSeconds > 0) "Resume" else "Start", fontSize = 17.sp)
                        }
                        if (block.isRunning || block.timerAccumulatedSeconds > 0) {
                            OutlinedButton(onClick = { confirmFinish = true }, Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Stop, null); Spacer(Modifier.width(6.dp)); Text("Finish Activity")
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(cardioIcon(block.exerciseUuid), null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(block.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            block.completed -> "Completed"
                            block.isRunning -> "In progress"
                            block.timerAccumulatedSeconds > 0 -> "Paused"
                            else -> "Ready when you are"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            RecordingStats(block, route)
            if (gps) {
                if (route != null && route.points.isNotEmpty()) {
                    RouteDrawing(route, Modifier.fillMaxWidth().height(230.dp), isCompleted = block.completed)
                } else {
                    SectionCard {
                        Column(Modifier.fillMaxWidth().heightIn(min = 150.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
                            Icon(Icons.Default.Map, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(if (block.completed) "No route recorded" else "Your route starts here", fontWeight = FontWeight.SemiBold)
                            Text(
                                when {
                                    block.completed -> "You can add the distance manually."
                                    block.isRunning -> "Your route appears here when GPS finds your location."
                                    else -> "Start to record your outdoor activity with GPS."
                                },
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (!block.completed) {
                    val permitted = app.routeRecorder.hasPermission()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            " " + if (!permitted && block.isRunning) GpsState.Denied.message else gpsState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!permitted) {
                        OutlinedButton(onClick = {
                            if (askedPermission) {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                            } else requestLocation()
                        }, Modifier.fillMaxWidth()) {
                            Text(if (askedPermission) "Open Location Settings" else "Allow Location")
                        }
                    }
                }
            } else {
                SectionCard {
                    Text("Start the timer. Add the distance from your machine when you finish.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            error?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                    Text(" $it", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("Finish activity?") },
            text = { Text("Save your time and distance for this activity.", style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                Button(onClick = {
                    confirmFinish = false
                    // Finish first so the timer and GPS stop together, then make sure the route is on disk.
                    val recorded = app.routeRecorder.routeFor(w.id, block.id)?.distanceMeters?.takeIf { gps && it > 0 }
                    store.finishCardio(block.id, recorded)
                    app.routeRecorder.flush()
                    val now = store.state.value
                    openReview(now != null && now.exercises.isEmpty() && now.cardio.all { it.completed })
                }) { Text("Finish Activity") }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Keep Recording") } },
        )
    }
    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Cancel workout?") },
            text = { Text("This activity and its recorded route will be discarded.") },
            confirmButton = { TextButton(onClick = { confirmCancel = false; onCancelWorkout() }) { Text("Cancel Workout", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Keep Recording") } },
        )
    }
    if (reviewing) {
        val recordedDistance = route?.distanceMeters?.takeIf { gps && it > 0 && block.distanceMeters == it }
        CardioResultDialog(
            block = block,
            recordedDurationSeconds = block.durationSeconds,
            recordedDistanceMeters = recordedDistance,
            saveTitle = if (reviewFinishesWorkout) "Save Workout" else "Save Result",
            onDismiss = { reviewing = false },
            onSave = { result ->
                store.saveCardioResult(block.id, result)
                reviewing = false
                if (reviewFinishesWorkout) scope.launch { onSaveWorkout() } else if (!embedded) nav.popBackStack()
            },
        )
    }
}

@Composable
private fun RecordingStats(block: CardioBlock, route: CardioRoute?) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(block.isRunning) {
        while (block.isRunning) { now = System.currentTimeMillis(); delay(500) }
    }
    val seconds = if (block.completed) block.durationSeconds ?: 0 else block.elapsedSeconds(now)
    val distance = if (block.completed) block.distanceMeters else route?.distanceMeters ?: block.distanceMeters
    val unit = if (block.distanceUnit == DistanceUnit.miles) DistanceUnit.miles else DistanceUnit.kilometers
    val metrics = CardioMetrics.calculate(seconds, distance, unit, block.profile)
    val showsSpeed = block.profile == CardioProfile.machineDistance
    val speed = if (seconds > 0 && distance != null && distance > 0) unit.fromMeters(distance) / (seconds / 3600.0) else null
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Time", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatClock(seconds.toLong()), fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Wgj.colors.cyan, maxLines = 1)
        }
        Row(Modifier.fillMaxWidth()) {
            StatColumn("Distance", distance?.let { "%.2f".format(unit.fromMeters(it)) } ?: "—", unit.symbol, Modifier.weight(1f))
            if (showsSpeed) StatColumn("Avg. speed", speed?.let { "%.1f".format(it) } ?: "—", "${unit.symbol}/h", Modifier.weight(1f))
            else StatColumn("Avg. pace", metrics.paceSecondsPerUnit?.let { formatClock(Math.round(it)) } ?: "—", "/${unit.symbol}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatColumn(title: String, value: String, unit: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// endregion

// region Result review

/**
 * Port of WorkoutCardioResultEditor: recorded time and GPS distance are shown
 * as results with an Edit option, typed values for everything else, and the
 * derived pace or speed as you type.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CardioResultDialog(
    block: CardioBlock,
    recordedDurationSeconds: Int?,
    recordedDistanceMeters: Double?,
    saveTitle: String,
    onDismiss: () -> Unit,
    onSave: (com.wegojim.android.data.CardioResult) -> Unit,
) {
    val outdoor = CardioRecording.recordsGps(block.exerciseUuid)
    val initialDuration = recordedDurationSeconds ?: block.durationSeconds
    var editingDuration by remember { mutableStateOf(initialDuration == null) }
    var minutes by remember { mutableStateOf(initialDuration?.let { (it / 60).toString() } ?: "") }
    var seconds by remember { mutableStateOf(initialDuration?.let { (it % 60).toString().padStart(2, '0') } ?: "") }
    var unit by remember { mutableStateOf(block.distanceUnit) }
    val originalText = remember { CardioResultValidator.distanceText(block.distanceMeters, block.distanceUnit) }
    var distanceText by remember { mutableStateOf(originalText) }
    var editingDistance by remember { mutableStateOf(block.distanceMeters == null) }
    var incline by remember { mutableStateOf(block.inclinePercent?.let(::formatNumber) ?: "") }
    var resistance by remember { mutableStateOf(block.resistanceLevel?.let(::formatNumber) ?: "") }
    var notes by remember { mutableStateOf(block.notes) }
    var error by remember { mutableStateOf<String?>(null) }

    val duration = if (editingDuration) {
        val m = minutes.toIntOrNull()
        val s = seconds.toIntOrNull()?.coerceIn(0, 59)
        if (m == null && s == null) null else (m ?: 0) * 60 + (s ?: 0)
    } else initialDuration
    fun validate() = CardioResultValidator.validate(
        durationSeconds = duration,
        distanceText = distanceText,
        distanceUnit = unit,
        originalDistanceMeters = block.distanceMeters?.takeIf { unit == block.distanceUnit },
        originalDistanceText = originalText,
        inclineText = incline,
        resistanceText = resistance,
        notes = notes,
        profile = block.profile,
    )
    val preview = validate().getOrNull()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (outdoor && recordedDurationSeconds != null) "Summary" else "Cardio Result") },
                    navigationIcon = { TextButton(onClick = onDismiss) { Text("Cancel") } },
                )
            },
            bottomBar = {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Button(
                        onClick = { validate().onSuccess(onSave).onFailure { error = it.message } },
                        modifier = Modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth().height(52.dp),
                    ) { Icon(Icons.Default.Check, null); Spacer(Modifier.width(6.dp)); Text(saveTitle) }
                }
            },
        ) { padding ->
            Column(
                Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column {
                    Text(block.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (recordedDurationSeconds != null) "Review your recorded activity. Edit anything that looks off."
                        else "Add what you did. Time or distance is enough.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SectionCard {
                    MetricHeader(Icons.Default.Timer, "Duration", editable = !editingDuration && initialDuration != null) { editingDuration = true }
                    if (!editingDuration && initialDuration != null) {
                        Text(formatClock(initialDuration.toLong()), fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
                    } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), label = { Text("Minutes") }, singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                        OutlinedTextField(seconds, { seconds = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = { Text("Seconds") }, singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                    }
                    if (block.profile.tracksDistance || block.distanceMeters != null) {
                        HorizontalDivider()
                        MetricHeader(
                            Icons.Default.LocationOn,
                            if (recordedDistanceMeters != null && !editingDistance) "GPS distance" else "Distance",
                            editable = !editingDistance && block.distanceMeters != null,
                        ) { editingDistance = true }
                        if (!editingDistance && block.distanceMeters != null) {
                            Text("${originalText.ifEmpty { "0" }} ${block.distanceUnit.symbol}", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = Wgj.colors.cyan)
                        } else {
                            OutlinedTextField(distanceText, { distanceText = it }, Modifier.fillMaxWidth(), label = { Text("Distance") }, suffix = { Text(unit.symbol) }, singleLine = true,
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                            Segmented(listOf(DistanceUnit.kilometers, DistanceUnit.miles, DistanceUnit.meters), unit, { it.symbol }) { unit = it }
                        }
                    }
                    val derived = preview?.let {
                        CardioSummary.metrics(it.durationSeconds, it.distanceMeters, it.distanceUnit, block.profile, null, null)
                            .filter { m -> m.kind == CardioSummary.Kind.pace || m.kind == CardioSummary.Kind.averageSpeed || m.kind == CardioSummary.Kind.rowingPace }
                    }.orEmpty()
                    if (derived.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            derived.forEach { Labeled(it.title, it.value) }
                        }
                    }
                }
                if (!outdoor && (block.profile.tracksIncline || block.profile.tracksResistance)) {
                    SectionCard {
                        if (block.profile.tracksIncline) OutlinedTextField(incline, { incline = it }, Modifier.fillMaxWidth(), label = { Text("Incline") }, suffix = { Text("%") }, singleLine = true)
                        if (block.profile.tracksResistance) OutlinedTextField(
                            resistance, { resistance = it }, Modifier.fillMaxWidth(),
                            label = { Text(if (block.profile == CardioProfile.stairClimber) "Level" else "Resistance") }, singleLine = true,
                        )
                    }
                }
                OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("Notes (optional)") }, minLines = 2)
                error?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                        Text(" $it", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricHeader(icon: ImageVector, title: String, editable: Boolean, onEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(" $title", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
        if (editable) TextButton(onClick = onEdit) { Text("Edit") }
    }
}

// endregion
