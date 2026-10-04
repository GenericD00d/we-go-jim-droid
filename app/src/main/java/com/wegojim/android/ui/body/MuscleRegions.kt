package com.wegojim.android.ui.body

/**
 * Port of ExerciseBodyMapRegionMapper: catalog muscle groups (the seed's 14
 * names) to MuscleMap regions, and back again for tap-to-filter.
 */
object MuscleRegions {
    private val byMuscle = mapOf(
        "Biceps" to listOf("biceps"),
        "Shoulders" to listOf("deltoids"),
        "Chest" to listOf("chest"),
        "Back" to listOf("lower-back", "rhomboids", "upper-back"),
        "Quadriceps" to listOf("quadriceps"),
        "Hamstrings" to listOf("hamstring"),
        "Glutes" to listOf("gluteal"),
        "Triceps" to listOf("triceps"),
        "Calves" to listOf("calves"),
        "Abs" to listOf("abs", "obliques"),
        "Forearms" to listOf("forearm"),
        "Traps" to listOf("trapezius"),
        "Adductors" to listOf("adductors"),
        "Abductors" to emptyList(),
    )

    fun regions(muscle: String): List<String> = byMuscle[muscle].orEmpty()

    /** Primary regions win over secondary ones, as on iOS. */
    fun highlight(primary: List<String>, secondary: List<String>): Pair<Set<String>, Set<String>> {
        val p = primary.flatMap(::regions).toSet()
        return p to (secondary.flatMap(::regions).toSet() - p)
    }

    /** The catalog muscle for a tapped region; sub-groups resolve through their parent. */
    fun muscleFor(slug: String): String? {
        val candidates = listOfNotNull(slug, BodySlugs.parent[slug])
        return candidates.firstNotNullOfOrNull { s -> byMuscle.entries.firstOrNull { s in it.value }?.key }
    }

    /** Per-region intensity from per-muscle heatmap scores (a region takes its strongest muscle). */
    fun regionScores(muscleScores: List<Pair<String, Double>>): Map<String, Double> {
        val out = mutableMapOf<String, Double>()
        muscleScores.forEach { (muscle, score) -> regions(muscle).forEach { r -> out[r] = maxOf(out[r] ?: 0.0, score) } }
        return out
    }
}
