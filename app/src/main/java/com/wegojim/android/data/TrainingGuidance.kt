package com.wegojim.android.data

/** Port of TrainingGuidanceService: per-exercise coaching while logging. */
object TrainingGuidance {
    enum class Tone { Accent, Success, Caution }
    enum class Kind { LowerCompound, UpperCompound, Isolation, Core, Conditioning, Unknown }

    data class Recommendation(
        val title: String,
        val summary: String,
        val warmups: IntRange,
        val workingSets: IntRange,
        val reps: IntRange,
        val restSeconds: IntRange,
    )

    data class Guidance(val title: String, val summary: String, val tone: Tone, val badge: String, val badgeDetail: String?)

    private val lower = listOf("hack squat", "hip thrust", "leg press", "split squat", "step-up", "deadlift", "lunge", "squat", "rdl")
    private val upper = listOf("pull-up", "chin-up", "pulldown", "push-up", "bench", "press", "row", "dip")
    private val isolation = listOf("pressdown", "kickback", "extension", "raise", "curl", "fly")

    fun classify(name: String, category: String, equipment: String, muscles: String): Kind {
        fun norm(s: String) = s.lowercase().replace(",", " ")
        val cat = norm(category)
        val mus = norm(muscles)
        val all = listOf(norm(name), cat, norm(equipment), mus).joinToString(" ")
        return when {
            "conditioning" in cat -> Kind.Conditioning
            "core" in cat || "abs" in mus || "core" in mus -> Kind.Core
            isolation.any { it in all } -> Kind.Isolation
            lower.any { it in all } -> Kind.LowerCompound
            upper.any { it in all } -> Kind.UpperCompound
            else -> Kind.Unknown
        }
    }

    fun classify(e: ExerciseEntry, catalog: ExerciseEntity?) =
        classify(e.name, catalog?.category ?: e.category, catalog?.equipment ?: "", catalog?.primaryMuscles ?: e.muscleSummary)

    fun recommendation(kind: Kind) = when (kind) {
        Kind.LowerCompound -> Recommendation("Progressive overload sweet spot", "Try 2 warmups, 3-4 working sets, 5-8 reps, and 120-180 sec rest. Adjust freely.", 2..2, 3..4, 5..8, 120..180)
        Kind.UpperCompound -> Recommendation("Solid default structure", "Try 1-2 warmups, 3-4 working sets, 6-10 reps, and 90-150 sec rest. Adjust freely.", 1..2, 3..4, 6..10, 90..150)
        Kind.Isolation -> Recommendation("Keep isolation work simple", "Try 0-1 warmups, 2-4 working sets, 10-15 reps, and 45-90 sec rest. Adjust freely.", 0..1, 2..4, 10..15, 45..90)
        Kind.Core -> Recommendation("Core work usually needs less setup", "Try 0-1 warmups, 2-4 working sets, 8-15 reps, and 45-75 sec rest. Adjust freely.", 0..1, 2..4, 8..15, 45..75)
        Kind.Conditioning -> Recommendation("Keep conditioning repeatable", "Try 1-2 ramp-up rounds, 3-5 hard rounds, 8-20 reps or 20-60 sec efforts, and 45-90 sec recovery.", 1..2, 3..5, 8..20, 45..90)
        Kind.Unknown -> Recommendation("Solid default structure", "Try 1-2 warmups, 3 working sets, 8-12 reps, and 60-120 sec rest. Adjust once the lift settles in.", 1..2, 3..3, 8..12, 60..120)
    }

    private fun step(unit: LoadUnit) = when (unit) { LoadUnit.kg -> 2.5; LoadUnit.lb -> 5.0; LoadUnit.bodyweight -> null }
    private fun load(w: Double, unit: LoadUnit) = "${if (w % 1.0 == 0.0) w.toInt().toString() else w.toString()} ${unit.label}"
    private fun r(range: IntRange) = "${range.first}-${range.last}"

    /** Guidance for the exercise card, given the sets logged so far. */
    fun forExercise(e: ExerciseEntry, catalog: ExerciseEntity?): Guidance {
        val kind = classify(e, catalog)
        val rec = recommendation(kind)
        val explicit = e.targetRepMin != null && e.targetRepMax != null && e.targetRepMin <= e.targetRepMax
        val range = if (explicit) e.targetRepMin!!..e.targetRepMax!! else rec.reps
        val working = e.sets.filter { it.completed && !it.isWarmup }
        val complete = e.sets.any { !it.isWarmup } && e.sets.all { it.completed }

        if (complete && explicit) overloadCue(working, range)?.let { return it }

        val last = working.lastOrNull() ?: return initial(kind, rec, range, e.sets.count { it.completed && it.isWarmup })
        val reps = last.reps ?: return inProgress(kind, range)
        if (last.unit == LoadUnit.bodyweight || last.weight == null) return bodyweight(range, reps)
        val loadText = load(last.weight, last.unit)
        return when {
            reps < range.first -> Guidance("Get the reps back", "That set dropped to $reps reps. Take a little more rest or reduce the load so the next set gets back into ${r(range)} clean reps.", Tone.Caution, "Reps Up", "Rest or reduce load")
            reps > range.last -> Guidance("Keep the reps in range", "That set hit $reps reps. Keep the next set inside ${r(range)} clean reps. If the rest of the work sets still clear the top, increase the load next session.", Tone.Success, "Reps Down", "Keep ${r(range)}")
            reps < range.last -> Guidance("Add a rep before increasing load", "$loadText is still working well. Try to beat $reps with the same setup and keep it inside ${r(range)}.", Tone.Accent, "Reps Up", "Same $loadText")
            else -> Guidance("Keep that load", "$loadText is right on target. Match it again clean before you increase it.", Tone.Accent, "Keep Load", loadText)
        }
    }

