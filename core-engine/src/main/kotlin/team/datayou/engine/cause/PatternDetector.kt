package team.datayou.engine.cause

import team.datayou.engine.EngineConfig
import team.datayou.engine.model.Cause
import team.datayou.engine.model.CauseEvent
import team.datayou.engine.model.MotionFrame
import kotlin.math.abs
import kotlin.math.sign

/**
 * 반복 패턴 판별 (차량 단위, 동승자와 무관).
 *
 * - 정체 가다 서다 : 120초 안에 정지 진입 3회 이상 + 그동안 최고속도 30km/h 이하 → 운전자 탓 아님(SITUATION)
 * - 브레이크 끊어 밟기 : 60초 안에 브레이크 ON 전환 6회 이상 + 그동안 최저속도 15km/h 이상(정체 아님)
 * - 연속 커브(좌우 흔들림) : 30초 안에 좌우 가속 부호가 4회 이상 바뀜 (|a_lat| ≥ 1.0)
 */
class PatternDetector(private val config: EngineConfig) {
    private val brakePresses = ArrayDeque<Long>()
    private val stops = ArrayDeque<Long>()
    private val latSignChanges = ArrayDeque<Long>()
    private val speedHistory = ArrayDeque<Pair<Long, Double>>()

    private var lastBrake: Boolean? = null
    private var wasMoving = false
    private var lastLatSign = 0.0

    private val lastFired = mutableMapOf<Cause, Long>()

    /** 지금 정체 상태인지 (원인 귀속에 사용). */
    var inTraffic: Boolean = false
        private set

    fun lastFiredMs(cause: Cause): Long? = lastFired[cause]

    fun process(f: MotionFrame): List<CauseEvent> {
        val now = f.timestampMs
        val out = mutableListOf<CauseEvent>()

        // 속도 이력 (끊어 밟기 판정용 최저속도)
        speedHistory.addLast(now to f.speedMps)
        trim(speedHistory, now, maxOf(config.pumpingWindowSec, config.stopAndGoWindowSec)) { it.first }

        // 브레이크 ON 전환
        f.brakePedalOn?.let { on ->
            if (on && lastBrake == false) brakePresses.addLast(now)
            lastBrake = on
        }
        trim(brakePresses, now, config.pumpingWindowSec) { it }

        // 정지 진입
        if (f.speedMps > config.movingSpeedMps) wasMoving = true
        if (wasMoving && f.speedMps < config.stopSpeedMps) {
            stops.addLast(now)
            wasMoving = false
        }
        trim(stops, now, config.stopAndGoWindowSec) { it }
        // 정체 = 짧은 간격의 정지 반복 + 그 사이 최고속도가 낮음 (신호등 사이를 빠르게 달리는 시내 주행과 구분)
        // 최고속도는 '최근 N번의 정지가 시작된 시점부터' 구간에서만 본다. (앞선 고속 주행이 판정을 늦추지 않게)
        inTraffic = if (stops.size >= config.stopAndGoMinStops) {
            val from = stops.elementAt(stops.size - config.stopAndGoMinStops)
            val maxSince = speedHistory.filter { it.first >= from }.maxOfOrNull { it.second } ?: 0.0
            maxSince <= config.stopAndGoMaxSpeedMps
        } else false

        // 좌우 가속 부호 전환
        if (abs(f.latAccel) >= config.curvesMinLatAccel) {
            val s = sign(f.latAccel)
            if (lastLatSign != 0.0 && s != lastLatSign) latSignChanges.addLast(now)
            lastLatSign = s
        }
        trim(latSignChanges, now, config.curvesWindowSec) { it }

        if (inTraffic) fire(Cause.STOP_AND_GO, now, stops.size.toDouble(), "최근 2분 정지 ${stops.size}회")?.let { out += it }

        val pumpingWindow = speedHistory.filter { (now - it.first) / 1000.0 <= config.pumpingWindowSec }
        val minSpeed = pumpingWindow.minOf { it.second }
        val windowFull = (now - pumpingWindow.first().first) / 1000.0 >= config.pumpingWindowSec * 0.9
        if (brakePresses.size >= config.pumpingMinPresses && windowFull && minSpeed >= config.pumpingMinSpeedMps) {
            fire(Cause.BRAKE_PUMPING, now, brakePresses.size.toDouble(), "1분간 브레이크 ${brakePresses.size}회")?.let { out += it }
        }

        if (latSignChanges.size >= config.curvesMinSignChanges) {
            fire(Cause.CONTINUOUS_CURVES, now, latSignChanges.size.toDouble(), "30초간 좌우 전환 ${latSignChanges.size}회")?.let { out += it }
        }
        return out
    }

    private fun fire(cause: Cause, now: Long, value: Double, detail: String): CauseEvent? {
        val last = lastFired[cause]
        if (last != null && (now - last) / 1000.0 < config.patternCooldownSec) return null
        lastFired[cause] = now
        return CauseEvent(cause, now, value, detail)
    }

    private inline fun <T> trim(q: ArrayDeque<T>, now: Long, windowSec: Double, ts: (T) -> Long) {
        while (q.isNotEmpty() && (now - ts(q.first())) / 1000.0 > windowSec) q.removeFirst()
    }
}
