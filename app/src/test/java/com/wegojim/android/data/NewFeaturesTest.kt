package com.wegojim.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** Ported from the iOS CardioRouteTests. */
class CardioRouteTest {
    private val start = 1_000_000L

    private fun route() = CardioRoute(sessionId = "s", activityId = "a").beginSegment()

    /** Appends a fix at [seconds] after start; returns the new route and whether a point was kept. */
    private fun CardioRoute.add(latitude: Double, seconds: Double, accuracy: Double = 5.0, maxSpeed: Double = 12.0): Pair<CardioRoute, Boolean> {
        val t = start + (seconds * 1000).toLong()
        val next = append(latitude, 18.0, t, accuracy, now = t, recordingStartedAt = start, maximumSpeedMetersPerSecond = maxSpeed)
        return next to (next.points.size > points.size)
    }

    @Test
    fun distanceUsesValidFixesAndIgnoresStationaryNoise() {
        var r = route()
        r.add(59.0, 0.0).let { (n, kept) -> assertTrue(kept); r = n }
        r.add(59.00001, 5.0).let { (n, kept) -> assertFalse(kept); r = n }
        r.add(59.0001, 10.0).let { (n, kept) -> assertTrue(kept); r = n }
        assertEquals(11.12, r.distanceMeters, 0.02)
        assertEquals(2, r.points.size)
    }

    @Test
    fun cyclingMeasuresFastTravelButStillBreaksImpossibleJumps() {
        var r = route()
        for ((lat, s) in listOf(59.0 to 0.0, 59.0009 to 5.0, 60.0 to 10.0)) r = r.add(lat, s, maxSpeed = 35.0).first
        assertEquals(100.08, r.distanceMeters, 0.02)
        assertEquals(r.points[0].segment, r.points[1].segment)
        assertNotEquals(r.points[1].segment, r.points[2].segment)
    }

    @Test
    fun pauseAndResumeNeverConnectUnrecordedTravel() {
        var r = route()
        r = r.add(59.0, 0.0).first
        r = r.add(59.0001, 5.0).first
        r = r.stop()
        r.add(60.0, 10.0).let { (n, kept) -> assertFalse(kept); r = n }
        r = r.beginSegment()
        r = r.add(60.0, 20.0).first
        r = r.add(60.0001, 25.0).first
        assertEquals(22.24, r.distanceMeters, 0.05)
        assertNotEquals(r.points[1].segment, r.points[2].segment)
    }

    @Test
    fun slowWalkWithContinuousFilteredFixesStillMeasuresDistance() {
        var r = route()
        for (s in 0..600 step 5) r = r.add(59 + s * 0.4 / 111_194.9266, s.toDouble(), accuracy = 30.0).first
        assertEquals(240.0, r.distanceMeters, 1.0)
        assertEquals(1, r.points.map { it.segment }.toSet().size)
    }

    @Test
    fun signalGapAfterFilteredFixStillBreaksRoute() {
        var r = route()
        r = r.add(59.0, 0.0).first
        r = r.add(59.00001, 5.0).first
        r.add(59.0001, 40.0).let { (n, kept) -> assertTrue(kept); r = n }
        assertEquals(0.0, r.distanceMeters, 0.0)
        assertNotEquals(r.points[0].segment, r.points[1].segment)
    }

    @Test
    fun signalGapAndImpossibleJumpCreateNewAnchors() {
        var r = route()
        r = r.add(59.0, 0.0).first
        r = r.add(60.0, 5.0).first
        r = r.add(60.001, 60.0).first
        assertEquals(0.0, r.distanceMeters, 0.0)
        assertEquals(3, r.points.map { it.segment }.toSet().size)
    }

    @Test
    fun rejectsInaccurateStaleOutOfOrderAndInvalidCoordinates() {
        var r = route()
        assertFalse(r.add(59.0, 0.0, accuracy = 80.0).second)
        assertFalse(r.add(59.0, 0.0, accuracy = -1.0).second)
        assertFalse(r.add(Double.NaN, 0.0).second)
        assertFalse(r.add(91.0, 0.0).second)
        assertEquals(r, r.append(59.0, 18.0, start, 5.0, now = start + 60_000, recordingStartedAt = start))
        r = r.add(59.0, 5.0).first
        assertFalse(r.add(59.0001, 4.0).second)
        assertFalse(r.add(59.0001, 5.0).second)
        assertEquals(1, r.points.size)
        // A cached fix from before the timer (re)started is ignored.
        assertEquals(route(), route().append(59.0, 18.0, start, 5.0, now = start, recordingStartedAt = start + 1_000))
    }

