package com.wegojim.android

import android.app.Application
import android.content.Context
import com.wegojim.android.data.ActiveWorkoutStore
import com.wegojim.android.data.AppDatabase
import com.wegojim.android.data.Repository
import com.wegojim.android.data.RouteStore
import com.wegojim.android.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import com.wegojim.android.data.CalorieEstimator

class WgjApp : Application() {
    val appScope = CoroutineScope(SupervisorJob())
    val settings by lazy { SettingsStore(this) }
    val database by lazy { AppDatabase.create(this) }
    val routes by lazy { RouteStore(this) }
    val routeRecorder by lazy { RouteRecorder(this, routes) }
    val repository by lazy { Repository(this, database, settings, routes) }
    val activeWorkout by lazy { ActiveWorkoutStore(this, repository, settings, routeRecorder, appScope, ::afterWorkoutSaved) }

    /** Set when the workout notification is tapped; the UI opens the active workout and clears it. */
    val openWorkoutRequest = MutableStateFlow(false)

    /** Background follow-ups for a saved workout: widget refresh and optional Health Connect export. */
    fun afterWorkoutSaved(workoutId: String) {
        appScope.launch {
            WeeklyGoalWidget.refresh(this@WgjApp)
            val s = settings.state.value
            if (s.healthConnectExport) {
                val workout = repository.workout(workoutId).first() ?: return@launch
                val kcal = if (s.showCalories) CalorieEstimator.estimate(workout, s.calorieProfile) else null
                HealthConnectExport.export(this@WgjApp, workout, kcal)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        RestTimerAlarm.createChannel(this)
        LiveWorkoutNotification.createChannel(this)
        appScope.launch {
            repository.seedCatalogIfNeeded()
            WeeklyGoalWidget.refresh(this@WgjApp)
        }
        // GPS and the workout notification follow the active workout and its settings.
        combine(activeWorkout.state, settings.state) { workout, _ -> workout }
            .onEach { workout ->
                routeRecorder.sync(workout)
                LiveWorkoutNotification.refresh(this)
            }
            .launchIn(appScope)
    }
}

val Context.app: WgjApp get() = applicationContext as WgjApp
