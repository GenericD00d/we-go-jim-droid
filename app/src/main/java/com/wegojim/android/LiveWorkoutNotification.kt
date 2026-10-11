package com.wegojim.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.wegojim.android.data.ActiveWorkout
import com.wegojim.android.data.CardioMetrics
import com.wegojim.android.data.CardioProfile
import com.wegojim.android.data.CardioRecording
import com.wegojim.android.data.CardioRoute
import com.wegojim.android.ui.formatClock
import com.wegojim.android.ui.formatNumber

/**
 * Ongoing workout notification: the Android counterpart of the iOS workout
 * Live Activity. Opt-in (off by default), except while a GPS route records,
 * when Android requires a visible notification for the location service.
 * Shows a summary only (timers, set progress, rest, distance and pace),
 * never route coordinates. Timers are drawn by the system chronometer.
 */
object LiveWorkoutNotification {
    const val ID = 3001
    private const val CHANNEL_ID = "live_workout"
    private var lastPostedAt = 0L
    private var lastContent: Content? = null

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Workout in progress", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows your active workout and outdoor recording"
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private data class Content(
        val title: String,
        val text: String,
        val subText: String?,
        val chronometerBase: Long?,
        val countDown: Boolean,
    )

    private fun content(workout: ActiveWorkout, route: CardioRoute?, now: Long): Content {
        // A lone activity has its own clock. Mixed workouts keep the session clock.
        val activity = CardioRecording.standaloneActivity(workout)
        if (activity != null) {
            val elapsed = activity.durationSeconds?.takeIf { activity.completed } ?: activity.elapsedSeconds(now)
            val meters = if (activity.completed) activity.distanceMeters
            else route?.takeIf { it.activityId == activity.id && it.sessionId == workout.id }?.distanceMeters ?: activity.distanceMeters
            val unit = activity.distanceUnit
            val metrics = CardioMetrics.calculate(elapsed, meters, unit, activity.profile)
            val status = when {
                activity.completed -> "Completed"
                activity.isRunning -> "In progress"
                activity.timerAccumulatedSeconds == 0 -> "Ready to start"
                else -> "Paused"
            }
            val details = listOfNotNull(
                meters?.let { "%.2f %s".format(unit.fromMeters(it), unit.symbol) },
                metrics.paceSecondsPerUnit?.let { "${formatClock(Math.round(it))} /${unit.symbol}" }
                    ?: metrics.speedPerHour?.takeIf { activity.profile == CardioProfile.machineDistance }?.let { "${formatNumber(Math.round(it * 10) / 10.0)} ${unit.symbol}/h" },
            )
            return Content(
                title = activity.name,
                text = (listOf(status) + details).joinToString(" · ") + if (!activity.isRunning) " · ${formatClock(elapsed.toLong())}" else "",
                subText = null,
                chronometerBase = if (activity.isRunning) now - elapsed * 1000L else null,
                countDown = false,
            )
        }
        val sets = workout.exercises.flatMap { it.sets }
        val done = sets.count { it.completed && it.dropStages.all { d -> d.completed } }
        val progress = if (sets.isEmpty()) "${workout.exercises.size} exercises" else "$done/${sets.size} sets"
        val restEndsAt = workout.restEndsAt?.takeIf { it > now }
        return if (restEndsAt != null) {
            Content(workout.name, "Rest · $progress", workout.restLabel, restEndsAt, countDown = true)
        } else {
            Content(workout.name, "Workout in progress · $progress", null, workout.startedAt, countDown = false)
        }
    }

    fun build(context: Context): Notification {
        val app = context.app
        val workout = app.activeWorkout.state.value
        val c = workout?.let { content(it, app.routeRecorder.route.value, System.currentTimeMillis()) }
            ?: Content("We Go Jim", "Recording your activity", null, null, false)
        val open = PendingIntent.getActivity(
            context, ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(MainActivity.EXTRA_OPEN_WORKOUT, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(c.title)
            .setContentText(c.text)
            .setSubText(c.subText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .apply {
                if (c.chronometerBase != null) {
                    setWhen(c.chronometerBase)
                    setShowWhen(true)
                    setUsesChronometer(true)
                    setChronometerCountDown(c.countDown)
                } else {
                    setShowWhen(false)
                }
            }
            .build()
    }

    private fun canPost(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * Posts, updates or clears the notification. Cheap to call often: identical
     * content is skipped, and changes to the text alone (GPS distance) are
     * throttled to one update every 5 s.
     */
    @SuppressLint("MissingPermission") // canPost() checks POST_NOTIFICATIONS first.
    @Synchronized
    fun refresh(context: Context) {
        val app = context.app
        val workout = app.activeWorkout.state.value
        val recording = app.routeRecorder.route.value?.isRecording == true
        val manager = NotificationManagerCompat.from(context)
        if (workout == null || (!app.settings.state.value.liveNotification && !recording) || !canPost(context)) {
            if (!recording) manager.cancel(ID)
            lastContent = null
            return
        }
        val now = System.currentTimeMillis()
        val content = content(workout, app.routeRecorder.route.value, now)
        val previous = lastContent
        if (content == previous) return
        val textOnly = previous != null && content.copy(text = previous.text) == previous
        if (textOnly && now - lastPostedAt < 5_000) return
        lastContent = content
        lastPostedAt = now
        runCatching { manager.notify(ID, build(context)) }
    }
}
