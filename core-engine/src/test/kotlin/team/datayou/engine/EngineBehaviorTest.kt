package team.datayou.engine

import team.datayou.engine.model.PassengerProfile
import team.datayou.engine.model.PassengerType
import team.datayou.engine.model.VehicleSample
import team.datayou.engine.replay.Csv
import team.datayou.engine.replay.ScenarioBuilder
import team.datayou.engine.replay.ScenarioBuilder.Companion.kmh
import team.datayou.engine.replay.Scenarios
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** UI 규칙(배지 유지·효과음)과 부가 기능을 검증한다. */
class EngineBehaviorTest {

    private fun updates(samples: List<VehicleSample>, type: PassengerType = PassengerType.CHILD) =
        DataYouEngine(listOf(PassengerProfile("p", type))).let { e -> samples.mapNotNull { e.process(it) } }

    @Test
    fun `원인 배지는 한 번 뜨면 30초 안에 다른 원인으로 바뀌지 않는다`() {
        val samples = ScenarioBuilder()
            .accelTo(kmh(50.0), 1.5)
            .curves(200.0, 0.2, 2.5)
            .decelTo(kmh(10.0), 4.5, brake = true)
            .accelTo(kmh(50.0), 3.2)
            .decelTo(kmh(10.0), 4.5, brake = true)
            .accelTo(kmh(50.0), 3.2)
            .curves(60.0, 0.2, 2.5)
            .build()
        val u = updates(samples)
        var prev: Pair<Any?, Long>? = null
        for (x in u) {
            val p = prev
            if (p != null && p.first != null && x.badge != null && x.badge != p.first) {
                val held = (x.timestampMs - p.second) / 1000.0
                assertTrue(held >= 30.0, "배지가 ${held}초 만에 바뀜")
            }
            if (p == null || x.badge != p.first) prev = x.badge to x.timestampMs
        }
    }

    @Test
    fun `효과음은 단계가 올라갈 때만 울린다`() {
        val u = updates(Scenarios.mountainCurves())
        val sounds = u.count { it.playAlertSound }
        val ups = u.zipWithNext().count { (a, b) -> b.focusPassenger.level.ordinal > a.focusPassenger.level.ordinal }
        assertEquals(ups, sounds)
        assertTrue(sounds in 1..2)
    }

    @Test
    fun `예측 안내는 주의 이상이고 회전이 300m 안일 때만 켜진다`() {
        val engine = DataYouEngine(listOf(PassengerProfile("p", PassengerType.CHILD)))
        assertFalse(engine.shouldPreAlert(200.0, isTurnManeuver = true), "편안할 때는 안내 안 함")
        Scenarios.mountainCurves().take(1500).forEach { engine.process(it) }
        assertTrue(engine.shouldPreAlert(200.0, isTurnManeuver = true))
        assertFalse(engine.shouldPreAlert(500.0, isTurnManeuver = true))
        assertFalse(engine.shouldPreAlert(200.0, isTurnManeuver = false))
    }

    @Test
    fun `원안 모드는 10분 안에 순간 이벤트 3회부터 알린다`() {
        val u = updates(Scenarios.cityAggressive(), PassengerType.ADULT)
        val firstLegacy = u.indexOfFirst { it.legacyAlert }
        val momentsBefore = u.take(firstLegacy + 1).sumOf { x -> x.newEvents.count { it.cause.kind.name == "MOMENT" } }
        assertTrue(firstLegacy >= 0)
        assertEquals(3, momentsBefore)
    }

    @Test
    fun `속 안 좋아 피드백은 민감도를 올리고 범위를 벗어나지 않는다`() {
        var p = PassengerProfile("p", PassengerType.ADULT)
        val base = p.doseSensitivity
        p = p.withFeedback(uncomfortable = true)
        assertTrue(p.doseSensitivity > base)
        repeat(50) { p = p.withFeedback(uncomfortable = true) }
        assertEquals(base * 2.0, p.doseSensitivity, 1e-9)
        repeat(100) { p = p.withFeedback(uncomfortable = false) }
        assertEquals(base * 0.5, p.doseSensitivity, 1e-9)
    }

    @Test
    fun `CSV로 저장했다 읽어도 같은 샘플이 나온다`() {
        val dir = createTempDirectory().toFile()
        val f = File(dir, "x.csv")
        val original = ScenarioBuilder(emitYaw = false).accelTo(kmh(30.0), 2.0).cruise(5.0).build()
        Csv.write(f, original)
        val back = Csv.read(f)
        assertEquals(original.size, back.size)
        assertEquals(original.last().timestampMs, back.last().timestampMs)
        assertEquals(original.last().speedMps, back.last().speedMps, 1e-3)
        assertEquals(null, back.first().yawRateRadPs)
    }

    @Test
    fun `주격 조사를 받침에 맞게 붙인다`() {
        assertEquals("아이가", withSubjectParticle("아이"))
        assertEquals("어르신이", withSubjectParticle("어르신"))
        assertEquals("반려견이", withSubjectParticle("반려견"))
        assertEquals("임산부가", withSubjectParticle("임산부"))
    }
}
