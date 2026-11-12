package team.datayou.engine.replay

import team.datayou.engine.model.TurnSignal
import team.datayou.engine.model.VehicleSample
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * 합성 주행 데이터 생성기 (10Hz).
 * 실주행 데이터를 모으기 전, 엔진 동작 검증과 에뮬레이터 시연용으로 쓴다.
 *
 * 예) ScenarioBuilder().cruise(30.0, kmh(60.0)).decelTo(0.0, 4.0, brake = true).build()
 */
class ScenarioBuilder(
    private val hz: Int = 10,
    seed: Int = 42,
    private val speedNoise: Double = 0.03,
    private val emitYaw: Boolean = true,
    private val emitGpsHeading: Boolean = true,
    private val emitBrake: Boolean = true,
) {
    private val rnd = Random(seed)
    private val out = mutableListOf<VehicleSample>()
    private var t = 0L
    private var v = 0.0
    private var heading = 90.0
    private var gpsHeading = 90.0
    private var lastGpsMs = 0L
    private val dt = 1.0 / hz

    private fun push(yaw: Double, brake: Boolean, signal: TurnSignal = TurnSignal.OFF) {
        heading = (heading + yaw * dt * 180.0 / PI + 360.0) % 360.0
        if (t - lastGpsMs >= 1000) { // GPS 방위각은 1Hz로만 갱신
            gpsHeading = heading
            lastGpsMs = t
        }
        out += VehicleSample(
            timestampMs = t,
            speedMps = max(0.0, v + rnd.nextDouble(-speedNoise, speedNoise)),
            yawRateRadPs = if (emitYaw) yaw + rnd.nextDouble(-0.002, 0.002) else null,
            headingDeg = if (emitGpsHeading) gpsHeading else null,
            brakePedalOn = if (emitBrake) brake else null,
            turnSignal = signal,
        )
        t += (1000 / hz)
    }

    /** 일정 속도로 주행 (yaw 0). */
    fun cruise(sec: Double, speedMps: Double = v): ScenarioBuilder = apply {
        v = speedMps
        repeat((sec * hz).toInt()) { push(0.0, false) }
    }

    /** 정지 상태로 대기. */
    fun idle(sec: Double): ScenarioBuilder = apply {
        v = 0.0
        repeat((sec * hz).toInt()) { push(0.0, brake = true) }
    }

    /** 목표 속도까지 일정 가속. */
    fun accelTo(target: Double, accel: Double): ScenarioBuilder = apply {
        while (v < target) {
            v = min(target, v + accel * dt)
            push(0.0, false)
        }
    }

    /** 목표 속도까지 일정 감속. brake=false면 회생·관성 감속(페달 미작동). */
    fun decelTo(target: Double, decel: Double, brake: Boolean): ScenarioBuilder = apply {
        while (v > target) {
            v = max(target, v - decel * dt)
            push(0.0, brake)
        }
    }

    /**
     * 연속 커브. 좌우 가속 a_lat = v·ω 가 진폭 latAmp, 주파수 freqHz로 흔들린다.
     */
    fun curves(sec: Double, freqHz: Double, latAmp: Double): ScenarioBuilder = apply {
        val n = (sec * hz).toInt()
        for (i in 0 until n) {
            val lat = latAmp * sin(2 * PI * freqHz * i * dt)
            val yaw = if (v > 0.5) lat / v else 0.0
            push(yaw, false)
        }
    }

    /** 차선 변경 1회 (좌우 가속 펄스). signal=true면 깜빡이를 1.5초 전부터 켬. */
    fun laneChange(latPeak: Double, durationSec: Double = 2.0, signal: Boolean): ScenarioBuilder = apply {
        if (signal) repeat((1.5 * hz).toInt()) { push(0.0, false, TurnSignal.LEFT) }
        val n = (durationSec * hz).toInt()
        for (i in 0 until n) {
            val lat = latPeak * sin(2 * PI * i / n) // 왼쪽 → 오른쪽 한 주기
            val yaw = if (v > 0.5) lat / v else 0.0
            push(yaw, false, if (signal) TurnSignal.LEFT else TurnSignal.OFF)
        }
    }

    /** 브레이크를 짧게 톡 밟고 놓기 (끊어 밟기 패턴용). */
    fun brakeTap(tapSec: Double, decel: Double, thenCruiseSec: Double, recoverAccel: Double = 0.8): ScenarioBuilder = apply {
        val start = v
        repeat((tapSec * hz).toInt()) {
            v = max(0.0, v - decel * dt)
            push(0.0, true)
        }
        var left = (thenCruiseSec * hz).toInt()
        while (left-- > 0) {
            v = min(start, v + recoverAccel * dt)
            push(0.0, false)
        }
    }

    fun build(): List<VehicleSample> = out.toList()

    companion object {
        fun kmh(x: Double) = x / 3.6
    }
}
