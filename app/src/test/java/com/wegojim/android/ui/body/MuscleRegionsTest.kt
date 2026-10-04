package com.wegojim.android.ui.body

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MuscleRegionsTest {
    @Test
    fun primaryWinsOverSecondary() {
        val (primary, secondary) = MuscleRegions.highlight(listOf("Chest"), listOf("Triceps", "Chest", "Shoulders"))
        assertEquals(setOf("chest"), primary)
        assertEquals(setOf("triceps", "deltoids"), secondary)
    }

    @Test
    fun tappedSubGroupsResolveToCatalogMuscles() {
        assertEquals("Chest", MuscleRegions.muscleFor("upper-chest"))
        assertEquals("Abs", MuscleRegions.muscleFor("serratus"))
        assertEquals("Back", MuscleRegions.muscleFor("lower-back"))
        assertEquals("Quadriceps", MuscleRegions.muscleFor("hip-flexors"))
        assertNull(MuscleRegions.muscleFor("knees"))
    }

    @Test
    fun regionTakesStrongestMuscle() {
        val scores = MuscleRegions.regionScores(listOf("Abs" to 0.4, "Back" to 1.0))
        assertEquals(0.4, scores.getValue("obliques"), 0.0)
        assertEquals(1.0, scores.getValue("upper-back"), 0.0)
    }

    @Test
    fun everyMappedRegionExistsInTheArtwork() {
        val slugs = (BodyPaths.front + BodyPaths.back).map { it.slug }.toSet()
        val mapped = listOf("Biceps", "Shoulders", "Chest", "Back", "Quadriceps", "Hamstrings", "Glutes", "Triceps",
            "Calves", "Abs", "Forearms", "Traps", "Adductors").flatMap(MuscleRegions::regions)
        // rhomboids is mapped (as on iOS) but MuscleMap only draws it on some bodies.
        assertTrue((mapped - "rhomboids").all { it in slugs })
        assertTrue((BodyPaths.front + BodyPaths.back).all { p -> p.all.all { it.startsWith("M") || it.startsWith("m") } })
    }
}
