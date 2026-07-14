package com.caderneta.virtual.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.caderneta.virtual.CadernetaApp
import com.caderneta.virtual.data.db.LinkedDevice
import com.caderneta.virtual.data.db.Trip
import com.caderneta.virtual.util.sameDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A day+vehicle bucket for the "grouped by day" view. */
data class TripGroup(
    val dayKey: String,
    val device: String,
    val trips: List<Trip>,
) {
    val first get() = trips.first()
    val last get() = trips.last()
    val totalMeters get() = trips.sumOf { it.distanceMeters }
    val startOdometer get() = first.odometerStart
    val endOdometer get() = last.odometerEnd
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as CadernetaApp).repository

    val devices: StateFlow<List<LinkedDevice>> =
        repo.observeDevices().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeTrip: StateFlow<Trip?> =
        repo.observeActiveTrip().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ── List filters ──
    private val _filter = MutableStateFlow<String?>(null)   // device address, null = all
    val filter = _filter.asStateFlow()
    private val _grouped = MutableStateFlow(false)
    val grouped = _grouped.asStateFlow()

    fun setFilter(address: String?) { _filter.value = address }
    fun toggleGrouped() { _grouped.value = !_grouped.value }

    /** Trips honoring the current device filter, oldest first (so the list reads
     *  top-to-bottom chronologically and the newest entries land at the bottom). */
    val trips: StateFlow<List<Trip>> = combine(repo.observeTrips(), _filter) { list, f ->
        (if (f == null) list else list.filter { it.deviceAddress == f })
            .filter { !it.isActive }
            .sortedBy { it.startTime }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Grouped view: same day + same vehicle collapsed into one bucket. */
    val groups: StateFlow<List<TripGroup>> = trips.let { flow ->
        combine(flow, _grouped) { list, _ ->
            val ascending = list.sortedBy { it.startTime }
            val buckets = LinkedHashMap<String, MutableList<Trip>>()
            ascending.forEach { t ->
                val key = "${t.deviceAddress}@${com.caderneta.virtual.util.Fmt.dayKey(t.startTime)}"
                buckets.getOrPut(key) { mutableListOf() }.add(t)
            }
            buckets.values.map { g ->
                TripGroup(
                    dayKey = com.caderneta.virtual.util.Fmt.dayKey(g.first().startTime),
                    device = g.first().deviceName,
                    trips = g.sortedBy { it.startTime },
                )
            }.sortedBy { it.first.startTime }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ── Selection (multi-select for batch odometer) ──
    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection = _selection.asStateFlow()
    private val _selectionMode = MutableStateFlow(false)
    val selectionMode = _selectionMode.asStateFlow()

    fun enterSelection() { _selectionMode.value = true; _grouped.value = false }
    fun exitSelection() { _selectionMode.value = false; _selection.value = emptySet() }
    fun toggleSelected(id: Long) {
        _selection.value = _selection.value.toMutableSet().apply { if (!add(id)) remove(id) }
    }

    // ── Odometer edits ──
    fun setOdometer(tripId: Long, odometer: Int?) = viewModelScope.launch {
        repo.setOdometer(tripId, odometer)
    }

    /** Apply an initial odometer to the current selection (ordered by time),
     *  cascading start→end→next.start. */
    fun applyBatchOdometer(initial: Int) = viewModelScope.launch {
        val orderedIds = trips.value.filter { it.id in _selection.value }
            .sortedBy { it.startTime }.map { it.id }
        repo.applyBatchOdometer(orderedIds, initial)
        exitSelection()
    }

    /** Deletes every currently selected trip (and its track points, via cascade). */
    fun deleteSelected() = viewModelScope.launch {
        repo.deleteTrips(_selection.value.toList())
        exitSelection()
    }

    // ── Device linking ──
    fun setDeviceEnabled(device: LinkedDevice, enabled: Boolean) = viewModelScope.launch {
        repo.upsertDevice(device.copy(enabled = enabled))
    }
    fun addOrUpdateDevice(address: String, name: String, enabled: Boolean) = viewModelScope.launch {
        repo.upsertDevice(LinkedDevice(address, name, enabled))
    }
}
