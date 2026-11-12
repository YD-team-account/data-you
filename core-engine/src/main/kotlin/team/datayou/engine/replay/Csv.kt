package team.datayou.engine.replay

import team.datayou.engine.model.TurnSignal
import team.datayou.engine.model.VehicleSample
import java.io.File
import java.util.Locale

/**
 * 주행 데이터 CSV 형식 (스마트폰 로거 실주행 데이터도 이 형식으로 변환해 사용)
 *
 * timestamp_ms,speed_mps,yaw_rate_rad_s,heading_deg,brake,turn_signal
 * 빈 칸 = 해당 센서 없음(null)
 */
object Csv {
    const val HEADER = "timestamp_ms,speed_mps,yaw_rate_rad_s,heading_deg,brake,turn_signal"

    fun write(file: File, samples: List<VehicleSample>) {
        file.parentFile?.mkdirs()
        file.bufferedWriter().use { w ->
            w.appendLine(HEADER)
            for (s in samples) {
                w.appendLine(
                    listOf(
                        s.timestampMs.toString(),
                        "%.4f".format(Locale.ROOT, s.speedMps),
                        s.yawRateRadPs?.let { "%.5f".format(Locale.ROOT, it) } ?: "",
                        s.headingDeg?.let { "%.2f".format(Locale.ROOT, it) } ?: "",
                        s.brakePedalOn?.let { if (it) "1" else "0" } ?: "",
                        s.turnSignal.name,
                    ).joinToString(","),
                )
            }
        }
    }

    fun read(file: File): List<VehicleSample> =
        file.readLines().drop(1).filter { it.isNotBlank() }.map { line ->
            val c = line.split(",")
            VehicleSample(
                timestampMs = c[0].toLong(),
                speedMps = c[1].toDouble(),
                yawRateRadPs = c.getOrNull(2)?.takeIf { it.isNotBlank() }?.toDouble(),
                headingDeg = c.getOrNull(3)?.takeIf { it.isNotBlank() }?.toDouble(),
                brakePedalOn = c.getOrNull(4)?.takeIf { it.isNotBlank() }?.let { it == "1" },
                turnSignal = c.getOrNull(5)?.takeIf { it.isNotBlank() }?.let { TurnSignal.valueOf(it) } ?: TurnSignal.OFF,
            )
        }
}
