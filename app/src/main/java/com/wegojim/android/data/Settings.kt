package com.wegojim.android.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserSettings(
    val displayName: String = "",
    val weightUnit: LoadUnit = LoadUnit.kg,
    val weeklyGoal: Int = 4,
    val defaultRestSeconds: Int = 120,
    val autoStartRestTimer: Boolean = true,
    val keepScreenAwake: Boolean = false,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("wgj_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<UserSettings> = _state.asStateFlow()

    var seedVersion: Int
        get() = prefs.getInt("seedVersion", 0)
        set(value) = prefs.edit { putInt("seedVersion", value) }

    fun update(transform: (UserSettings) -> UserSettings) {
        val next = transform(_state.value)
        prefs.edit {
            putString("displayName", next.displayName)
            putString("weightUnit", next.weightUnit.name)
            putInt("weeklyGoal", next.weeklyGoal)
            putInt("defaultRestSeconds", next.defaultRestSeconds)
            putBoolean("autoStartRestTimer", next.autoStartRestTimer)
            putBoolean("keepScreenAwake", next.keepScreenAwake)
        }
        _state.value = next
    }

    private fun read() = UserSettings(
        displayName = prefs.getString("displayName", "") ?: "",
        weightUnit = runCatching { LoadUnit.valueOf(prefs.getString("weightUnit", "kg")!!) }.getOrDefault(LoadUnit.kg),
        weeklyGoal = prefs.getInt("weeklyGoal", 4),
        defaultRestSeconds = prefs.getInt("defaultRestSeconds", 120),
        autoStartRestTimer = prefs.getBoolean("autoStartRestTimer", true),
        keepScreenAwake = prefs.getBoolean("keepScreenAwake", false),
    )
}
