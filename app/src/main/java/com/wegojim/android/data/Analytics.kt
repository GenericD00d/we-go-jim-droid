package com.wegojim.android.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private fun Long.localDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
fun weekStartOf(date: LocalDate): LocalDate = date.with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1)
private fun pct(current: Double, baseline: Double): Double =
    if (baseline <= 0) (if (current > 0) 100.0 else 0.0) else Math.round((current - baseline) / baseline * 1000) / 10.0

enum class Direction { Up, Down, Flat;
    companion object {
        fun of(current: Double, previous: Double) = when {
            current - previous > 0.0001 -> Up
            previous - current > 0.0001 -> Down
            else -> Flat
        }
    }
}

// region Workout comparison (port of WorkoutProgressSnapshotBuilder)

data class MetricDelta(val title: String, val previous: String, val current: String, val delta: String, val direction: Direction)
data class ExerciseComparison(
    val exerciseUuid: String,
    val name: String,
    val previousBest: String,
    val currentBest: String,
    val delta: String,
    val direction: Direction,
    val magnitude: Double,
)
data class Highlight(val title: String, val value: String, val detail: String, val direction: Direction)
data class WorkoutComparison(
    val previous: WorkoutEntity,
    val current: WorkoutEntity,
    val sameTemplate: Boolean,
    val metrics: List<MetricDelta>,
    val exercises: List<ExerciseComparison>,
    val highlights: List<Highlight>,
)

object WorkoutCompare {
    /** Default pair: the latest two runs of the same template, else the latest two workouts. */
    fun defaultPair(workouts: List<WorkoutEntity>): Pair<WorkoutEntity, WorkoutEntity>? {
        if (workouts.size < 2) return null
        val sorted = workouts.sortedByDescending { it.startedAt }
        val newestByTemplate = mutableMapOf<String, WorkoutEntity>()
        for (w in sorted) {
            val t = w.templateId ?: continue
            val newest = newestByTemplate[t]
            if (newest == null) newestByTemplate[t] = w else return w to newest
        }
        return sorted[1] to sorted[0]
    }

    private data class Best(val text: String, val e1rm: Double?, val reps: Int, val volume: Double)

    private fun best(e: ExerciseEntry): Best {
        val working = e.sets.filter { !it.isWarmup && it.completed }
        val volume = working.sumOf { s -> s.weight?.takeIf { s.unit != LoadUnit.bodyweight }?.let { PerformanceMath.volumeKg(it, s.reps ?: 0, s.unit) } ?: 0.0 }
        val top = working.maxWithOrNull(compareBy({ s -> s.weight?.takeIf { s.unit != LoadUnit.bodyweight }?.let { PerformanceMath.estimatedOneRepMax(PerformanceMath.toKg(it, s.unit), s.reps ?: 0) } ?: 0.0 }, { it.reps ?: 0 }))
        val e1rm = top?.weight?.takeIf { top.unit != LoadUnit.bodyweight }?.let { PerformanceMath.estimatedOneRepMax(PerformanceMath.toKg(it, top.unit), top.reps ?: 0) }
        return Best(top?.let { describe(it) } ?: "–", e1rm, working.maxOfOrNull { it.reps ?: 0 } ?: 0, volume)
    }

    private fun describe(s: SetEntry) = when {
        s.unit == LoadUnit.bodyweight -> "BW × ${s.reps}"
        else -> "${trim(s.weight ?: 0.0)} ${s.unit.label} × ${s.reps}"
    }

    private fun trim(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)

