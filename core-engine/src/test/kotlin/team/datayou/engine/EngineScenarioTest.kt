package team.datayou.engine

import team.datayou.engine.model.Cause
import team.datayou.engine.model.GaugeLevel
import team.datayou.engine.model.PassengerProfile
import team.datayou.engine.model.PassengerType
import team.datayou.engine.model.VehicleSample
import team.datayou.engine.replay.ScenarioBuilder
import team.datayou.engine.replay.ScenarioBuilder.Companion.kmh
import team.datayou.engine.replay.Scenarios
import team.datayou.engine.report.TripReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 시나리오별로 원인 7종이 의도대로 분류되는지 검증한다. */
class EngineScenarioTest {

    private class Run(val updates: List<EngineUpdate>, val report: TripReport) {
        fun count(c: Cause) = report.eventCounts[c] ?: 0
        val peak get() = report.passengers.maxOf { it.peakGauge }
    }

    private fun run(samples: List<VehicleSample>, vararg types: PassengerType = arrayOf(PassengerType.CHILD)): Run {
        val engine = DataYouEngine(types.mapIndexed { i, t -> PassengerProfile("p$i", t) })
        val updates = samples.mapNotNull { engine.process(it) }
        return Run(updates, engine.finishTrip())
    }

    @Test
    fun `부드러운 고속도로 주행은 개선 이벤트 없이 편안함을 유지한다`() {
        val r = run(Scenarios.smoothHighway())
        assertEquals(0, r.report.eventCounts.filterKeys { it.actionable }.size)
        assertTrue(r.peak < 40.0, "peak=${r.peak}")
        assertTrue(r.report.smoothTrip)
        assertNull(r.report.topImprovement)
    }

    @Test
    fun `시내 급출발 급제동은 브레이크 급제동과 급출발로 분류되고 정체로 오인하지 않는다`() {
        val r = run(Scenarios.cityAggressive())
        assertTrue(r.count(Cause.HARD_BRAKE) >= 5)
        assertTrue(r.count(Cause.HARD_ACCEL) >= 5)
        assertEquals(0, r.count(Cause.STOP_AND_GO), "신호 사이 50km/h 주행은 정체가 아님")
        assertTrue(r.peak >= 70.0)
        val card = r.updates.mapNotNull { it.stopCard }.firstOrNull()
        assertNotNull(card)
        assertTrue(card.cause in setOf(Cause.HARD_BRAKE, Cause.HARD_ACCEL))
    }

    @Test
    fun `페달 없이 급감속하면 회생 급감속으로 분류한다`() {
        val r = run(Scenarios.evStrongRegen())
        assertTrue(r.count(Cause.REGEN_DECEL) >= 4)
        assertEquals(0, r.count(Cause.HARD_BRAKE))
    }

    @Test
    fun `브레이크 정보가 없는 차량은 급감속을 급제동으로 본다`() {
        val samples = ScenarioBuilder(emitBrake = false)
            .accelTo(kmh(60.0), 1.5).cruise(10.0).decelTo(0.0, 4.0, brake = true).idle(3.0).build()
        val r = run(samples)
        assertEquals(1, r.count(Cause.HARD_BRAKE))
        assertEquals(0, r.count(Cause.REGEN_DECEL))
    }

    @Test
    fun `정체 가다 서다는 감지하되 운전자 탓으로 코칭하지 않는다`() {
        val r = run(Scenarios.trafficStopAndGo())
        assertTrue(r.count(Cause.STOP_AND_GO) >= 1)
        assertEquals(0, r.report.eventCounts.filterKeys { it.actionable }.values.sum())
        assertTrue(r.updates.mapNotNull { it.stopCard }.none { it.cause.actionable })
        assertNull(r.report.topImprovement)
    }

