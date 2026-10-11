package com.wegojim.android.data

import java.text.NumberFormat
import kotlin.math.max
import kotlin.math.roundToInt

/** A reviewed cardio result, ready to save (port of ValidatedWorkoutCardioResult). */
data class CardioResult(
    val durationSeconds: Int?,
    val distanceMeters: Double?,
    val distanceUnit: DistanceUnit,
    val inclinePercent: Double?,
    val resistanceLevel: Double?,
    val notes: String,
)

/** Port of WorkoutCardioResultValidator. Returns the result or a readable error. */
object CardioResultValidator {
    fun validate(
        durationSeconds: Int?,
        distanceText: String,
        distanceUnit: DistanceUnit,
        originalDistanceMeters: Double?,
        originalDistanceText: String?,
        inclineText: String,
        resistanceText: String,
        notes: String,
        profile: CardioProfile,
    ): Result<CardioResult> = runCatching {
        if (durationSeconds != null && durationSeconds < 0) error("Duration can't be negative.")
        val duration = durationSeconds?.takeIf { it > 0 }
        // The editable text is rounded, so a very short distance can display as 0.
        // Keep the source measurement until the text is actually edited.
        val distance = if (originalDistanceMeters != null && originalDistanceMeters > 0 && distanceText == originalDistanceText) {
            originalDistanceMeters
        } else {
            val text = distanceText.trim()
            if (text.isEmpty()) null
            else {
                val value = parseNumber(text) ?: error("Enter a valid distance.")
                if (value > 0) distanceUnit.toMeters(value).also { if (!it.isFinite()) error("Enter a valid distance.") } else null
            }
        }
        if (duration == null && distance == null) error("Add a duration or a distance.")
        val incline = if (!profile.tracksIncline || inclineText.isBlank()) null
        else (parseNumber(inclineText) ?: error("Enter a valid incline.")).coerceIn(0.0, 100.0)
        val resistance = if (!profile.tracksResistance || resistanceText.isBlank()) null
        else (parseNumber(resistanceText) ?: error("Enter a valid level.")).also { if (it < 0) error("Level can't be negative.") }
        CardioResult(duration, distance, distanceUnit, incline, resistance, notes.trim())
    }

    /** Accepts "5,2" and "5.2". */
    fun parseNumber(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    /** Editable distance text: up to two decimals, no grouping. */
    fun distanceText(meters: Double?, unit: DistanceUnit): String {
        if (meters == null || !meters.isFinite() || meters <= 0) return ""
        val value = Math.round(unit.fromMeters(meters) * 100) / 100.0
        return if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
    }
}

/** Port of WorkoutCardioResultSummaryFormatter: the metrics shown for a saved activity, most telling first. */
object CardioSummary {
    enum class Kind { pace, averageSpeed, rowingPace, level, duration, distance, incline, resistance }

    data class Metric(val kind: Kind, val title: String, val value: String)

    fun metrics(c: CardioBlock): List<Metric> =
        metrics(c.durationSeconds, c.distanceMeters, c.distanceUnit, c.profile, c.inclinePercent, c.resistanceLevel)

    fun metrics(
        durationSeconds: Int?,
        distanceMeters: Double?,
        unit: DistanceUnit,
        profile: CardioProfile,
        inclinePercent: Double?,
        resistanceLevel: Double?,
    ): List<Metric> {
        val duration = durationSeconds?.takeIf { it > 0 }
        val distance = distanceMeters?.takeIf { it.isFinite() && it > 0 }
        val calc = CardioMetrics.calculate(duration, distance, unit, profile)
        val speed = if (duration != null && distance != null) unit.fromMeters(distance) / (duration / 3600.0) else null
        val out = mutableListOf<Metric>()
        when (profile) {
            CardioProfile.walkRun, CardioProfile.treadmill ->
                calc.paceSecondsPerUnit?.let { out += Metric(Kind.pace, "Pace", "${pace(it)} /${unit.symbol}") }
            CardioProfile.machineDistance -> speed?.let { out += Metric(Kind.averageSpeed, "Avg Speed", speedText(it, unit)) }
            CardioProfile.rower -> calc.rowSplitSecondsPer500m?.let { out += Metric(Kind.rowingPace, "500 m Pace", "${pace(it)} /500 m") }
            CardioProfile.stairClimber, CardioProfile.timeOnly -> Unit
        }
        if (profile == CardioProfile.stairClimber) resistanceLevel?.takeIf { it.isFinite() && it >= 0 }?.let {
            out += Metric(Kind.level, "Level", number(it))
        }
        duration?.let { out += Metric(Kind.duration, "Duration", durationText(it)) }
        distance?.let { out += Metric(Kind.distance, "Distance", "${number(unit.fromMeters(it))} ${unit.symbol}") }
        if (profile == CardioProfile.walkRun || profile == CardioProfile.treadmill) speed?.let {
            out += Metric(Kind.averageSpeed, "Avg Speed", speedText(it, unit))
        }
        if (profile.tracksIncline) inclinePercent?.takeIf { it.isFinite() }?.let {
            out += Metric(Kind.incline, "Incline", "${number(it.coerceIn(0.0, 100.0))}%")
        }
        if (profile.tracksResistance && profile != CardioProfile.stairClimber) resistanceLevel?.takeIf { it.isFinite() && it >= 0 }?.let {
            out += Metric(Kind.resistance, "Resistance", number(it))
        }
        return out
    }

    /** "12 min" or "12:30". */
    fun durationText(seconds: Int): String {
        val s = max(0, seconds)
        return if (s % 60 == 0) "${s / 60} min" else "%d:%02d".format(s / 60, s % 60)
    }

    fun pace(seconds: Double): String {
        val r = max(0, seconds.roundToInt())
        return if (r >= 3600) "%d:%02d:%02d".format(r / 3600, r % 3600 / 60, r % 60) else "%d:%02d".format(r / 60, r % 60)
    }

    private fun speedText(speed: Double, unit: DistanceUnit) = "${number(speed)} " + when (unit) {
        DistanceUnit.kilometers -> "km/h"
        DistanceUnit.miles -> "mph"
        DistanceUnit.meters -> "m/h"
    }

    fun number(value: Double): String = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(value)
}
