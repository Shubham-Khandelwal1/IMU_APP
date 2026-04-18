package com.robomanipal.imusensor.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.robomanipal.imusensor.IMUApplication
import com.robomanipal.imusensor.sensor.OrientationData
import com.robomanipal.imusensor.sensor.SensorReading
import com.robomanipal.imusensor.streaming.StreamConfig
import com.robomanipal.imusensor.streaming.StreamFormat
import com.robomanipal.imusensor.streaming.UDPStreamer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Manages the UDP streaming lifecycle.
 * Reads latest sensor values from the shared [SensorRepository] and pushes
 * them through [UDPStreamer] at the configured rate.
 */
class StreamViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as IMUApplication).sensorRepository
    private val streamer = UDPStreamer()

    private val _config = MutableStateFlow(StreamConfig())
    val config: StateFlow<StreamConfig> = _config.asStateFlow()

    private val _packetsSent = MutableStateFlow(0L)
    val packetsSent: StateFlow<Long> = _packetsSent.asStateFlow()

    private val _errors = MutableStateFlow(0L)
    val errors: StateFlow<Long> = _errors.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private var streamJob: Job? = null

    // Latest sensor values (collected separately for streaming)
    private var latestAccel: SensorReading? = null
    private var latestGyro: SensorReading? = null
    private var latestMag: SensorReading? = null
    private var latestOrient: OrientationData? = null

    init {
        // Continuously track latest values even when not streaming
        viewModelScope.launch { repo.accelerometer.collect { latestAccel = it } }
        viewModelScope.launch { repo.gyroscope.collect { latestGyro = it } }
        viewModelScope.launch { repo.magnetometer.collect { latestMag = it } }
        viewModelScope.launch { repo.orientation.collect { latestOrient = it } }
    }

    fun updateConfig(config: StreamConfig) {
        _config.value = config
    }

    fun updateIp(ip: String) {
        _config.value = _config.value.copy(targetIp = ip)
    }

    fun updatePort(port: Int) {
        _config.value = _config.value.copy(port = port)
    }

    fun updateFormat(format: StreamFormat) {
        _config.value = _config.value.copy(format = format)
    }

    fun updateSampleRate(hz: Int) {
        _config.value = _config.value.copy(sampleRateHz = hz)
    }

    fun toggleStreaming() {
        if (_isStreaming.value) stopStreaming() else startStreaming()
    }

    private fun startStreaming() {
        val cfg = _config.value
        streamJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                streamer.open(cfg)
                _isStreaming.value = true
                val intervalMs = 1000L / cfg.sampleRateHz
                while (isActive) {
                    streamer.send(latestAccel, latestGyro, latestMag, latestOrient)
                    _packetsSent.value = streamer.packetsSent
                    _errors.value = streamer.errors
                    delay(intervalMs)
                }
            } catch (_: Exception) {
                _errors.value++
            } finally {
                streamer.close()
                _isStreaming.value = false
            }
        }
    }

    private fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
        streamer.close()
        _isStreaming.value = false
    }

    override fun onCleared() {
        stopStreaming()
    }
}
