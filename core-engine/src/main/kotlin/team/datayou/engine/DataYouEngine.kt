package team.datayou.engine

import team.datayou.engine.cause.PatternDetector
import team.datayou.engine.dose.PassengerTracker
import team.datayou.engine.model.Cause
import team.datayou.engine.model.CauseEvent
import team.datayou.engine.model.CauseKind
import team.datayou.engine.model.GaugeLevel
import team.datayou.engine.model.PassengerProfile
import team.datayou.engine.model.VehicleSample
import team.datayou.engine.report.TripRecorder
import team.datayou.engine.report.TripReport
import team.datayou.engine.signal.BandPass
import team.datayou.engine.signal.SignalProcessor

/**
 * 다타유 엔진 진입점. 안드로이드 의존성이 없는 순수 Kotlin이라
 * 앱(AAOS)·CLI·테스트 어디서나 같은 코드로 동작한다.
 *
 * 사용법:
 *   val engine = DataYouEngine(listOf(PassengerProfile("kid", PassengerType.CHILD)))
 *   차량 샘플이 들어올 때마다 engine.process(sample) → EngineUpdate로 UI 갱신
 *   주행 종료 시 engine.finishTrip() → TripReport
 */
class DataYouEngine(
    passengers: List<PassengerProfile>,
    private val config: EngineConfig = EngineConfig(),
) {
    init {
        require(passengers.isNotEmpty()) { "동승자 프로필이 최소 1명 필요합니다" }
    }

    private val signal = SignalProcessor(config)
    private val bandLong = BandPass(config.bandLowHz, config.bandHighHz)
    private val bandLat = BandPass(config.bandLowHz, config.bandHighHz)
    private val patterns = PatternDetector(config)
    private val trackers = passengers.associate { it.id to PassengerTracker(it, config) }
    private val recorder = TripRecorder()

    private var badge: Cause? = null
    private var badgeSinceMs: Long = 0
    private var stoppedSinceMs: Long? = null
    private var stopped = false
    private var lastLongMomentCause: Pair<Cause, Long>? = null
    private var lastLatMomentMs: Long? = null

    /** 대표 동승자 = 게이지가 가장 높은 동승자. 주행 중 아이콘에 이 한 명만 표시한다. */
    private fun focusTracker(): PassengerTracker = trackers.values.maxBy { it.gauge }

    fun process(sample: VehicleSample): EngineUpdate? {
        val f = signal.process(sample) ?: return null
        val now = f.timestampMs

        val bl = bandLong.update(f.longAccel, f.dtSec)
        val bt = bandLat.update(f.latAccel, f.dtSec)
        val longPower = bl * bl
        val latPower = bt * bt

        val patternEvents = patterns.process(f)
        val longCause = attributeLongitudinal(now)
        val latCause = attributeLateral(now)

        val newEvents = mutableListOf<CauseEvent>()
        newEvents += patternEvents
        val seenMoment = mutableSetOf<Pair<Cause, Long>>()
        val results = trackers.values.associateWith { t ->
            t.step(f, longPower, latPower, longCause, latCause, patternEvents)
        }
        for (r in results.values) {
            // 같은 이벤트가 동승자마다 중복 기록되지 않도록 원인+시각으로 1회만
            for (e in r.momentEvents) if (seenMoment.add(e.cause to e.timestampMs)) newEvents += e
        }
        val legacy = results.values.any { it.legacyTriggered }
        // 효과음은 대표 동승자(가장 힘든 1명)의 단계가 올라갈 때만
        val levelUp = results.getValue(focusTracker()).levelUp
        for (e in newEvents) {
            when (e.cause) {
                Cause.HARD_BRAKE, Cause.REGEN_DECEL, Cause.HARD_ACCEL -> lastLongMomentCause = e.cause to now
                Cause.SHARP_LATERAL -> lastLatMomentMs = now
                else -> {}
            }
        }

        recorder.onFrame(f, newEvents)
        updateStopped(f.speedMps, now)
        val focus = focusTracker()
        updateBadge(focus, now)

        // 정차 카드: 정체 중이면 '운전자 탓 아님' 안내, 아니면 고칠 수 있는 원인 1위를 코칭.
        // 같은 내용을 신호마다 반복하지 않도록 카드 사이 최소 간격을 둔다.
        val cardCooled = lastCardMs?.let { (now - it) / 1000.0 >= config.stopCardCooldownSec } ?: true
        val stopCard = if (justStopped && cardCooled && focus.level != GaugeLevel.COMFORT) {
            if (patterns.inTraffic) StopCard(focus.profile, focus.level, Cause.STOP_AND_GO, 0)
            else focus.topActionableCause()?.let { StopCard(focus.profile, focus.level, it, recorder.countSinceLastStop(it)) }
        } else null
        if (stopCard != null) lastCardMs = now
        if (justStopped) recorder.markStop()
        justStopped = false

        return EngineUpdate(
            timestampMs = now,
            speedMps = f.speedMps,
            vehicleStopped = stopped,
            passengers = trackers.values.map { PassengerState(it.profile, it.gauge, it.level) },
            focusPassenger = PassengerState(focus.profile, focus.gauge, focus.level),
            badge = badge,
            newEvents = newEvents,
            playAlertSound = levelUp,
            stopCard = stopCard,
            legacyAlert = legacy,
        )
    }

    /** 동승자 피드백 ("속 안 좋아" / "괜찮아"). 향후 Gleo STT와 연결. */
    fun feedback(passengerId: String, uncomfortable: Boolean) {
        trackers[passengerId]?.applyFeedback(uncomfortable)
    }

    /**
     * NaviHelper getTBTInfo 결과로 예측 안내 여부를 판단.
     * 게이지가 '주의' 이상이고 다음 회전이 300m 이내면 true → 아이콘 강조 + 효과음.
     */
    fun shouldPreAlert(distanceToManeuverM: Double, isTurnManeuver: Boolean): Boolean =
        isTurnManeuver &&
            distanceToManeuverM in 0.0..config.preAlertDistanceM &&
            focusTracker().level != GaugeLevel.COMFORT

    fun finishTrip(odometerKm: Double? = null): TripReport =
        recorder.build(trackers.values.toList(), odometerKm)

    // ── 내부 ─────────────────────────────────────────

    private var justStopped = false
    private var lastCardMs: Long? = null

    private fun updateStopped(speed: Double, now: Long) {
        if (speed < config.stoppedSpeedMps) {
            if (stoppedSinceMs == null) stoppedSinceMs = now
            if (!stopped && (now - stoppedSinceMs!!) / 1000.0 >= config.stoppedMinSec) {
                stopped = true
                justStopped = true
            }
        } else {
            stoppedSinceMs = null
            stopped = false
        }
    }

    /** 앞뒤 저주파 흔들림의 원인 귀속. */
    private fun attributeLongitudinal(now: Long): Cause? {
        if (patterns.inTraffic) return Cause.STOP_AND_GO
        patterns.lastFiredMs(Cause.BRAKE_PUMPING)?.let { if ((now - it) / 1000.0 <= 60) return Cause.BRAKE_PUMPING }
        lastLongMomentCause?.let { (c, t) -> if ((now - t) / 1000.0 <= 30) return c }
        return null // 귀속 불명은 게이지에는 반영하되 배지 후보에서 제외
    }

    /** 좌우 저주파 흔들림의 원인 귀속. */
    private fun attributeLateral(now: Long): Cause? {
        lastLatMomentMs?.let { if ((now - it) / 1000.0 <= 10) return Cause.SHARP_LATERAL }
        return Cause.CONTINUOUS_CURVES
    }

    /**
     * 주행 중 원인 배지 선정 규칙
     *  - 대표 동승자가 '편안'이면 배지 없음
     *  - 한 번 띄운 배지는 최소 30초 유지 (깜빡임 방지)
     *  - 새 원인이 현재 배지보다 1.2배 이상 클 때만 교체
     */
    private fun updateBadge(focus: PassengerTracker, now: Long) {
        if (focus.level == GaugeLevel.COMFORT) {
            badge = null
            return
        }
        val candidate = focus.topCause() ?: return
        val current = badge
        if (current == null) {
            badge = candidate
            badgeSinceMs = now
            return
        }
        if (candidate == current) return
        val held = (now - badgeSinceMs) / 1000.0
        if (held >= config.badgeMinHoldSec && focus.score(candidate) >= focus.score(current) * config.badgeSwitchRatio) {
            badge = candidate
            badgeSinceMs = now
        }
    }
}

