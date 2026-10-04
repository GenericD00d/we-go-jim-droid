package com.wegojim.android.data

/** Port of WorkoutPerformanceMath.swift. */
object PerformanceMath {
    private const val LB_TO_KG = 0.45359237

    /** Epley estimate, matching the iOS app. */
    fun estimatedOneRepMax(weight: Double, reps: Int): Double =
        if (reps <= 1) weight else weight * (1 + reps / 30.0)

    fun toKg(value: Double, unit: LoadUnit): Double = when (unit) {
        LoadUnit.lb -> value * LB_TO_KG
        LoadUnit.kg, LoadUnit.bodyweight -> value
    }

    fun fromKg(valueKg: Double, unit: LoadUnit): Double = when (unit) {
        LoadUnit.lb -> valueKg / LB_TO_KG
        LoadUnit.kg, LoadUnit.bodyweight -> valueKg
    }

    fun volumeKg(weight: Double, reps: Int, unit: LoadUnit): Double =
        if (unit == LoadUnit.bodyweight) 0.0 else toKg(weight, unit) * maxOf(0, reps)
}
