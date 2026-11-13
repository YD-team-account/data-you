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

    /**
     * GPS 방위각(보통 1Hz)으로 각속도를 구한다.
     * 값이 새로 바뀐 순간끼리의 실제 간격으로 나눠야 한다. 고정 주기(0.9초)로 나누면
     * 갱신 주기와 어긋나 각속도가 부풀려지고(약 11%) 가짜 '급회전'이 생긴다.
     * 방위각이 1.5초 넘게 그대로면 직진으로 본다.
     */
    private fun updateHeading(s: VehicleSample) {
        val h = s.headingDeg ?: return
        val prevH = lastHeadingDeg
        val prevT = lastHeadingTs
        if (prevH == null || prevT == null) {
            lastHeadingDeg = h
            lastHeadingTs = s.timestampMs
            return
        }
        val dtH = (s.timestampMs - prevT) / 1000.0
        if (h != prevH) {
            if (dtH > 0) {
                var d = h - prevH
                if (d > 180) d -= 360
                if (d < -180) d += 360
                headingYawRate = (d * PI / 180.0) / dtH
            }
            lastHeadingDeg = h
            lastHeadingTs = s.timestampMs
        } else if (dtH > GPS_STRAIGHT_AFTER_SEC) {
            headingYawRate = 0.0
        }
    }

    private companion object {
        const val GPS_STRAIGHT_AFTER_SEC = 1.5
    }
}
