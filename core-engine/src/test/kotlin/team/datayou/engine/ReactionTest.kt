package team.datayou.engine

import team.datayou.engine.model.Cause
import team.datayou.engine.model.PassengerProfile
import team.datayou.engine.model.PassengerType
import team.datayou.engine.model.ReactionKind
import team.datayou.engine.model.VehicleSample
import team.datayou.engine.replay.Scenarios
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 캐릭터 반응(몸이 쏠리는 방향)과 휴식 표정 규칙. */
class ReactionTest {

    private fun run(samples: List<VehicleSample>, type: PassengerType = PassengerType.CHILD): List<EngineUpdate> {
        val e = DataYouEngine(listOf(PassengerProfile("p", type)))
        return samples.mapNotNull { e.process(it) }
    }

    @Test
    fun `급출발은 뒤로 젖히고 급제동은 앞으로 쏠린다`() {
        val rs = run(Scenarios.cityAggressive()).mapNotNull { it.reaction }
        val byCause = rs.groupBy { it.cause }
        assertTrue(byCause.getValue(Cause.HARD_ACCEL).all { it.kind == ReactionKind.LEAN_BACK })
        assertTrue(byCause.getValue(Cause.HARD_BRAKE).all { it.kind == ReactionKind.LEAN_FORWARD })
    }

    @Test
    fun `회생 급감속도 앞으로 쏠린다`() {
        val rs = run(Scenarios.evStrongRegen()).mapNotNull { it.reaction }
        assertTrue(rs.any { it.cause == Cause.REGEN_DECEL && it.kind == ReactionKind.LEAN_FORWARD })
    }

    @Test
    fun `왼쪽으로 차선을 바꾸면 몸은 오른쪽으로 기울어진다`() {
        // 시나리오의 차선 변경은 왼쪽으로 먼저 꺾는다 (좌우 가속 +)
        val r = run(Scenarios.abruptLaneChange(signal = true)).mapNotNull { it.reaction }.first { it.cause == Cause.SHARP_LATERAL }
        assertEquals(ReactionKind.TILT_RIGHT, r.kind)
    }

    @Test
    fun `연속 커브는 좌우 기울어짐으로 반응한다`() {
        val rs = run(Scenarios.mountainCurves()).mapNotNull { it.reaction }
        assertTrue(rs.isNotEmpty())
        assertTrue(rs.all { it.kind == ReactionKind.TILT_LEFT || it.kind == ReactionKind.TILT_RIGHT })
    }

    @Test
    fun `정체는 캐릭터 반응 대상이 아니다`() {
        val rs = run(Scenarios.trafficStopAndGo()).mapNotNull { it.reaction }
        assertTrue(rs.none { it.cause == Cause.STOP_AND_GO })
    }

    @Test
    fun `반응은 최소 2초 간격으로만 나온다`() {
        val ts = run(Scenarios.cityAggressive()).mapNotNull { it.reaction?.timestampMs }
        assertTrue(ts.zipWithNext().all { (a, b) -> b - a >= 2000 })
    }

    @Test
    fun `부드러운 고속도로를 1분 넘게 달리면 쉬는 표정이 된다`() {
        val us = run(Scenarios.smoothHighway())
        assertTrue(us.any { it.resting })
        // 출발 직후 1분 동안은 아직 쉬지 않는다
        assertFalse(us.filter { it.timestampMs < 60_000 }.any { it.resting })
        // 쉬는 동안에는 늘 편안 단계이고 달리는 중이다
        assertTrue(us.filter { it.resting }.all { !it.vehicleStopped && it.focusPassenger.level.ordinal == 0 })
    }

    @Test
    fun `급한 운전이 이어지면 쉬지 않는다`() {
        val us = run(Scenarios.cityAggressive())
        val restMs = us.filter { it.resting }.size * 100L
        assertTrue(restMs < 20_000, "rest=${restMs}ms")
    }
}
