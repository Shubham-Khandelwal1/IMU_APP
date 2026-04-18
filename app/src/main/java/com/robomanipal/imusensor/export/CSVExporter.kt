package com.robomanipal.imusensor.export

import android.content.Context
import android.os.Environment
import com.robomanipal.imusensor.sensor.SensorReading
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Exports buffered sensor data to a CSV file in the app's Documents directory.
 */
object CSVExporter {

    fun export(
        context: Context,
        accel: List<SensorReading>,
        gyro: List<SensorReading>,
        mag: List<SensorReading>,
    ): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        val file = File(dir, "imu_export_$timestamp.csv")

        file.bufferedWriter().use { w ->
            w.write("index,accel_x,accel_y,accel_z,gyro_x,gyro_y,gyro_z,mag_x,mag_y,mag_z\n")
            val n = maxOf(accel.size, gyro.size, mag.size)
            for (i in 0 until n) {
                val a = accel.getOrNull(i)
                val g = gyro.getOrNull(i)
                val m = mag.getOrNull(i)
                w.write(buildString {
                    append(i).append(',')
                    append(f(a?.x)).append(',').append(f(a?.y)).append(',').append(f(a?.z)).append(',')
                    append(f(g?.x)).append(',').append(f(g?.y)).append(',').append(f(g?.z)).append(',')
                    append(f(m?.x)).append(',').append(f(m?.y)).append(',').append(f(m?.z))
                    append('\n')
                })
            }
        }

        return file.absolutePath
    }

    private fun f(v: Float?): String = if (v != null) "%.6f".format(v) else ""
}
