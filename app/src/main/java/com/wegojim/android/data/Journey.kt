package com.wegojim.android.data

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

// Port of the iOS Journey: TrainingJourneyBuilder, CardioPersonalRecordService,
// JourneyCelebrationBuilder, JourneyAchievementCatalog, JourneyInsightsBuilder and
// TrainingYearRecapBuilder. Everything is rebuilt from completed history on demand;
// no badges are stored. Dates are epoch millis, days use the device time zone.

data class JourneyWorkout(
    val id: String,
    val name: String,
    val date: Long,
    val durationSeconds: Int,
    val hasStrength: Boolean,
    val hasCardio: Boolean,
    val activities: List<JourneyActivity>,
    val completedExercises: List<JourneyCompletedExercise> = emptyList(),
)

data class JourneyCompletedExercise(val id: String, val name: String, val isLegExercise: Boolean = false)

data class JourneyActivity(
    val id: String,
    val exerciseId: String,
    val name: String,
    val distanceMeters: Double,
    val durationSeconds: Int,
    val isWalkRun: Boolean,
    val isOutdoor: Boolean,
    val profile: CardioProfile = CardioProfile.walkRun,
    val isCycling: Boolean = false,
) {
    val hasDistance: Boolean
        get() = distanceMeters.isFinite() && distanceMeters > 0 && profile != CardioProfile.timeOnly && profile != CardioProfile.stairClimber
}

data class JourneyPerformance(val sessionId: String, val date: Long, val value: Double)

/** One strength metric per exercise: "<uuid>-weight" (heaviest kg) or "<uuid>-reps" (bodyweight reps). */
data class JourneyExercise(
    val id: String,
    val name: String,
    val isReps: Boolean,
    val usesAddedWeight: Boolean = false,
    val performances: List<JourneyPerformance>,
)

enum class MilestoneKind { beginning, workouts, strength, cardio, consistency, celebration }

/** A shareable PR: port of WorkoutCompletionPersonalRecord. */
data class PersonalRecord(val id: String, val exerciseName: String, val performanceText: String, val detailText: String)

data class JourneyMilestone(
    val id: String,
    val sessionId: String,
    val date: Long,
    val kind: MilestoneKind,
    val title: String,
    val detail: String,
    val activityId: String? = null,
    val chart: List<JourneyPerformance> = emptyList(),
    val chartUnit: String? = null,
    val personalRecord: PersonalRecord? = null,
    val recordKey: String? = null,
    val playfulTitle: String? = null,
    val unlockedAt: Long? = null,
)

data class JourneyDay(val date: LocalDate, val workoutCount: Int, val hasStrength: Boolean, val hasCardio: Boolean)

data class JourneyMonth(val month: YearMonth, val leadingDays: Int, val days: List<JourneyDay>, val workouts: List<JourneyWorkout>) {
    val activeDays: Int get() = days.count { it.workoutCount > 0 }
}

data class JourneyYear(val year: Int, val months: List<JourneyMonth>) {
    val title: String get() = year.toString()
}

data class JourneyNextMilestone(val id: String, val title: String, val detail: String, val progress: Double)
data class JourneyFact(val id: String, val title: String, val value: String)
data class JourneyYearHighlights(val year: Int, val title: String, val facts: List<JourneyFact>, val moments: List<String>)
data class JourneyInsights(
    val facts: List<JourneyFact> = emptyList(),
    val nextMilestones: List<JourneyNextMilestone> = emptyList(),
    val years: List<JourneyYearHighlights> = emptyList(),
)

data class JourneyAchievementGoal(
    val id: String,
    val title: String,
    val requirement: String,
    val category: String,
    val isEarned: Boolean,
    val progress: Double? = null,
    val progressText: String? = null,
    val seriesId: String? = null,
    val seriesTitle: String? = null,
)

data class JourneyAchievementSeries(val id: String, val title: String, val category: String, val goals: List<JourneyAchievementGoal>) {
    val nextGoal: JourneyAchievementGoal? get() = goals.firstOrNull { !it.isEarned }
    val latestCleared: JourneyAchievementGoal? get() = goals.lastOrNull { it.isEarned }
    val currentGoal: JourneyAchievementGoal? get() = nextGoal ?: goals.lastOrNull()
    val clearedCount: Int get() = goals.count { it.isEarned }
}

data class JourneySnapshot(
    val workoutCount: Int,
    val activeDays: Int,
    val durationSeconds: Int,
    val walkRunDistanceMeters: Double,
    val firstWorkoutDate: Long?,
    /** Newest year first. */
    val years: List<JourneyYear>,
    /** Newest first. */
    val milestones: List<JourneyMilestone>,
    val distanceUnit: DistanceUnit,
    val otherCardioDistanceMeters: Double = 0.0,
    val insights: JourneyInsights = JourneyInsights(),
    val achievementGoals: List<JourneyAchievementGoal> = emptyList(),
) {
    val recordCount: Int get() = milestones.count { it.personalRecord != null }

    companion object {
        val empty = JourneySnapshot(0, 0, 0, 0.0, null, emptyList(), emptyList(), DistanceUnit.kilometers)
    }
}

/** Port of JourneyFormatting plus the number styles the Journey copy uses. */
object JourneyFormat {
    fun number(value: Double, maxFraction: Int = 0, minFraction: Int = 0): String = NumberFormat.getNumberInstance().apply {
        maximumFractionDigits = maxFraction
        minimumFractionDigits = minFraction
    }.format(value)

    fun number(value: Int): String = NumberFormat.getIntegerInstance().format(value)

    fun distance(meters: Double, unit: DistanceUnit) = "${number(unit.fromMeters(meters), 1)} ${unit.symbol}"

    fun time(seconds: Int): String {
        val minutes = max(0, seconds) / 60
        if (minutes < 60) return "${number(minutes)} min"
        return "${number(minutes / 60.0, 1)} h"
    }