data class PassengerState(
    val profile: PassengerProfile,
    val gauge: Double,
    val level: GaugeLevel,
)

/** 정차 시 보여줄 카드 (캐릭터 애니메이션 + 원인 + 한 줄 코칭, Gleo TTS로 읽기). */
data class StopCard(
    val passenger: PassengerProfile,
    val level: GaugeLevel,
    val cause: Cause,
    val countSinceLastStop: Int,
) {
    val message: String
        get() {
            val who = withSubjectParticle(passenger.displayName)
            val mood = if (level == GaugeLevel.DISCOMFORT) "많이 힘들어해요" else "조금 힘들어해요"
            // 순간 이벤트만 횟수를 보여준다 (반복 패턴은 횟수보다 상황 설명이 자연스러움)
            val what = when {
                !cause.actionable -> null
                cause.kind == CauseKind.MOMENT && countSinceLastStop > 0 -> "${cause.label} ${countSinceLastStop}회"
                else -> cause.label
            }
            return listOfNotNull("$who $mood", what, cause.coaching).joinToString(" · ")
        }
}

/** 받침 유무에 따라 주격 조사 '이/가'를 붙인다. (아이가, 어르신이) */
internal fun withSubjectParticle(word: String): String {
    val last = word.lastOrNull() ?: return word
    if (last !in '가'..'힣') return "${word}이(가)"
    val hasFinal = (last - '가') % 28 != 0
    return if (hasFinal) "${word}이" else "${word}가"
}

data class EngineUpdate(
    val timestampMs: Long,
    val speedMps: Double,
    val vehicleStopped: Boolean,
    val passengers: List<PassengerState>,
    /** 주행 중 아이콘으로 표시할 동승자 (가장 힘든 1명). */
    val focusPassenger: PassengerState,
    /** 주행 중 원인 배지 (없으면 null). STOP_AND_GO는 회색 '상황' 배지로 표시. */
    val badge: Cause?,
    val newEvents: List<CauseEvent>,
    /** 게이지 단계가 올라간 순간 true → 효과음 1회. */
    val playAlertSound: Boolean,
    /** 정차로 전환된 순간에만 채워짐. */
    val stopCard: StopCard?,
    /** 원안 모드(N분 내 M회)를 켠 경우의 알림 조건. */
    val legacyAlert: Boolean,
)
