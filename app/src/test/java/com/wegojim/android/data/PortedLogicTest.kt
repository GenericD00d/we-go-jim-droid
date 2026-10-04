package com.wegojim.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TemplateTransferTest {
    // Fixtures copied from the iOS TemplateTransferServiceTests.
    private val versionFive = """
        {"formatVersion":5,"exportedAt":"2026-07-20T12:00:00Z","template":{"name":"Legacy Cardio","notes":"",
        "preWorkoutCardio":{"catalogExerciseUUID":"legacy-walk","exerciseNameSnapshot":"Walk","categorySnapshot":"Cardio","muscleSummarySnapshot":"Legs","targetDurationSeconds":300},
        "postWorkoutCardio":{"catalogExerciseUUID":"legacy-bike","exerciseNameSnapshot":"Bike","categorySnapshot":"Cardio","muscleSummarySnapshot":"Legs","targetDurationSeconds":600},
        "exercises":[]}}
    """.trimIndent()

    private val versionSix = """
        {"formatVersion":6,"exportedAt":"2026-07-20T12:00:00Z","artifact":{"kind":"template","template":{"name":"Legacy Cardio","notes":"",
        "preWorkoutCardio":{"catalogExerciseUUID":"legacy-walk","exerciseNameSnapshot":"Walk","categorySnapshot":"Cardio","muscleSummarySnapshot":"Legs","targetDurationSeconds":300},
        "postWorkoutCardio":{"catalogExerciseUUID":"legacy-bike","exerciseNameSnapshot":"Bike","categorySnapshot":"Cardio","muscleSummarySnapshot":"Legs","targetDurationSeconds":600},
        "exercises":[]}}}
    """.trimIndent()

    @Test
    fun importsLegacyCardioLikeIos() {
        for (file in listOf(versionFive, versionSix)) {
            val t = TemplateTransfer.import(file).templates.single()
            assertEquals(listOf(CardioRole.warmUp, CardioRole.finisher), t.cardio.map { it.role })
            assertEquals(listOf(CardioGoal.time, CardioGoal.time), t.cardio.map { it.goal })
            assertEquals(listOf(300, 600), t.cardio.map { it.targetDurationSeconds })
        }
    }

    @Test
    fun roundTripKeepsStructure() {
        val group = newId()
        val template = TemplateEntity(
            name = "Push",
            exercises = listOf(
                ExerciseEntry(
                    exerciseUuid = "seed-bench", name = "Bench", restSeconds = 90, targetRepMin = 6, targetRepMax = 10,
                    supersetGroupId = group, supersetRestSeconds = 150,
                    components = listOf(ExerciseRef("seed-incline", "Incline")),
                    sets = listOf(
                        SetEntry(isWarmup = true, targetReps = 10, targetWeight = 40.0),
                        SetEntry(targetReps = 8, targetWeight = 60.0, dropStages = listOf(DropStage(targetReps = 8, targetWeight = 47.5))),
                    ),
                ),
                ExerciseEntry(exerciseUuid = "seed-row", name = "Row", supersetGroupId = group, supersetRestSeconds = 150, sets = listOf(SetEntry(targetReps = 10))),
            ),
            cardio = listOf(CardioBlock(exerciseUuid = "seed-bike", name = "Bike", goal = CardioGoal.distance, targetDistanceMeters = 5000.0)),
        )
        val text = TemplateTransfer.exportTemplate(template)
        assertTrue("iOS decodes dates without fractional seconds", Regex("\"exportedAt\": \"[^\"]+:\\d\\dZ\"").containsMatchIn(text))
        val back = TemplateTransfer.import(text).templates.single()
        assertEquals("Push", back.name)
        assertEquals(2, back.exercises.size)
        val bench = back.exercises[0]
        assertEquals(listOf(true, false), bench.sets.map { it.isWarmup })
        assertEquals(47.5, bench.sets[1].dropStages.single().targetWeight!!, 0.0)
        assertEquals("seed-incline", bench.components.single().exerciseUuid)
        assertEquals(bench.supersetGroupId, back.exercises[1].supersetGroupId)
        assertEquals(150, bench.supersetRestSeconds)
        assertEquals(CardioGoal.distance, back.cardio.single().goal)
    }

    @Test
    fun exportsFolderArtifactAndRejectsGarbage() {
        val result = TemplateTransfer.import(TemplateTransfer.exportFolder("Split", listOf(TemplateEntity(name = "A"), TemplateEntity(name = "B"))))
        assertEquals("Split", result.folderName)
        assertEquals(listOf("A", "B"), result.templates.map { it.name })
        val error = runCatching { TemplateTransfer.import("{\"hello\":1}") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}

class PerformanceMathTest {
    @Test
    fun epleyMatchesIos() {
        assertEquals(100.0, PerformanceMath.estimatedOneRepMax(100.0, 1), 1e-9)
        assertEquals(100.0 * (1 + 8 / 30.0), PerformanceMath.estimatedOneRepMax(100.0, 8), 1e-9)
        assertEquals(45.359237, PerformanceMath.toKg(100.0, LoadUnit.lb), 1e-9)
        assertEquals(0.0, PerformanceMath.volumeKg(50.0, 10, LoadUnit.bodyweight), 0.0)
    }
}

class WorkoutEditingTest {
    private fun ex(name: String) = ExerciseEntry(exerciseUuid = name, name = name, sets = listOf(SetEntry(weight = 100.0, reps = 5)))

    @Test
    fun supersetsMoveAndUnlinkTogether() {
        var list = listOf(ex("a"), ex("b"), ex("c"))
        list = WorkoutEditing.supersetWithNext(list, list[0].id)
        assertEquals(mapOf(list[0].id to "A1", list[1].id to "A2"), WorkoutEditing.supersetLabels(list))
        assertFalse(WorkoutEditing.canSupersetWithNext(list, list[1].id))
        list = WorkoutEditing.move(list, list[2].id, -1)
        assertEquals(listOf("c", "a", "b"), list.map { it.name })
        list = WorkoutEditing.remove(list, list[1].id)
        assertNull(list.first { it.name == "b" }.supersetGroupId)
    }

    @Test
    fun dropStageRoundsToPlates() {
        val list = listOf(ex("a"))
        val updated = WorkoutEditing.addDrop(list, list[0].id, list[0].sets[0].id)
        assertEquals(80.0, updated[0].sets[0].dropStages.single().weight!!, 0.0)
    }

    @Test
    fun templateSyncReportsAddedSets() {
        val template = TemplateEntity(name = "T", exercises = listOf(ex("a")))
        val workout = WorkoutEditing.workoutFromTemplate(template) { null }
        assertTrue(TemplateSyncPreview.build(template, workout).isEmpty)
        val more = workout.copy(exercises = WorkoutEditing.addSet(workout.exercises, workout.exercises[0].id, false, LoadUnit.kg))
        assertEquals(listOf("a: 1 → 2 sets"), TemplateSyncPreview.build(template, more).edited)
    }
}

class CalorieEstimatorTest {
    private val profile = CalorieProfile(CalorieSex.male, LocalDate.of(1990, 1, 1), 180.0, 80.0)

    private fun workout(minutes: Int, sets: Int, cardio: List<CardioBlock> = emptyList()) = WorkoutEntity(
        id = "w", templateId = null, name = "W", startedAt = 0, endedAt = minutes * 60_000L, durationSeconds = minutes * 60,
        totalVolumeKg = 0.0, prCount = 0, notes = "",
        exercises = listOf(ExerciseEntry(exerciseUuid = "x", name = "x", sets = List(sets) { SetEntry(reps = 5, weight = 50.0, completed = true) })),
        cardio = cardio,
    )

    @Test
    fun strengthTimeIsCappedBySetCount() {
        val today = LocalDate.of(2026, 10, 3)
        // 10 working sets cap strength time at 30 minutes, so 60 and 30 minute sessions match.
        assertEquals(CalorieEstimator.estimate(workout(30, 10), profile, today), CalorieEstimator.estimate(workout(60, 10), profile, today))
        val kcal = CalorieEstimator.estimate(workout(30, 10), profile, today)!!
        assertTrue(kcal in 60..200 && kcal % 5 == 0)
        assertNull(CalorieEstimator.estimate(workout(30, 10), profile.copy(heightCm = null), today))
    }

    @Test
    fun treadmillInclineRaisesMet() {
        val flat = CardioBlock(exerciseUuid = "t", name = "Treadmill Walk", profile = CardioProfile.treadmill, durationSeconds = 1800, distanceMeters = 2500.0, completed = true)
        assertTrue(CalorieEstimator.cardioMet(flat.copy(inclinePercent = 12.0)) > CalorieEstimator.cardioMet(flat))
    }
}
