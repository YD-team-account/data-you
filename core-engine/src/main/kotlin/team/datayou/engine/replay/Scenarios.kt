package team.datayou.engine.replay

import team.datayou.engine.model.VehicleSample
import team.datayou.engine.replay.ScenarioBuilder.Companion.kmh

/** 데모·테스트용 표준 시나리오 모음. */
object Scenarios {

    /** 고속도로를 부드럽게 달리고, 깜빡이를 켜고 완만하게 차선 변경. */
    fun smoothHighway(): List<VehicleSample> = ScenarioBuilder(seed = 1)
        .accelTo(kmh(100.0), 1.2)
        .cruise(90.0)
        .laneChange(1.0, durationSec = 4.0, signal = true)
        .cruise(90.0)
        .laneChange(1.0, durationSec = 4.0, signal = true)
        .cruise(60.0)
        .decelTo(0.0, 1.0, brake = true)
        .idle(5.0)
        .build()

    /** 시내 급출발·급제동 반복 (브레이크 페달 사용). */
    fun cityAggressive(): List<VehicleSample> {
        val b = ScenarioBuilder(seed = 2)
        repeat(6) {
            b.accelTo(kmh(50.0), 3.2).cruise(15.0).decelTo(0.0, 4.5, brake = true).idle(8.0)
        }
        return b.build()
    }

    /** 전기차 강한 회생제동: 페달을 떼면 급감속 (브레이크 페달 미작동). */
    fun evStrongRegen(): List<VehicleSample> {
        val b = ScenarioBuilder(seed = 3)
        b.accelTo(kmh(60.0), 1.5)
        repeat(5) {
            b.cruise(10.0).decelTo(kmh(25.0), 3.4, brake = false).accelTo(kmh(60.0), 1.5)
        }
        return b.cruise(10.0).decelTo(0.0, 1.2, brake = true).idle(5.0).build()
    }

    /** 정체 구간 가다 서다 (운전자 탓 아님). */
    fun trafficStopAndGo(): List<VehicleSample> {
        val b = ScenarioBuilder(seed = 4)
        repeat(8) {
            b.accelTo(kmh(18.0), 1.6).cruise(4.0).decelTo(0.0, 1.8, brake = true).idle(6.0)
        }
        return b.build()
    }

    /** 막히지 않는 도로에서 브레이크를 짧게 자주 끊어 밟음 (운전 습관). */
    fun brakePumping(): List<VehicleSample> {
        val b = ScenarioBuilder(seed = 5).accelTo(kmh(60.0), 1.5).cruise(20.0)
        repeat(14) { b.brakeTap(tapSec = 1.0, decel = 1.8, thenCruiseSec = 6.0) }
        return b.cruise(20.0).decelTo(0.0, 1.2, brake = true).idle(5.0).build()
    }

    /**
     * 산길 연속 커브: 시속 42km로 반경 52~72m, 45~80도 커브가 좌우로 이어진다.
     * 커브 하나에 약 5초 → 좌우 흔들림 약 0.1Hz (멀미 유발 대역 하단). 지도에 그리면 실제 굽이길.
     */
    fun mountainCurves(gpsOnly: Boolean = false): List<VehicleSample> = ScenarioBuilder(seed = 6, emitYaw = !gpsOnly)
        .accelTo(kmh(42.0), 1.5)
        .cruise(6.0)
        .winding(mountainRoad(seed = 6, count = 48, minR = 52.0, maxR = 72.0))
        .cruise(20.0)
        .decelTo(0.0, 1.2, brake = true)
        .idle(5.0)
        .build()

    /** 좌우가 번갈아 나오는 산길 커브 목록 (회전각 도, 반경 m). */
    fun mountainRoad(seed: Int, count: Int, minR: Double = 50.0, maxR: Double = 75.0): List<Pair<Double, Double>> {
        val r = kotlin.random.Random(seed)
        return List(count) { i ->
            val angle = 45.0 + r.nextDouble() * 35.0
            val radius = minR + r.nextDouble() * (maxR - minR)
            (if (i % 2 == 0) angle else -angle) to radius
        }
    }

    /** 깜빡이 없이 급하게 차선 변경. */
    fun abruptLaneChange(signal: Boolean): List<VehicleSample> = ScenarioBuilder(seed = 7)
        .accelTo(kmh(80.0), 1.5)
        .cruise(20.0)
        .laneChange(4.0, durationSec = 2.3, signal = signal) // 2.3초에 한 차로(약 3.4m) 이동, 최대 좌우 4m/s²
        .cruise(20.0)
        .build()

    /**
     * 시연용 가족 나들이 (약 9분): 시내 출발 → 신호 대기·교차로 회전 → 전기차 회생 감속 → 고속도로
     * → 강변 연속 커브 → 정체 → 도착. 원인 여러 종류와 정차 카드가 한 번에 나온다.
     */
    fun familyTrip(): List<VehicleSample> {
        val b = ScenarioBuilder(seed = 8)
        // 시내: 급출발·급제동, 교차로 우회전·좌회전
        b.idle(3.0).accelTo(kmh(50.0), 3.2).cruise(12.0).decelTo(0.0, 4.5, brake = true).idle(10.0)
        b.accelTo(kmh(40.0), 2.0).cruise(4.0).turnAtIntersection(-90.0, resumeSpeed = kmh(50.0))
        b.accelTo(kmh(50.0), 3.0).cruise(10.0).decelTo(0.0, 4.2, brake = true).idle(12.0)
        b.accelTo(kmh(40.0), 2.0).cruise(5.0).turnAtIntersection(90.0, resumeSpeed = kmh(40.0))
        // 회생제동 강하게
        b.accelTo(kmh(60.0), 1.6)
        repeat(3) { b.cruise(8.0).decelTo(kmh(30.0), 3.4, brake = false).accelTo(kmh(60.0), 1.6) }
        // 고속도로: 부드럽게, 깜빡이 켜고 차선 변경
        b.accelTo(kmh(90.0), 1.2).cruise(40.0).laneChange(1.2, 4.0, signal = true).cruise(30.0)
        // 강변 연속 커브 (반경 60~80m, 시속 40km)
        b.decelTo(kmh(40.0), 1.2, brake = true).winding(mountainRoad(seed = 8, count = 26, minR = 60.0, maxR = 80.0)).cruise(10.0)
        b.decelTo(0.0, 1.5, brake = true).idle(12.0)
        // 정체 구간
        repeat(4) { b.accelTo(kmh(16.0), 1.5).cruise(3.0).decelTo(0.0, 1.7, brake = true).idle(5.0) }
        // 도착 전 부드러운 주행으로 회복
        b.accelTo(kmh(40.0), 1.2).cruise(60.0).decelTo(0.0, 1.2, brake = true).idle(5.0)
        return b.build()
    }

    val all: Map<String, () -> List<VehicleSample>> = linkedMapOf(
        "family_trip" to ::familyTrip,
        "smooth_highway" to ::smoothHighway,
        "city_aggressive" to ::cityAggressive,
        "ev_strong_regen" to ::evStrongRegen,
        "traffic_stop_and_go" to ::trafficStopAndGo,
        "brake_pumping" to ::brakePumping,
        "mountain_curves" to { mountainCurves(false) },
        "mountain_curves_gps_only" to { mountainCurves(true) },
        "abrupt_lane_change" to { abruptLaneChange(false) },
    )
}