    /** Next-session cue once every set is done (requires an explicit rep range). */
    private fun overloadCue(working: List<SetEntry>, range: IntRange): Guidance? {
        val sets = working.filter { it.unit != LoadUnit.bodyweight && it.weight != null && it.reps != null }
        if (sets.isEmpty()) return null
        val ref = sets.last()
        val st = step(ref.unit)
        return when {
            sets.all { it.reps!! > range.last } -> {
                val next = st?.let { load(ref.weight!! + it, ref.unit) }
                Guidance(
                    "Increase the load next time",
                    if (next == null) "You cleared the whole range. Add a little load next time and bring it back to ${r(range)} clean reps."
                    else "You cleared the whole range. Move to $next next time and work back into ${r(range)} clean reps.",
                    Tone.Success, "Increase Load", next?.let { "Next: $it" } ?: "Next workout",
                )
            }
            sets.all { it.reps!! < range.first } -> {
                val next = st?.let { load(maxOf(0.0, ref.weight!! - it), ref.unit) }
                Guidance(
                    "Reduce the load next time",
                    if (next == null) "You missed the floor. Pull the load back a touch next time and rebuild ${r(range)} clean reps."
                    else "You missed the floor. Drop to $next next time and rebuild ${r(range)} clean reps.",
                    Tone.Caution, "Reduce Load", next?.let { "Next: $it" } ?: "Next workout",
                )
            }
            else -> {
                val l = load(ref.weight!!, ref.unit)
                Guidance("Keep the load the same", "$l is in the right spot for now. Keep every work set inside ${r(range)} before you increase it.", Tone.Accent, "Keep Load", "Next: $l")
            }
        }
    }

    private fun initial(kind: Kind, rec: Recommendation, range: IntRange, warmupsDone: Int): Guidance {
        val w = rec.warmups
        val instruction = when {
            warmupsDone == 0 && w.last == 0 -> "You can go straight into the work sets if the groove already feels ready."
            warmupsDone == 0 && w.first == w.last -> "Take ${w.first} ramp-up ${if (w.first == 1) "set" else "sets"} before the work sets."
            warmupsDone == 0 -> "Take ${w.first}-${w.last} ramp-up sets before the work sets."
            warmupsDone < w.first -> "One more ramp-up set is worth it if the pattern still feels cold."
            else -> "Warmups look covered, so make the first work set count."
        }
        val badge = when {
            warmupsDone == 0 && w.last == 0 -> "Optional ramp"
            warmupsDone == 0 && w.first == w.last -> if (w.first == 1) "1 ramp set" else "${w.first} ramp sets"
            warmupsDone == 0 -> "${w.first}-${w.last} ramp sets"
            warmupsDone < w.first -> "1 more ramp set"
            else -> "First work set"
        }
        val (title, body) = when (kind) {
            Kind.LowerCompound -> "Warm up and brace" to "Big lower-body lifts win when your torso stays locked and the first work set lands in ${r(range)} reps."
            Kind.UpperCompound -> "Warm up and set your shoulders" to "Pack the shoulders, repeat the same bar path, and aim for ${r(range)} reps."
            Kind.Isolation -> "Warm up and stay controlled" to "Control the stretch, keep tension on the target muscle, and make ${r(range)} reps feel deliberate."
            Kind.Core -> "Warm up and brace first" to "Keep ribs down, move slow, and make every rep in ${r(range)} look clean."
            Kind.Conditioning -> "Warm up and pace the effort" to "Start smooth so the later rounds can still hit ${r(range)} reps with control."
            Kind.Unknown -> "Warm up and find a consistent groove" to "Use the early sets to settle the pattern and aim for ${r(range)} clean reps."
        }
        return Guidance(title, "$instruction $body", Tone.Accent, "Warm Up", badge)
    }

    private fun inProgress(kind: Kind, range: IntRange): Guidance {
        val summary = when (kind) {
            Kind.LowerCompound -> "Keep the brace solid and make the next one land in ${r(range)} clean reps."
            Kind.UpperCompound -> "Set the shoulders, repeat the same path, and keep the next one in ${r(range)} clean reps."
            Kind.Isolation -> "Stay on the target muscle, avoid momentum, and keep the next one in ${r(range)} clean reps."
            Kind.Core -> "Keep ribs down, brace first, and make the next one land in ${r(range)} clean reps."
            Kind.Conditioning -> "Keep the early pace under control so the next effort still hits ${r(range)} reps."
            Kind.Unknown -> "Repeat the same setup and keep the next one around ${r(range)} clean reps."
        }
        return Guidance("Keep the next set consistent", "Your work sets are in progress. $summary", Tone.Accent, "Stay Consistent", "${r(range)} clean")
    }

    private fun bodyweight(range: IntRange, reps: Int) = when {
        reps < range.first -> Guidance("Get the reps back", "That set dropped to $reps reps. Take a little more rest or use assistance so the next one gets back into ${r(range)} clean reps.", Tone.Caution, "Reps Up", "Rest or assist")
        reps > range.last -> Guidance("Make the variation harder", "That set hit $reps reps. Slow the lowering, add a pause, or progress the variation so the next one comes back into ${r(range)} clean reps.", Tone.Accent, "Reps Down", "Harder variation")
        reps < range.last -> Guidance("Add reps before you progress the variation", "That set was on target. Stay with the same variation and try to beat $reps while keeping it inside ${r(range)}.", Tone.Accent, "Reps Up", "Same variation")
        else -> Guidance("Repeat the top end", "That set hit the top clean. Match it again before you make the variation harder.", Tone.Accent, "Match Reps", "Match the set")
    }
}
