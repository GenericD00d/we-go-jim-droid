package com.wegojim.android

import android.app.Application
import android.content.Context
import com.wegojim.android.data.ActiveWorkoutStore
import com.wegojim.android.data.AppDatabase
import com.wegojim.android.data.Repository
import com.wegojim.android.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WgjApp : Application() {
    val appScope = CoroutineScope(SupervisorJob())
    val settings by lazy { SettingsStore(this) }
    val database by lazy { AppDatabase.create(this) }
    val repository by lazy { Repository(this, database, settings) }
    val activeWorkout by lazy { ActiveWorkoutStore(this, repository, settings, appScope) }

    override fun onCreate() {
        super.onCreate()
        RestTimerAlarm.createChannel(this)
        appScope.launch { repository.seedCatalogIfNeeded() }
    }
}

val Context.app: WgjApp get() = applicationContext as WgjApp
