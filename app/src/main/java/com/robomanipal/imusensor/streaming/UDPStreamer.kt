package com.robomanipal.imusensor.streaming

import com.robomanipal.imusensor.sensor.OrientationData
import com.robomanipal.imusensor.sensor.SensorReading
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Sends sensor data over UDP as CSV or JSON packets.
 * All networking runs on [Dispatchers.IO].
 */
class UDPStreamer {

    private var socket: DatagramSocket? = null
    private var address: InetAddress? = null
    private var port: Int = 8765
    private var format: StreamFormat = StreamFormat.JSON

    var packetsSent: Long = 0L
        private set
    var errors: Long = 0L
        private set

    fun open(config: StreamConfig) {
        close()
        address = InetAddress.getByName(config.targetIp)
        port = config.port
        format = config.format
        socket = DatagramSocket()
        packetsSent = 0L
        errors = 0L
    }

    fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    /**
     * Format a complete sensor snapshot and send it over UDP.
     */
    fun send(
        accel: SensorReading?,
        gyro: SensorReading?,
        mag: SensorReading?,
        orient: OrientationData?,
    ) {
        val s = socket ?: return
        val a = address ?: return
        val payload = when (format) {
            StreamFormat.CSV  -> buildCsvLine(accel, gyro, mag, orient)
            StreamFormat.JSON -> buildJsonLine(accel, gyro, mag, orient)
        }
        try {
            val bytes = payload.toByteArray(Charsets.UTF_8)
            s.send(DatagramPacket(bytes, bytes.size, a, port))
            packetsSent++
        } catch (_: Exception) {
            errors++
        }
    }

    // ── Formatters ─────────────────────────────────────────────────────
    private fun buildCsvLine(
        a: SensorReading?, g: SensorReading?, m: SensorReading?, o: OrientationData?
    ): String = buildString {
        append(System.currentTimeMillis()).append(',')
        append(f(a?.x)).append(',').append(f(a?.y)).append(',').append(f(a?.z)).append(',')
        append(f(g?.x)).append(',').append(f(g?.y)).append(',').append(f(g?.z)).append(',')
        append(f(m?.x)).append(',').append(f(m?.y)).append(',').append(f(m?.z)).append(',')
        append(f(o?.quaternion?.get(0))).append(',')
        append(f(o?.quaternion?.get(1))).append(',')
        append(f(o?.quaternion?.get(2))).append(',')
        append(f(o?.quaternion?.get(3))).append(',')
        append(f(o?.yaw)).append(',')
        append(f(o?.pitch)).append(',')
        append(f(o?.roll))
        append('\n')
    }

    private fun buildJsonLine(
        a: SensorReading?, g: SensorReading?, m: SensorReading?, o: OrientationData?
    ): String = buildString {
        append("{")
        append("\"t\":${System.currentTimeMillis()},")
        append("\"accel\":[${f(a?.x)},${f(a?.y)},${f(a?.z)}],")
        append("\"gyro\":[${f(g?.x)},${f(g?.y)},${f(g?.z)}],")
        append("\"mag\":[${f(m?.x)},${f(m?.y)},${f(m?.z)}],")
        append("\"quat\":[${f(o?.quaternion?.get(0))},${f(o?.quaternion?.get(1))},${f(o?.quaternion?.get(2))},${f(o?.quaternion?.get(3))}],")
        append("\"euler\":{\"yaw\":${f(o?.yaw)},\"pitch\":${f(o?.pitch)},\"roll\":${f(o?.roll)}}")
        append("}\n")
    }

    private fun f(v: Float?): String = if (v != null) "%.4f".format(v) else "0"
}
