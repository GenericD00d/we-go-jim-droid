# We Go Jim — Android

An Android (Kotlin + Jetpack Compose) port of [We Go Jim](https://github.com/Hortlund/we-go-jim),
the local-first iOS workout tracker by Andreas Hortlund (MIT License, see `LICENSE-original-WGJ`).

## Build

Requires JDK 17 and the Android SDK (compileSdk 36).

```sh
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # signed with the local debug key, for sideloading
```

## Architecture

- `data/` — Room database (exercise catalog, templates, completed workouts, flattened set facts),
  `ActiveWorkoutStore` (memory-first, snapshotted to `active_workout.json` for crash/relaunch recovery),
  `SettingsStore`, and `PerformanceMath` (Epley e1RM, kg/lb normalisation — ported from iOS).
- `ui/` — Compose screens. Business rules live in `data/`, screens stay thin.
- The bundled 247-exercise catalog (`assets/ExercisesSeed.json`) is the same file the iOS app ships.
