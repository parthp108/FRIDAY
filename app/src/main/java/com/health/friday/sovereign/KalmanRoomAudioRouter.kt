package com.health.friday.sovereign

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.math.pow

enum class ActiveAudioTarget {
    PHONE_SPEAKER,
    USER_EARBUDS,
    ROOM_SATELLITE_SPEAKER
}

class KalmanRoomAudioRouter(context: Context) {

    companion object {
        private const val TAG = "FRIDAY_AUDIO_HANDOFF"
        private const val PROCESS_NOISE_Q = 0.08
        private const val MEASUREMENT_NOISE_R = 6.5
        private const val RSSI_AT_ONE_METER = -59.0
        private const val PATH_LOSS_EXPONENT_N = 2.7
        private const val ENTER_ROOM_METERS = 2.5
        private const val EXIT_ROOM_METERS = 4.2

        fun chooseTarget(inRoom: Boolean, hasWyoming: Boolean, hasEarbuds: Boolean): ActiveAudioTarget {
            if (inRoom && hasWyoming) return ActiveAudioTarget.ROOM_SATELLITE_SPEAKER
            if (hasEarbuds) return ActiveAudioTarget.USER_EARBUDS
            return ActiveAudioTarget.PHONE_SPEAKER
        }
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var kalmanRssiEstimate = -75.0
    private var kalmanErrorCovariance = 4.0
    private var initialized = false

    var isInsideRoomZone: Boolean = false
        private set

    @Synchronized
    fun updateRoomAnchorRssi(rawRssiDbm: Int): Double {
        val z = rawRssiDbm.toDouble()
        if (!initialized) {
            kalmanRssiEstimate = z
            kalmanErrorCovariance = 1.0
            initialized = true
        } else {
            val priorCovariance = kalmanErrorCovariance + PROCESS_NOISE_Q
            val kalmanGain = priorCovariance / (priorCovariance + MEASUREMENT_NOISE_R)
            kalmanRssiEstimate += kalmanGain * (z - kalmanRssiEstimate)
            kalmanErrorCovariance = (1.0 - kalmanGain) * priorCovariance
        }

        val estimatedMeters = 10.0.pow((RSSI_AT_ONE_METER - kalmanRssiEstimate) / (10.0 * PATH_LOSS_EXPONENT_N))

        if (!isInsideRoomZone && estimatedMeters <= ENTER_ROOM_METERS) {
            isInsideRoomZone = true
            Log.i(TAG, "Entered room zone (d=${"%.2f".format(estimatedMeters)}m, RSSI=${"%.1f".format(kalmanRssiEstimate)}dBm)")
        } else if (isInsideRoomZone && estimatedMeters >= EXIT_ROOM_METERS) {
            isInsideRoomZone = false
            Log.i(TAG, "Exited room zone (d=${"%.2f".format(estimatedMeters)}m, RSSI=${"%.1f".format(kalmanRssiEstimate)}dBm)")
        }

        return estimatedMeters
    }

    fun selectOptimalAudioRoute(wyomingSatelliteNode: DiscoveredMeshNode?): ActiveAudioTarget {
        if (isInsideRoomZone && wyomingSatelliteNode != null) {
            return ActiveAudioTarget.ROOM_SATELLITE_SPEAKER
        }

        val connectedDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val earbuds = connectedDevices.firstOrNull { dev ->
            dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }

        if (earbuds != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { audioManager.setCommunicationDevice(earbuds) }
            }
            return ActiveAudioTarget.USER_EARBUDS
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { audioManager.clearCommunicationDevice() }
        }
        return ActiveAudioTarget.PHONE_SPEAKER
    }

    suspend fun streamPcmToWyomingSpeaker(
        node: DiscoveredMeshNode,
        pcmBytes: ByteArray,
        sampleRate: Int = 22050,
        channels: Int = 1
    ): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(node.hostAddress, node.port), 1500)
                val out: OutputStream = socket.getOutputStream()

                val startHeader = JSONObject().apply {
                    put("type", "audio-start")
                    put("data", JSONObject().apply {
                        put("rate", sampleRate)
                        put("width", 2)
                        put("channels", channels)
                    })
                }.toString() + "\n"
                out.write(startHeader.toByteArray(Charsets.UTF_8))

                val chunkHeader = JSONObject().apply {
                    put("type", "audio-chunk")
                    put("data", JSONObject().apply {
                        put("rate", sampleRate)
                        put("width", 2)
                        put("channels", channels)
                    })
                    put("payload_length", pcmBytes.size)
                }.toString() + "\n"
                out.write(chunkHeader.toByteArray(Charsets.UTF_8))
                out.write(pcmBytes)

                val stopHeader = JSONObject().apply {
                    put("type", "audio-stop")
                }.toString() + "\n"
                out.write(stopHeader.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Wyoming TCP audio hand-off failed; falling back to local TTS", e)
            false
        }
    }
}
