package team.datayou.engine.signal

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.tan

/**
 * 조향각(핸들 각도)으로 요레이트(yaw rate)를 추정한다. 자전거 모델(kinematic bicycle model).
 *
 *   바퀴 조향각 δ = 핸들 각도 / 조향비
 *   yaw rate     = v · tan(δ) / 축간거리
 *
 * Pleos Vehicle SDK `SteeringWheel.registerSteeringWheelAngle`(핸들 각도, 도)를 받아
 * 자이로가 없는 차량에서도 좌우 가속(= v · yaw)을 계산하기 위해 쓴다.
 * 저속·큰 조향에서는 타이어 미끄러짐을 무시하므로 약간 과대 추정될 수 있다.
 *
 * 기본값은 준대형 세단 기준(조향비 14.5 : 1, 축간거리 2.9 m). 차종별 값은 CarInfo로 보정할 수 있다.
 */
data class SteeringYaw(
    val steeringRatio: Double = 14.5,
    val wheelbaseM: Double = 2.9,
    /** 핸들 중립 근처의 흔들림은 0으로 본다 (도) */
    val deadbandDeg: Double = 1.5,
) {
    /** @param steeringWheelDeg 핸들 각도(도). 왼쪽(반시계)이 +. @return rad/s, 왼쪽 회전이 + */
    fun yawRate(speedMps: Double, steeringWheelDeg: Double): Double {
        if (abs(steeringWheelDeg) < deadbandDeg || speedMps <= 0.0) return 0.0
        val wheelRad = steeringWheelDeg / steeringRatio * PI / 180.0
        return speedMps * tan(wheelRad) / wheelbaseM
    }

    /** 역변환: 요레이트를 만들려면 핸들을 몇 도 돌려야 하나. 시나리오 CSV → 에뮬레이터 조향각 주입에 쓴다. */
    fun steeringWheelDegFor(speedMps: Double, yawRateRadPs: Double): Double {
        if (speedMps < 0.5) return 0.0
        return atan(yawRateRadPs * wheelbaseM / speedMps) * 180.0 / PI * steeringRatio
    }
}
