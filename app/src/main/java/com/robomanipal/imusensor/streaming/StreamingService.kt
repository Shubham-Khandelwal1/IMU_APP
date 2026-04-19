package com.robomanipal.imusensor.streaming

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.robomanipal.imusensor.IMUApplication
import com.robomanipal.imusensor.MainActivity
import com.robomanipal.imusensor.sensor.SensorRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground Service that keeps UDP streaming alive when the screen turns off.
 *
 * Shows a persistent notification with live packet count and a stop action.
 * The service owns its own coroutine scope — completely independent of the
 * Activity lifecycle.
 */
class StreamingService : Service() {

    companion object {
        private const val CHANNEL_ID   = "imu_streaming"
        private const val NOTIF_ID     = 1001
        private const val ACTION_STOP  = "com.robomanipal.imusensor.STOP_STREAMING"

        const val EXTRA_IP     = "ip"
        const val EXTRA_PORT   = "port"
        const val EXTRA_RATE   = "rate"
        const val EXTRA_FORMAT = "format"

        // ── Shared observable state (read by ViewModel) ─────────────
        private val _isRunning   = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _packetsSent = MutableStateFlow(0L)
        val packetsSent: StateFlow<Long> = _packetsSent.asStateFlow()

        private val _errors      = MutableStateFlow(0L)
        val errors: StateFlow<Long> = _errors.asStateFlow()

        fun start(context: Context, config: StreamConfig) {
            val intent = Intent(context, StreamingService::class.java).apply {
                putExtra(EXTRA_IP,     config.targetIp)
                putExtra(EXTRA_PORT,   config.port)
                putExtra(EXTRA_RATE,   config.sampleRateHz)
                putExtra(EXTRA_FORMAT, config.format.name)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StreamingService::class.java))
        }
    }

    private var scope: CoroutineScope? = null
    private val streamer = UDPStreamer()
    private lateinit var repo: SensorRepository
    private var isStreamingActive = false

    override fun onCreate() {
        super.onCreate()
        repo = (application as IMUApplication).sensorRepository
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Prevent duplicate starts
        if (isStreamingActive) return START_NOT_STICKY

        val ip     = intent?.getStringExtra(EXTRA_IP)     ?: "192.168.1.100"
        val port   = intent?.getIntExtra(EXTRA_PORT, 8765) ?: 8765
        val rateHz = intent?.getIntExtra(EXTRA_RATE, 100)  ?: 100
        val format = try {
            StreamFormat.valueOf(intent?.getStringExtra(EXTRA_FORMAT) ?: "JSON")
        } catch (_: Exception) { StreamFormat.JSON }

        val config = StreamConfig(targetIp = ip, port = port, sampleRateHz = rateHz, format = format)

        // Go foreground — API 34+ requires the service type parameter
        val notification = buildNotification(0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        isStreamingActive = true
        _isRunning.value   = true
        _packetsSent.value = 0L
        _errors.value      = 0L

        // Ensure sensors are running
        repo.startListening()

        // Fresh scope for each start
        scope?.cancel()
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = newScope

        // Collect latest sensor values
        var latestAccel = repo.accelerometer.replayCache.firstOrNull()
        var latestGyro  = repo.gyroscope.replayCache.firstOrNull()
        var latestMag   = repo.magnetometer.replayCache.firstOrNull()
        var latestOrient = repo.orientation.replayCache.firstOrNull()

        newScope.launch { repo.accelerometer.collect { latestAccel = it } }
        newScope.launch { repo.gyroscope.collect { latestGyro = it } }
        newScope.launch { repo.magnetometer.collect { latestMag = it } }
        newScope.launch { repo.orientation.collect { latestOrient = it } }

        // Streaming loop
        newScope.launch {
            try {
                streamer.open(config)
                val intervalMs = 1000L / config.sampleRateHz
                var lastNotifUpdate = 0L

                while (isActive) {
                    streamer.send(latestAccel, latestGyro, latestMag, latestOrient)
                    _packetsSent.value = streamer.packetsSent
                    _errors.value      = streamer.errors

                    // Update notification every ~2 seconds
                    val now = System.currentTimeMillis()
                    if (now - lastNotifUpdate > 2000) {
                        lastNotifUpdate = now
                        runCatching {
                            val nm = getSystemService(NotificationManager::class.java)
                            nm.notify(NOTIF_ID, buildNotification(streamer.packetsSent))
                        }
                    }

                    delay(intervalMs)
                }
            } catch (_: Exception) {
                _errors.value++
            } finally {
                streamer.close()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isStreamingActive = false
        scope?.cancel()
        scope = null
        streamer.close()
        _isRunning.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Notification helpers ───────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "IMU Streaming",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows when IMU data is being streamed over UDP"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(packets: Long): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, StreamingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentTitle("IMU Streaming")
            .setContentText("$packets packets sent")
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .setSilent(true)
            .build()
    }
}
