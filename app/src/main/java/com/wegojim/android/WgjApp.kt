package com.wegojim.android

import android.app.Application
import android.content.Context
import com.wegojim.android.data.ActiveWorkoutStore
import com.wegojim.android.data.AppDatabase
import com.wegojim.android.data.Repository
import com.wegojim.android.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.wegojim.android.data.CalorieEstimator

class WgjApp : Application() {
    val appScope = CoroutineScope(SupervisorJob())
    val settings by lazy { SettingsStore(this) }
    val database by lazy { AppDatabase.create(this) }
    val repository by lazy { Repository(this, database, settings) }
    val activeWorkout by lazy { ActiveWorkoutStore(this, repository, settings, appScope, ::afterWorkoutSaved) }

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
        appScope.launch {
            repository.seedCatalogIfNeeded()
            WeeklyGoalWidget.refresh(this@WgjApp)
        }
    }
}

val Context.app: WgjApp get() = applicationContext as WgjApp
