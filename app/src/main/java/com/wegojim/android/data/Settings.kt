package com.wegojim.android.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.LocalDate

data class UserSettings(
    val displayName: String = "",
    val weightUnit: LoadUnit = LoadUnit.kg,
    val weeklyGoal: Int = 4,
    val defaultRestSeconds: Int = 120,
    val autoStartRestTimer: Boolean = true,
    val keepScreenAwake: Boolean = false,
    val distanceUnit: DistanceUnit = DistanceUnit.kilometers,
    val sex: CalorieSex? = null,
    val birthDate: LocalDate? = null,
    val heightCm: Double? = null,
    val bodyWeightKg: Double? = null,
    val showCalories: Boolean = true,
    val dashboard: List<DashboardWidget> = DashboardWidget.defaults,
) {
    val calorieProfile: CalorieProfile get() = CalorieProfile(sex, birthDate, heightCm, bodyWeightKg)
}

@Serializable
enum class WidgetKind(val title: String) {
    weeklyGoal("Weekly Goal"),
    coachBrief("Coach Brief"),
    streaks("Streaks"),
    prs("PRs"),
    muscleHeatmap("Muscle Heatmap"),
    consistencyCalendar("Consistency"),
    topExercises("Top Exercises"),
    exerciseTrend("Exercise Trend"),
    calories("Calories"),
}

/** A configurable card on the profile dashboard (port of ProfileWidgetConfig). */
@Serializable
data class DashboardWidget(
    val id: String = newId(),
    val kind: WidgetKind,
    val exerciseUuid: String? = null,
    val exerciseName: String? = null,
    val metric: TrendMetric = TrendMetric.oneRepMax,
) {
    companion object {
        val defaults = listOf(
            WidgetKind.weeklyGoal, WidgetKind.coachBrief, WidgetKind.streaks, WidgetKind.prs,
            WidgetKind.muscleHeatmap, WidgetKind.consistencyCalendar, WidgetKind.topExercises,
        ).map { DashboardWidget(id = it.name, kind = it) }
    }
}

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
            putString("distanceUnit", next.distanceUnit.name)
            putString("sex", next.sex?.name)
            if (next.birthDate != null) putLong("birthDate", next.birthDate.toEpochDay()) else remove("birthDate")
            putString("heightCm", next.heightCm?.toString())
            putString("bodyWeightKg", next.bodyWeightKg?.toString())
            putBoolean("showCalories", next.showCalories)
            putString("dashboard", AppJson.encodeToString(ListSerializer(DashboardWidget.serializer()), next.dashboard))
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
        distanceUnit = runCatching { DistanceUnit.valueOf(prefs.getString("distanceUnit", null)!!) }.getOrDefault(regionalDistanceUnit()),
        sex = runCatching { CalorieSex.valueOf(prefs.getString("sex", null)!!) }.getOrNull(),
        birthDate = if (prefs.contains("birthDate")) LocalDate.ofEpochDay(prefs.getLong("birthDate", 0)) else null,
        heightCm = prefs.getString("heightCm", null)?.toDoubleOrNull(),
        bodyWeightKg = prefs.getString("bodyWeightKg", null)?.toDoubleOrNull(),
        showCalories = prefs.getBoolean("showCalories", true),
        dashboard = prefs.getString("dashboard", null)?.let {
            runCatching { AppJson.decodeFromString(ListSerializer(DashboardWidget.serializer()), it) }.getOrNull()
        } ?: DashboardWidget.defaults,
    )
}

/** Miles in the US and UK, kilometers elsewhere (matches the iOS regional default). */
fun regionalDistanceUnit(): DistanceUnit =
    if (java.util.Locale.getDefault().country in setOf("US", "GB", "LR", "MM")) DistanceUnit.miles else DistanceUnit.kilometers
