package com.robomanipal.imusensor.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Thin wrapper around [SensorManager].
 * Registers listeners for accel / gyro / mag / rotation-vector and publishes
 * every reading as a [SharedFlow] so consumers can collect at their own pace.
 */
class SensorRepository(context: Context) {

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // ── Flows ──────────────────────────────────────────────────────────
    private val _accel = MutableSharedFlow<SensorReading>(replay = 1, extraBufferCapacity = 64)
    val accelerometer: SharedFlow<SensorReading> = _accel

    private val _gyro = MutableSharedFlow<SensorReading>(replay = 1, extraBufferCapacity = 64)
    val gyroscope: SharedFlow<SensorReading> = _gyro

    private val _mag = MutableSharedFlow<SensorReading>(replay = 1, extraBufferCapacity = 64)
    val magnetometer: SharedFlow<SensorReading> = _mag

    private val _orient = MutableSharedFlow<OrientationData>(replay = 1, extraBufferCapacity = 64)
    val orientation: SharedFlow<OrientationData> = _orient

    // ── Availability flags ─────────────────────────────────────────────
    val hasAccelerometer: Boolean get() = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    val hasGyroscope: Boolean     get() = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
    val hasMagnetometer: Boolean  get() = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null
    val hasRotationVector: Boolean get() = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null

    // ── Listeners ──────────────────────────────────────────────────────
    private val accelListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            _accel.tryEmit(SensorReading(e.timestamp, e.values[0], e.values[1], e.values[2], e.accuracy))
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    private val gyroListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            _gyro.tryEmit(SensorReading(e.timestamp, e.values[0], e.values[1], e.values[2], e.accuracy))
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    private val magListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            _mag.tryEmit(SensorReading(e.timestamp, e.values[0], e.values[1], e.values[2], e.accuracy))
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    private val rotVecListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            // Quaternion from rotation vector
            val quat = FloatArray(4)
            SensorManager.getQuaternionFromVector(quat, e.values)

            // Rotation matrix (3×3)
            val rm = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rm, e.values)

            // Euler angles (radians → degrees)
            val ori = FloatArray(3)
            SensorManager.getOrientation(rm, ori)

            val yawDeg   = Math.toDegrees(ori[0].toDouble()).toFloat()
            val pitchDeg = Math.toDegrees(ori[1].toDouble()).toFloat()
            val rollDeg  = Math.toDegrees(ori[2].toDouble()).toFloat()

            _orient.tryEmit(
                OrientationData(
                    quaternion     = quat,
                    euler          = floatArrayOf(yawDeg, pitchDeg, rollDeg),
                    rotationMatrix = rm,
                    timestamp      = e.timestamp,
                )
            )
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    // ── Start / Stop ───────────────────────────────────────────────────
    fun startListening(rate: Int = SensorManager.SENSOR_DELAY_GAME) {
        sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sm.registerListener(accelListener, it, rate)
        }
        sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sm.registerListener(gyroListener, it, rate)
        }
        sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.let {
            sm.registerListener(magListener, it, rate)
        }
        sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            sm.registerListener(rotVecListener, it, rate)
        }
    }

    fun stopListening() {
        sm.unregisterListener(accelListener)
        sm.unregisterListener(gyroListener)
        sm.unregisterListener(magListener)
        sm.unregisterListener(rotVecListener)
    }
}
