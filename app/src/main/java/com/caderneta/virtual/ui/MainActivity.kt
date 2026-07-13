package com.caderneta.virtual.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.caderneta.virtual.CadernetaApp
import com.caderneta.virtual.data.db.LinkedDevice
import com.caderneta.virtual.data.db.TrackPoint
import com.caderneta.virtual.data.db.Trip
import com.caderneta.virtual.ui.screens.*
import com.caderneta.virtual.ui.theme.CadernetaTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.flow.Flow

/** Bundle passed to the detail screen so it can observe one trip + its route. */
class TripDetailData(val trip: Flow<Trip?>, val points: Flow<List<TrackPoint>>)

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CadernetaTheme {
                val vm: MainViewModel = viewModel()
                val repo = (application as CadernetaApp).repository

                // ── Runtime permissions ──
                val perms = buildList {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                    add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
                }
                val permState = rememberMultiplePermissionsState(perms)
                LaunchedEffect(Unit) { if (!permState.allPermissionsGranted) permState.launchMultiplePermissionRequest() }

                // Background location must be requested on its own screen, AFTER the
                // foreground location grant — otherwise the system silently denies it,
                // and without it the service cannot track once the app is backgrounded.
                val bgLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    rememberPermissionState(Manifest.permission.ACCESS_BACKGROUND_LOCATION) else null
                LaunchedEffect(permState.allPermissionsGranted) {
                    if (permState.allPermissionsGranted && bgLocation != null && !bgLocation.status.isGranted) {
                        bgLocation.launchPermissionRequest()
                    }
                }

                // Bonded devices from the OS (needs BLUETOOTH_CONNECT on 12+).
                val bonded = remember { mutableStateOf(emptyList<LinkedDevice>()) }
                LaunchedEffect(permState.allPermissionsGranted) {
                    bonded.value = readBondedDevices(this@MainActivity)
                }

                // Decide onboarding ONCE, from persisted state — not from the devices
                // flow, whose initial emission is an empty list and would otherwise force
                // onboarding on every launch even when a vehicle is already linked.
                var onboardingDone by remember { mutableStateOf<Boolean?>(null) }
                LaunchedEffect(Unit) { onboardingDone = repo.hasLinkedDevices() }

                val nav = rememberNavController()
                if (onboardingDone == false) {
                    OnboardingScreen(vm, bonded.value) { onboardingDone = true }
                } else if (onboardingDone == true) {
                    NavHost(nav, startDestination = "list") {
                        composable("list") {
                            TripListScreen(
                                vm,
                                onOpen = { nav.navigate("detail/$it") },
                                onSettings = { nav.navigate("settings") },
                                onBatch = { nav.navigate("batch") },
                            )
                        }
                        composable("detail/{id}") { entry ->
                            val id = entry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                            TripDetailScreen(vm, id,
                                TripDetailData(repo.observeTrip(id), repo.observePoints(id))) { nav.popBackStack() }
                        }
                        composable("batch") { BatchOdometerScreen(vm) { nav.popBackStack() } }
                        composable("settings") { SettingsScreen(vm, bonded.value) { nav.popBackStack() } }
                    }
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun readBondedDevices(context: Context): List<LinkedDevice> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
    ) return emptyList()
    val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return emptyList()
    val adapter = mgr.adapter ?: return emptyList()
    return try {
        adapter.bondedDevices.orEmpty().map { device ->
            val label = deviceLabel(device)
            LinkedDevice(device.address, label, enabled = false)
        }
    } catch (_: SecurityException) { emptyList() }
}

/** User-defined alias (o apelido dado nas configurações do Android) quando
 *  disponível — API 30+ —, caindo para o nome original em versões anteriores. */
@SuppressLint("MissingPermission")
private fun deviceLabel(device: android.bluetooth.BluetoothDevice): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            device.alias?.takeIf { it.isNotBlank() }?.let { return it }
        } catch (_: SecurityException) { /* sem permissão — usa o nome */ }
    }
    return device.name ?: device.address
}