    fun build(previous: WorkoutEntity, current: WorkoutEntity, formatVolume: (Double) -> String): WorkoutComparison {
        fun working(w: WorkoutEntity) = w.exercises.sumOf { e -> e.sets.count { !it.isWarmup && it.completed } }
        fun signed(v: Int) = if (v > 0) "+$v" else "$v"
        val volumeDelta = current.totalVolumeKg - previous.totalVolumeKg
        val metrics = listOf(
            MetricDelta("Volume", formatVolume(previous.totalVolumeKg), formatVolume(current.totalVolumeKg),
                (if (volumeDelta > 0) "+" else if (volumeDelta < 0) "−" else "") + formatVolume(abs(volumeDelta)), Direction.of(current.totalVolumeKg, previous.totalVolumeKg)),
            MetricDelta("Duration", minutes(previous.durationSeconds), minutes(current.durationSeconds),
                signed(current.durationSeconds / 60 - previous.durationSeconds / 60) + "m",
                // Compare whole minutes so the colour matches the rounded label.
                Direction.of((current.durationSeconds / 60).toDouble(), (previous.durationSeconds / 60).toDouble())),
            MetricDelta("PRs", "${previous.prCount}", "${current.prCount}", signed(current.prCount - previous.prCount), Direction.of(current.prCount.toDouble(), previous.prCount.toDouble())),
            MetricDelta("Working sets", "${working(previous)}", "${working(current)}", signed(working(current) - working(previous)), Direction.of(working(current).toDouble(), working(previous).toDouble())),
            MetricDelta("Exercises", "${previous.exercises.size}", "${current.exercises.size}", signed(current.exercises.size - previous.exercises.size), Direction.of(current.exercises.size.toDouble(), previous.exercises.size.toDouble())),
        )
        val prevByUuid = previous.exercises.associateBy { it.exerciseUuid }
        val comparisons = current.exercises.mapNotNull { c ->
            val p = prevByUuid[c.exerciseUuid] ?: return@mapNotNull null
            val pb = best(p)
            val cb = best(c)
            val (text, dir, mag) = when {
                pb.e1rm != null && cb.e1rm != null && pb.e1rm > 0 -> {
                    val change = pct(cb.e1rm, pb.e1rm)
                    Triple(if (change == 0.0) "Same estimated 1RM" else "${if (change > 0) "+" else ""}$change% est. 1RM", Direction.of(cb.e1rm, pb.e1rm), abs(change))
                }
                else -> {
                    val d = cb.reps - pb.reps
                    Triple(if (d == 0) "Same top reps" else "${signed(d)} reps", Direction.of(cb.reps.toDouble(), pb.reps.toDouble()), abs(d).toDouble())
                }
            }
            ExerciseComparison(c.exerciseUuid, c.name, pb.text, cb.text, text, dir, mag)
        }.sortedWith(compareByDescending<ExerciseComparison> { it.magnitude }.thenBy { it.name })

        val highlights = if (comparisons.isEmpty()) {
            listOf(Highlight("No shared exercises", "Fresh lineup", "These two workouts don't repeat any completed exercises.", Direction.Flat))
        } else {
            val improved = comparisons.count { it.direction == Direction.Up }
            val mover = comparisons.first()
            val workload = Direction.of(volumeDelta, 0.0)
            val prDir = Direction.of((current.prCount - previous.prCount).toDouble(), 0.0)
            listOf(
                Highlight("Repeated exercises", "${comparisons.size}", "$improved moved up since the earlier workout.", if (improved > 0) Direction.Up else Direction.Flat),
                Highlight("Biggest mover", mover.name, mover.delta, mover.direction),
                Highlight("Workload", when (workload) { Direction.Up -> "More work"; Direction.Down -> "Less work"; else -> "Same work" }, metrics[0].delta, workload),
                Highlight("PR signal", when (prDir) { Direction.Up -> "New hits"; Direction.Down -> "Fewer hits"; else -> "Steady" }, signed(current.prCount - previous.prCount), prDir),
            )
        }
        return WorkoutComparison(previous, current, previous.templateId != null && previous.templateId == current.templateId, metrics, comparisons, highlights)
    }

    private fun minutes(seconds: Int) = "${seconds / 60}m"
}

// endregion

