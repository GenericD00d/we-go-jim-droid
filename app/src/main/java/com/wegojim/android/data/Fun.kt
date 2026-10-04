package com.wegojim.android.data

import java.time.LocalDate

/** Port of ProfileAthleteType: a cosmetic profile badge. */
enum class AthleteType(val title: String, val subtitle: String) {
    strengthTraining("Strength Training", "General barbell and dumbbell work with steady progressive overload."),
    powerlifting("Powerlifting", "Squat, bench, deadlift, and a soft spot for heavy singles."),
    olympicLifting("Olympic Lifting", "Snatch, clean and jerk, and sharp bar speed under pressure."),
    bodybuilding("Bodybuilding", "Hypertrophy blocks, mind-muscle focus, and pump-driven sessions."),
    hybridAthlete("Hybrid Athlete", "Lifts hard, runs hard, and refuses to choose one identity."),
    strongman("Strongman", "Carries, odd objects, and events that look mildly illegal."),
    calisthenics("Calisthenics", "Bodyweight skill, control, and clean lines under tension."),
    running("Running", "Mileage, pace, and the occasional identity crisis after leg day."),
    functionalFitness("Functional Fitness", "Mixed-modal suffering with a whiteboard and a clock nearby."),
    endurance("Endurance", "Long efforts, patient pacing, and an engine-first mindset."),
    garageGymRat("Garage Gym Rat", "Home setup, no excuses, and one loyal speaker doing heavy work."),
    benchMerchant("Bench Merchant", "Lives for chest day and takes the shortest route there."),
    legDaySurvivor("Leg Day Survivor", "Still walking, technically."),
    deadliftEnthusiast("Deadlift Enthusiast", "Finds spiritual meaning in picking heavy things off the floor."),
    cardioCriminal("Cardio Criminal", "Avoids steady-state like it owes money."),
    machineMaxxer("Machine Maxxer", "Perfect angles, controlled reps, and plate-loaded peace."),
    mobilityMonk("Mobility Monk", "Owns a lacrosse ball and knows exactly where the hips are hiding."),
    weekendWarrior("Weekend Warrior", "All gas on Saturday, surprisingly sore until Tuesday."),
    dadStrength("Dad Strength", "Uncanny grip strength and zero warmup theatrics."),
    chaosGoblin("Chaos Goblin", "Programming is optional. Vibes are mandatory."),
    cycling("Cycling", "Power numbers, zone work, and suspiciously sharp tan lines."),
    swimming("Swimming", "Laps, breathing rhythm, and silent cardio violence."),
    trailRunning("Trail Running", "Vert, dirt, and joyful suffering in scenic places."),
    climbing("Climbing", "Grip strength, precision, and chalk on absolutely everything."),
    martialArts("Martial Arts", "Skill work, rounds, and a calm switch into violence."),
    yogaFlow("Yoga Flow", "Mobility, balance, and strength without panic lifting."),
    racketSports("Racket Sports", "Footwork, repeat sprints, and sneaky shoulder durability."),
    squatSorcerer("Squat Sorcerer", "Disappears into the hole and reappears stronger."),
    chalkGoblin("Chalk Goblin", "Leaves fingerprints on every surface in the building."),
    proteinProphet("Protein Prophet", "Can turn any conversation into grams per meal."),
    preworkoutAstronaut("Preworkout Astronaut", "Launch sequence begins at one and a half scoops."),
    deloadDenier("Deload Denier", "Says recovery is for cowards, pays for it later."),
    cableCowboy("Cable Cowboy", "Can build an entire workout from one cable tower."),
    pumpChaser("Pump Chaser", "Will add one more finisher for science."),
    repRangeBandit("Rep Range Bandit", "Treats 8-12 reps as a loose suggestion, not a law."),
    plateCollector("Plate Collector", "Warmups somehow use every plate in the gym."),
    spreadsheetTactician("Spreadsheet Tactician", "Tracks everything and still claims it is intuitive."),
    restDayRevisionist("Rest Day Revisionist", "Calls it active recovery and somehow ends up training."),
    latsCartographer("Lats Cartographer", "Still mapping the route to true back width.");

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name }
    }
}

/** Port of GymEasterEggPolicy. Cosmetic only; no training values depend on it. */
object GymEasterEgg {
    const val SEARCH_MESSAGE = "No results. We go jim."

    fun searchMatches(query: String) = query.trim().lowercase() in setOf("motivation", "excuses")

    private val legMuscles = setOf("Quadriceps", "Hamstrings", "Glutes", "Calves", "Adductors")

    fun isLegDay(scores: List<Pair<String, Double>>): Boolean {
        val total = scores.sumOf { it.second }
        return total > 0 && scores.filter { it.first in legMuscles }.sumOf { it.second } / total >= 0.6
    }

    /** Stable per workout, so reopening a recap doesn't reroll the joke. */
    private fun seed(id: String): ULong = id.uppercase().encodeToByteArray()
        .fold(14695981039346656037uL) { h, b -> (h xor b.toUByte().toULong()) * 1099511628211uL }

    fun completionMessage(workoutId: String, workingSets: Int, hasPr: Boolean, legDay: Boolean, gymBroMode: Boolean): String? {
        if (workingSets <= 0) return null
        val roll = seed(workoutId)
        if (hasPr && roll % 4uL == 0uL) return "LIGHT WEIGHT, BABY!"
        if (legDay && workingSets >= 3 && roll % 3uL == 0uL) {
            return if (roll % 2uL == 0uL) "Workout saved. Stairs are now your enemy." else "Good luck sitting down tomorrow."
        }
        if (!gymBroMode && roll % 5uL != 0uL) return null
        val messages = listOf(
            "Heavy circles moved successfully.",
            "The lore continues.",
            "You vs. gravity. Rematch pending.",
            "All that work just to put the weights back.",
            "One more set. Famous last words.",
        )
        return messages[((roll / 7uL) % messages.size.toULong()).toInt()]
    }
}

/** Port of BirthdayCelebrationPolicy, including leap-day birthdays on Feb 28. */
object Birthday {
    fun isBirthday(birthDate: LocalDate?, today: LocalDate = LocalDate.now()): Boolean {
        if (birthDate == null || !birthDate.isBefore(today)) return false
        var day = birthDate.dayOfMonth
        if (birthDate.monthValue == 2 && day == 29 && today.monthValue == 2 && !today.isLeapYear) day = 28
        return birthDate.monthValue == today.monthValue && day == today.dayOfMonth
    }

    fun greeting(name: String) = if (name.isBlank()) "Happy birthday!" else "Happy birthday, ${name.trim()}!"
}
