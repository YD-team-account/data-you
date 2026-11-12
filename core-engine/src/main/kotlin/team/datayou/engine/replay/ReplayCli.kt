package team.datayou.engine.replay

import team.datayou.engine.DataYouEngine
import team.datayou.engine.model.GaugeLevel
import team.datayou.engine.model.PassengerProfile
import team.datayou.engine.model.PassengerType
import team.datayou.engine.model.VehicleSample
import java.io.File

/**
 * 사용법 (프로젝트 루트에서)
 *   ./gradlew :core-engine:run --args="list"
 *   ./gradlew :core-engine:run --args="scenario mountain_curves CHILD"
 *   ./gradlew :core-engine:run --args="csv sample-data/mountain_curves.csv PREGNANT,DOG"
 *   ./gradlew :core-engine:run --args="export sample-data"     # 시나리오 전체를 CSV로 저장
 */
fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        "list" -> Scenarios.all.keys.forEach(::println)
        "scenario" -> {
            val name = args.getOrNull(1) ?: error("시나리오 이름이 필요합니다. list로 확인하세요.")
            val gen = Scenarios.all[name] ?: error("알 수 없는 시나리오: $name")
            replay(gen(), parsePassengers(args.getOrNull(2)), verbose = true)
        }
        "csv" -> {
            val f = File(args.getOrNull(1) ?: error("CSV 경로가 필요합니다"))
            replay(Csv.read(f), parsePassengers(args.getOrNull(2)), verbose = true)
        }
        "summary" -> {
            // README용: 모든 시나리오를 아이 프로필로 재생한 결과 표
            println("| 시나리오 | 최고 게이지 | 감지된 원인 | 다음 주행 팁 |")
            println("|---|---|---|---|")
            for ((n, g) in Scenarios.all) {
                val e = DataYouEngine(parsePassengers(args.getOrNull(1)))
                g().forEach { e.process(it) }
                val r = e.finishTrip()
                val causes = r.eventCounts.entries.joinToString(", ") { "${it.key.label} ${it.value}" }.ifEmpty { "-" }
                println("| `$n` | %.0f | $causes | ${r.topImprovement?.coaching ?: "-"} |".format(r.passengers.maxOf { it.peakGauge }))
            }
        }
        "timeline" -> {
            // 인터랙티브 프로토타입용 JSON (docs/prototype)
            val out = File(args.getOrNull(1) ?: "docs/prototype/timeline.json")
            val names = listOf("family_trip", "mountain_curves", "city_aggressive", "ev_strong_regen", "traffic_stop_and_go", "brake_pumping")
            TimelineExport.write(out, names)
            println("${out.path} 저장 (${out.length() / 1024}KB)")
        }
        "export" -> {
            val dir = File(args.getOrNull(1) ?: "sample-data")
            Scenarios.all.forEach { (n, g) -> Csv.write(File(dir, "$n.csv"), g()) }
            println("${Scenarios.all.size}개 시나리오를 ${dir.path}에 저장했습니다")
        }
        else -> {
            println("명령: list | scenario <이름> [동승자들] | csv <파일> [동승자들] | export <폴더>")
            println("동승자: ${PassengerType.entries.joinToString(",")} (쉼표로 여러 명)")
        }
    }
}

private fun parsePassengers(arg: String?): List<PassengerProfile> =
    (arg ?: "CHILD").split(",").mapIndexed { i, t ->
        PassengerProfile(id = "p$i", type = PassengerType.valueOf(t.trim().uppercase()))
    }

fun replay(samples: List<VehicleSample>, passengers: List<PassengerProfile>, verbose: Boolean) {
    val engine = DataYouEngine(passengers)
    var lastLevel = GaugeLevel.COMFORT
    var lastBadge: String? = null
    for (s in samples) {
        val u = engine.process(s) ?: continue
        if (!verbose) continue
        val sec = "%6.1fs".format(u.timestampMs / 1000.0)
        u.newEvents.forEach { println("$sec  이벤트  ${it.cause.label}  (${it.detail})") }
        if (u.playAlertSound) println("$sec  ♪ 효과음 · 단계 상승 → ${u.focusPassenger.level}")
        val badge = u.badge?.label
        if (u.focusPassenger.level != lastLevel || badge != lastBadge) {
            println("$sec  [주행 중 표시] ${u.focusPassenger.profile.displayName} ${u.focusPassenger.level} 게이지 %.0f · 배지: ${badge ?: "없음"}".format(u.focusPassenger.gauge))
            lastLevel = u.focusPassenger.level
            lastBadge = badge
        }
        u.stopCard?.let { println("$sec  [정차 카드] ${it.message}") }
    }
    println()
    print(engine.finishTrip().toText())
}