// region Muscle heatmap (port of WorkoutMuscleHeatmapBuilder)

object MuscleHeatmap {
    val muscles = listOf(
        "Chest", "Shoulders", "Biceps", "Triceps", "Forearms", "Back", "Traps", "Abs",
        "Quadriceps", "Hamstrings", "Glutes", "Calves", "Adductors", "Abductors",
    )
    private const val PRIMARY = 1.0
    private const val SECONDARY = 0.35

    private val aliases = mapOf(
        "Biceps" to listOf("biceps"),
        "Shoulders" to listOf("shoulder", "deltoid", "delts"),
        "Chest" to listOf("chest", "pec"),
        "Back" to listOf("back", "lats", "latissimus", "rhomboid"),
        "Quadriceps" to listOf("quadriceps", "quads"),
        "Hamstrings" to listOf("hamstring"),
        "Glutes" to listOf("glute"),
        "Triceps" to listOf("triceps"),
        "Calves" to listOf("calves", "calf"),
        "Abs" to listOf("abs", "core", "abdominal", "oblique"),
        "Forearms" to listOf("forearm"),
        "Traps" to listOf("traps", "trapezius"),
        "Adductors" to listOf("adductor"),
        "Abductors" to listOf("abductor"),
    )

    private fun fromSummary(summary: String): List<String> {
        val s = summary.lowercase()
        return aliases.filter { (_, tokens) -> tokens.any { s.contains(it) } }.keys.toList()
    }

    /** Score per muscle: completed working sets × 1.0 (primary) or 0.35 (secondary), normalised to 0..1. */
    fun build(workouts: List<WorkoutEntity>, catalog: Map<String, ExerciseEntity>): List<Pair<String, Double>> {
        val scores = mutableMapOf<String, Double>()
        workouts.flatMap { it.exercises }.forEach { e ->
            val sets = e.sets.count { !it.isWarmup && it.completed && (it.reps ?: 0) > 0 }
            if (sets == 0) return@forEach
            val c = catalog[e.exerciseUuid]
            val primary = c?.primaryList ?: fromSummary(e.muscleSummary)
            val secondary = (c?.secondaryList ?: emptyList()).filter { it !in primary }
            primary.forEach { scores[it] = (scores[it] ?: 0.0) + PRIMARY * sets }
            secondary.forEach { scores[it] = (scores[it] ?: 0.0) + SECONDARY * sets }
        }
        val maxScore = max(scores.values.maxOrNull() ?: 1.0, 1.0)
        return scores.filter { it.value > 0 }.map { it.key to min(it.value / maxScore, 1.0) }.sortedByDescending { it.second }
    }
}

// endregion

// region Calories (port of WorkoutCalorieEstimator v2)

enum class CalorieSex(val label: String) { male("Male"), female("Female") }

data class CalorieProfile(val sex: CalorieSex?, val birthDate: LocalDate?, val heightCm: Double?, val weightKg: Double?) {
    val isComplete: Boolean get() = sex != null && birthDate != null && (heightCm ?: 0.0) > 0 && (weightKg ?: 0.0) > 0
}

object CalorieEstimator {
    private const val STRENGTH_MET = 3.5
    private const val MAX_SET_SECONDS = 180
    private const val MAX_WARMUP_SECONDS = 90
    private const val MAX_STRENGTH_SECONDS = 180 * 60
    private const val MAX_CARDIO_BLOCK_SECONDS = 180 * 60
    private const val MAX_CARDIO_SECONDS = 240 * 60

