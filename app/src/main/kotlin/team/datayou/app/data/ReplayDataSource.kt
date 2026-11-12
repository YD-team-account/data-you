package team.datayou.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import team.datayou.engine.model.TurnSignal
import team.datayou.engine.model.VehicleSample

/**
 * assets/의 주행 CSV(sample-data와 같은 형식)를 10Hz로 재생한다.
 * speedFactor 배속이면 100ms마다 샘플 speedFactor개를 흘려보낸다.
 */
class ReplayDataSource(
    private val context: Context,
    private val assetName: String,
    override val label: String,
    private val scope: CoroutineScope,
    @Volatile var speedFactor: Int = 1,
) : VehicleDataSource {

    private var job: Job? = null

    override fun start(onSample: (VehicleSample) -> Unit, onEnd: () -> Unit) {
        job = scope.launch(Dispatchers.Default) {
            val samples = withContext(Dispatchers.IO) { load() }
            var i = 0
            while (isActive && i < samples.size) {
                val n = speedFactor.coerceAtLeast(1)
                repeat(n) {
                    if (i < samples.size) onSample(samples[i++])
                }
                delay(100)
            }
            if (isActive) onEnd()
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
    }

    private fun load(): List<VehicleSample> =
        context.assets.open(assetName).bufferedReader().useLines { lines ->
            lines.drop(1).filter { it.isNotBlank() }.map { line ->
                val c = line.split(",")
                VehicleSample(
                    timestampMs = c[0].toLong(),
                    speedMps = c[1].toDouble(),
                    yawRateRadPs = c.getOrNull(2)?.takeIf { it.isNotBlank() }?.toDouble(),
                    headingDeg = c.getOrNull(3)?.takeIf { it.isNotBlank() }?.toDouble(),
                    brakePedalOn = c.getOrNull(4)?.takeIf { it.isNotBlank() }?.let { it == "1" },
                    turnSignal = c.getOrNull(5)?.takeIf { it.isNotBlank() }?.let { TurnSignal.valueOf(it) } ?: TurnSignal.OFF,
                )
            }.toList()
        }

    companion object {
        /** 앱에서 고를 수 있는 시연 시나리오 (assets 파일명 → 표시 이름) */
        val SCENARIOS = linkedMapOf(
            "family_trip.csv" to "가족 나들이",
            "mountain_curves.csv" to "산길 연속 커브",
            "city_aggressive.csv" to "시내 급출발·급제동",
            "ev_strong_regen.csv" to "전기차 강한 회생제동",
            "traffic_stop_and_go.csv" to "정체 가다 서다",
            "brake_pumping.csv" to "브레이크 끊어 밟기",
            "smooth_highway.csv" to "부드러운 고속도로",
        )
    }
}