    @Test
    fun routeSurvivesJsonRoundTripAndValidates() {
        var r = route()
        r = r.add(59.0, 0.0).first
        r = r.add(59.0001, 5.0).first
        val back = AppJson.decodeFromString(CardioRoute.serializer(), AppJson.encodeToString(CardioRoute.serializer(), r.stop()))
        assertEquals(r.stop(), back)
        assertTrue(back.isValid())
        assertFalse(r.isValid()) // still recording
    }
}

/** Ported from the iOS WorkoutSupersetProgressionTests. */
class SupersetProgressionTest {
    private fun set(warmup: Boolean = false, completed: Boolean = false) =
        SetEntry(isWarmup = warmup, reps = 8, weight = 40.0, completed = completed)

    private fun pair(first: List<SetEntry>, second: List<SetEntry>) =
        ExerciseEntry(id = "A", exerciseUuid = "a", name = "Bench", sets = first, supersetGroupId = "g") to
            ExerciseEntry(id = "B", exerciseUuid = "b", name = "Row", sets = second, supersetGroupId = "g")

    private fun next(first: List<SetEntry>, second: List<SetEntry>) =
        pair(first, second).let { (a, b) -> SupersetProgression.nextStep(a, b, emptyMap()) }

    private fun rest(first: List<SetEntry>, second: List<SetEntry>, exercise: String, index: Int): Int {
        val (a, b) = pair(first, second)
        val own = if (exercise == "A") a else b
        return SupersetProgression.restSeconds(own.sets[index].id, exercise, a, b, 75)
    }

    private fun List<SetEntry>.done(i: Int) = mapIndexed { j, s -> if (i == j) s.copy(completed = true) else s }

    @Test
    fun alternatesMatchingSetsAndRestsOnlyAfterTheRound() {
        var first = listOf(set(), set())
        var second = listOf(set(), set())
        assertEquals(first[0].id, next(first, second)?.setId)
        first = first.done(0)
        assertEquals(second[0].id, next(first, second)?.setId)
        assertEquals(0, rest(first, second, "A", 0))
        second = second.done(0)
        assertEquals(first[1].id, next(first, second)?.setId)
        assertEquals(2, next(first, second)?.round)
        assertEquals(75, rest(first, second, "B", 0))
        first = first.done(1)
        assertEquals(second[1].id, next(first, second)?.setId)
        second = second.done(1)
        assertNull(next(first, second))
        assertEquals(75, rest(first, second, "B", 1))
    }

    @Test
    fun dropsMustAllFinishBeforeMovingToPartnerOrResting() {
        var first = listOf(set(completed = true).copy(dropStages = listOf(DropStage(), DropStage(completed = true))))
        val second = listOf(set(completed = true))
        assertEquals(first[0].id, next(first, second)?.setId)
        assertEquals(1, next(first, second)?.nextDrop)
        assertEquals(0, rest(first, second, "A", 0))
        first = listOf(first[0].copy(dropStages = first[0].dropStages.map { it.copy(completed = true) }))
        assertNull(next(first, second))
        assertEquals(75, rest(first, second, "A", 0))
    }

    @Test
    fun warmupsAlternateWithoutShiftingWorkingRounds() {
        var first = listOf(set(warmup = true), set())
        var second = listOf(set(warmup = true), set(warmup = true), set())
        assertNull(next(first, second)?.round)
        first = first.done(0)
        assertEquals(0, rest(first, second, "A", 0))
        second = second.done(0)
        assertEquals(75, rest(first, second, "B", 0))
        assertEquals(second[1].id, next(first, second)?.setId)
        second = second.done(1)
        assertEquals(75, rest(first, second, "B", 1))
        first = first.done(1)
        assertEquals(second[2].id, next(first, second)?.setId)
        assertEquals(1, next(first, second)?.round)
        assertEquals("Set 1", next(first, second)?.setLabel)
        assertEquals(0, rest(first, second, "A", 1))
    }

