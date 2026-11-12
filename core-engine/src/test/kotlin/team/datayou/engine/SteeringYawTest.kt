package team.datayou.engine

import team.datayou.engine.model.Cause
import team.datayou.engine.model.PassengerProfile
import team.datayou.engine.model.PassengerType
import team.datayou.engine.replay.Scenarios
import team.datayou.engine.signal.SteeringYaw
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Pleos 조향각 → 요레이트 변환과, 그 변환을 거쳐도 원인 판별이 유지되는지 검증한다. */
class SteeringYawTest {
    private val sy = SteeringYaw()

    @Test
    fun `중립 근처와 정차 중에는 회전 없음`() {
        assertEquals(0.0, sy.yawRate(20.0, 1.0))
        assertEquals(0.0, sy.yawRate(0.0, 90.0))
    }

    @Test
    fun `왼쪽이 + 이고 크기는 자전거 모델과 같다`() {
        val v = 30 / 3.6
        val yaw = sy.yawRate(v, 45.0)
        assertTrue(yaw > 0)
        assertTrue(sy.yawRate(v, -45.0) < 0)
        // 시속 30km, 핸들 45도 → 좌우 가속 약 1.3 m/s² (일반 교차로 회전 수준)
        val lat = v * yaw
        assertTrue(lat in 1.1..1.5, "lat=$lat")
    }

    @Test
    fun `역변환 왕복`() {
        val v = 15.0
        for (yaw in listOf(-0.3, -0.05, 0.08, 0.25)) {
            val deg = sy.steeringWheelDegFor(v, yaw)
            assertEquals(yaw, sy.yawRate(v, deg), 1e-6)
        }
    }

    @Test
    fun `산길 시나리오를 조향각으로만 넣어도 연속 커브를 잡는다`() {
        // 시나리오의 방위각을 버리고, 요레이트 → 핸들 각도(소수 1자리로 반올림, VHAL 정밀도 가정) → 요레이트로 바꿔 넣는다
        val base = Scenarios.all.getValue("mountain_curves")()
        var prevHeading: Double? = null
        var prevTs = 0L
        val viaSteering = base.map { s ->
            val yaw = s.yawRateRadPs ?: s.headingDeg?.let { h ->
                val p = prevHeading
                prevHeading = h
                val dt = (s.timestampMs - prevTs) / 1000.0
                prevTs = s.timestampMs
                if (p == null || dt <= 0) 0.0 else {
                    var d = h - p
                    if (d > 180) d -= 360
                    if (d < -180) d += 360
                    Math.toRadians(d) / dt
                }
            } ?: 0.0
            val deg = Math.round(sy.steeringWheelDegFor(s.speedMps, yaw) * 10) / 10.0
            s.copy(yawRateRadPs = sy.yawRate(s.speedMps, deg), headingDeg = null)
        }
        val engine = DataYouEngine(listOf(PassengerProfile("c", PassengerType.CHILD)))
        val causes = viaSteering.mapNotNull { engine.process(it) }.flatMap { it.newEvents }.map { it.cause }.toSet()
        val report = engine.finishTrip()
        assertTrue(Cause.CONTINUOUS_CURVES in causes || report.eventCounts.containsKey(Cause.CONTINUOUS_CURVES), "causes=$causes")
        assertTrue(abs(report.passengers.first().peakGauge) > 40, "peak=${report.passengers.first().peakGauge}")
    }
}
