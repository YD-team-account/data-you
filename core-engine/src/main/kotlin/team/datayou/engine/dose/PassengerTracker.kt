package team.datayou.engine.dose

import team.datayou.engine.EngineConfig
import team.datayou.engine.cause.MomentDetector
import team.datayou.engine.model.Cause
import team.datayou.engine.model.CauseEvent
import team.datayou.engine.model.GaugeLevel
import team.datayou.engine.model.PassengerProfile
import kotlin.math.exp

/**
 * 동승자 1명의 멀미 게이지(0~100)와 원인별 기여도를 관리한다.
 *
 * 게이지 = Σ(대역통과 가속도 파워 × doseGain × 민감도 × dt) + 이벤트 가산점 − 조용한 주행 시 회복
 *
 * ISO 2631-1의 멀미 누적량(MSDV) 개념을 단순화했다:
 *  - 같은 점: 멀미 유발 대역(약 0.1~0.5Hz)의 가속도를 시간에 따라 누적
 *  - 다른 점: 앞뒤·좌우 축을 함께 사용, 회복(감쇠) 항 추가, 동승자 피드백으로 개인 보정
 */
class PassengerTracker(
    profile: PassengerProfile,
    private val config: EngineConfig,
) {
    var profile: PassengerProfile = profile
        private set

    private val moments = MomentDetector(config, profile.eventThresholdScale)

    var gauge: Double = 0.0
        private set
    var level: GaugeLevel = GaugeLevel.COMFORT
        private set

    /** 원인별 기여도 (시간에 따라 감쇠). 배지 선정에 사용. */
    private val ledger = mutableMapOf<Cause, Double>()
    /** 주행 전체 누적 기여도 (감쇠 없음). 리포트 원인 비율에 사용. */
    val tripContribution = mutableMapOf<Cause, Double>()

    var peakGauge = 0.0
        private set
    val secondsInLevel = mutableMapOf(GaugeLevel.COMFORT to 0.0, GaugeLevel.CAUTION to 0.0, GaugeLevel.DISCOMFORT to 0.0)

    private val recentMomentEvents = ArrayDeque<Long>()

    fun applyFeedback(uncomfortable: Boolean) {
        profile = profile.withFeedback(uncomfortable)
    }

    /**
     * @param longPower 앞뒤 대역통과 가속도 제곱
     * @param latPower  좌우 대역통과 가속도 제곱
     * @param longCause 앞뒤 흔들림을 귀속할 원인 (엔진이 상황을 보고 결정)
     * @param latCause  좌우 흔들림을 귀속할 원인
     * @return 이번 프레임에 이 동승자 기준으로 확정된 순간 이벤트
     */
    fun step(
        frame: team.datayou.engine.model.MotionFrame,
        longPower: Double,
        latPower: Double,
        longCause: Cause?,
        latCause: Cause?,
        patternEvents: List<CauseEvent>,
    ): StepResult {
        val dt = frame.dtSec
        val sens = profile.doseSensitivity
        val before = level

        // 1) 저주파 누적
        val longDose = longPower * config.doseGain * sens * dt
        val latDose = latPower * config.doseGain * sens * dt
        var delta = longDose + latDose
        longCause?.let { add(it, longDose) }
        latCause?.let { add(it, latDose) }

        // 2) 순간 이벤트 가산점
        val momentEvents = moments.process(frame)
        for (e in momentEvents) {
            val bonus = config.momentEventBonus * sens
            delta += bonus
            add(e.cause, bonus)
            recentMomentEvents.addLast(e.timestampMs)
        }
        // 3) 반복 패턴 가산점 (정체는 상황이므로 게이지에는 반영하되 배지는 회색 처리)
        for (e in patternEvents) {
            val bonus = config.patternEventBonus * sens
            delta += bonus
            add(e.cause, bonus)
        }

        // 4) 조용한 주행이면 회복
        if (longPower + latPower < config.quietPowerThreshold && momentEvents.isEmpty()) {
            delta -= config.recoveryPerSec * dt
        }

        gauge = (gauge + delta).coerceIn(0.0, 100.0)
        peakGauge = maxOf(peakGauge, gauge)
        level = nextLevel(level, gauge)
        secondsInLevel[level] = secondsInLevel.getValue(level) + dt

        // 기여도 감쇠
        val decay = exp(-dt / config.attributionTauSec)
        ledger.replaceAll { _, v -> v * decay }

        // 원안 모드: 최근 N분 순간 이벤트 수
        while (recentMomentEvents.isNotEmpty() &&
            (frame.timestampMs - recentMomentEvents.first()) / 60000.0 > config.legacyWindowMin
        ) recentMomentEvents.removeFirst()

        return StepResult(
            momentEvents = momentEvents,
            levelUp = level.ordinal > before.ordinal,
            legacyTriggered = recentMomentEvents.size >= config.legacyMinEvents,
        )
    }

    /** 현재 가장 크게 기여하는 원인. */
    fun topCause(minScore: Double = 1.0): Cause? =
        ledger.filterValues { it >= minScore }.maxByOrNull { it.value }?.key

    fun score(cause: Cause): Double = ledger[cause] ?: 0.0

    /** 운전자가 고칠 수 있는 원인 중 최근 1위 (정차 카드 코칭용). */
    fun topActionableCause(): Cause? =
        ledger.filter { it.key.actionable && it.value >= 1.0 }.maxByOrNull { it.value }?.key

    private fun add(cause: Cause, v: Double) {
        if (v <= 0) return
        ledger[cause] = (ledger[cause] ?: 0.0) + v
        tripContribution[cause] = (tripContribution[cause] ?: 0.0) + v
    }

    private fun nextLevel(cur: GaugeLevel, g: Double): GaugeLevel {
        val h = config.levelHysteresis
        return when (cur) {
            GaugeLevel.COMFORT -> when {
                g >= config.discomfortAt -> GaugeLevel.DISCOMFORT
                g >= config.cautionAt -> GaugeLevel.CAUTION
                else -> GaugeLevel.COMFORT
            }
            GaugeLevel.CAUTION -> when {
                g >= config.discomfortAt -> GaugeLevel.DISCOMFORT
                g < config.cautionAt - h -> GaugeLevel.COMFORT
                else -> GaugeLevel.CAUTION
            }
            GaugeLevel.DISCOMFORT -> when {
                g < config.cautionAt - h -> GaugeLevel.COMFORT
                g < config.discomfortAt - h -> GaugeLevel.CAUTION
                else -> GaugeLevel.DISCOMFORT
            }
        }
    }

    data class StepResult(
        val momentEvents: List<CauseEvent>,
        val levelUp: Boolean,
        val legacyTriggered: Boolean,
    )
}
