package com.caderneta.virtual.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A Bluetooth device the user linked as a "vehicle" to monitor.
 * `address` is the immutable MAC; `name` is the friendly Bluetooth name and is
 * also what trips are filtered/grouped by in the UI.
 */
@Entity(tableName = "linked_devices")
data class LinkedDevice(
    @PrimaryKey val address: String,
    val name: String,
    val enabled: Boolean = true,
)

/**
 * One recorded trip: from Bluetooth connect to disconnect.
 * Distance is accumulated live from GPS fixes (meters). Odometer values are
 * user-supplied; [odometerStart] is entered by the user and [odometerEnd] is
 * derived (start + round(distanceMeters/1000)).
 */
@Entity(
    tableName = "trips",
    indices = [Index("deviceAddress"), Index("startTime")],
)
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceAddress: String,
    val deviceName: String,
    val startTime: Long,                 // epoch millis, moment of connection
    val endTime: Long? = null,           // epoch millis, moment of disconnection (null while active)
    val startLat: Double,
    val startLng: Double,
    val startAddress: String? = null,
    val endLat: Double? = null,
    val endLng: Double? = null,
    val endAddress: String? = null,
    val distanceMeters: Double = 0.0,    // accumulated from track points
    val odometerStart: Int? = null,      // km, user supplied
) {
    /** Distance in whole km, rounded — the unit the odometer uses. */
    val distanceKmRounded: Int get() = Math.round(distanceMeters / 1000.0).toInt()

    /** Derived end odometer, or null if start not informed. */
    val odometerEnd: Int? get() = odometerStart?.plus(distanceKmRounded)

    val isActive: Boolean get() = endTime == null
}

/** A GPS sample belonging to a trip — used to draw the detailed route on the map. */
@Entity(
    tableName = "track_points",
    foreignKeys = [ForeignKey(
        entity = Trip::class,
        parentColumns = ["id"],
        childColumns = ["tripId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("tripId")],
)
data class TrackPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val time: Long,
    val lat: Double,
    val lng: Double,
)
