package com.wegojim.android.ui

import com.wegojim.android.data.LoadUnit
import com.wegojim.android.data.PerformanceMath
import com.wegojim.android.data.SetEntry
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.WeekFields
import java.util.Locale

private val decimal = DecimalFormat("#.##")
private val grouped = DecimalFormat("#,##0")
private val oneDecimal = DecimalFormat("#.#")

fun formatNumber(value: Double): String = decimal.format(value)

/** Accepts both "57,5" and "57.5". */
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

fun formatSet(set: SetEntry): String {
    val reps = set.reps ?: set.targetReps
    val weight = set.weight ?: set.targetWeight
    return when {
        set.unit == LoadUnit.bodyweight -> if (weight != null && weight > 0) "BW+${formatNumber(weight)} × ${reps ?: "–"}" else "BW × ${reps ?: "–"}"
        weight == null -> "${reps ?: "–"} reps"
        else -> "${formatNumber(weight)} ${set.unit.label} × ${reps ?: "–"}"
    }
}

fun formatKg(valueKg: Double, unit: LoadUnit): String {
    val display = if (unit == LoadUnit.lb) LoadUnit.lb else LoadUnit.kg
    return "${oneDecimal.format(PerformanceMath.fromKg(valueKg, display))} ${display.label}"
}

fun formatVolume(valueKg: Double, unit: LoadUnit): String {
    val display = if (unit == LoadUnit.lb) LoadUnit.lb else LoadUnit.kg
    return "${grouped.format(PerformanceMath.fromKg(valueKg, display))} ${display.label}"
}

/** "1:02:03" or "12:34". */
fun formatClock(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** "1h 05m" or "45m". */
fun formatDuration(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    return if (h > 0) "%dh %02dm".format(h, m) else "${maxOf(m, if (totalSeconds > 0) 1 else 0)}m"
}

fun formatRest(seconds: Int): String = if (seconds % 60 == 0) "${seconds / 60}:00" else formatClock(seconds.toLong())

private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
private val dayFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

fun formatDate(millis: Long): String = millis.toLocalDate().format(dateFormatter)
fun formatDayShort(millis: Long): String = millis.toLocalDate().format(dayFormatter)
fun formatTime(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFormatter)
fun formatMonth(millis: Long): String = millis.toLocalDate().format(monthFormatter)

fun startOfWeek(date: LocalDate = LocalDate.now()): LocalDate =
    date.with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1)

fun plural(count: Int, noun: String): String = "$count ${if (count == 1) noun else noun + "s"}"
