package com.robomanipal.imusensor.viewmodel

import android.app.Application
import android.hardware.SensorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.robomanipal.imusensor.IMUApplication
import com.robomanipal.imusensor.sensor.OrientationData
import com.robomanipal.imusensor.sensor.SensorReading
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Central ViewModel that collects every sensor flow from [SensorRepository]
 * and maintains rolling buffers for the charts + latest values for the UI cards.
 */
class SensorViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as IMUApplication).sensorRepository

    // ── Configuration ──────────────────────────────────────────────────
    companion object {
        const val BUFFER_SIZE = 200
    }

    // ── Sensor availability ────────────────────────────────────────────
    val hasAccelerometer  = repo.hasAccelerometer
    val hasGyroscope      = repo.hasGyroscope
    val hasMagnetometer   = repo.hasMagnetometer
    val hasRotationVector = repo.hasRotationVector

    // ── Latest values ──────────────────────────────────────────────────
    private val _latestAccel  = MutableStateFlow(SensorReading(0, 0f, 0f, 0f))
    val latestAccel: StateFlow<SensorReading> = _latestAccel.asStateFlow()

    private val _latestGyro   = MutableStateFlow(SensorReading(0, 0f, 0f, 0f))
    val latestGyro: StateFlow<SensorReading> = _latestGyro.asStateFlow()

    private val _latestMag    = MutableStateFlow(SensorReading(0, 0f, 0f, 0f))
    val latestMag: StateFlow<SensorReading> = _latestMag.asStateFlow()

    private val _orientation  = MutableStateFlow(OrientationData())
    val orientation: StateFlow<OrientationData> = _orientation.asStateFlow()

    // ── Rolling chart buffers ──────────────────────────────────────────
    private val _accelBuffer = mutableListOf<SensorReading>()
    private val _accelFlow   = MutableStateFlow<List<SensorReading>>(emptyList())
    val accelBuffer: StateFlow<List<SensorReading>> = _accelFlow.asStateFlow()

    private val _gyroBuffer  = mutableListOf<SensorReading>()
    private val _gyroFlow    = MutableStateFlow<List<SensorReading>>(emptyList())
    val gyroBuffer: StateFlow<List<SensorReading>> = _gyroFlow.asStateFlow()

    private val _magBuffer   = mutableListOf<SensorReading>()
    private val _magFlow     = MutableStateFlow<List<SensorReading>>(emptyList())
    val magBuffer: StateFlow<List<SensorReading>> = _magFlow.asStateFlow()

    // ── Sample rate tracking ───────────────────────────────────────────
    private val _sampleRateHz = MutableStateFlow(0f)
    val sampleRateHz: StateFlow<Float> = _sampleRateHz.asStateFlow()
    private var lastAccelTimestamp = 0L
    private var sampleCount = 0
    private var sampleWindowStart = System.nanoTime()

    // ── Sensor delay setting ───────────────────────────────────────────
    private val _sensorDelay = MutableStateFlow(SensorManager.SENSOR_DELAY_GAME)
    val sensorDelay: StateFlow<Int> = _sensorDelay.asStateFlow()

    init {
        startSensors()
        collectFlows()
    }

    private fun startSensors() {
        repo.startListening(_sensorDelay.value)
    }

    fun updateSensorDelay(delay: Int) {
        _sensorDelay.value = delay
        repo.stopListening()
        repo.startListening(delay)
    }

    private fun collectFlows() {
        viewModelScope.launch {
            repo.accelerometer.collect { r ->
                _latestAccel.value = r
                addToBuffer(_accelBuffer, r, _accelFlow)
                trackSampleRate(r.timestamp)
            }
        }
        viewModelScope.launch {
            repo.gyroscope.collect { r ->
                _latestGyro.value = r
                addToBuffer(_gyroBuffer, r, _gyroFlow)
            }
        }
        viewModelScope.launch {
            repo.magnetometer.collect { r ->
                _latestMag.value = r
                addToBuffer(_magBuffer, r, _magFlow)
            }
        }
        viewModelScope.launch {
            repo.orientation.collect { o ->
                _orientation.value = o
            }
        }
    }

    private fun addToBuffer(
        buf: MutableList<SensorReading>,
        r: SensorReading,
        flow: MutableStateFlow<List<SensorReading>>,
    ) {
        if (buf.size >= BUFFER_SIZE) buf.removeAt(0)
        buf.add(r)
        flow.value = buf.toList()  // snapshot for Compose
    }

    private fun trackSampleRate(timestamp: Long) {
        sampleCount++
        val now = System.nanoTime()
        val elapsed = (now - sampleWindowStart) / 1_000_000_000.0
        if (elapsed >= 1.0) {
            _sampleRateHz.value = (sampleCount / elapsed).toFloat()
            sampleCount = 0
            sampleWindowStart = now
        }
    }

    override fun onCleared() {
        repo.stopListening()
    }
}
