package com.health.friday.telemetry.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID

@SuppressLint("MissingPermission")
class WearableBleGattClient(
    private val context: Context,
    private val motionGate: StationaryMotionGate = StationaryMotionGate(context),
    private val hrvAnalyzer: SlidingWindowHrvAnalyzer = SlidingWindowHrvAnalyzer()
) {
    companion object {
        private const val TAG = "FRIDAY_BLE_GATT"
        val HEART_RATE_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HEART_RATE_MEASUREMENT_CHAR_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCCD_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private var activeGatt: BluetoothGatt? = null

    private val _stressEvents = MutableSharedFlow<BiometricStressSnapshot>(extraBufferCapacity = 16)
    val stressEvents: SharedFlow<BiometricStressSnapshot> = _stressEvents.asSharedFlow()

    fun connectToWearable(macAddress: String) {
        motionGate.startMonitoring()
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter ?: return
        val device = adapter.getRemoteDevice(macAddress)

        activeGatt = device.connectGatt(context, true, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        motionGate.stopMonitoring()
        activeGatt?.disconnect()
        activeGatt?.close()
        activeGatt = null
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to wearable GATT server. Discovering services...")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "Wearable disconnected; autoConnect waiting for range.")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val hrService = gatt.getService(HEART_RATE_SERVICE_UUID) ?: return
            val hrChar = hrService.getCharacteristic(HEART_RATE_MEASUREMENT_CHAR_UUID) ?: return

            gatt.setCharacteristicNotification(hrChar, true)

            val cccd = hrChar.getDescriptor(CCCD_DESCRIPTOR_UUID) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(cccd)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == HEART_RATE_MEASUREMENT_CHAR_UUID) {
                processIncomingGattBytes(value)
            }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
                characteristic.uuid == HEART_RATE_MEASUREMENT_CHAR_UUID
            ) {
                @Suppress("DEPRECATION")
                processIncomingGattBytes(characteristic.value ?: return)
            }
        }
    }

    fun processIncomingGattBytes(rawBytes: ByteArray) {
        val packet = BiometricPacketParser.parseHeartRateMeasurement(rawBytes) ?: return
        val stationary = motionGate.isCurrentlyStationary(packet.timestampMs)
        val snapshot = hrvAnalyzer.ingestPacket(packet, stationary) ?: return

        _stressEvents.tryEmit(snapshot)
    }
}
