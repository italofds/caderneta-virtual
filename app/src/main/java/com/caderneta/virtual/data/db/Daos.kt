package com.caderneta.virtual.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkedDeviceDao {
    @Query("SELECT * FROM linked_devices ORDER BY name")
    fun observeAll(): Flow<List<LinkedDevice>>

    @Query("SELECT * FROM linked_devices WHERE enabled = 1")
    suspend fun getEnabled(): List<LinkedDevice>

    @Query("SELECT * FROM linked_devices WHERE address = :address LIMIT 1")
    suspend fun findByAddress(address: String): LinkedDevice?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: LinkedDevice)

    @Query("DELETE FROM linked_devices WHERE address = :address")
    suspend fun delete(address: String)
}

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY startTime DESC")
    fun observeAll(): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE deviceAddress = :address ORDER BY startTime DESC")
    fun observeByDevice(address: String): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE id = :id")
    fun observeById(id: Long): Flow<Trip?>

    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Trip?

    @Query("SELECT * FROM trips WHERE endTime IS NULL LIMIT 1")
    fun observeActive(): Flow<Trip?>

    @Query("SELECT * FROM trips WHERE endTime IS NULL LIMIT 1")
    suspend fun getActive(): Trip?

    @Query("SELECT * FROM trips WHERE id IN (:ids) ORDER BY startTime ASC")
    suspend fun getByIds(ids: List<Long>): List<Trip>

    @Insert
    suspend fun insert(trip: Trip): Long

    @Update
    suspend fun update(trip: Trip)

    @Query("UPDATE trips SET odometerStart = :odo WHERE id = :id")
    suspend fun setOdometer(id: Long, odo: Int?)

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteTrip(id: Long)

    // ── Track points ──
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPoint(point: TrackPoint)

    @Query("SELECT * FROM track_points WHERE tripId = :tripId ORDER BY time ASC")
    fun observePoints(tripId: Long): Flow<List<TrackPoint>>

    @Query("SELECT * FROM track_points WHERE tripId = :tripId ORDER BY time ASC")
    suspend fun getPoints(tripId: Long): List<TrackPoint>

    /** Applies an initial odometer across an ordered selection, cascading the
     *  derived end into the next trip's start. Runs in one transaction. */
    @Transaction
    suspend fun applyBatchOdometer(ids: List<Long>, initialOdometer: Int) {
        val trips = getByIds(ids)
        var cursor = initialOdometer
        for (t in trips) {
            setOdometer(t.id, cursor)
            cursor += t.distanceKmRounded
        }
    }
}
