package team.datayou.engine.report

import team.datayou.engine.dose.PassengerTracker
import team.datayou.engine.model.Cause
import team.datayou.engine.model.CauseEvent
import team.datayou.engine.model.GaugeLevel
import team.datayou.engine.model.MotionFrame
import team.datayou.engine.model.PassengerProfile

/** 주행 1회의 기록을 모아 하차 리포트를 만든다. */
class TripRecorder {
    private var startMs: Long? = null
    private var endMs: Long = 0
    private var distanceM = 0.0
    private val events = mutableListOf<CauseEvent>()
    private var lastStopIndex = 0

    fun onFrame(f: MotionFrame, newEvents: List<CauseEvent>) {
        if (startMs == null) startMs = f.timestampMs
        endMs = f.timestampMs
        distanceM += f.speedMps * f.dtSec
        events += newEvents
    }

    fun markStop() {
        lastStopIndex = events.size
    }

    fun countSinceLastStop(cause: Cause): Int =
        events.subList(lastStopIndex, events.size).count { it.cause == cause }

    fun build(trackers: List<PassengerTracker>, odometerKm: Double?): TripReport {
        // Pleos Vehicle SDK Odometer 값이 있으면 우선 사용, 없으면 속도 적분
        val km = odometerKm ?: (distanceM / 1000.0)
        val minutes = ((endMs - (startMs ?: endMs)) / 60000.0)
        val counts = Cause.entries.associateWith { c -> events.count { it.cause == c } }.filterValues { it > 0 }
        val perPassenger = trackers.map { t ->
            val total = t.tripContribution.values.sum().takeIf { it > 0 } ?: 1.0
            PassengerReport(
                profile = t.profile,
                peakGauge = t.peakGauge,
                minutesInLevel = t.secondsInLevel.mapValues { it.value / 60.0 },
                causeShare = t.tripContribution
                    .mapValues { it.value / total }
                    .entries.sortedByDescending { it.value }
                    .associate { it.key to it.value },
            )
        }
        val actionable = counts.filterKeys { it.actionable }
        val eventsPer10Km = if (km > 0.1) actionable.values.sum() / km * 10.0 else 0.0
        val focus = perPassenger.maxBy { it.peakGauge }
        val smooth = actionable.isEmpty() && perPassenger.all { it.peakGauge < 40.0 }
        val topFix = if (smooth) null else focus.causeShare.keys.firstOrNull { it.actionable }

        return TripReport(
            durationMin = minutes,
            distanceKm = km,
            eventCounts = counts,
            actionableEventsPer10Km = eventsPer10Km,
            passengers = perPassenger,
            events = events.toList(),
            topImprovement = topFix,
            smoothTrip = smooth,
        )
    }
}

data class PassengerReport(
    val profile: PassengerProfile,
    val peakGauge: Double,
    val minutesInLevel: Map<GaugeLevel, Double>,
    /** 원인별 게이지 기여 비율 (합 1.0, 큰 순서). */
    val causeShare: Map<Cause, Double>,
)

data class TripReport(
    val durationMin: Double,
    val distanceKm: Double,
    val eventCounts: Map<Cause, Int>,
    /** 운전자가 고칠 수 있는 이벤트의 10km당 횟수. 주행 간 비교 지표. */
    val actionableEventsPer10Km: Double,
    val passengers: List<PassengerReport>,
    val events: List<CauseEvent>,
    /** 다음 주행에서 가장 먼저 고치면 좋은 원인. */
    val topImprovement: Cause?,
    /** 칭찬 대상 주행인지 (스트릭 집계용). */
    val smoothTrip: Boolean,
) {
    fun toText(): String = buildString {
        appendLine("── 다 타유! 주행 리포트 ──")
        appendLine("주행 %.1f분 · %.1fkm".format(durationMin, distanceKm))
        if (smoothTrip) appendLine("부드러운 주행이었어요. 동승자 모두 편안했어요.")
        for (p in passengers) {
            appendLine()
            appendLine("[${p.profile.displayName}] 최고 게이지 %.0f".format(p.peakGauge))
            appendLine(
                "  편안 %.1f분 / 주의 %.1f분 / 힘듦 %.1f분".format(
                    p.minutesInLevel[GaugeLevel.COMFORT] ?: 0.0,
                    p.minutesInLevel[GaugeLevel.CAUTION] ?: 0.0,
                    p.minutesInLevel[GaugeLevel.DISCOMFORT] ?: 0.0,
                ),
            )
            if (p.peakGauge >= 5.0) p.causeShare.entries.take(3).forEach { (c, s) ->
                val tag = if (c.actionable) "" else " (운전자 탓 아님)"
                appendLine("  - ${c.label}$tag: %.0f%%".format(s * 100))
            }
        }
        appendLine()
        appendLine("원인별 횟수: " + eventCounts.entries.joinToString { "${it.key.label} ${it.value}회" }.ifEmpty { "없음" })
        appendLine("10km당 개선 가능 이벤트: %.1f회".format(actionableEventsPer10Km))
        topImprovement?.let { appendLine("다음 주행 팁: ${it.coaching}") }
    }
}