    fun date(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

    fun monthYear(month: YearMonth): String =
        "${month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())} ${month.year}"

    fun shortMonthYear(month: YearMonth): String =
        "${month.month.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.getDefault())} ${month.year}"
}

private fun ordered(workouts: List<JourneyWorkout>) = workouts.sortedWith(compareBy({ it.date }, { it.id }))

private fun sortedPoints(points: List<JourneyPerformance>) = points.sortedWith(compareBy({ it.date }, { it.sessionId }))

internal class Days(val zone: ZoneId) {
    private val weekFields = WeekFields.of(Locale.getDefault())
    fun day(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    fun week(millis: Long): LocalDate = day(millis).with(weekFields.dayOfWeek(), 1)
    fun startMillis(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val firstDayOfWeek = weekFields.firstDayOfWeek

    /** Longest run of consecutive calendar weeks with at least one workout. */
    fun longestWeekStreak(workouts: List<JourneyWorkout>): Int {
        var longest = 0
        var streak = 0
        var previous: LocalDate? = null
        workouts.map { week(it.date) }.toSortedSet().forEach { w ->
            streak = if (previous?.plusWeeks(1) == w) streak + 1 else 1
            longest = max(longest, streak)
            previous = w
        }
        return longest
    }
}

object TrainingJourney {
    private val workoutThresholds = setOf(10, 25, 50, 100, 250, 500, 1_000, 2_500, 5_000, 10_000)
    private val hourSteps = listOf(10, 24, 50, 100, 168, 250, 500, 1_000)
    private val distanceSteps = listOf(10.0, 50.0, 100.0, 250.0, 500.0, 1_000.0, 2_500.0, 5_000.0, 10_000.0)
    private const val LB = 0.45359237

    fun build(
        workouts: List<JourneyWorkout>,
        exercises: List<JourneyExercise>,
        distanceUnit: DistanceUnit = DistanceUnit.kilometers,
        loadUnit: LoadUnit = LoadUnit.kg,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Long = System.currentTimeMillis(),
    ): JourneySnapshot {
        val days = Days(zone)
        val ordered = ordered(workouts)
        val visible = ordered.map { it.id }.toSet()
        val byDay = ordered.groupBy { days.day(it.date) }
        val events = mutableListOf<JourneyMilestone>()
        ordered.firstOrNull()?.let {
            events += JourneyMilestone("beginning", it.id, it.date, MilestoneKind.beginning, "Your first workout", it.name)
        }
        var distance = 0.0
        var otherDistance = 0.0
        var nextOtherStep = 0
        var trainingSeconds = 0
        var nextTimeStep = 0
        val activeDays = mutableSetOf<LocalDate>()
        var nextDistanceStep = 0
        // Distance thresholds follow the chosen display unit, with meters using km milestones.
        val milestoneUnit = if (distanceUnit == DistanceUnit.meters) DistanceUnit.kilometers else distanceUnit
        val longestByExercise = mutableMapOf<String, Double>()
        ordered.forEachIndexed { index, workout ->
            if (index + 1 in workoutThresholds) {
                events += JourneyMilestone("workouts-${index + 1}", workout.id, workout.date, MilestoneKind.workouts,
                    "${index + 1} workouts completed", "Every session adds up.")
            }
            if (activeDays.add(days.day(workout.date)) && activeDays.size in setOf(30, 100, 365, 1_000)) {
                events += JourneyMilestone("active-days-${activeDays.size}", workout.id, workout.date, MilestoneKind.consistency,
                    "${activeDays.size} active days", "Days you made time for yourself. Every one counts.")
            }
            trainingSeconds += max(0, workout.durationSeconds)
            var crossedHours: Int? = null
            while (nextTimeStep < hourSteps.size && trainingSeconds >= hourSteps[nextTimeStep] * 3_600) {
                crossedHours = hourSteps[nextTimeStep]
                nextTimeStep++
            }
            crossedHours?.let {
                events += JourneyMilestone("training-hours-$it", workout.id, workout.date, MilestoneKind.workouts,
                    "$it hours of training", "Time invested in getting stronger, fitter, and feeling good.")
            }
            for (activity in workout.activities.sortedBy { it.id }) {
                val valid = activity.hasDistance
                val previous = longestByExercise[activity.exerciseId]
                if (previous == null) {
                    val detail = if (valid) "${JourneyFormat.distance(activity.distanceMeters, distanceUnit)} · ${JourneyFormat.time(activity.durationSeconds)}"
                    else JourneyFormat.time(activity.durationSeconds)
                    events += JourneyMilestone("first-cardio-${activity.exerciseId}", workout.id, workout.date, MilestoneKind.cardio,
                        "Your first ${activity.name.lowercase()}", detail, activityId = activity.id.takeIf { activity.isOutdoor })
                }
                longestByExercise[activity.exerciseId] = max(previous ?: 0.0, if (valid) activity.distanceMeters else 0.0)
                if (!valid) continue
                if (activity.isWalkRun) distance += activity.distanceMeters else otherDistance += activity.distanceMeters
            }
            // A long outing can cross several thresholds. Feature the highest one reached.
            var crossed: Double? = null
            while (nextDistanceStep < distanceSteps.size && distance >= milestoneUnit.toMeters(distanceSteps[nextDistanceStep])) {
                crossed = distanceSteps[nextDistanceStep]
                nextDistanceStep++
            }
            crossed?.let {
                events += JourneyMilestone("total-distance-${milestoneUnit.name}-$it", workout.id, workout.date, MilestoneKind.cardio,
                    "${JourneyFormat.distance(milestoneUnit.toMeters(it), milestoneUnit)} on foot", "Total recorded walking and running distance.")
            }
            var crossedOther: Double? = null
            while (nextOtherStep < distanceSteps.size && otherDistance >= milestoneUnit.toMeters(distanceSteps[nextOtherStep])) {
                crossedOther = distanceSteps[nextOtherStep]
                nextOtherStep++
            }
            crossedOther?.let {
                events += JourneyMilestone("other-distance-${milestoneUnit.name}-$it", workout.id, workout.date, MilestoneKind.cardio,
                    "${JourneyFormat.distance(milestoneUnit.toMeters(it), milestoneUnit)} beyond walking and running",
                    "Total recorded cycling, rowing, and other distance cardio.")
            }
        }
        events += CardioRecords.milestones(ordered, distanceUnit)
        for (exercise in exercises.sortedBy { it.id }) {
            val points = sortedPoints(exercise.performances.filter { it.sessionId in visible && it.value.isFinite() && it.value > 0 })
            val first = points.firstOrNull() ?: continue
            fun valueText(value: Double) = if (exercise.isReps) "${value.toInt()} reps"
            else "${JourneyFormat.number(if (loadUnit == LoadUnit.lb) value / LB else value, 1)} ${loadUnit.label}"
            var record = first.value
            points.forEachIndexed { index, point ->
                if (index == 0 || point.value <= record) return@forEachIndexed
                record = point.value
                val id = "strength-${exercise.id}-${point.sessionId}"
                events += JourneyMilestone(
                    id, point.sessionId, point.date, MilestoneKind.strength,
                    title = "${exercise.name} · ${valueText(point.value)}",
                    detail = when {
                        exercise.isReps -> "Bodyweight sets. First recorded: ${valueText(first.value)}."
                        exercise.usesAddedWeight -> "Added weight. First recorded: ${valueText(first.value)}."
                        else -> "First recorded: ${valueText(first.value)}."
                    },
                    chart = chartPoints(points, index).map {
                        it.copy(value = if (exercise.isReps || loadUnit != LoadUnit.lb) it.value else it.value / LB)
                    },
                    chartUnit = if (exercise.isReps) "reps" else loadUnit.label,
                    personalRecord = PersonalRecord(
                        id, exercise.name, valueText(point.value),
                        when {
                            exercise.isReps -> "Most bodyweight reps"
                            exercise.usesAddedWeight -> "Heaviest added weight"
                            else -> "Heaviest weight"
                        },
                    ),
                    recordKey = "strength-${exercise.id}",
                )
            }
        }
        events += consistencyEvents(ordered, days)
        val byMonth = ordered.groupBy { YearMonth.from(days.day(it.date)) }
        val firstYear = days.day(min(ordered.firstOrNull()?.date ?: now, now)).year
        val lastYear = days.day(max(ordered.lastOrNull()?.date ?: now, now)).year
        val years = (firstYear..lastYear).map { year ->
            JourneyYear(year, (1..12).map { m ->
                val month = YearMonth.of(year, m)
                val monthDays = (1..month.lengthOfMonth()).map { d ->
                    val date = month.atDay(d)
                    val sessions = byDay[date].orEmpty()
                    JourneyDay(date, sessions.size, sessions.any { it.hasStrength }, sessions.any { it.hasCardio })
                }
                val leading = (month.atDay(1).dayOfWeek.value - days.firstDayOfWeek.value + 7) % 7
                JourneyMonth(month, leading, monthDays, byMonth[month].orEmpty())
            })
        }.reversed()
        val withCelebrations = JourneyCelebrations.milestones(ordered, exercises, events, distanceUnit, days, now)
        val insights = JourneyInsightsBuilder.build(ordered, exercises, years, withCelebrations, distanceUnit, days, now)
        val snapshot = JourneySnapshot(
            workoutCount = ordered.size,
            activeDays = byDay.size,
            durationSeconds = ordered.sumOf { max(0, it.durationSeconds) },
            walkRunDistanceMeters = distance,
            firstWorkoutDate = ordered.firstOrNull()?.date,
            years = years,
            milestones = withCelebrations.sortedWith(compareByDescending<JourneyMilestone> { it.date }.thenBy { it.id }),
            distanceUnit = distanceUnit,
            otherCardioDistanceMeters = otherDistance,
            insights = insights,
        )
        return snapshot.copy(achievementGoals = JourneyAchievements.build(snapshot, days))
    }

    private fun chartPoints(points: List<JourneyPerformance>, lastIndex: Int): List<JourneyPerformance> =
        if (lastIndex < 24) points.take(lastIndex + 1) else (0 until 24).map { points[(it.toDouble() * lastIndex / 23).toInt()] }

    private fun consistencyEvents(workouts: List<JourneyWorkout>, days: Days): List<JourneyMilestone> {
        val weeks = workouts.groupBy { days.week(it.date) }
        var previous: LocalDate? = null
        var streak = 0
        val achieved = mutableSetOf<Int>()
        val events = mutableListOf<JourneyMilestone>()
        for (week in weeks.keys.sorted()) {
            streak = if (previous?.plusWeeks(1) == week) streak + 1 else 1
            previous = week
            if (streak !in setOf(4, 10, 12, 26, 52) || !achieved.add(streak)) continue
            val workout = weeks.getValue(week).minWith(compareBy({ it.date }, { it.id }))
            events += JourneyMilestone("consistency-$streak", workout.id, workout.date, MilestoneKind.consistency,
                "$streak consecutive training weeks", "At least one workout each week.")
        }
        return events
    }

    /** The visible completed history, shaped for the Journey (port of TrainingJourneyLoader). */
    fun inputs(workouts: List<WorkoutEntity>, catalog: Map<String, ExerciseEntity>): Pair<List<JourneyWorkout>, List<JourneyExercise>> {
        val legMuscles = setOf("Quadriceps", "Hamstrings", "Glutes", "Calves", "Adductors")
        val journeyWorkouts = workouts.map { w ->
            JourneyWorkout(
                id = w.id,
                name = w.name,
                date = w.endedAt,
                durationSeconds = w.durationSeconds,
                hasStrength = w.exercises.any { e -> e.sets.isNotEmpty() },
                hasCardio = w.cardio.any { it.completed },
                activities = w.cardio.filter { it.completed }.map(::activity),
                completedExercises = w.exercises.filter { it.sets.isNotEmpty() }.distinctBy { it.exerciseUuid }.map { e ->
                    JourneyCompletedExercise(e.exerciseUuid, e.name, catalog[e.exerciseUuid]?.primaryList.orEmpty().any { it in legMuscles })
                },
            )
        }
        val byExercise = workouts.flatMap { w -> w.exercises.map { w to it } }.groupBy { it.second.exerciseUuid }
        val exercises = byExercise.flatMap { (uuid, entries) ->
            val name = entries.maxBy { it.first.endedAt }.second.name
            val weights = entries.mapNotNull { (w, e) ->
                e.sets.filter { !it.isWarmup && it.unit != LoadUnit.bodyweight && (it.weight ?: 0.0) > 0 && (it.reps ?: 0) > 0 }
                    .maxOfOrNull { PerformanceMath.toKg(it.weight!!, it.unit) }
                    ?.let { JourneyPerformance(w.id, w.endedAt, it) }
            }
            // Only plain bodyweight sets are comparable for rep records; added weight changes the effort.
            val reps = entries.mapNotNull { (w, e) ->
                e.sets.filter { !it.isWarmup && it.unit == LoadUnit.bodyweight && (it.weight ?: 0.0) <= 0 }
                    .maxOfOrNull { it.reps ?: 0 }?.takeIf { it > 0 }
                    ?.let { JourneyPerformance(w.id, w.endedAt, it.toDouble()) }
            }
            listOf(
                JourneyExercise("$uuid-weight", name, isReps = false, performances = weights),
                JourneyExercise("$uuid-reps", name, isReps = true, performances = reps),
            )
        }
        return journeyWorkouts to exercises
    }

    fun activity(block: CardioBlock) = JourneyActivity(
        id = block.id,
        exerciseId = block.exerciseUuid,
        name = block.name,
        distanceMeters = block.distanceMeters ?: 0.0,
        durationSeconds = max(0, block.durationSeconds ?: 0),
        isWalkRun = block.profile == CardioProfile.walkRun || block.profile == CardioProfile.treadmill,
        isOutdoor = CardioRecording.recordsGps(block.exerciseUuid),
        profile = block.profile,
        isCycling = CardioRecording.isCycling(block.exerciseUuid),
    )
}

/**
 * Port of CardioPersonalRecordService: one chronological calculation shared by
 * the Journey and workout completion. Baselines and comparisons stay within the
 * same exercise and tracking profile; the first attempt only sets a baseline.
 */
object CardioRecords {
    fun milestones(workouts: List<JourneyWorkout>, unit: DistanceUnit): List<JourneyMilestone> {
        val longest = mutableMapOf<Pair<String, CardioProfile>, Double>()
        val fastest = mutableMapOf<Pair<String, CardioProfile>, Double>()
        val result = mutableListOf<JourneyMilestone>()
        for (workout in ordered(workouts)) {
            val groups = workout.activities.filter { it.hasDistance }.groupBy { it.exerciseId to it.profile }
            for (key in groups.keys.sortedWith(compareBy({ it.first }, { it.second.name }))) {
                val activities = groups.getValue(key).sortedBy { it.id }
                val farthest = activities.maxByOrNull { it.distanceMeters } ?: continue
                longest[key]?.let { previous ->
                    if (farthest.distanceMeters > previous + 0.001) {
                        val (record, before) = distanceTexts(farthest.distanceMeters, previous, unit)
                        result += event(workout, farthest, "distance", "Longest distance", record, "Previous best: $before.")
                    }
                }
                longest[key] = max(longest[key] ?: 0.0, farthest.distanceMeters)
                // Whole-activity average, never an instantaneous GPS speed or an inferred split.
                // A 1 km minimum keeps tiny warm-ups and accidental timer stops out of speed records.
                fun speed(a: JourneyActivity) = a.distanceMeters / a.durationSeconds
                val quickest = activities.filter { it.distanceMeters >= 1_000 && it.durationSeconds > 0 }.maxByOrNull(::speed) ?: continue
                val value = speed(quickest)
                fastest[key]?.let { previous ->
                    if (value > previous + 0.000001) {
                        val (record, before) = speedTexts(value, previous, unit)
                        result += event(workout, quickest, "speed", "Fastest average speed", record,
                            "Previous best: $before. ${JourneyFormat.distance(quickest.distanceMeters, unit)} in ${JourneyFormat.time(quickest.durationSeconds)}.")
                    }
                }
                fastest[key] = max(fastest[key] ?: 0.0, value)
            }
        }
        return result
    }

    /** Cardio PRs set in one saved workout, judged against everything up to it. */
    fun forWorkout(workoutId: String, workouts: List<WorkoutEntity>, unit: DistanceUnit): List<PersonalRecord> {
        val target = workouts.firstOrNull { it.id == workoutId } ?: return emptyList()
        if (target.cardio.none { it.completed }) return emptyList()
        val ids = target.cardio.map { it.exerciseUuid }.toSet()
        val history = workouts.filter { it.endedAt <= target.endedAt }.map { w ->
            JourneyWorkout(w.id, w.name, w.endedAt, w.durationSeconds, hasStrength = false, hasCardio = true,
                activities = w.cardio.filter { it.completed && it.exerciseUuid in ids }.map(TrainingJourney::activity))
        }
        return milestones(history, unit).filter { it.sessionId == workoutId }.mapNotNull { it.personalRecord }
    }

    private fun distanceTexts(record: Double, previous: Double, unit: DistanceUnit) =
        comparisonTexts(unit.fromMeters(record), unit.fromMeters(previous), unit.symbol, 1)

    private fun speedTexts(record: Double, previous: Double, unit: DistanceUnit): Pair<String, String> {
        val speedUnit = if (unit == DistanceUnit.miles) DistanceUnit.miles else DistanceUnit.kilometers
        return comparisonTexts(speedUnit.fromMeters(record * 3_600), speedUnit.fromMeters(previous * 3_600), "${speedUnit.symbol}/h", 2)
    }

    /** Adds precision only when rounding would hide the improvement. */
    private fun comparisonTexts(record: Double, previous: Double, suffix: String, initialPlaces: Int): Pair<String, String> {
        var places = initialPlaces
        while (true) {
            val r = JourneyFormat.number(record, places)
            val p = JourneyFormat.number(previous, places)
            if (r != p || places == 7) return "$r $suffix" to "$p $suffix"
            places++
        }
    }

    private fun event(workout: JourneyWorkout, activity: JourneyActivity, metric: String, title: String, value: String, detail: String): JourneyMilestone {
        val id = "cardio-pr-$metric-${activity.id}"
        return JourneyMilestone(
            id, workout.id, workout.date, MilestoneKind.cardio,
            title = "${activity.name} · $title",
            detail = "$value · $detail",
            activityId = activity.id.takeIf { activity.isOutdoor },
            personalRecord = PersonalRecord(id, activity.name, "$title · $value", detail),
            recordKey = "cardio-${activity.exerciseId}-${activity.profile.name}-$metric",
        )
    }
}

/**
 * Port of JourneyCelebrationBuilder: deterministic celebrations from visible,
 * completed history. PRs stay separate from badges so badges never inflate
 * record counts.
 */
private object JourneyCelebrations {
    fun milestones(
        workouts: List<JourneyWorkout>,
        exercises: List<JourneyExercise>,
        existing: List<JourneyMilestone>,
        unit: DistanceUnit,
        days: Days,
        now: Long,
    ): List<JourneyMilestone> {
        val first = workouts.firstOrNull() ?: return existing
        val events = existing.toMutableList()
        val eventIndex = mutableMapOf<String, Int>()
        events.forEachIndexed { i, e -> eventIndex.putIfAbsent(e.id, i) }
        val awarded = mutableSetOf<String>()
        fun caption(index: Int, text: String) { events[index] = events[index].copy(playfulTitle = text) }
        fun add(id: String, workout: JourneyWorkout, title: String, detail: String, meme: String, unlockedAt: Long? = null) {
            if (!awarded.add(id)) return
            events += JourneyMilestone("celebration-$id", workout.id, workout.date, MilestoneKind.celebration, title, detail,
                playfulTitle = meme, unlockedAt = unlockedAt)
        }
        // Give existing achievements a playful caption instead of creating duplicate badges.
        events.indices.forEach { i ->
            when (events[i].id) {
                "beginning" -> caption(i, "Achievement unlocked: showing up")
                "workouts-100" -> caption(i, "The warm-up became a lifestyle")
                "consistency-10" -> caption(i, "We go jim. Again.")
                "consistency-52" -> caption(i, "Rest days were part of the plan")
                "training-hours-24" -> caption(i, "A day invested in you")
                "training-hours-168" -> caption(i, "A week invested in you")
            }
        }
        val records = existing.filter { it.personalRecord != null }.sortedWith(compareBy({ it.date }, { it.id }))
        val recordsBySession = records.groupBy { it.sessionId }
        var recordCount = 0
        val lastSessionByKey = mutableMapOf<String, String>()
        val previousRecordSessionByKey = mutableMapOf<String, String>()
        val keysBySession = mutableMapOf<String, MutableSet<String>>()
        val visible = workouts.map { it.id }.toSet()
        for (exercise in exercises) {
            exercise.performances.filter { it.sessionId in visible && it.value.isFinite() && it.value > 0 }.forEach {
                keysBySession.getOrPut(it.sessionId) { mutableSetOf() } += "strength-${exercise.id}"
            }
        }
        val seenExercises = mutableSetOf<String>()
        val seenYears = mutableSetOf<Int>()
        var foot = 0.0
        var cycling = 0.0
        var rowing = 0.0
        var previous: JourneyWorkout? = null
        var firstWeightRecord = false
        var firstRepRecord = false
        val repsKeys = exercises.filter { it.isReps }.map { "strength-${it.id}" }.toSet()
        val firstDay = days.day(first.date)
        for (workout in workouts) {
            val day = days.day(workout.date)
            if (seenYears.add(day.year) && workout.id != first.id) {
                add("new-year-${day.year}", workout, "First workout of the year", day.year.toString(), "The lore continues")
            }
            previous?.let { if (day >= days.day(it.date).plusDays(30)) {
                add("comeback-${workout.id}", workout, "Welcome back",
                    "Your first workout after at least 30 days away. It's good to see you.", "Back at it")
            } }
            previous = workout
            for (years in listOf(1, 2, 3, 5, 10)) {
                if (day >= firstDay.plusYears(years.toLong())) {
                    add("anniversary-$years", workout, "$years-year training anniversary",
                        "Celebrated with a workout. Your story started ${JourneyFormat.date(first.date, days.zone)}.", "The gains have lore now")
                }
            }
            if (workout.hasStrength && workout.hasCardio) {
                add("mixed", workout, "Your first strength + cardio workout", "Two ways to move, one session.", "Strength meets stamina")
            }
            if (workout.completedExercises.any { it.isLegExercise }) {
                add("leg-day", workout, "Your first recorded leg exercise",
                    "A completed strength exercise with legs or glutes as a primary muscle group.", "Leg day has entered the chat")
            }
            seenExercises += workout.completedExercises.map { it.id }
            seenExercises += workout.activities.map { it.exerciseId }
            for (count in listOf(3, 5, 10)) if (seenExercises.size >= count) {
                add("variety-$count", workout, "$count different exercises tried", "Across your completed workouts.", "Mixing it up")
            }
            for (activity in workout.activities) {
                eventIndex["first-cardio-${activity.exerciseId}"]?.let { if (events[it].sessionId == workout.id) caption(it, "Side quest unlocked") }
                if (!activity.hasDistance) continue
                if (activity.isWalkRun) foot += activity.distanceMeters
                if (activity.isCycling) cycling += activity.distanceMeters
                if (activity.profile == CardioProfile.rower) rowing += activity.distanceMeters
                val baseKey = "cardio-${activity.exerciseId}-${activity.profile.name}"
                val keys = keysBySession.getOrPut(workout.id) { mutableSetOf() }
                keys += "$baseKey-distance"
                if (activity.distanceMeters >= 1_000 && activity.durationSeconds > 0) keys += "$baseKey-speed"
            }
            for ((meters, name, meme) in listOf(
                Triple(21_097.5, "A half marathon in total", "Half marathon. Full commitment."),
                Triple(42_195.0, "A marathon in total", "That's a marathon!"),
                Triple(100_000.0, "100 km on foot in total", "You've walked into another postcode"),
            )) {
                if (foot < meters) continue
                // The ordinary 100 km milestone already represents this achievement in metric units.
                val metricMilestone = eventIndex["total-distance-kilometers-100.0"]
                if (meters == 100_000.0 && unit != DistanceUnit.miles && metricMilestone != null) caption(metricMilestone, meme)
                else add("foot-${meters.toInt()}", workout, name, "Accumulated walking and running across workouts, not a single outing.", meme)
            }
            if (cycling >= 100_000) add("cycling-100km", workout, "100 km of cycling in total", "Accumulated across your cycling workouts.", "Tour de You")
            if (rowing >= 50_000) add("rowing-50km", workout, "50 km of rowing in total", "Accumulated across your rowing workouts.", "Oar-some behaviour")
            val sessionRecords = recordsBySession[workout.id].orEmpty()
            if (sessionRecords.mapNotNull { it.recordKey }.toSet().size >= 3) {
                add("pr-printer", workout, "Three PRs in one workout",
                    "At least three distinct exercise and record-type bests in one session.", "PR printer goes brrr")
            }
            for (record in sessionRecords) {
                val key = record.recordKey ?: continue
                if (lastSessionByKey[key] != null && lastSessionByKey[key] == previousRecordSessionByKey[key]) {
                    add("sequel", workout, "Back-to-back breakthroughs",
                        "You beat a PR from your previous completed session for the same exercise and metric.", "The sequel was stronger")
                }
                previousRecordSessionByKey[key] = workout.id
                if (record.kind == MilestoneKind.strength) {
                    val reps = key in repsKeys
                    if ((reps && !firstRepRecord) || (!reps && !firstWeightRecord)) {
                        eventIndex[record.id]?.let { caption(it, if (reps) "Same human. More horsepower." else "Gravity filed a complaint") }
                    }
                    if (reps) firstRepRecord = true else firstWeightRecord = true
                }
            }
            keysBySession[workout.id]?.forEach { lastSessionByKey[it] = workout.id }
            recordCount += sessionRecords.size
            for (count in listOf(10, 25, 50, 100)) if (recordCount >= count) {
                add("records-$count", workout, "$count Journey PRs",
                    "Individual strength and cardio records across your journey.", "A collection of breakthroughs")
            }
        }
        val byId = workouts.associateBy { it.id }
        for (exercise in exercises) {
            val points = sortedPoints(exercise.performances.filter { it.sessionId in visible && it.value.isFinite() && it.value > 0 })
            val baseline = points.firstOrNull() ?: continue
            for (point in points.drop(1)) {
                val workout = byId[point.sessionId] ?: continue
                for (percent in listOf(25, 50, 100)) if (point.value / baseline.value >= 1 + percent / 100.0) {
                    add("improvement-${exercise.id}-$percent", workout, "${exercise.name}: $percent% above your starting point",
                        if (exercise.isReps) "Compared with your first recorded bodyweight-rep result."
                        else "Compared with your first recorded weight result for this exercise.",
                        "Look how far you've come")
                }
            }
        }
        val months = workouts.groupBy { YearMonth.from(days.day(it.date)) }
        var bestMonthDays = 0
        for (month in months.keys.sorted()) {
            val sessions = months.getValue(month)
            val activeDays = mutableSetOf<LocalDate>()
            for (workout in sessions) {
                activeDays += days.day(workout.date)
                for (count in listOf(5, 10, 15, 20)) if (activeDays.size >= count) {
                    add("rhythm-$count", workout, "$count active days in a month",
                        "The first month you reached this milestone. Each day counts once.", "Finding your rhythm")
                }
            }
            val end = days.startMillis(month.plusMonths(1).atDay(1))
            if (end > now) continue
            val last = sessions.last()
            if (bestMonthDays > 0 && activeDays.size > bestMonthDays) {
                add("best-month-$month", last, "Your most active month yet",
                    "${JourneyFormat.monthYear(month)}: ${activeDays.size} active days, beating your previous best of $bestMonthDays.",
                    "Your strongest month yet", unlockedAt = end)
            }
            bestMonthDays = max(bestMonthDays, activeDays.size)
            if (sessions.any { it.hasStrength } && sessions.any { it.hasCardio } && sessions.any { recordsBySession[it.id].orEmpty().isNotEmpty() }) {
                add("montage", last, "A month of strength, cardio and PRs", JourneyFormat.monthYear(month),
                    "Main character training montage", unlockedAt = end)
            }
        }
        return events
    }
}

/** Port of JourneyAchievementCatalog: a finite guide to milestone families. Individual PRs stay in earned history. */
object JourneyAchievements {
    /** Keeps catalog order: thresholds within a series run from first to last. */
    fun series(goals: List<JourneyAchievementGoal>): List<JourneyAchievementSeries> {
        val grouped = goals.groupBy { it.seriesId ?: it.id }
        val seen = mutableSetOf<String>()
        return goals.mapNotNull { goal ->
            val id = goal.seriesId ?: goal.id
            if (!seen.add(id)) null else JourneyAchievementSeries(id, goal.seriesTitle ?: goal.title, goal.category, grouped.getValue(id))
        }
    }

    internal fun build(snapshot: JourneySnapshot, days: Days): List<JourneyAchievementGoal> {
        val events = snapshot.milestones
        val ids = events.map { it.id }.toSet()
        val workouts = snapshot.years.flatMap { it.months }.flatMap { it.workouts }
        val activities = workouts.flatMap { it.activities }.filter { it.hasDistance }
        val goals = mutableListOf<JourneyAchievementGoal>()
        fun badge(id: String, title: String, requirement: String, category: String, earned: Boolean? = null, series: Pair<String, String>? = null) {
            goals += JourneyAchievementGoal(id, title, requirement, category, earned ?: (id in ids), seriesId = series?.first, seriesTitle = series?.second)
        }
        fun count(id: String, title: String, category: String, current: Double, target: Double, unit: String, series: Pair<String, String>? = null) {
            goals += JourneyAchievementGoal(
                id, title, "Reach ${JourneyFormat.number(target, 1)} $unit.", category, current >= target,
                progress = min(1.0, max(0.0, current / target)),
                progressText = "${JourneyFormat.number(min(current, target), 1)} / ${JourneyFormat.number(target, 1)} $unit",
                seriesId = series?.first, seriesTitle = series?.second,
            )
        }
        val beginnings = "Firsts & variety"
        badge("beginning", "Your first workout", "Complete a workout.", beginnings)
        badge("first-cardio", "Side quest unlocked", "Complete your first cardio activity.", beginnings,
            earned = events.any { it.id.startsWith("first-cardio-") })
        badge("celebration-mixed", "Strength meets stamina", "Complete strength and cardio in one workout.", beginnings)
        badge("celebration-leg-day", "Leg day has entered the chat",
            "Complete a strength exercise with legs or glutes as a known primary muscle group.", beginnings)
        val variety = workouts.flatMap { w -> w.completedExercises.map { it.id } + w.activities.map { it.exerciseId } }.toSet().size
        for (target in listOf(3, 5, 10)) {
            count("celebration-variety-$target", "$target different exercises tried", beginnings, variety.toDouble(), target.toDouble(),
                "exercises", "variety" to "Exercise explorer")
        }
        val consistency = "Showing up"
        for (target in listOf(10, 25, 50, 100, 250, 500, 1_000, 2_500, 5_000, 10_000)) {
            count("workouts-$target", "$target workouts completed", consistency, snapshot.workoutCount.toDouble(), target.toDouble(),
                "workouts", "workouts" to "Showing up")
        }
        for (target in listOf(30, 100, 365, 1_000)) {
            count("active-days-$target", "$target active days", consistency, snapshot.activeDays.toDouble(), target.toDouble(),
                "days", "active-days" to "Days for you")
        }
        for (target in listOf(10, 24, 50, 100, 168, 250, 500, 1_000)) {
            count("training-hours-$target", "$target hours of training", consistency, snapshot.durationSeconds / 3_600.0, target.toDouble(),
                "hours", "training-hours" to "Time well spent")
        }
        val longest = days.longestWeekStreak(workouts)
        for (target in listOf(4, 10, 12, 26, 52)) {
            count("consistency-$target", "$target consecutive training weeks", consistency, longest.toDouble(), target.toDouble(),
                "consecutive weeks", "consistency" to "We go jim. Again.")
        }
        val monthlyBest = snapshot.years.flatMap { it.months }.maxOfOrNull { it.activeDays } ?: 0
        for (target in listOf(5, 10, 15, 20)) {
            count("celebration-rhythm-$target", "$target active days in a month", consistency, monthlyBest.toDouble(), target.toDouble(),
                "days in one month", "rhythm" to "Finding your rhythm")
        }
        badge("best-month", "Your most active month yet",
            "Finish a month with more active days than any earlier completed month.", consistency,
            earned = events.any { it.id.startsWith("celebration-best-month-") })
        val distance = "Going places"
        val unit = if (snapshot.distanceUnit == DistanceUnit.meters) DistanceUnit.kilometers else snapshot.distanceUnit
        for (target in listOf(10, 50, 100, 250, 500, 1_000, 2_500, 5_000, 10_000)) {
            count("foot-distance-$target", "$target ${unit.symbol} on foot", distance, unit.fromMeters(snapshot.walkRunDistanceMeters),
                target.toDouble(), unit.symbol, "foot-distance" to "On foot")
            count("other-distance-$target", "$target ${unit.symbol} of other cardio", distance, unit.fromMeters(snapshot.otherCardioDistanceMeters),
                target.toDouble(), unit.symbol, "other-distance" to "Beyond walking and running")
        }
        for ((meters, title) in listOf(21_097.5 to "A half marathon in total", 42_195.0 to "A marathon in total")) {
            count("foot-${meters.toInt()}", title, distance, snapshot.walkRunDistanceMeters / 1_000, meters / 1_000,
                "km on foot across workouts", "foot-adventures" to "Every step adds up")
        }
        if (unit == DistanceUnit.miles) {
            count("foot-100km", "100 km on foot in total", distance, snapshot.walkRunDistanceMeters / 1_000, 100.0,
                "km on foot across workouts", "foot-adventures" to "Every step adds up")
        }
        count("cycling-100km", "Tour de You", distance, activities.filter { it.isCycling }.sumOf { it.distanceMeters } / 1_000, 100.0,
            "km cycling across workouts")
        count("rowing-50km", "Oar-some behaviour", distance, activities.filter { it.profile == CardioProfile.rower }.sumOf { it.distanceMeters } / 1_000,
            50.0, "km rowing across workouts")
        val records = "Breakthroughs"
        badge("strength-pr", "Gravity filed a complaint", "Beat your heaviest weight for an exercise.", records,
            earned = events.any { it.kind == MilestoneKind.strength && it.recordKey?.endsWith("-weight") == true })
        badge("bodyweight-pr", "Same human. More horsepower.", "Beat your bodyweight-rep record for an exercise.", records,
            earned = events.any { it.kind == MilestoneKind.strength && it.recordKey?.endsWith("-reps") == true })
        badge("distance-pr", "Longest distance",
            "Beat your distance for the same cardio exercise and tracking type. Your first attempt sets the baseline.", records,
            earned = events.any { it.id.startsWith("cardio-pr-distance-") })
        badge("speed-pr", "Fastest average speed",
            "Beat your whole-activity average speed for the same exercise and tracking type, over at least 1 km. Your first qualifying attempt sets the baseline.",
            records, earned = events.any { it.id.startsWith("cardio-pr-speed-") })
        val recordCount = events.count { it.personalRecord != null }
        for (target in listOf(10, 25, 50, 100)) {
            count("records-$target", "$target Journey PRs", records, recordCount.toDouble(), target.toDouble(), "PRs",
                "records" to "A collection of breakthroughs")
        }
        for (percent in listOf(25, 50, 100)) {
            badge("improvement-$percent", "$percent% above your starting point",
                "Improve an exercise's weight or bodyweight reps by $percent% over its first recorded result.", records,
                earned = events.any { it.id.startsWith("celebration-improvement-") && it.id.endsWith("-$percent") },
                series = "improvements" to "Look how far you've come")
        }
        badge("celebration-pr-printer", "PR printer goes brrr", "Earn three distinct exercise-and-record-type PRs in one workout.", records)
        badge("celebration-sequel", "The sequel was stronger",
            "Beat a PR again in your next completed session for the same exercise and metric.", records)
        badge("celebration-montage", "Main character training montage", "Finish a month containing strength, cardio and a PR.", records)
        val story = "Your story"
        for (years in listOf(1, 2, 3, 5, 10)) {
            badge("celebration-anniversary-$years", "$years-year training anniversary",
                "Complete a workout on or after the $years-year anniversary of your first workout.", story,
                series = "anniversaries" to "The gains have lore now")
        }
        badge("new-year", "The lore continues", "Log your first workout in a later calendar year.", story,
            earned = events.any { it.id.startsWith("celebration-new-year-") })
        badge("comeback", "Back at it",
            "A welcome back if you return after 30 days away. No need to take a break to chase this one.", story,
            earned = events.any { it.id.startsWith("celebration-comeback-") })
        return goals
    }
}

/** Port of JourneyInsightsBuilder. */
private object JourneyInsightsBuilder {
    fun build(
        workouts: List<JourneyWorkout>,
        exercises: List<JourneyExercise>,
        years: List<JourneyYear>,
        events: List<JourneyMilestone>,
        unit: DistanceUnit,
        days: Days,
        now: Long,
    ): JourneyInsights {
        if (workouts.isEmpty()) return JourneyInsights()
        val weeks = workouts.map { days.week(it.date) }.toSet()
        val longest = days.longestWeekStreak(workouts)
        val bestMonth = years.flatMap { it.months }.sortedBy { it.month }
            .fold(null as JourneyMonth?) { best, m -> if (m.activeDays > (best?.activeDays ?: 0)) m else best }
        val facts = mutableListOf(
            JourneyFact("streak", "Longest weekly streak", if (longest == 1) "1 week" else "$longest weeks"),
            JourneyFact("average", "Workouts per active week", JourneyFormat.number(workouts.size.toDouble() / max(1, weeks.size), 1)),
        )
        bestMonth?.let { facts += JourneyFact("month", "Most active month", "${JourneyFormat.shortMonthYear(it.month)} · ${it.activeDays} days") }
        val today = Instant.ofEpochMilli(now).atZone(days.zone).toLocalDate()
        val lastYearSameDay = today.minusYears(1)
        val currentDays = workouts.filter { days.day(it.date).year == today.year && it.date <= now }.map { days.day(it.date) }.toSet().size
        val previousDays = workouts.map { days.day(it.date) }.filter { it.year == lastYearSameDay.year && it <= lastYearSameDay }.toSet().size
        facts += JourneyFact("year-comparison", "Active days · this year / last year",
            "$currentDays / $previousDays through ${today.format(DateTimeFormatter.ofPattern("d MMM"))}")

        val records = events.filter { it.personalRecord != null }
        val activeDays = workouts.map { days.day(it.date) }.toSet().size
        val hours = workouts.sumOf { max(0, it.durationSeconds) } / 3_600.0
        val foot = workouts.flatMap { it.activities }.filter { it.isWalkRun && it.distanceMeters.isFinite() && it.distanceMeters > 0 }.sumOf { it.distanceMeters }
        val candidates = mutableListOf<JourneyNextMilestone>()
        fun next(id: String, current: Double, thresholds: List<Double>, title: (Int) -> String, remaining: (Int) -> String) {
            val target = thresholds.firstOrNull { it > current } ?: return
            candidates += JourneyNextMilestone(id, title(target.toInt()), remaining(ceil(target - current).toInt()), min(1.0, max(0.0, current / target)))
        }
        next("workouts", workouts.size.toDouble(), listOf(10.0, 25.0, 50.0, 100.0, 250.0, 500.0, 1_000.0, 2_500.0, 5_000.0, 10_000.0),
            { "$it workouts" }, { "$it more sessions. Every one counts." })
        next("days", activeDays.toDouble(), listOf(30.0, 100.0, 365.0, 1_000.0),
            { "$it active days" }, { "$it more active days. Your pace, your journey." })
        next("hours", hours, listOf(10.0, 24.0, 50.0, 100.0, 168.0, 250.0, 500.0, 1_000.0),
            { "$it training hours" }, { "About $it more hours, one workout at a time." })
        next("records", records.size.toDouble(), listOf(10.0, 25.0, 50.0, 100.0),
            { "$it Journey PRs" }, { "$it more breakthroughs. No deadline." })
        if (foot > 0) {
            val milestoneUnit = if (unit == DistanceUnit.meters) DistanceUnit.kilometers else unit
            next("distance", milestoneUnit.fromMeters(foot), listOf(10.0, 50.0, 100.0, 250.0, 500.0, 1_000.0, 2_500.0, 5_000.0, 10_000.0),
                { "$it ${milestoneUnit.symbol} on foot" }, { "About $it ${milestoneUnit.symbol} to go across your workouts." })
        }
        val recordsBySession = records.groupBy { it.sessionId }
        val eventsBySession = events.groupBy { it.sessionId }
        val highlights = years.mapNotNull { year ->
            val sessions = ordered(year.months.flatMap { it.workouts })
            if (sessions.isEmpty()) return@mapNotNull null
            val ids = sessions.map { it.id }.toSet()
            val frequency = mutableMapOf<String, Pair<String, Int>>()
            for (session in sessions) {
                val names = mutableMapOf<String, String>()
                session.completedExercises.forEach { names[it.id] = it.name }
                session.activities.forEach { names[it.exerciseId] = it.name }
                names.forEach { (id, name) -> frequency[id] = name to ((frequency[id]?.second ?: 0) + 1) }
            }
            val favorite = frequency.entries.sortedWith(compareByDescending<Map.Entry<String, Pair<String, Int>>> { it.value.second }.thenBy { it.key }).firstOrNull()
            val mostActive = year.months.fold(null as JourneyMonth?) { best, m -> if (m.activeDays > (best?.activeDays ?: 0)) m else best }
            val yearEvents = sessions.flatMap { eventsBySession[it.id].orEmpty() }
            val yearFacts = mutableListOf(
                JourneyFact("moments", "Milestones earned", JourneyFormat.number(yearEvents.size)),
                JourneyFact("prs", "Journey PRs", JourneyFormat.number(sessions.sumOf { recordsBySession[it.id].orEmpty().size })),
            )
            favorite?.let { yearFacts += JourneyFact("favorite", "Favourite exercise · most sessions", "${it.value.first} · ${it.value.second} sessions") }
            mostActive?.let {
                yearFacts += JourneyFact("month", "Most active month",
                    "${it.month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())} · ${it.activeDays} days")
            }
            // Compare each metric's first result of this year with its best later result.
            data class Improvement(val id: String, val name: String, val metric: String, val percent: Double)
            val improvements = mutableListOf<Improvement>()
            for (exercise in exercises) {
                val points = sortedPoints(exercise.performances.filter { it.sessionId in ids && it.value.isFinite() && it.value > 0 })
                val first = points.firstOrNull() ?: continue
                val best = points.drop(1).maxOfOrNull { it.value } ?: continue
                if (best > first.value) {
                    improvements += Improvement(exercise.id, exercise.name, if (exercise.isReps) "bodyweight reps" else "weight", (best / first.value - 1) * 100)
                }
            }
            val baselines = mutableMapOf<String, Double>()
            val cardioImprovements = mutableMapOf<String, Improvement>()
            for (session in sessions) {
                val sessionBest = mutableMapOf<String, Triple<String, String, Double>>()
                for (activity in session.activities.filter { it.hasDistance }) {
                    val key = "${activity.exerciseId}-${activity.profile.name}"
                    if (activity.distanceMeters > (sessionBest["$key-distance"]?.third ?: 0.0)) {
                        sessionBest["$key-distance"] = Triple(activity.name, "distance", activity.distanceMeters)
                    }
                    if (activity.distanceMeters >= 1_000 && activity.durationSeconds > 0) {
                        val speed = activity.distanceMeters / activity.durationSeconds
                        if (speed > (sessionBest["$key-speed"]?.third ?: 0.0)) sessionBest["$key-speed"] = Triple(activity.name, "average speed", speed)
                    }
                }
                for ((key, value) in sessionBest) {
                    val baseline = baselines[key]
                    if (baseline == null) baselines[key] = value.third
                    else {
                        val percent = (value.third / baseline - 1) * 100
                        if (percent > (cardioImprovements[key]?.percent ?: 0.0)) cardioImprovements[key] = Improvement(key, value.first, value.second, percent)
                    }
                }
            }
            improvements += cardioImprovements.values
            improvements.filter { it.percent.isFinite() }
                .sortedWith(compareByDescending<Improvement> { it.percent }.thenBy { it.id })
                .take(3)
                .forEach {
                    yearFacts += JourneyFact("improvement-${it.id}", "${it.name} · ${it.metric}",
                        "+${JourneyFormat.number(it.percent, if (it.percent >= 100) 0 else 1)}% from your first result this year")
                }
            val moments = yearEvents.sortedWith(compareByDescending<JourneyMilestone> { it.date }.thenBy { it.id })
                .take(5).map { e -> e.playfulTitle?.let { "$it · ${e.title}" } ?: e.title }
            JourneyYearHighlights(year.year, year.title, yearFacts, moments)
        }
        return JourneyInsights(
            facts = facts,
            nextMilestones = candidates.sortedWith(compareByDescending<JourneyNextMilestone> { it.progress }.thenBy { it.id }).take(3),
            years = highlights,
        )
    }
}

/** Port of TrainingYearRecap and its builder: the same visible history as the Journey. */
data class TrainingYearRecap(
    val year: Int,
    val isYearInProgress: Boolean,
    val workoutCount: Int,
    val activeDays: Int,
    val durationSeconds: Int,
    val walkRunDistanceMeters: Double,
    val distanceUnit: DistanceUnit,
    val trainingWeeks: Int,
    val milestoneCount: Int,
    /** Short month label to workout count, January first. */
    val months: List<Pair<String, Int>>,
    val busiestMonth: String?,
    val busiestMonthWorkoutCount: Int,
    val highlight: String?,
) {
    val periodLabel: String get() = if (isYearInProgress) "Year so far" else "Year in training"

    companion object {
        fun build(snapshot: JourneySnapshot, zone: ZoneId = ZoneId.systemDefault(), now: Long = System.currentTimeMillis()): List<TrainingYearRecap> {
            val days = Days(zone)
            val thisYear = days.day(now).year
            return snapshot.years.mapNotNull { year ->
                val workouts = year.months.flatMap { it.workouts }
                if (workouts.isEmpty()) return@mapNotNull null
                val sessionIds = workouts.map { it.id }.toSet()
                val milestones = snapshot.milestones.filter { it.sessionId in sessionIds }
                val highlight = milestones.firstOrNull { it.kind == MilestoneKind.strength }
                    ?: milestones.firstOrNull { it.kind == MilestoneKind.cardio && it.personalRecord != null }
                    ?: milestones.firstOrNull()
                // First month wins ties, keeping the recap deterministic.
                val busiest = year.months.fold(null as JourneyMonth?) { best, m -> if (m.workouts.size > (best?.workouts?.size ?: 0)) m else best }
                TrainingYearRecap(
                    year = year.year,
                    isYearInProgress = year.year == thisYear,
                    workoutCount = workouts.size,
                    activeDays = workouts.map { days.day(it.date) }.toSet().size,
                    durationSeconds = workouts.sumOf { max(0, it.durationSeconds) },
                    walkRunDistanceMeters = workouts.flatMap { it.activities }
                        .filter { it.isWalkRun && it.distanceMeters.isFinite() && it.distanceMeters > 0 }.sumOf { it.distanceMeters },
                    distanceUnit = snapshot.distanceUnit,
                    trainingWeeks = workouts.map { days.week(it.date) }.toSet().size,
                    milestoneCount = milestones.size,
                    months = year.months.map { it.month.month.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.getDefault()) to it.workouts.size },
                    busiestMonth = busiest?.month?.month?.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()),
                    busiestMonthWorkoutCount = busiest?.workouts?.size ?: 0,
                    highlight = highlight?.title,
                )
            }
        }
    }
}