    /** Active kcal rounded down to 5, or null when the profile is incomplete or the estimate is negligible. */
    fun estimate(w: WorkoutEntity, profile: CalorieProfile, today: LocalDate = LocalDate.now()): Int? {
        if (!profile.isComplete) return null
        val weight = profile.weightKg!!
        val age = ChronoUnit.YEARS.between(profile.birthDate, today).toInt()
        if (age !in 13..110) return null
        val rmr = 10 * weight + 6.25 * profile.heightCm!! - 5 * age + if (profile.sex == CalorieSex.male) 5.0 else -161.0
        val rmrPerMinute = rmr / 1440
        fun active(seconds: Int, met: Double) = max(0.0, met * 3.5 * weight / 200 - rmrPerMinute) * seconds / 60

        val cardio = w.cardio.filter { it.completed }
        val cardioSeconds = cardio.sumOf { max(0, it.durationSeconds ?: 0) }
        val working = w.exercises.sumOf { e -> e.sets.count { !it.isWarmup && it.completed } }
        val warmups = w.exercises.sumOf { e -> e.sets.count { it.isWarmup && it.completed } }
        val setSeconds = min(MAX_STRENGTH_SECONDS, min(working, MAX_STRENGTH_SECONDS / MAX_SET_SECONDS) * MAX_SET_SECONDS +
            min(warmups, MAX_STRENGTH_SECONDS / MAX_WARMUP_SECONDS) * MAX_WARMUP_SECONDS)
        val strengthSeconds = min(max(0, w.durationSeconds - cardioSeconds), setSeconds)
        var calories = active(strengthSeconds, STRENGTH_MET)
        var remaining = MAX_CARDIO_SECONDS
        for (c in cardio) {
            if (remaining <= 0) break
            val s = min(min(max(0, c.durationSeconds ?: 0), MAX_CARDIO_BLOCK_SECONDS), remaining)
            calories += active(s, cardioMet(c))
            remaining -= s
        }
        val rounded = (calories / 5).toInt() * 5
        return rounded.takeIf { it >= 5 }
    }

    private fun speedKmh(c: CardioBlock): Double? {
        val d = c.durationSeconds ?: return null
        val m = c.distanceMeters ?: return null
        return if (d > 0 && m > 0) m / d * 3.6 else null
    }

    fun cardioMet(c: CardioBlock): Double {
        val id = "${c.exerciseUuid} ${c.name}".lowercase()
        return when {
            "cross" in id || "elliptical" in id -> 5.0
            "bike" in id || "cycle" in id -> 4.0
            "row" in id || c.profile == CardioProfile.rower -> 5.0
            "stair" in id || c.profile == CardioProfile.stairClimber -> 6.0
            "run" in id -> running(c)
            "walk" in id -> walking(c)
            c.profile == CardioProfile.walkRun -> walking(c)
            c.profile == CardioProfile.treadmill -> speedKmh(c)?.let { if (it >= 6.4) running(c) else walking(c) } ?: walking(c)
            else -> 4.0
        }
    }

    private fun walking(c: CardioBlock): Double {
        val speed = speedKmh(c)
        val level = when {
            speed == null -> 3.8
            speed < 4.5 -> 3.0
            speed < 5.6 -> 3.8
            speed < 6.4 -> 4.8
            else -> 5.5
        }
        val incline = c.inclinePercent?.takeIf { it > 0 } ?: return level
        val mpm = min(max(0.0, speed ?: 3.0) * 1000 / 60, 100.0)
        val grade = min(max(incline, 0.0), 30.0) / 100
        val inclineMet = (0.1 * mpm + 1.8 * mpm * grade + 3.5) / 3.5
        return min(9.0, max(level, inclineMet))
    }

    private fun running(c: CardioBlock): Double {
        val speed = speedKmh(c)
        val level = when {
            speed == null -> 6.5
            speed < 7.2 -> 6.5
            speed < 8.8 -> 7.8
            speed < 10.1 -> 8.5
            else -> 9.3
        }
        val incline = c.inclinePercent?.takeIf { it > 0 } ?: return level
        val mpm = max(0.0, speed ?: 6.4) * 1000 / 60
        val grade = min(max(incline, 0.0), 30.0) / 100
        val inclineMet = (0.2 * mpm + 0.9 * mpm * grade + 3.5) / 3.5
        return min(12.0, max(level, inclineMet))
    }
}

