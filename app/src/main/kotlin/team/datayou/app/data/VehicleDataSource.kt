package team.datayou.app.data

import team.datayou.engine.model.VehicleSample

/**
 * 엔진에 넣을 차량 샘플의 출처.
 * - [ReplayDataSource]      : sample-data CSV 재생 (일반 AAOS 에뮬레이터·시연)
 * - [PleosVehicleDataSource] : AAOS 속도 + Pleos Vehicle SDK(Brake·TurnSignal·SteeringWheel·Odometer)
 */
interface VehicleDataSource {
    /** 화면에 보여줄 이름 */
    val label: String

    /** 샘플이 들어올 때마다 onSample 호출. onEnd는 재생이 끝났을 때(실시간 소스는 호출 안 함). */
    fun start(onSample: (VehicleSample) -> Unit, onEnd: () -> Unit)

    fun stop()
}

/**
 * 다음 회전 안내(TBT) 정보. 실시간 모드는 NaviHelper(`PleosNavigation`),
 * 재생 모드는 주행거리 기반 가짜 안내(`FakeNavigation`)를 쓴다.
 */
data class TbtInfo(val distanceM: Double, val isTurn: Boolean, val text: String)

class FakeNavigation(private val intervalM: Double = 1200.0) : NavigationSource {
    private var distM = 0.0
    private var lastTs: Long? = null

    override fun update(sample: VehicleSample): TbtInfo {
        lastTs?.let { distM += sample.speedMps * (sample.timestampMs - it) / 1000.0 }
        lastTs = sample.timestampMs
        val remain = intervalM - (distM % intervalM)
        return TbtInfo(remain, isTurn = true, text = "우회전 · 강변로")
    }
}
