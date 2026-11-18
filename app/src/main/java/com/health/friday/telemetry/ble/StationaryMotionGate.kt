package com.health.friday.telemetry.ble

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.util.concurrent.ConcurrentLinkedDeque

class StationaryMotionGate(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val stepTimestampsMs = ConcurrentLinkedDeque<Long>()

    fun startMonitoring() {
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stopMonitoring() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_DETECTOR) {
            val now = System.currentTimeMillis()
            stepTimestampsMs.addLast(now)
            pruneOldSteps(now)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * Returns true if the user has taken fewer than 6 steps in the last 90 seconds.
     */
    fun isCurrentlyStationary(nowMs: Long = System.currentTimeMillis()): Boolean {
        pruneOldSteps(nowMs)
        return stepTimestampsMs.size < 6
    }

    private fun pruneOldSteps(nowMs: Long) {
        val cutoff = nowMs - 90_000L
        while (true) {
            val head = stepTimestampsMs.peekFirst() ?: break
            if (head < cutoff) {
                stepTimestampsMs.pollFirst()
            } else {
                break
            }
        }
    }
}
