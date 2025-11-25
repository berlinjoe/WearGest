package com.example.mymouse.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import java.util.concurrent.Executor

@SuppressLint("MissingPermission")
class BluetoothHidHelper(private val context: Context, private val onStateChanged: (String) -> Unit) {

    private var bluetoothHidDevice: BluetoothHidDevice? = null
    private var hostDevice: BluetoothDevice? = null

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter

    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                bluetoothHidDevice = proxy as BluetoothHidDevice
                registerApp()
                onStateChanged("Service Connected")
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                bluetoothHidDevice = null
                onStateChanged("Service Disconnected")
            }
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            onStateChanged("App Status: Registered=$registered")
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    hostDevice = device
                    onStateChanged("Connected to ${device?.name}")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    hostDevice = null
                    onStateChanged("Disconnected")
                }
                BluetoothProfile.STATE_CONNECTING -> onStateChanged("Connecting...")
            }
        }
    }

    fun init() {
        bluetoothAdapter?.getProfileProxy(context, serviceListener, BluetoothProfile.HID_DEVICE)
    }

    fun cleanup() {
        bluetoothHidDevice?.unregisterApp()
        bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, bluetoothHidDevice)
    }

    private fun registerApp() {
        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            "Wear Mouse",
            "Wear OS Mouse",
            "Google",
            BluetoothHidDevice.SUBCLASS1_MOUSE,
            MOUSE_REPORT_DESCRIPTOR
        )

        val qosSettings = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800,
            9,
            0,
            11250,
            BluetoothHidDeviceAppQosSettings.MAX
        )

        bluetoothHidDevice?.registerApp(
            sdpSettings,
            null,
            qosSettings,
            Executor { it.run() },
            callback
        )
    }

    fun sendMouseReport(dx: Int, dy: Int, leftBtn: Boolean, rightBtn: Boolean) {
        val device = hostDevice ?: return
        val buttons = (if (leftBtn) 1 else 0) or (if (rightBtn) 2 else 0)
        
        // Clamp values to byte range -127 to 127
        val clampedDx = dx.coerceIn(-127, 127)
        val clampedDy = dy.coerceIn(-127, 127)

        val report = ByteArray(4)
        report[0] = buttons.toByte()
        report[1] = clampedDx.toByte()
        report[2] = clampedDy.toByte()
        report[3] = 0 // Scroll

        bluetoothHidDevice?.sendReport(device, 0, report)
    }

    companion object {
        // Standard Mouse Report Descriptor
        private val MOUSE_REPORT_DESCRIPTOR = byteArrayOf(
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x02.toByte(), // Usage (Mouse)
            0xA1.toByte(), 0x01.toByte(), // Collection (Application)
            0x09.toByte(), 0x01.toByte(), //   Usage (Pointer)
            0xA1.toByte(), 0x00.toByte(), //   Collection (Physical)
            0x05.toByte(), 0x09.toByte(), //     Usage Page (Button)
            0x19.toByte(), 0x01.toByte(), //     Usage Minimum (1)
            0x29.toByte(), 0x03.toByte(), //     Usage Maximum (3)
            0x15.toByte(), 0x00.toByte(), //     Logical Minimum (0)
            0x25.toByte(), 0x01.toByte(), //     Logical Maximum (1)
            0x95.toByte(), 0x03.toByte(), //     Report Count (3)
            0x75.toByte(), 0x01.toByte(), //     Report Size (1)
            0x81.toByte(), 0x02.toByte(), //     Input (Data, Variable, Absolute)
            0x95.toByte(), 0x01.toByte(), //     Report Count (1)
            0x75.toByte(), 0x05.toByte(), //     Report Size (5)
            0x81.toByte(), 0x03.toByte(), //     Input (Constant) for padding
            0x05.toByte(), 0x01.toByte(), //     Usage Page (Generic Desktop)
            0x09.toByte(), 0x30.toByte(), //     Usage (X)
            0x09.toByte(), 0x31.toByte(), //     Usage (Y)
            0x09.toByte(), 0x38.toByte(), //     Usage (Wheel)
            0x15.toByte(), 0x81.toByte(), //     Logical Minimum (-127)
            0x25.toByte(), 0x7F.toByte(), //     Logical Maximum (127)
            0x75.toByte(), 0x08.toByte(), //     Report Size (8)
            0x95.toByte(), 0x03.toByte(), //     Report Count (3)
            0x81.toByte(), 0x06.toByte(), //     Input (Data, Variable, Relative)
            0xC0.toByte(),                //   End Collection
            0xC0.toByte()                 // End Collection
        )
    }
}
