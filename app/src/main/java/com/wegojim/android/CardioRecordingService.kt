package com.wegojim.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.wegojim.android.data.ActiveWorkout
import com.wegojim.android.data.CardioRecording
import com.wegojim.android.data.CardioRoute
import com.wegojim.android.data.RouteStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GpsState(val message: String) {
    Inactive("GPS records your distance and route."),
    Locating("Finding GPS…"),
    Recording("GPS recording · Works with your screen locked"),
    Paused("GPS paused"),
    Denied("Location is off. Add distance when you finish."),
    Unavailable("GPS signal unavailable. Recording will resume when it returns."),
}

/**
 * Port of CardioRouteRecorder. Follows the active workout: while an outdoor
 * activity's timer runs, a location foreground service feeds fixes into its
 * route; pausing, finishing or leaving the workout stops GPS. The route is
 * saved at those boundaries and every few seconds while recording.
 */
class RouteRecorder(private val context: Context, private val store: RouteStore) {
    private val _route = MutableStateFlow<CardioRoute?>(null)
    val route: StateFlow<CardioRoute?> = _route.asStateFlow()
    private val _gpsState = MutableStateFlow(GpsState.Inactive)
    val gpsState: StateFlow<GpsState> = _gpsState.asStateFlow()

    private var recordingStartedAt = Long.MAX_VALUE
    private var maxSpeed = 12.0
    private var lastSavedAt = 0L
    private var serviceRunning = false

    fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** The saved route for a completed or paused activity, without touching GPS. */
    fun routeFor(sessionId: String, activityId: String): CardioRoute? =
        _route.value?.takeIf { it.activityId == activityId && it.sessionId == sessionId }
            ?: store.load(activityId)?.takeIf { it.sessionId == sessionId }

    /** Called on every active workout change; only timer boundaries start or stop GPS. */
    @Synchronized
    fun sync(workout: ActiveWorkout?) {
        val running = workout?.cardio?.firstOrNull { it.isRunning && !it.completed && CardioRecording.recordsGps(it.exerciseUuid) }
        val current = _route.value
        if (workout == null || running == null) {
            if (current?.isRecording == true) finishSegment(current)
            stopService()
            val shown = workout?.cardio?.firstOrNull { it.id == current?.activityId }
            _gpsState.value = if (shown != null && !shown.completed && shown.timerAccumulatedSeconds > 0) GpsState.Paused else GpsState.Inactive
            return
        }
        if (current?.activityId == running.id && current.isRecording) {
            // Permission granted after the timer started: begin now without a new segment.
            if (!serviceRunning && hasPermission()) startService()
            return
        }
        if (current != null && current.isRecording) finishSegment(current)
        val base = (if (current?.activityId == running.id) current else store.load(running.id)?.takeIf { it.sessionId == workout.id })
            ?: CardioRoute(sessionId = workout.id, activityId = running.id)
        recordingStartedAt = maxOf(System.currentTimeMillis(), running.timerStartedAt ?: 0L)
        maxSpeed = CardioRecording.maximumSpeedMetersPerSecond(running.exerciseUuid)
        val started = base.beginSegment()
        _route.value = started
        store.save(started)
        if (hasPermission()) startService() else _gpsState.value = GpsState.Denied
    }

    /** Stops GPS and writes the route before a workout is saved. */
    @Synchronized
    fun flush() {
        _route.value?.let { if (it.isRecording) finishSegment(it) else store.save(it) }
        stopService()
    }

    fun forget(activityIds: Collection<String>) {
        synchronized(this) {
            if (_route.value?.activityId in activityIds) {
                _route.value = null
                stopService()
                _gpsState.value = GpsState.Inactive
            }
        }
        store.delete(activityIds)
    }

    private fun finishSegment(route: CardioRoute) {
        val stopped = route.stop()
        _route.value = stopped
        store.save(stopped)
    }

    @Synchronized
    internal fun onLocation(location: Location) {
        val route = _route.value ?: return
        val now = System.currentTimeMillis()
        val next = route.append(
            latitude = location.latitude,
            longitude = location.longitude,
            timestamp = location.time,
            accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else Double.NaN,
            now = now,
            recordingStartedAt = recordingStartedAt,
            maximumSpeedMetersPerSecond = maxSpeed,
        )
        if (next.points.size > route.points.size) _gpsState.value = GpsState.Recording
        if (next == route) return
        _route.value = next
        if (now - lastSavedAt > 10_000) {
            lastSavedAt = now
            store.save(next)
        }
        LiveWorkoutNotification.refresh(context)
    }

    internal fun onProviderChanged(enabled: Boolean) {
        if (_route.value?.isRecording == true) _gpsState.value = if (enabled) GpsState.Locating else GpsState.Unavailable
    }

    private fun startService() {
        _gpsState.value = GpsState.Locating
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, CardioRecordingService::class.java))
            serviceRunning = true
        }.onFailure { _gpsState.value = GpsState.Unavailable }
    }

    private fun stopService() {
        if (!serviceRunning) return
        serviceRunning = false
        context.stopService(Intent(context, CardioRecordingService::class.java))
    }
}

/** Location foreground service, so a route keeps recording with the screen off. */
class CardioRecordingService : Service(), LocationListener {
    private val manager by lazy { getSystemService(LocationManager::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val recorder = app.routeRecorder
        val notification = LiveWorkoutNotification.build(this)
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        val started = runCatching {
            ServiceCompat.startForeground(this, LiveWorkoutNotification.ID, notification, type)
        }.isSuccess
        if (!started || !recorder.hasPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }
        manager.removeUpdates(this)
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) recorder.onProviderChanged(false)
        runCatching { manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, this, Looper.getMainLooper()) }
            .onFailure { recorder.onProviderChanged(false) }
        return START_NOT_STICKY
    }

    override fun onLocationChanged(location: Location) = app.routeRecorder.onLocation(location)

    override fun onProviderEnabled(provider: String) = app.routeRecorder.onProviderChanged(true)

    override fun onProviderDisabled(provider: String) = app.routeRecorder.onProviderChanged(false)

    override fun onDestroy() {
        manager.removeUpdates(this)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        LiveWorkoutNotification.refresh(this)
        super.onDestroy()
    }
}
