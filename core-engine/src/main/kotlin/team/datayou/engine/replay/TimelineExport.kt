package team.datayou.engine.replay

import team.datayou.engine.DataYouEngine
import team.datayou.engine.model.Cause
import team.datayou.engine.model.GaugeLevel
import team.datayou.engine.model.PassengerProfile
import team.datayou.engine.model.PassengerType
import java.io.File
import java.util.Locale

/**
 * 인터랙티브 프로토타입(HTML)용 타임라인 JSON 생성.
 * 시나리오 × 동승자 종류마다 엔진을 실제로 돌려 1Hz 프레임으로 기록한다.
 * 프로토타입은 이 값을 그대로 재생하므로, 화면에 보이는 게이지·배지·카드는 모두 엔진 결과다.
 */
object TimelineExport {

    fun write(out: File, scenarioNames: List<String>, stepEvery: Int = 10) {
        val sb = StringBuilder()
        sb.append("{\"causes\":{")
        sb.append(Cause.entries.joinToString(",") { c ->
            "\"${c.name}\":{\"label\":${q(c.label)},\"coach\":${q(c.coaching)},\"kind\":\"${c.kind.name}\",\"actionable\":${c.actionable}}"
        })
        sb.append("},\"scenarios\":{")
        sb.append(scenarioNames.joinToString(",") { name ->
            val samples = Scenarios.all.getValue(name)()
            val runs = PassengerType.entries.filter { it != PassengerType.ADULT }.joinToString(",") { type -> "\"${type.name}\":" + run(samples, type, stepEvery) }
            "\"$name\":{$runs}"
        })
        sb.append("}}")
        out.parentFile?.mkdirs()
        out.writeText(sb.toString())
    }

    private fun run(samples: List<team.datayou.engine.model.VehicleSample>, type: PassengerType, stepEvery: Int): String {
        val engine = DataYouEngine(listOf(PassengerProfile("p", type)))
        val frames = mutableListOf<String>()
        val pendingEvents = mutableListOf<String>()
        var pendingSound = false
        var pendingCard: String? = null
        var pendingCardCause: String? = null
        var i = 0
        for (s in samples) {
            val u = engine.process(s) ?: continue
            u.newEvents.forEach { pendingEvents += "\"${it.cause.name}\"" }
            pendingSound = pendingSound || u.playAlertSound
            u.stopCard?.let { pendingCard = it.message; pendingCardCause = it.cause.name }
            if (i++ % stepEvery != 0) continue
            val f = buildString {
                append("[")
                append(fmt(u.timestampMs / 1000.0)).append(",")
                append(Math.round(u.speedMps * 3.6)).append(",")
                append(Math.round(u.focusPassenger.gauge)).append(",")
                append(u.focusPassenger.level.ordinal).append(",")
                append(u.badge?.let { "\"${it.name}\"" } ?: "null").append(",")
                append(if (u.vehicleStopped) 1 else 0).append(",")
                append(if (pendingSound) 1 else 0).append(",")
                append("[").append(pendingEvents.joinToString(",")).append("],")
                append(pendingCard?.let { q(it) } ?: "null").append(",")
                append(pendingCardCause?.let { "\"$it\"" } ?: "null")
                append("]")
            }
            frames += f
            pendingEvents.clear(); pendingSound = false; pendingCard = null; pendingCardCause = null
        }
        val r = engine.finishTrip()
        val p = r.passengers.first()
        val share = p.causeShare.entries.filter { it.value >= 0.01 }.take(4)
            .joinToString(",") { "[\"${it.key.name}\",${fmt(it.value)}]" }
        val counts = r.eventCounts.entries.joinToString(",") { "\"${it.key.name}\":${it.value}" }
        val report = "{\"min\":${fmt(r.durationMin)},\"km\":${fmt(r.distanceKm)},\"peak\":${Math.round(p.peakGauge)}," +
            "\"lv\":[${fmt(p.minutesInLevel[GaugeLevel.COMFORT] ?: 0.0)},${fmt(p.minutesInLevel[GaugeLevel.CAUTION] ?: 0.0)},${fmt(p.minutesInLevel[GaugeLevel.DISCOMFORT] ?: 0.0)}]," +
            "\"share\":[$share],\"counts\":{$counts},\"per10\":${fmt(r.actionableEventsPer10Km)}," +
            "\"tip\":${r.topImprovement?.let { "\"${it.name}\"" } ?: "null"},\"smooth\":${r.smoothTrip}}"
        // 프레임 형식: [t초, km/h, 게이지, 단계(0~2), 배지, 정차여부, 효과음, 이벤트[], 정차카드문구, 정차카드원인]
        return "{\"frames\":[${frames.joinToString(",")}],\"report\":$report}"
    }

    private fun fmt(d: Double) = String.format(Locale.ROOT, "%.1f", d)

    private fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