// endregion

// region Exercise trend

@kotlinx.serialization.Serializable
enum class TrendMetric(val title: String) { oneRepMax("Est. 1RM"), maxWeight("Max weight"), volume("Volume"), maxReps("Max reps") }

data class TrendPoint(val date: LocalDate, val valueKgOrReps: Double)

object ExerciseTrend {
    /** One point per session: the best value of the metric in that workout. */
    fun series(facts: List<SetFactEntity>, metric: TrendMetric): List<TrendPoint> = facts
        .filter { !it.isWarmup }
        .groupBy { it.workoutId }
        .mapNotNull { (_, sets) ->
            val value = when (metric) {
                TrendMetric.oneRepMax -> sets.mapNotNull { it.e1rmKg }.maxOrNull()
                TrendMetric.maxWeight -> sets.mapNotNull { it.weightKg }.maxOrNull()
                TrendMetric.volume -> sets.sumOf { it.volumeKg }.takeIf { it > 0 }
                TrendMetric.maxReps -> sets.maxOfOrNull { it.reps }?.toDouble()
            } ?: return@mapNotNull null
            TrendPoint(sets.first().completedAt.localDate(), value)
        }
        .sortedBy { it.date }
}

// endregion

// region Consistency and streaks

object Consistency {
    fun workoutDays(workouts: List<WorkoutEntity>): Map<LocalDate, Int> = workouts.groupingBy { it.startedAt.localDate() }.eachCount()

    /** Consecutive weeks (ending this week or last) meeting the weekly goal. */
    fun goalStreakWeeks(workouts: List<WorkoutEntity>, goal: Int, today: LocalDate = LocalDate.now()): Int {
        val perWeek = workouts.groupingBy { weekStartOf(it.startedAt.localDate()) }.eachCount()
        var week = weekStartOf(today)
        if ((perWeek[week] ?: 0) < goal) week = week.minusWeeks(1)
        var streak = 0
        while ((perWeek[week] ?: 0) >= goal) { streak++; week = week.minusWeeks(1) }
        return streak
    }

    fun activeWeekStreak(workouts: List<WorkoutEntity>, today: LocalDate = LocalDate.now()): Int = goalStreakWeeks(workouts, 1, today)

    fun longestDayStreak(workouts: List<WorkoutEntity>): Int {
        val days = workouts.map { it.startedAt.localDate() }.toSortedSet()
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        days.forEach { d -> run = if (prev != null && prev!!.plusDays(1) == d) run + 1 else 1; best = max(best, run); prev = d }
        return best
    }
}

// endregion

// region Weekly coach brief (port of WeeklyCoachInsightService + fallback narrative)

data class CoachSignal(val exerciseUuid: String, val name: String, val deltaPct: Double) {
    val summary: String get() = "$name is ${if (deltaPct > 0) "up" else "down"} ${"%.1f".format(abs(deltaPct))}% vs the six-week baseline."
}

data class CoachBrief(
    val headline: String,
    val body: String,
    val whatImproved: String,
    val whatChanged: String,
    val whyFlat: String,
    val rising: List<CoachSignal>,
    val watch: List<CoachSignal>,
)

object WeeklyCoach {
    private const val BASELINE_WEEKS = 6

    private class Bucket {
        val sessions = mutableSetOf<String>()
        var volume = 0.0
        val effort = mutableMapOf<String, Double>()
        val names = mutableMapOf<String, String>()
    }