    @Test
    fun unequalCountsContinueAsSoloRounds() {
        val first = listOf(set(completed = true))
        var second = listOf(set(completed = true), set())
        assertEquals(second[1].id, next(first, second)?.setId)
        second = second.done(1)
        assertEquals(75, rest(first, second, "B", 1))
        assertNull(next(first, second))
        assertNull(next(emptyList(), emptyList()))
    }
}

/** Ported from the iOS CardioPersonalRecordTests, TrainingJourneyTests and JourneyCelebrationTests. */
class JourneyTest {
    private val zone = ZoneOffset.UTC
    private var nextId = 0
    private fun id() = "id-${nextId++}"
    private fun day(n: Int) = LocalDate.of(2026, 1, 1).plusDays(n.toLong()).atTime(12, 0).toInstant(zone).toEpochMilli()

    private fun activity(distance: Double, seconds: Int = 600, exercise: String = "run", profile: CardioProfile = CardioProfile.walkRun) =
        JourneyActivity(id(), exercise, exercise, distance, seconds, profile == CardioProfile.walkRun || profile == CardioProfile.treadmill, false, profile)

    private fun workout(day: Int, activities: List<JourneyActivity> = emptyList(), strength: Boolean = false, duration: Int = 600,
                        exercises: List<JourneyCompletedExercise> = emptyList()) =
        JourneyWorkout(id(), "Workout", day(day), duration, strength, activities.isNotEmpty(), activities, exercises)

    private fun build(workouts: List<JourneyWorkout>, exercises: List<JourneyExercise> = emptyList(), unit: DistanceUnit = DistanceUnit.kilometers) =
        TrainingJourney.build(workouts, exercises, unit, LoadUnit.kg, zone, now = day(400))

    @Test
    fun cardioRecordsAreIndependentPerExerciseAndProfile() {
        for ((exercise, profile) in listOf("walk" to CardioProfile.walkRun, "run" to CardioProfile.walkRun, "bike" to CardioProfile.machineDistance, "row" to CardioProfile.rower)) {
            val first = workout(1, listOf(activity(2_000.0, exercise = exercise, profile = profile)))
            val second = workout(2, listOf(activity(3_000.0, exercise = exercise, profile = profile)))
            val records = CardioRecords.milestones(listOf(second, first), DistanceUnit.kilometers)
            assertEquals(2, records.size)
            assertTrue(records.all { it.sessionId == second.id && it.personalRecord != null })
            assertTrue(records.any { it.personalRecord!!.performanceText.contains("18 km/h") })
        }
    }

    @Test
    fun baselinesTiesAndShortOrUntimedAttemptsDoNotAwardSpeed() {
        val workouts = listOf(
            workout(1, listOf(activity(1_000.0))), workout(2, listOf(activity(1_000.0))),
            workout(3, listOf(activity(999.0, seconds = 1), activity(Double.NaN), activity(Double.POSITIVE_INFINITY), activity(-1.0))),
            workout(4, listOf(activity(2_000.0, seconds = 0))), workout(5, listOf(activity(3_000.0, seconds = -10))),
        )
        val records = CardioRecords.milestones(workouts, DistanceUnit.kilometers)
        assertFalse(records.any { "speed" in it.id })
        assertEquals(2, records.count { "distance" in it.id })
    }

    @Test
    fun timeOnlyCannotWinAndMilesOnlyChangePresentation() {
        val workouts = listOf(workout(1, listOf(activity(1_609.344))), workout(2, listOf(activity(3_218.688))))
        val miles = CardioRecords.milestones(workouts, DistanceUnit.miles)
        assertEquals(CardioRecords.milestones(workouts, DistanceUnit.kilometers).map { it.id }, miles.map { it.id })
        assertTrue(miles.any { it.personalRecord!!.performanceText.contains("12 mi/h") })
        val timeOnly = listOf(workout(1, listOf(activity(1_000.0, profile = CardioProfile.timeOnly))), workout(2, listOf(activity(5_000.0, profile = CardioProfile.timeOnly))))
        assertTrue(CardioRecords.milestones(timeOnly, DistanceUnit.kilometers).isEmpty())
    }

