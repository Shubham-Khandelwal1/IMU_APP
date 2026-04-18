package com.robomanipal.imusensor

import android.app.Application
import com.robomanipal.imusensor.sensor.SensorRepository

/**
 * Application-level singleton so [SensorRepository] is shared
 * between the sensor ViewModel and the stream ViewModel.
 */
class IMUApplication : Application() {
    val sensorRepository by lazy { SensorRepository(this) }
}
