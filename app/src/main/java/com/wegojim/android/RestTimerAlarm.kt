package com.wegojim.android

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Schedules the "rest finished" notification so it fires while the app is backgrounded. */
object RestTimerAlarm {
    private const val CHANNEL_ID = "rest_timer"
    private const val NOTIFICATION_ID = 1001
    private const val EXTRA_LABEL = "label"
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_ID = "notificationId"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Rest timer", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alerts when a rest period is over"
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun schedule(context: Context, triggerAtMillis: Long, label: String) =
        setAlarm(context, triggerAtMillis, pendingIntent(context, 0, "Rest finished", label, NOTIFICATION_ID))

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, 0, null, null, NOTIFICATION_ID))
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** Fires when a timed cardio block reaches its goal. */
    fun scheduleCardio(context: Context, blockId: String, triggerAtMillis: Long, name: String) =
        setAlarm(context, triggerAtMillis, pendingIntent(context, cardioCode(blockId), "Cardio goal reached", name, cardioCode(blockId)))

    fun cancelCardio(context: Context, blockId: String) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, cardioCode(blockId), null, null, cardioCode(blockId)))
    }

    private fun cardioCode(blockId: String) = 2000 + (blockId.hashCode() and 0xFFFF)

    private fun setAlarm(context: Context, triggerAtMillis: Long, pending: PendingIntent) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (canExact) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }

    private fun pendingIntent(context: Context, requestCode: Int, title: String?, label: String?, notificationId: Int) = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, RestTimerReceiver::class.java)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_LABEL, label)
            .putExtra(EXTRA_ID, notificationId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    internal fun notify(context: Context, intent: Intent) {
        val label = intent.getStringExtra(EXTRA_LABEL)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(intent.getStringExtra(EXTRA_TITLE) ?: "Rest finished")
            .setContentText(label ?: "Time for your next set")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify(intent.getIntExtra(EXTRA_ID, NOTIFICATION_ID), notification)
    }
}

class RestTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        RestTimerAlarm.notify(context, intent)
        // The ongoing workout notification switches from the rest countdown back to the session clock.
        LiveWorkoutNotification.refresh(context)
    }
}
