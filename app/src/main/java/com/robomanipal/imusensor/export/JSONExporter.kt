package com.robomanipal.imusensor.export

import android.content.Context
import android.os.Environment
import com.robomanipal.imusensor.sensor.SensorReading
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Exports buffered sensor data to a JSON file in the app's Documents directory.
 */
object JSONExporter {

    fun export(
        context: Context,
        accel: List<SensorReading>,
        gyro: List<SensorReading>,
        mag: List<SensorReading>,
    ): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        val file = File(dir, "imu_export_$timestamp.json")

        file.bufferedWriter().use { w ->
            w.write("[\n")
            val n = maxOf(accel.size, gyro.size, mag.size)
            for (i in 0 until n) {
                val a = accel.getOrNull(i)
                val g = gyro.getOrNull(i)
                val m = mag.getOrNull(i)
                if (i > 0) w.write(",\n")
                w.write(buildString {
                    append("  {")
                    append("\"i\":$i,")
                    append("\"accel\":[${f(a?.x)},${f(a?.y)},${f(a?.z)}],")
                    append("\"gyro\":[${f(g?.x)},${f(g?.y)},${f(g?.z)}],")
                    append("\"mag\":[${f(m?.x)},${f(m?.y)},${f(m?.z)}]")
                    append("}")
                })
            }
            w.write("\n]\n")
        }

        return file.absolutePath
    }

    private fun f(v: Float?): String = if (v != null) "%.6f".format(v) else "0"
}
