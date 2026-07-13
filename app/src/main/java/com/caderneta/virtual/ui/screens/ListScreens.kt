package com.caderneta.virtual.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.caderneta.virtual.data.db.LinkedDevice
import com.caderneta.virtual.data.db.Trip
import com.caderneta.virtual.ui.MainViewModel
import com.caderneta.virtual.ui.TripGroup
import com.caderneta.virtual.ui.theme.colorForDevice
import com.caderneta.virtual.util.Fmt

// ══════════════════════ ONBOARDING ══════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(vm: MainViewModel, bonded: List<LinkedDevice>, onDone: () -> Unit) {
    val linked by vm.devices.collectAsState()
    val enabledCount = merged(bonded, linked).count { it.enabled }
    Scaffold { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.weight(1f).padding(24.dp)) {
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.DirectionsCar, null, tint = MaterialTheme.colorScheme.secondary) }
                Spacer(Modifier.height(20.dp))
                Text("Caderneta Virtual", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Selecione os dispositivos Bluetooth dos veículos que deseja monitorar. Sempre que o celular se conectar a um deles, o trajeto será registrado automaticamente.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Text("DISPOSITIVOS PAREADOS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(10.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(merged(bonded, linked), key = { it.address }) { d ->
                        DeviceCard(d) { vm.addOrUpdateDevice(d.address, d.name, !d.enabled) }
                    }
                }
            }
            Column(Modifier.padding(24.dp)) {
                Button(
                    onClick = onDone, enabled = enabledCount > 0,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text("Continuar") }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Você pode alterar os vínculos depois em Configurações.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(d: LinkedDevice, onToggle: () -> Unit) {
    val on = d.enabled
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(16.dp),
        color = if (on) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center) { Icon(Icons.Default.DirectionsCar, null, tint = MaterialTheme.colorScheme.secondary) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(d.name, style = MaterialTheme.typography.bodyLarge)
                Text(d.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Icon(if (on) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank, null,
                tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        }
    }
}

/** Merge the OS's bonded devices with our saved link state so toggles persist. */
private fun merged(bonded: List<LinkedDevice>, saved: List<LinkedDevice>): List<LinkedDevice> {
    val byAddr = saved.associateBy { it.address }.toMutableMap()
    bonded.forEach { b -> if (b.address !in byAddr) byAddr[b.address] = b.copy(enabled = false) }
    return byAddr.values.sortedBy { it.name }
}

// ══════════════════════ TRIP LIST ══════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripListScreen(vm: MainViewModel, onOpen: (Long) -> Unit, onSettings: () -> Unit, onBatch: () -> Unit) {
    val devices by vm.devices.collectAsState()
    val filter by vm.filter.collectAsState()
    val grouped by vm.grouped.collectAsState()
    val trips by vm.trips.collectAsState()
    val groups by vm.groups.collectAsState()
    val selMode by vm.selectionMode.collectAsState()
    val selection by vm.selection.collectAsState()
    val active by vm.activeTrip.collectAsState()
    val enabled = devices.filter { it.enabled }

    Scaffold(
        topBar = {
            if (selMode) {
                TopAppBar(
                    navigationIcon = { IconButton({ vm.exitSelection() }) { Icon(Icons.Default.Close, null) } },
                    title = { Text("${selection.size} selecionado${if (selection.size == 1) "" else "s"}") },
                    actions = {
                        TextButton(onClick = { if (selection.isNotEmpty()) onBatch() }, enabled = selection.isNotEmpty()) {
                            Icon(Icons.Default.Speed, null); Spacer(Modifier.width(4.dp)); Text("Odômetro")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                )
            } else {
                TopAppBar(
                    title = { Text("Caderneta Virtual") },
                    actions = {
                        IconButton({ vm.enterSelection() }) { Icon(Icons.Default.Checklist, "Selecionar") }
                        IconButton(onSettings) { Icon(Icons.Default.Settings, "Configurações") }
                    },
                )
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Filter chips
            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FilterChip(filter == null, { vm.setFilter(null) }, { Text("Todos") }) }
                items(enabled, key = { it.address }) { d ->
                    FilterChip(filter == d.address, { vm.setFilter(d.address) }, { Text(d.name) })
                }
                item {
                    FilterChip(grouped, { vm.toggleGrouped() },
                        { Text("Agrupar por dia") },
                        leadingIcon = { Icon(Icons.Default.CalendarViewDay, null, Modifier.size(18.dp)) })
                }
            }

            LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (active != null) item {
                    RecordingBanner(active!!) { onOpen(active!!.id) }
                }
                if (!grouped) {
                    var lastDay: String? = null
                    val ordered = trips
                    ordered.forEach { t ->
                        val dk = Fmt.dayKey(t.startTime)
                        if (dk != lastDay) {
                            lastDay = dk
                            item(key = "h$dk") {
                                Text(Fmt.longDay(t.startTime),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                        item(key = t.id) {
                            TripCard(t, selMode, t.id in selection) {
                                if (selMode) vm.toggleSelected(t.id) else onOpen(t.id)
                            }
                        }
                    }
                } else {
                    items(groups, key = { it.dayKey + it.device }) { g -> GroupCard(g, onOpen) }
                }
                if ((if (grouped) groups.isEmpty() else trips.isEmpty()) && active == null) item {
                    Column(Modifier.fillMaxWidth().padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Route, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(12.dp))
                        Text("Nenhum trajeto registrado para este filtro.",
                            color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun RecordingBanner(trip: Trip, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Gravando — ${trip.deviceName}", fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Iniciado às ${Fmt.time(trip.startTime)} · ${Fmt.km(trip.distanceMeters)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            Icon(Icons.Default.BluetoothConnected, null, tint = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun KmChip(text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun Dot(color: Color) = Box(Modifier.size(10.dp).clip(CircleShape).background(color))

@Composable
private fun TripCard(t: Trip, selMode: Boolean, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(14.dp)) {
            if (selMode) {
                Icon(if (selected) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank, null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(colorForDevice(t.deviceAddress)); Spacer(Modifier.width(8.dp))
                    Text(t.deviceName, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    KmChip(Fmt.km(t.distanceMeters))
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    Endpoint("INÍCIO", Fmt.time(t.startTime), t.startAddress, Fmt.odometer(t.odometerStart), Modifier.weight(1f))
                    Icon(Icons.Default.ArrowForward, null, Modifier.padding(top = 16.dp).size(18.dp),
                        tint = MaterialTheme.colorScheme.outlineVariant)
                    Endpoint("FIM", t.endTime?.let { Fmt.time(it) } ?: "—", t.endAddress, Fmt.odometer(t.odometerEnd), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Endpoint(label: String, time: String, place: String?, odo: String, mod: Modifier) {
    Column(mod) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Text(time, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.titleMedium)
        if (place != null) Text(place, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Od. $odo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun GroupCard(g: TripGroup, onOpen: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(colorForDevice(g.first.deviceAddress)); Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(Fmt.longDay(g.first.startTime), fontWeight = FontWeight.Medium)
                    Text("${g.device} · ${g.trips.size} trajeto${if (g.trips.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                KmChip(Fmt.km(g.totalMeters))
            }
            Spacer(Modifier.height(12.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text("INÍCIO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(Fmt.time(g.first.startTime), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.titleMedium)
                    Text("Od. ${Fmt.odometer(g.startOdometer)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                Icon(Icons.Default.ArrowForward, null, Modifier.padding(top = 16.dp).size(18.dp), tint = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.weight(1f)) {
                    Text("FIM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text(g.last.endTime?.let { Fmt.time(it) } ?: "—", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.titleMedium)
                    Text("Od. ${Fmt.odometer(g.endOdometer)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            TextButton({ expanded = !expanded }, Modifier.fillMaxWidth()) {
                Text(if (expanded) "Ocultar trajetos" else "Ver trajetos")
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            if (expanded) g.trips.forEach { t ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(t.id) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Route, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(10.dp))
                    Text("${Fmt.time(t.startTime)}–${t.endTime?.let { Fmt.time(it) } ?: "—"}",
                        Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Fmt.km(t.distanceMeters), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
