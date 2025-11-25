package com.example.mymouse.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.roundToInt

class SensorHelper(context: Context, private val onMove: (Int, Int) -> Unit, private val onTilt: (Float, Float) -> Unit) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private var accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    
    // Sensitivity factor
    private val sensitivity = 15.0f

    fun start() {
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            // Gyroscope values are in rad/s
            // event.values[0]: x-axis (Pitch) -> Mouse Y
            // event.values[1]: y-axis (Roll) 
            // event.values[2]: z-axis (Yaw) -> Mouse X

            // Mapping:
            // Rotate wrist around Z (Yaw) -> Mouse X
            // Rotate wrist around X (Pitch) -> Mouse Y
            
            // Note: Directions might need inversion depending on preference
            val deltaX = (-event.values[2] * sensitivity).roundToInt()
            val deltaY = (-event.values[0] * sensitivity).roundToInt()

            if (deltaX != 0 || deltaY != 0) {
                onMove(deltaX, deltaY)
            }
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            // Accelerometer values (m/s^2) including gravity
            // values[0]: x-axis
            // values[1]: y-axis
            onTilt(event.values[0], event.values[1])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