    @Test
    fun emptyHistoryHasNoInventedMilestones() {
        val s = build(emptyList())
        assertEquals(0, s.workoutCount)
        assertTrue(s.milestones.isEmpty())
        assertTrue(s.insights.nextMilestones.isEmpty())
    }

    @Test
    fun totalsMilestonesAndStrengthRecords() {
        val workouts = (0 until 10).map { workout(it * 7, strength = true, duration = 3_600) }
        val bench = JourneyExercise("bench-weight", "Bench", isReps = false,
            performances = workouts.mapIndexed { i, w -> JourneyPerformance(w.id, w.date, 60.0 + if (i == 5) 10 else 0) })
        val s = build(workouts, listOf(bench))
        assertEquals(10, s.workoutCount)
        assertEquals(36_000, s.durationSeconds)
        val ids = s.milestones.map { it.id }.toSet()
        assertTrue("beginning" in ids)
        assertTrue("workouts-10" in ids)
        assertTrue("training-hours-10" in ids)
        assertTrue("consistency-4" in ids)
        // Only the session that beat the lifetime best is a record.
        val records = s.milestones.filter { it.kind == MilestoneKind.strength }
        assertEquals(listOf(workouts[5].id), records.map { it.sessionId })
        assertEquals("strength-bench-weight", records.single().recordKey)
        assertEquals("Bench · 70 kg", records.single().title)
        // Newest first, deterministic.
        assertEquals(s.milestones.sortedWith(compareByDescending<JourneyMilestone> { it.date }.thenBy { it.id }), s.milestones)
        assertTrue(s.achievementGoals.first { it.id == "strength-pr" }.isEarned)
        assertTrue(s.achievementGoals.first { it.id == "workouts-10" }.isEarned)
        assertFalse(s.achievementGoals.first { it.id == "workouts-25" }.isEarned)
    }

    @Test
    fun onFootDistanceIgnoresMachinesAndCoalescesThresholds() {
        val s = build(listOf(
            workout(1, listOf(activity(12_000.0))),
            workout(2, listOf(activity(60_000.0, exercise = "bike", profile = CardioProfile.machineDistance))),
            workout(3, listOf(activity(100_000.0))),
        ))
        assertEquals(112_000.0, s.walkRunDistanceMeters, 0.001)
        assertEquals(60_000.0, s.otherCardioDistanceMeters, 0.001)
        val ids = s.milestones.map { it.id }
        assertTrue("total-distance-kilometers-10.0" in ids)
        assertTrue("total-distance-kilometers-100.0" in ids)
        assertFalse("total-distance-kilometers-50.0" in ids) // crossed with 100 in one workout
        assertTrue("other-distance-kilometers-50.0" in ids)
        // The ordinary 100 km milestone carries the meme instead of a duplicate badge.
        assertEquals("You've walked into another postcode", s.milestones.first { it.id == "total-distance-kilometers-100.0" }.playfulTitle)
        assertFalse("celebration-foot-100000" in ids)
        assertTrue("celebration-foot-21097" in ids)
    }

    @Test
    fun celebrationsUseCompletedExercisesAndCalendarDays() {
        val legs = JourneyCompletedExercise("squat", "Squat", isLegExercise = true)
        val workouts = listOf(
            workout(0, strength = true, exercises = listOf(JourneyCompletedExercise("bench", "Bench"))),
            workout(1, listOf(activity(1_000.0)), strength = true, exercises = listOf(legs)),
            workout(40, strength = true, exercises = listOf(JourneyCompletedExercise("row", "Row"))),
            workout(366, strength = true),
        )
        val ids = build(workouts).milestones.map { it.id }.toSet()
        assertTrue("celebration-mixed" in ids)
        assertTrue("celebration-leg-day" in ids)
        assertTrue("celebration-variety-3" in ids)
        assertTrue("celebration-comeback-${workouts[2].id}" in ids)
        assertTrue("celebration-anniversary-1" in ids)
        assertTrue("celebration-new-year-2027" in ids)
    }