    @Test
    fun `막히지 않는 도로에서 잦은 브레이크는 끊어 밟기로 분류한다`() {
        val r = run(Scenarios.brakePumping())
        assertTrue(r.count(Cause.BRAKE_PUMPING) >= 1)
        assertEquals(0, r.count(Cause.STOP_AND_GO))
        assertEquals(Cause.BRAKE_PUMPING, r.report.topImprovement)
    }

    @Test
    fun `0_2Hz 연속 커브는 아이 게이지를 힘듦까지 올린다`() {
        val r = run(Scenarios.mountainCurves())
        assertTrue(r.count(Cause.CONTINUOUS_CURVES) >= 1)
        assertTrue(r.updates.any { it.focusPassenger.level == GaugeLevel.DISCOMFORT })
        assertTrue(r.updates.any { it.badge == Cause.CONTINUOUS_CURVES })
    }

    @Test
    fun `자이로가 없어도 GPS 방위각으로 연속 커브를 감지한다`() {
        val r = run(Scenarios.mountainCurves(gpsOnly = true))
        assertTrue(r.count(Cause.CONTINUOUS_CURVES) >= 1)
        assertTrue(r.peak >= 70.0, "peak=${r.peak}")
    }

    @Test
    fun `완만한 연속 커브에서 어르신은 아이보다 게이지가 늦게 찬다`() {
        // 어르신 프리셋: 누적(멀미) 민감도는 낮고, 순간 쏠림 민감도는 높다.
        // 순간 임계값 아래의 완만한 커브(1.8m/s²)로 누적 민감도 차이만 비교한다.
        val gentle = ScenarioBuilder().accelTo(kmh(50.0), 1.5).curves(240.0, 0.2, 1.8).build()
        val child = run(gentle, PassengerType.CHILD)
        val senior = run(gentle, PassengerType.SENIOR)
        assertEquals(0, senior.count(Cause.SHARP_LATERAL))
        assertTrue(senior.peak < child.peak * 0.7, "senior=${senior.peak} child=${child.peak}")
    }

    @Test
    fun `어르신은 아이보다 약한 순간 쏠림도 이벤트로 잡는다`() {
        val jolt = ScenarioBuilder().accelTo(kmh(60.0), 1.5).cruise(10.0).laneChange(2.8, 2.5, signal = false).cruise(10.0).build()
        assertEquals(1, run(jolt, PassengerType.SENIOR).count(Cause.SHARP_LATERAL))
        assertEquals(0, run(jolt, PassengerType.CHILD).count(Cause.SHARP_LATERAL))
    }

    @Test
    fun `깜빡이 유무로 급차선변경과 급회전을 구분한다`() {
        val withSignal = run(Scenarios.abruptLaneChange(signal = true))
        val noSignal = run(Scenarios.abruptLaneChange(signal = false))
        val a = withSignal.report.events.first { it.cause == Cause.SHARP_LATERAL }
        val b = noSignal.report.events.first { it.cause == Cause.SHARP_LATERAL }
        assertTrue(a.detail.startsWith("급차선변경"), a.detail)
        assertTrue(b.detail.startsWith("급회전"), b.detail)
    }

    @Test
    fun `부드러운 주행이 이어지면 게이지가 회복되고 배지가 사라진다`() {
        val samples = ScenarioBuilder()
            .accelTo(kmh(50.0), 1.5)
            .curves(240.0, 0.2, 2.5)
            .cruise(600.0)
            .build()
        val r = run(samples)
        assertTrue(r.peak >= 70.0)
        val last = r.updates.last()
        assertEquals(GaugeLevel.COMFORT, last.focusPassenger.level)
        assertNull(last.badge)
    }

    @Test
    fun `여러 동승자 중 가장 힘든 한 명을 대표로 표시한다`() {
        val r = run(Scenarios.cityAggressive(), PassengerType.ADULT, PassengerType.PREGNANT)
        val u = r.updates.last()
        val maxGauge = u.passengers.maxOf { it.gauge }
        assertEquals(maxGauge, u.focusPassenger.gauge)
        assertEquals(PassengerType.PREGNANT, u.focusPassenger.profile.type)
    }
}
