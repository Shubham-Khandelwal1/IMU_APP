package com.robomanipal.imusensor.streaming

/**
 * Configuration for the UDP sensor data stream.
 */
data class StreamConfig(
    val targetIp: String   = "192.168.1.100",
    val port: Int          = 8765,
    val format: StreamFormat = StreamFormat.JSON,
    val sampleRateHz: Int  = 100,
    val isActive: Boolean  = false,
)

enum class StreamFormat { CSV, JSON }