    fun build(facts: List<SetFactEntity>, today: LocalDate = LocalDate.now()): CoachBrief {
        val thisWeek = weekStartOf(today)
        val current = Bucket()
        val baseline = sortedMapOf<LocalDate, Bucket>(compareByDescending { it })
        facts.filter { !it.isWarmup }.forEach { f ->
            val week = weekStartOf(f.completedAt.localDate())
            val bucket = when {
                week == thisWeek -> current
                week < thisWeek -> baseline.getOrPut(week) { Bucket() }
                else -> return@forEach
            }
            bucket.sessions += f.workoutId
            bucket.volume += f.volumeKg
            val effort = if (f.volumeKg > 0) f.volumeKg else f.reps.toDouble()
            bucket.effort[f.exerciseUuid] = (bucket.effort[f.exerciseUuid] ?: 0.0) + effort
            bucket.names[f.exerciseUuid] = f.exerciseName
        }
        val weeks = baseline.values.take(BASELINE_WEEKS)
        val workouts = current.sessions.size
        val countSentence = "You logged ${if (workouts == 1) "1 workout" else "$workouts workouts"} this week."
        if (weeks.size < BASELINE_WEEKS) {
            val fallback = "Not enough recent training history to build a stable weekly baseline."
            return CoachBrief("Weekly Coach Recap", fallback, fallback, fallback, fallback, emptyList(), emptyList())
        }
        val volumeDelta = pct(current.volume, weeks.sumOf { it.volume } / weeks.size)
        val consistency = (workouts - weeks.sumOf { it.sessions.size }.toDouble() / weeks.size).roundToInt()
        val uuids = (current.effort.keys + weeks.flatMap { it.effort.keys }).toSortedSet()
        val signals = uuids.mapNotNull { id ->
            val now = current.effort[id] ?: 0.0
            val base = weeks.sumOf { it.effort[id] ?: 0.0 } / BASELINE_WEEKS
            val d = pct(now, base)
            if (d == 0.0) null else CoachSignal(id, current.names[id] ?: weeks.firstNotNullOfOrNull { it.names[id] } ?: "Exercise", d)
        }
        val rising = signals.filter { it.deltaPct > 0 }.sortedWith(compareByDescending<CoachSignal> { it.deltaPct }.thenBy { it.name }).take(3)
        val watch = signals.filter { it.deltaPct < 0 }.sortedWith(compareBy<CoachSignal> { it.deltaPct }.thenBy { it.name }).take(3)

        fun volumeSentence() = when {
            volumeDelta > 0 -> "Total training volume is up ${"%.1f".format(volumeDelta)}% vs your recent baseline."
            volumeDelta < 0 -> "Total training volume is down ${"%.1f".format(abs(volumeDelta))}% vs your recent baseline."
            else -> "Total training volume is in line with your recent baseline."
        }
        fun consistencySentence(d: Int, positive: Boolean) =
            "You trained $d ${if (d == 1) "session" else "sessions"} ${if (positive) "more" else "fewer"} than your recent baseline."

        val headline = when {
            rising.isNotEmpty() -> "${rising.first().name} Led The Week"
            watch.isNotEmpty() -> "${watch.first().name} Needs Attention"
            volumeDelta > 0 -> "Volume Moved Up"
            volumeDelta < 0 -> "Volume Moved Down"
            else -> "Weekly Coach Recap"
        }
        val body = listOfNotNull(countSentence, rising.firstOrNull()?.summary, watch.firstOrNull()?.summary ?: volumeSentence()).joinToString(" ")
        val improved = rising.firstOrNull()?.summary
            ?: if (volumeDelta > 0) volumeSentence() else if (consistency > 0) consistencySentence(consistency, true)
            else "This week stayed steady without a standout improvement signal yet."
        val changed = "$countSentence " + (rising.firstOrNull()?.summary ?: watch.firstOrNull()?.summary ?: volumeSentence())
        val flat = watch.firstOrNull()?.summary
            ?: if (volumeDelta < 0) volumeSentence() else if (consistency < 0) consistencySentence(-consistency, false)
            else "There is not a clear drop-off signal in this snapshot yet."
        return CoachBrief(headline, body, improved, changed, flat, rising, watch)
    }
}

// endregion
