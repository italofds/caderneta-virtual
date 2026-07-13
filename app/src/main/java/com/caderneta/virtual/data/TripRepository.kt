package com.caderneta.virtual.data

import android.content.Context
import com.caderneta.virtual.data.db.AppDatabase
import com.caderneta.virtual.data.db.LinkedDevice
import com.caderneta.virtual.data.db.TrackPoint
import com.caderneta.virtual.data.db.Trip
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth over the Room database. Exposed as a lazy singleton via
 * [CadernetaApp.repository]; ViewModels and the recording service share it.
 */
class TripRepository(private val db: AppDatabase) {

    private val devices = db.linkedDeviceDao()
    private val trips = db.tripDao()

    // ── Linked devices ──
    fun observeDevices(): Flow<List<LinkedDevice>> = devices.observeAll()
    suspend fun enabledDevices(): List<LinkedDevice> = devices.getEnabled()
    suspend fun findDevice(address: String): LinkedDevice? = devices.findByAddress(address)
    suspend fun upsertDevice(device: LinkedDevice) = devices.upsert(device)
    suspend fun removeDevice(address: String) = devices.delete(address)

    /** True once the user has completed onboarding (linked ≥1 device). */
    suspend fun hasLinkedDevices(): Boolean = devices.getEnabled().isNotEmpty()

    // ── Trips ──
    fun observeTrips(): Flow<List<Trip>> = trips.observeAll()
    fun observeTripsByDevice(address: String): Flow<List<Trip>> = trips.observeByDevice(address)
    fun observeTrip(id: Long): Flow<Trip?> = trips.observeById(id)
    suspend fun observeTripSnapshot(id: Long): Trip? = trips.getById(id)
    fun observeActiveTrip(): Flow<Trip?> = trips.observeActive()
    suspend fun getActiveTrip(): Trip? = trips.getActive()

    suspend fun startTrip(trip: Trip): Long = trips.insert(trip)
    suspend fun updateTrip(trip: Trip) = trips.update(trip)
    suspend fun setOdometer(id: Long, odometer: Int?) = trips.setOdometer(id, odometer)
    suspend fun applyBatchOdometer(ids: List<Long>, initial: Int) =
        trips.applyBatchOdometer(ids, initial)

    // ── Track points (route) ──
    suspend fun addPoint(point: TrackPoint) = trips.insertPoint(point)
    fun observePoints(tripId: Long): Flow<List<TrackPoint>> = trips.observePoints(tripId)
    suspend fun getPoints(tripId: Long): List<TrackPoint> = trips.getPoints(tripId)

    companion object {
        fun from(context: Context) = TripRepository(AppDatabase.get(context))
    }
}
