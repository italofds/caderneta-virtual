package com.caderneta.virtual.service

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.caderneta.virtual.CadernetaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires on every Bluetooth ACL connect/disconnect (works even when the app was
 * killed — the OS instantiates this receiver). When the connected/disconnected
 * device is one the user linked, it starts or stops [TripRecordingService].
 *
 * This is the mechanism that makes recording start "by itself" the moment the
 * phone pairs with the car's Bluetooth.
 */
class BluetoothConnectionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        // Re-arm after reboot: the manifest-registered receiver is enough to catch
        // future ACL events, so nothing else is required here.
        if (action == Intent.ACTION_BOOT_COMPLETED) return

        val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else
            @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        device ?: return

        val address = device.address ?: return
        val repo = (context.applicationContext as CadernetaApp).repository
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val linked = repo.findDevice(address)?.takeIf { it.enabled } ?: return@launch
                when (action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        val start = Intent(context, TripRecordingService::class.java).apply {
                            this.action = TripRecordingService.ACTION_START
                            putExtra(TripRecordingService.EXTRA_ADDRESS, linked.address)
                            putExtra(TripRecordingService.EXTRA_NAME, linked.name)
                        }
                        runCatching { ContextCompat.startForegroundService(context, start) }
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        val stop = Intent(context, TripRecordingService::class.java).apply {
                            this.action = TripRecordingService.ACTION_STOP
                            putExtra(TripRecordingService.EXTRA_ADDRESS, linked.address)
                        }
                        runCatching { ContextCompat.startForegroundService(context, stop) }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
