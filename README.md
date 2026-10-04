# We Go Jim — Android

An Android (Kotlin + Jetpack Compose) port of [We Go Jim](https://github.com/Hortlund/we-go-jim),
the local-first iOS workout tracker by Andreas Hortlund (MIT License, see `LICENSE-original-WGJ`).

## Install

Sideload `app/build/outputs/apk/release/app-release.apk` (Android 8.0+). It is signed with the local
debug key, so uninstall it before installing a build signed with a different key.

## Build

Requires JDK 17 and the Android SDK (compileSdk 36).

```sh
./gradlew assembleRelease        # sideloadable APK
./gradlew testDebugUnitTest      # JVM tests for the ported logic
```

## Features (iOS parity)

- **Templates**: folders, warm-up/working sets, rep ranges, rest, dropsets, two-exercise supersets,
  rotating alternatives, cardio blocks, notes; share/import `.wgjtemplate` files (same format as iOS v8).
- **Logging**: start empty or from a template, previous-performance hints with *Fill Last*, rest timer
  with on-time notifications, superset round rest, cardio timers with goal alerts and pace/speed/split,
  replace/switch exercises, crash-safe restore, keep-screen-awake, training guidance.
- **Finish**: summary with PRs (Epley e1RM), calories, muscles worked, confetti, shareable card image,
  optional "update template with today's changes" diff.
- **History**: list and calendar, edit or delete completed workouts, save any workout as a template.
- **Progress**: workout-vs-workout comparison and per-exercise trends (1RM, max weight, volume, reps).
- **Profile**: configurable dashboard (weekly goal, coach brief, streaks, PRs, muscle heatmap,
  consistency, top exercises, exercise trends, calories), avatar, athlete types, six themes.
- **Data**: Room database on device, Android Auto Backup, manual backup/restore file, optional
  Health Connect export, weekly-goal home screen widget.

## Differences from iOS

| iOS | Android |
| --- | --- |
| CloudKit private backup | Android Auto Backup + backup file export/restore |
| Apple Health export | Health Connect export (opt-in) |
| On-device Foundation Models coach copy | The iOS deterministic coach recap (same rules) |
| Interactive SVG body map (MuscleMap package) | Muscle-group heatmap tiles (same scoring) |
| Exercise images fetched from an online catalog | Not included; the bundled catalog has no images |

## Architecture

- `data/` — Room entities/DAOs, `Repository`, `ActiveWorkoutStore` (memory-first, snapshotted to
  `active_workout.json`), `WorkoutEditor`/`WorkoutEditing` (shared structure edits), and ports of the iOS
  services: `PerformanceMath`, `Analytics` (comparison, heatmap, calories, trends, coach),
  `TrainingGuidance`, `Transfer` (`.wgjtemplate` + backup).
- `ui/` — Compose screens; business rules stay in `data/`.
- The bundled 247-exercise catalog (`assets/ExercisesSeed.json`) is the same file the iOS app ships.
