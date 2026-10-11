package com.wegojim.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wegojim.android.app
import com.wegojim.android.data.ActiveWorkout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

object Routes {
    const val PROFILE = "profile"
    const val HISTORY = "history"
    const val START = "start"
    const val PROGRESS = "progress"
    const val EXERCISES = "exercises"
    const val WORKOUT = "workout"
    const val TEMPLATE = "template/{id}?folder={folder}"
    const val EDIT_WORKOUT = "editWorkout/{id}"
    const val WORKOUT_DETAIL = "workoutDetail/{id}?summary={summary}"
    const val EXERCISE_DETAIL = "exercise/{uuid}"
    const val SETTINGS = "settings"
    const val CARDIO = "cardio/{id}"
    const val JOURNEY = "journey"
    const val ACHIEVEMENTS = "achievements/{page}"

    fun template(id: String, folderId: String? = null) = "template/$id" + (folderId?.let { "?folder=$it" } ?: "")
    fun editWorkout(id: String) = "editWorkout/$id"
    fun workoutDetail(id: String, summary: Boolean = false) = "workoutDetail/$id?summary=$summary"
    fun exercise(uuid: String) = "exercise/$uuid"
    fun cardio(activityId: String) = "cardio/$activityId"
    fun achievements(page: String) = "achievements/$page"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.PROFILE, "Profile", Icons.Default.Person),
    Tab(Routes.HISTORY, "History", Icons.Default.History),
    Tab(Routes.START, "Workout", Icons.Default.AddCircle),
    Tab(Routes.PROGRESS, "Progress", Icons.AutoMirrored.Filled.ShowChart),
    Tab(Routes.EXERCISES, "Exercises", Icons.Default.FitnessCenter),
)

@Composable
fun WgjRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val active by LocalContext.current.app.activeWorkout.state.collectAsStateWithLifecycle()
    val onTab = tabs.any { it.route == route }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (onTab) {
                Column {
                    active?.let { ActiveWorkoutBanner(it) { nav.navigate(Routes.WORKOUT) } }
                    NavigationBar {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route,
                                onClick = { nav.switchTab(tab.route) },
                                icon = { Icon(tab.icon, null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.START,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.START) { StartScreen(nav) }
            composable(Routes.HISTORY) { HistoryScreen(nav) }
            composable(Routes.PROGRESS) { ProgressScreen(nav) }
            composable(Routes.EXERCISES) { ExercisesScreen(nav) }
            composable(Routes.PROFILE) { ProfileScreen(nav) }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }
            composable(Routes.WORKOUT) { ActiveWorkoutScreen(nav) }
            composable(
                Routes.TEMPLATE,
                arguments = listOf(navArgument("folder") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { TemplateEditorScreen(nav, it.arguments?.getString("id") ?: "new", it.arguments?.getString("folder")) }
            composable(Routes.EDIT_WORKOUT) { EditWorkoutScreen(nav, it.arguments?.getString("id")!!) }
            composable(Routes.WORKOUT_DETAIL) {
                WorkoutDetailScreen(nav, it.arguments?.getString("id")!!, it.arguments?.getString("summary") == "true")
            }
            composable(Routes.EXERCISE_DETAIL) { ExerciseDetailScreen(nav, it.arguments?.getString("uuid")!!) }
            composable(Routes.CARDIO) {
                val store = LocalContext.current.app.activeWorkout
                val scope = rememberCoroutineScope()
                CardioRecordingScreen(nav, it.arguments?.getString("id")!!, onSaveWorkout = {
                    scope.launch {
                        val id = store.finish()
                        if (id != null) nav.navigate(Routes.workoutDetail(id, summary = true)) { popUpTo(Routes.START) }
                    }
                })
            }
            composable(Routes.JOURNEY) { JourneyScreen(nav) }
            composable(Routes.ACHIEVEMENTS) { AchievementsScreen(nav, it.arguments?.getString("page") ?: "earned") }
        }
    }

    // Tapping the workout notification opens the active workout.
    val openRequested by LocalContext.current.app.openWorkoutRequest.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(openRequested) {
        if (!openRequested) return@LaunchedEffect
        context.app.openWorkoutRequest.value = false
        if (context.app.activeWorkout.isActive && nav.currentDestination?.route != Routes.WORKOUT) nav.navigate(Routes.WORKOUT)
    }
}

fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun ActiveWorkoutBanner(workout: ActiveWorkout, onOpen: () -> Unit) {
    val elapsed = rememberElapsedSeconds(workout.startedAt)
    Surface(onClick = onOpen, color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Workout in progress", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(workout.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Text(formatClock(elapsed), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Icon(Icons.Default.KeyboardArrowUp, "Open workout", Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
fun rememberElapsedSeconds(since: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    return (now - since) / 1000
}

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
}
