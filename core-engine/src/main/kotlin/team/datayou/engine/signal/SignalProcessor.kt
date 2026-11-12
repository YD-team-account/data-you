package team.datayou.engine.signal

import team.datayou.engine.EngineConfig
import team.datayou.engine.model.MotionFrame
import team.datayou.engine.model.VehicleSample
import kotlin.math.PI

/**
 * 원시 샘플 → 앞뒤 가속도, 좌우 가속도, jerk.
 *
 * 좌우 가속도 추정 우선순위:
 *  1) 자이로 yaw rate가 있으면  a_lat = v × ω
 *  2) 없으면 GPS 방위각 변화율로 ω를 근사  (정밀도 낮음, 1Hz 수준)
 *  3) 둘 다 없으면 0
 */
class SignalProcessor(private val config: EngineConfig = EngineConfig()) {
    private val speedLp = LowPass(config.speedSmoothingTauSec)
    private val accelLp = LowPass(config.accelSmoothingTauSec)

    private var lastTs: Long? = null
    private var lastSmoothedSpeed: Double? = null
    private var lastSmoothedAccel: Double? = null
    private var lastHeadingDeg: Double? = null
    private var lastHeadingTs: Long? = null
    private var headingYawRate: Double = 0.0

    /** 첫 샘플은 기준점만 잡고 null을 반환한다. */
    fun process(s: VehicleSample): MotionFrame? {
        val prevTs = lastTs
        lastTs = s.timestampMs
        if (prevTs == null) {
            lastSmoothedSpeed = speedLp.update(s.speedMps, 0.0)
            lastSmoothedAccel = accelLp.update(0.0, 0.0)
            updateHeading(s)
            return null
        }
        val dt = (s.timestampMs - prevTs) / 1000.0
        if (dt <= 0.0) return null

        val v = speedLp.update(s.speedMps, dt)
        val rawAccel = (v - (lastSmoothedSpeed ?: v)) / dt
        lastSmoothedSpeed = v

        val accel = accelLp.update(rawAccel, dt)
        val jerk = (accel - (lastSmoothedAccel ?: accel)) / dt
        lastSmoothedAccel = accel

        updateHeading(s)
        val yaw = s.yawRateRadPs ?: headingYawRate
        val lat = v * yaw

        return MotionFrame(
            timestampMs = s.timestampMs,
            dtSec = dt,
            speedMps = v,
            longAccel = accel,
            latAccel = lat,
            jerk = jerk,
            brakePedalOn = s.brakePedalOn,
            turnSignal = s.turnSignal,
        )
    }

    private fun updateHeading(s: VehicleSample) {
        val h = s.headingDeg ?: return
        val prevH = lastHeadingDeg
        val prevT = lastHeadingTs
        if (prevH != null && prevT != null) {
            // GPS 방위각은 보통 1Hz로 갱신되므로 약 1초마다 각속도를 다시 계산 (0/360 경계 보정)
            val dtH = (s.timestampMs - prevT) / 1000.0
            if (dtH >= GPS_HEADING_INTERVAL_SEC) {
                var d = h - prevH
                if (d > 180) d -= 360
                if (d < -180) d += 360
                headingYawRate = (d * PI / 180.0) / dtH
                lastHeadingDeg = h
                lastHeadingTs = s.timestampMs
            }
        } else {
            lastHeadingDeg = h
            lastHeadingTs = s.timestampMs
        }
    }

    private companion object {
        const val GPS_HEADING_INTERVAL_SEC = 0.9
    }
}
