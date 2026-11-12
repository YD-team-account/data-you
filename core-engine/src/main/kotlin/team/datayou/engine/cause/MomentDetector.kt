package team.datayou.engine.cause

import team.datayou.engine.EngineConfig
import team.datayou.engine.model.Cause
import team.datayou.engine.model.CauseEvent
import team.datayou.engine.model.MotionFrame
import kotlin.math.abs
import kotlin.math.max

/**
 * 순간 이벤트(급제동·회생 급감속·급출발·급차선변경/급회전) 판별.
 * 동승자마다 임계값 배율이 다르므로 동승자별로 하나씩 둔다.
 *
 * 규칙: 임계값을 eventMinDurationSec 이상 연속으로 넘으면 1회 이벤트.
 * 같은 채널은 eventCooldownSec 동안 다시 울리지 않는다.
 */
class MomentDetector(
    private val config: EngineConfig,
    thresholdScale: Double,
) {
    private val decelTh = config.hardBrakeDecel * thresholdScale
    private val accelTh = config.hardAccel * thresholdScale
    private val latTh = config.sharpLateral * thresholdScale

    private val decel = Channel()
    private val accel = Channel()
    private val lateral = Channel()
    private var lastSignalOnMs: Long? = null

    fun process(f: MotionFrame): List<CauseEvent> {
        if (f.turnSignal.isOn) lastSignalOnMs = f.timestampMs
        val out = mutableListOf<CauseEvent>()

        decel.step(f, -f.longAccel, decelTh, extraFlag = f.brakePedalOn == true)?.let { (peak, brakeSeen) ->
            // 브레이크 정보가 없는 차량(null)은 급제동으로 본다
            val cause = if (brakeSeen || f.brakePedalOn == null) Cause.HARD_BRAKE else Cause.REGEN_DECEL
            out += CauseEvent(cause, f.timestampMs, peak, "감속 %.1f m/s²".format(peak))
        }
        accel.step(f, f.longAccel, accelTh)?.let { (peak, _) ->
            out += CauseEvent(Cause.HARD_ACCEL, f.timestampMs, peak, "가속 %.1f m/s²".format(peak))
        }
        lateral.step(f, abs(f.latAccel), latTh)?.let { (peak, _) ->
            val sig = lastSignalOnMs
            val laneChange = sig != null && (f.timestampMs - sig) / 1000.0 <= config.turnSignalWindowSec
            val kind = if (laneChange) "급차선변경" else "급회전·커브"
            out += CauseEvent(Cause.SHARP_LATERAL, f.timestampMs, peak, "$kind %.1f m/s²".format(peak))
        }
        return out
    }

    /** 한 채널의 "임계 초과 구간" 상태 머신. */
    private inner class Channel {
        private var aboveSinceMs: Long? = null
        private var peak = 0.0
        private var flagSeen = false
        private var firedThisExcursion = false
        private var lastFiredMs: Long? = null

        /** 이벤트가 확정되면 (peak, flagSeen)을 반환. */
        fun step(f: MotionFrame, value: Double, threshold: Double, extraFlag: Boolean = false): Pair<Double, Boolean>? {
            if (value < threshold) {
                aboveSinceMs = null
                peak = 0.0
                flagSeen = false
                firedThisExcursion = false
                return null
            }
            if (aboveSinceMs == null) aboveSinceMs = f.timestampMs
            peak = max(peak, value)
            flagSeen = flagSeen || extraFlag
            if (firedThisExcursion) return null

            val held = (f.timestampMs - aboveSinceMs!!) / 1000.0
            val cooled = lastFiredMs?.let { (f.timestampMs - it) / 1000.0 >= config.eventCooldownSec } ?: true
            if (held >= config.eventMinDurationSec && cooled) {
                firedThisExcursion = true
                lastFiredMs = f.timestampMs
                return peak to flagSeen
            }
            return null
        }
    }
}
