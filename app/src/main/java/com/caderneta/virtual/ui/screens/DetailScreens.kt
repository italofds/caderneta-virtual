package com.caderneta.virtual.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.caderneta.virtual.data.db.TrackPoint
import com.caderneta.virtual.data.db.Trip
import com.caderneta.virtual.ui.MainViewModel
import com.caderneta.virtual.ui.theme.colorForDevice
import com.caderneta.virtual.ui.TripDetailData
import com.caderneta.virtual.util.Fmt
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import kotlinx.coroutines.flow.map

// ══════════════════════ DETAIL + MAP ══════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(vm: MainViewModel, tripId: Long, repoObserve: TripDetailData, onBack: () -> Unit) {
    val trip by repoObserve.trip.collectAsState(initial = null)
    val points by repoObserve.points.collectAsState(initial = emptyList())
    var editOpen by remember { mutableStateOf(false) }
    val t = trip

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Detalhes do trajeto") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
        )
    }) { pad ->
        if (t == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val color = colorForDevice(t.deviceAddress)
        LazyColumn(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(16.dp))) {
                    RouteMap(t, points, color)
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(8.dp))
                    Text(t.deviceName, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text(Fmt.longDay(t.startTime), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                }
            }
            item {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(16.dp)) {
                        Timeline("Partida", Fmt.time(t.startTime), t.startAddress, Fmt.odometer(t.odometerStart), false)
                        Spacer(Modifier.height(4.dp))
                        Timeline("Chegada", t.endTime?.let { Fmt.time(it) } ?: "—", t.endAddress, Fmt.odometer(t.odometerEnd), true)
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Straighten, null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(8.dp))
                            Text("Distância percorrida", Modifier.weight(1f))
                            Text(Fmt.km(t.distanceMeters), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { editOpen = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Default.Speed, null); Spacer(Modifier.width(8.dp)); Text("Informar odômetro inicial")
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (editOpen && t != null) {
        OdometerDialog(
            initial = t.odometerStart,
            distanceKm = t.distanceKmRounded,
            onDismiss = { editOpen = false },
            onSave = { vm.setOdometer(t.id, it); editOpen = false },
        )
    }
}

@Composable
private fun RouteMap(t: Trip, points: List<TrackPoint>, color: Color) {
    val path = points.map { LatLng(it.lat, it.lng) }
    val start = path.firstOrNull() ?: LatLng(t.startLat, t.startLng)
    val end = path.lastOrNull() ?: LatLng(t.endLat ?: t.startLat, t.endLng ?: t.startLng)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(start, 14f)
    }
    LaunchedEffect(path.size) {
        if (path.size >= 2) {
            val b = LatLngBounds.builder().apply { path.forEach { include(it) } }.build()
            runCatching { cameraPositionState.animate(com.google.android.gms.maps.CameraUpdateFactory.newLatLngBounds(b, 80)) }
        }
    }
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
    ) {
        if (path.size >= 2) Polyline(points = path, color = color, width = 14f)
        Marker(state = rememberMarkerState(position = start), title = "Início")
        if (t.endTime != null) Marker(state = rememberMarkerState(position = end), title = "Destino")
    }
}

@Composable
private fun Timeline(label: String, time: String, place: String?, odo: String, isEnd: Boolean) {
    Row {
        Column(Modifier.width(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (isEnd) Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.secondary)
            else Box(Modifier.padding(top = 5.dp).size(12.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(3.dp, MaterialTheme.colorScheme.secondary, CircleShape))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(time, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.titleMedium)
            if (place != null) Text(place, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Odômetro: $odo km", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

// ══════════════════════ ODOMETER DIALOG (single) ══════════════════════
@Composable
fun OdometerDialog(initial: Int?, distanceKm: Int, onDismiss: () -> Unit, onSave: (Int?) -> Unit) {
    var text by remember { mutableStateOf(initial?.toString() ?: "") }
    val value = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Odômetro inicial") },
        text = {
            Column {
                Text("Informe o odômetro do veículo no início deste trajeto. O valor final será calculado automaticamente.",
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { s -> text = s.filter { it.isDigit() } },
                    label = { Text("Odômetro inicial (km)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (value != null) "Odômetro final: ${Fmt.odometer(value + distanceKm)} km (início + $distanceKm km)" else " ",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value) }, enabled = value != null) { Text("Salvar") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } },
    )
}

// ══════════════════════ BATCH ODOMETER ══════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchOdometerScreen(vm: MainViewModel, onDone: () -> Unit) {
    val trips by vm.trips.collectAsState()
    val selection by vm.selection.collectAsState()
    val selected = remember(trips, selection) { trips.filter { it.id in selection }.sortedBy { it.startTime } }
    var text by remember { mutableStateOf("") }
    val initial = text.toIntOrNull()

    Scaffold(topBar = {
        TopAppBar(title = { Text("Odômetro em lote") },
            navigationIcon = { IconButton(onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
    }, bottomBar = {
        Box(Modifier.padding(16.dp)) {
            Button(onClick = { initial?.let { vm.applyBatchOdometer(it); onDone() } },
                enabled = initial != null, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("Aplicar odômetros")
            }
        }
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Informe o odômetro no início do primeiro trajeto selecionado. Os demais serão calculados em sequência, somando a distância de cada trajeto.",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
            }
            item {
                OutlinedTextField(value = text, onValueChange = { s -> text = s.filter { it.isDigit() } },
                    label = { Text("Odômetro inicial (km)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth())
            }
            item {
                Text("${selected.size} TRAJETO${if (selected.size == 1) "" else "S"} SELECIONADO${if (selected.size == 1) "" else "S"}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 8.dp))
            }
            var cursor = initial
            items(selected, key = { it.id }) { t ->
                val s = cursor
                val e = s?.plus(t.distanceKmRounded)
                cursor = e
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${Fmt.shortDay(t.startTime)} · ${Fmt.time(t.startTime)}–${t.endTime?.let { Fmt.time(it) } ?: "—"} · ${t.deviceName}",
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(Fmt.km(t.distanceMeters), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${Fmt.odometer(s)} → ${Fmt.odometer(e)}", style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }
    }
}

// ══════════════════════ SETTINGS ══════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, bonded: List<com.caderneta.virtual.data.db.LinkedDevice>, onBack: () -> Unit) {
    val linked by vm.devices.collectAsState()
    Scaffold(topBar = {
        TopAppBar(title = { Text("Configurações") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } })
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("VEÍCULOS VINCULADOS", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 8.dp))
            }
            val list = run {
                val byAddr = linked.associateBy { it.address }.toMutableMap()
                bonded.forEach { b -> if (b.address !in byAddr) byAddr[b.address] = b.copy(enabled = false) }
                byAddr.values.sortedBy { it.name }
            }
            items(list, key = { it.address }) { d ->
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DirectionsCar, null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.name, style = MaterialTheme.typography.bodyLarge)
                            Text(d.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(checked = d.enabled, onCheckedChange = { vm.addOrUpdateDevice(d.address, d.name, it) })
                    }
                }
            }
            item {
                Text("Trajetos só são registrados para os dispositivos vinculados.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