    @Test
    fun goalSeriesKeepCatalogOrder() {
        val s = build((0 until 12).map { workout(it) })
        val series = JourneyAchievements.series(s.achievementGoals)
        val workouts = series.first { it.id == "workouts" }
        assertEquals(listOf("workouts-10", "workouts-25"), workouts.goals.take(2).map { it.id })
        assertEquals("workouts-10", workouts.latestCleared?.id)
        assertEquals("workouts-25", workouts.nextGoal?.id)
        assertEquals(1, workouts.clearedCount)
    }

    @Test
    fun yearRecapCountsOnlyThatYear() {
        val workouts = listOf(workout(10), workout(11), workout(400))
        val recaps = TrainingYearRecap.build(build(workouts), zone, now = day(400))
        assertEquals(listOf(2027, 2026), recaps.map { it.year })
        assertEquals(2, recaps[1].workoutCount)
        assertTrue(recaps[0].isYearInProgress)
        assertFalse(recaps[1].isYearInProgress)
        assertEquals(12, recaps[1].months.size)
    }

    @Test
    fun workoutCardioRecordsJudgeAgainstEarlierHistoryOnly() {
        fun saved(dayIndex: Int, meters: Double) = WorkoutEntity(
            id = "w$dayIndex", templateId = null, name = "Run", startedAt = day(dayIndex) - 600_000, endedAt = day(dayIndex),
            durationSeconds = 600, totalVolumeKg = 0.0, prCount = 0, notes = "", exercises = emptyList(),
            cardio = listOf(CardioBlock(id = "c$dayIndex", exerciseUuid = "seed-outdoor-run", name = "Outdoor Run", profile = CardioProfile.walkRun,
                durationSeconds = 600, distanceMeters = meters, completed = true)),
        )
        val all = listOf(saved(1, 2_000.0), saved(2, 3_000.0), saved(3, 4_000.0))
        assertTrue(CardioRecords.forWorkout("w1", all, DistanceUnit.kilometers).isEmpty())
        val second = CardioRecords.forWorkout("w2", all, DistanceUnit.kilometers)
        assertEquals(2, second.size)
        assertTrue(second.any { it.performanceText.startsWith("Longest distance") })
    }
}

class CardioResultTest {
    @Test
    fun keepsUnchangedSourceDistanceAndValidates() {
        val text = CardioResultValidator.distanceText(1234.5678, DistanceUnit.kilometers)
        assertEquals("1.23", text)
        val kept = CardioResultValidator.validate(600, text, DistanceUnit.kilometers, 1234.5678, text, "", "", " ", CardioProfile.walkRun).getOrThrow()
        assertEquals(1234.5678, kept.distanceMeters!!, 0.0)
        val edited = CardioResultValidator.validate(null, "2,5", DistanceUnit.kilometers, 1234.5678, text, "", "", "", CardioProfile.walkRun).getOrThrow()
        assertEquals(2_500.0, edited.distanceMeters!!, 0.001)
        assertTrue(CardioResultValidator.validate(null, "", DistanceUnit.kilometers, null, null, "", "", "", CardioProfile.walkRun).isFailure)
        assertTrue(CardioResultValidator.validate(60, "abc", DistanceUnit.kilometers, null, null, "", "", "", CardioProfile.walkRun).isFailure)
        val treadmill = CardioResultValidator.validate(60, "", DistanceUnit.kilometers, null, null, "150", "", "", CardioProfile.treadmill).getOrThrow()
        assertEquals(100.0, treadmill.inclinePercent!!, 0.0)
    }

    @Test
    fun summaryLeadsWithPaceForRunsAndSpeedForBikes() {
        val run = CardioSummary.metrics(1_800, 6_000.0, DistanceUnit.kilometers, CardioProfile.walkRun, null, null)
        assertEquals(CardioSummary.Kind.pace, run.first().kind)
        assertEquals("5:00 /km", run.first().value)
        val bike = CardioSummary.metrics(3_600, 30_000.0, DistanceUnit.kilometers, CardioProfile.machineDistance, null, 8.0)
        assertEquals(CardioSummary.Kind.averageSpeed, bike.first().kind)
        assertEquals(listOf(CardioSummary.Kind.averageSpeed, CardioSummary.Kind.duration, CardioSummary.Kind.distance, CardioSummary.Kind.resistance), bike.map { it.kind })
        assertEquals("60 min", CardioSummary.durationText(3_600))
    }
}
