package com.caderneta.virtual.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.caderneta.virtual.CadernetaApp
import com.caderneta.virtual.R
import com.caderneta.virtual.data.TripRepository
import com.caderneta.virtual.data.db.TrackPoint
import com.caderneta.virtual.data.db.Trip
import com.caderneta.virtual.ui.MainActivity
import com.caderneta.virtual.util.Geo
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Foreground service that records a trip while a linked vehicle is connected.
 *
 * Lifecycle:
 *  - START (from [BluetoothConnectionReceiver] on ACL_CONNECT): grabs one fix for
 *    the origin, inserts an open [Trip], then streams location updates, appending
 *    [TrackPoint]s and accumulating distance.
 *  - STOP (on ACL_DISCONNECT): grabs the final fix, closes the trip with end time,
 *    destination and total distance, then stops itself.
 *
 * Kept alive by an ongoing notification (foregroundServiceType=location), so it
 * survives the app being backgrounded / swiped away.
 */
class TripRecordingService : LifecycleService() {

    private val repo: TripRepository get() = (application as CadernetaApp).repository
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private var activeTripId: Long = -1L
    private var deviceName: String = ""
    private var lastLat: Double? = null
    private var lastLng: Double? = null
    private var accumMeters: Double = 0.0
    private var pointCount = 0

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            onNewFix(loc)
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> handleStart(
                intent.getStringExtra(EXTRA_ADDRESS).orEmpty(),
                intent.getStringExtra(EXTRA_NAME).orEmpty(),
            )
            ACTION_STOP -> handleStop()
            else -> stopSelf()
        }
        return START_STICKY
    }

    // ── Start recording ──
    private fun handleStart(address: String, name: String) {
        if (address.isBlank()) { stopSelf(); return }
        deviceName = name
        // Promote to foreground immediately with the connectedDevice type only. That
        // type has no "while-in-use" restriction, so it is allowed to start even
        // though the service was launched from the background by the Bluetooth
        // connection broadcast (a location-typed start would be blocked here).
        startAsForeground(buildNotification(name, "Iniciando gravação…"), withLocation = false)

        if (!hasLocationPermission()) {
            // Cannot track without location; keep a trip record with origin unknown.
            lifecycleScope.launch { openTrip(address, name, null) }
            return
        }

        // Now that we're already a foreground service, add the location type so GPS
        // keeps streaming while the app is in the background.
        startAsForeground(buildNotification(name, "Gravando trajeto"), withLocation = true)

        lifecycleScope.launch {
            // If a stale active trip exists (e.g. app killed mid-trip), close it first.
            repo.getActiveTrip()?.let { stale ->
                repo.updateTrip(stale.copy(endTime = System.currentTimeMillis()))
            }
            val origin = lastKnownLocation()
            openTrip(address, name, origin)
            requestUpdates()
        }
    }

    private suspend fun openTrip(address: String, name: String, origin: Location?) {
        val now = System.currentTimeMillis()
        lastLat = origin?.latitude
        lastLng = origin?.longitude
        accumMeters = 0.0
        pointCount = 0
        val trip = Trip(
            deviceAddress = address,
            deviceName = name,
            startTime = now,
            startLat = origin?.latitude ?: 0.0,
            startLng = origin?.longitude ?: 0.0,
            startAddress = origin?.let { reverseGeocode(it.latitude, it.longitude) },
        )
        activeTripId = repo.startTrip(trip)
        origin?.let { repo.addPoint(TrackPoint(tripId = activeTripId, time = now, lat = it.latitude, lng = it.longitude)) }
        updateNotification(name, "Gravando trajeto")
    }

    // ── Location stream ──
    private fun requestUpdates() {
        if (!hasLocationPermission()) return
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000L)
            .setMinUpdateIntervalMillis(2000L)
            .setMinUpdateDistanceMeters(8f)
            .build()
        try {
            fused.requestLocationUpdates(req, locationCallback, mainLooper)
        } catch (_: SecurityException) { /* permission revoked at runtime */ }
    }

    private fun onNewFix(loc: Location) {
        if (activeTripId < 0) return
        val pLat = lastLat; val pLng = lastLng
        if (pLat != null && pLng != null) {
            accumMeters += Geo.distance(pLat, pLng, loc.latitude, loc.longitude)
        }
        lastLat = loc.latitude
        lastLng = loc.longitude
        pointCount++
        val now = System.currentTimeMillis()
        lifecycleScope.launch {
            repo.addPoint(TrackPoint(tripId = activeTripId, time = now, lat = loc.latitude, lng = loc.longitude))
            // Persist running distance so the UI banner reflects progress live.
            repo.observeTripSnapshot(activeTripId)?.let { t ->
                if (t.id == activeTripId) repo.updateTrip(t.copy(distanceMeters = accumMeters))
            }
            updateNotification(deviceName, "Gravando · ${String.format(Locale("pt","BR"), "%.1f km", accumMeters/1000)}")
        }
    }

    // ── Stop recording ──
    private fun handleStop() {
        lifecycleScope.launch {
            try {
                fused.removeLocationUpdates(locationCallback)
                val trip = if (activeTripId >= 0) repo.observeTripSnapshot(activeTripId) else repo.getActiveTrip()
                if (trip != null) {
                    val finalMeters = accumMeters.takeIf { it > 0 } ?: trip.distanceMeters
                    if (finalMeters < MIN_DISTANCE_METERS) {
                        // Trajeto abaixo de 0,1 km: descarta em vez de registrar
                        // (pontos do trajeto caem em cascata pela FK).
                        repo.deleteTrip(trip.id)
                    } else {
                        val dest = if (hasLocationPermission()) lastKnownLocation() else null
                        val endLat = dest?.latitude ?: lastLat ?: trip.startLat
                        val endLng = dest?.longitude ?: lastLng ?: trip.startLng
                        repo.updateTrip(
                            trip.copy(
                                endTime = System.currentTimeMillis(),
                                endLat = endLat,
                                endLng = endLng,
                                endAddress = reverseGeocode(endLat, endLng),
                                distanceMeters = finalMeters,
                            )
                        )
                    }
                }
            } finally {
                activeTripId = -1L
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    // ── Helpers ──
    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private suspend fun lastKnownLocation(): Location? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) return@withContext null
        try {
            com.google.android.gms.tasks.Tasks.await(
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            )
        } catch (_: Exception) { try { com.google.android.gms.tasks.Tasks.await(fused.lastLocation) } catch (_: Exception) { null } }
    }

    private suspend fun reverseGeocode(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        try {
            @Suppress("DEPRECATION")
            Geocoder(this@TripRecordingService, Locale("pt", "BR"))
                .getFromLocation(lat, lng, 1)
                ?.firstOrNull()
                ?.let { a -> listOfNotNull(a.thoroughfare, a.subThoroughfare).joinToString(", ").ifBlank { a.locality } }
        } catch (_: Exception) { null }
    }

    // ── Notification ──
    private fun buildNotification(title: String, text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gravando: $title")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(title, text))
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.recording_channel_name), NotificationManager.IMPORTANCE_LOW)
                    .apply { description = getString(R.string.recording_channel_desc) }
            )
        }
    }

    // Start in foreground with the correct service type on Android 10+/14.
    // Starts with connectedDevice (safe from background); pass withLocation=true
    // once running to add the location type for background GPS tracking.
    private fun startAsForeground(notif: Notification, withLocation: Boolean) {
        var type = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        if (withLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        try {
            androidx.core.app.ServiceCompat.startForeground(this, NOTIF_ID, notif, type)
        } catch (_: Exception) {
            // Background start blocked (e.g. battery optimization on). The trip is
            // still persisted; the user must allow unrestricted background use.
        }
    }

    companion object {
        const val ACTION_START = "com.caderneta.virtual.START"
        const val ACTION_STOP = "com.caderneta.virtual.STOP"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_NAME = "name"
        private const val CHANNEL_ID = "trip_recording"
        private const val NOTIF_ID = 42
        /** Trajetos com distância inferior a isto (0,1 km) são descartados. */
        private const val MIN_DISTANCE_METERS = 100.0
    }
}
