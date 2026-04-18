package com.robomanipal.imusensor.sensor

/**
 * Raw reading from a single 3-axis sensor (accel / gyro / mag).
 */
data class SensorReading(
    val timestamp: Long,   // System.nanoTime()-based
    val x: Float,
    val y: Float,
    val z: Float,
    val accuracy: Int = 0,
)

/**
 * Fused orientation derived from the rotation-vector sensor.
 */
data class OrientationData(
    val quaternion: FloatArray    = FloatArray(4),   // w, x, y, z
    val euler: FloatArray         = FloatArray(3),   // yaw, pitch, roll  (degrees)
    val rotationMatrix: FloatArray = FloatArray(9),  // 3×3 row-major
    val timestamp: Long           = 0L,
) {
    /** Convenience accessors */
    val yaw   get() = euler[0]
    val pitch get() = euler[1]
    val roll  get() = euler[2]

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OrientationData) return false
        return timestamp == other.timestamp
    }

    override fun hashCode(): Int = timestamp.hashCode()
}
