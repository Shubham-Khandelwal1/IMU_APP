package com.robomanipal.imusensor.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.robomanipal.imusensor.streaming.StreamConfig
import com.robomanipal.imusensor.streaming.StreamFormat
import com.robomanipal.imusensor.streaming.StreamingService
import kotlinx.coroutines.flow.*

/**
 * Manages the UDP streaming lifecycle via [StreamingService].
 *
 * Instead of running a coroutine inside the ViewModel (which dies when
 * the screen turns off), this delegates to a Foreground Service that
 * keeps streaming alive independently of the Activity lifecycle.
 *
 * State is observed from the service's companion StateFlows.
 */
class StreamViewModel(application: Application) : AndroidViewModel(application) {

    private val _config = MutableStateFlow(StreamConfig())
    val config: StateFlow<StreamConfig> = _config.asStateFlow()

    /** Observed directly from the service singleton. */
    val isStreaming: StateFlow<Boolean> = StreamingService.isRunning
    val packetsSent: StateFlow<Long>   = StreamingService.packetsSent
    val errors: StateFlow<Long>        = StreamingService.errors

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
        val context = getApplication<Application>()
        if (isStreaming.value) {
            StreamingService.stop(context)
        } else {
            StreamingService.start(context, _config.value)
        }
    }

    override fun onCleared() {
        // Don't stop the service — it should keep streaming even if the
        // user navigates away. They stop via button or notification.
    }
}
