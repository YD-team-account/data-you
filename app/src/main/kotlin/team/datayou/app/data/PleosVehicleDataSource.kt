package team.datayou.app.data

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import team.datayou.engine.model.TurnSignal
import team.datayou.engine.model.VehicleSample
import team.datayou.engine.signal.SteeringYaw

/**
 * Pleos Connect 실시간 데이터 소스 (M3).
 *
 * Pleos 정책(AAOS 표준 API가 있으면 표준 우선)에 따라:
 * - 속도        : AAOS CarPropertyManager PERF_VEHICLE_SPEED
 * - 브레이크 페달 : Pleos Vehicle SDK Brake.registerBrakePedalStatus        (pleos.car.permission.CAR_DRIVE)
 * - 방향지시등    : Pleos Vehicle SDK TurnSignal.registerTurnSignalState     (pleos.car.permission.CAR_LIGHTS)
 * - 핸들 각도    : Pleos Vehicle SDK SteeringWheel.registerSteeringWheelAngle → [SteeringYaw]로 요레이트 추정
 * - 누적 주행거리 : Pleos Vehicle SDK Odometer.registerDistanceDriven         → 리포트 주행거리
 *
 * 채널마다 따로 연결하고, 안 되는 채널은 null로 두고 계속 동작한다(차종별 지원 차이).
 * 일반 AAOS 에뮬레이터에서는 Pleos 서비스가 없으므로 속도만 들어온다.
 * 콜백은 불규칙하게 오므로 마지막 값을 들고 있다가 10Hz로 샘플을 만든다.
 */
class PleosVehicleDataSource(
    private val context: Context,
    private val scope: CoroutineScope,
    private val steering: SteeringYaw = SteeringYaw(),
) : VehicleDataSource {

    override val label: String = "실시간 · Pleos 차량"

    /** 채널별 수신 상태. 값이 한 번이라도 들어오면 true. */
    data class Channels(
        val speed: Boolean = false,
        val brake: Boolean = false,
        val turnSignal: Boolean = false,
        val steering: Boolean = false,
        val odometer: Boolean = false,
    ) {
        fun summary(unsupported: Set<String> = emptySet()) = listOf(
            Triple("속도", "speed", speed), Triple("브레이크", "brake", brake), Triple("깜빡이", "turnSignal", turnSignal),
            Triple("조향", "steering", steering), Triple("주행거리", "odometer", odometer),
        ).joinToString(" ") { (n, key, ok) -> n + when { ok -> "✓"; key in unsupported -> "✗"; else -> "–" } }
    }

    @Volatile var channels = Channels()
        private set

    /** 데모 막대 표시용: ✓ 수신 중, – 아직 없음, ✗ 차종 미지원 */
    fun channelSummary(): String = channels.summary(unsupported)

    /** 속도 채널 연결 실패 (Car 서비스·권한 문제) */
    @Volatile var available: Boolean = true
        private set

    @Volatile private var speedMps = 0.0
    @Volatile private var brakeOn: Boolean? = null
    @Volatile private var signal = TurnSignal.OFF
    @Volatile private var steeringDeg: Double? = null
    @Volatile private var odometerStartKm: Double? = null
    @Volatile private var odometerNowKm: Double? = null

    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null
    private var vehicle: Vehicle? = null
    private var job: Job? = null

    // ── AAOS 속도 ──
    private val speedCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            if (value.propertyId == VehiclePropertyIds.PERF_VEHICLE_SPEED) {
                (value.value as? Float)?.let {
                    speedMps = it.toDouble()
                    mark { copy(speed = true) }
                }
            }
        }

        override fun onErrorEvent(propertyId: Int, zone: Int) {
            Log.w(TAG, "speed error id=$propertyId zone=$zone")
        }
    }

    // ── Pleos Vehicle SDK 리스너 ──
    private val brakeListener = object : BrakePedalStatusListener {
        override fun onFailed(e: Exception) = failed("brake", e)
        override fun onBrakePedalStatusUpdated(status: Boolean?) {
            brakeOn = status
            mark { copy(brake = true) }
        }
    }
    private val turnListener = object : TurnSignalStateListener {
        override fun onFailed(e: Exception) = failed("turn", e)
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {
            signal = when (state) {
                TurnSignalValue.LEFT -> TurnSignal.LEFT
                TurnSignalValue.RIGHT -> TurnSignal.RIGHT
                else -> TurnSignal.OFF
            }
            mark { copy(turnSignal = true) }
        }
    }
    private val steeringListener = object : SteeringWheelAngleListener {
        override fun onFailed(e: Exception) = failed("steering", e)
        override fun onSteeringWheelAngleUpdated(angle: Float?) {
            steeringDeg = angle?.toDouble()
            mark { copy(steering = true) }
        }
    }
    private val odometerListener = object : DistanceDrivenListener {
        override fun onFailed(e: Exception) = failed("odometer", e)
        override fun onDistanceDrivenUpdated(km: Float?) {
            val v = km?.toDouble() ?: return
            if (odometerStartKm == null) odometerStartKm = v
            odometerNowKm = v
            mark { copy(odometer = true) }
        }
    }

    override fun start(onSample: (VehicleSample) -> Unit, onEnd: () -> Unit) {
        connectSpeed()
        connectPleos()
        val t0 = System.currentTimeMillis()
        job = scope.launch(Dispatchers.Default) {
            var lastLog = 0L
            while (isActive) {
                val now = System.currentTimeMillis() - t0
                val v = speedMps
                onSample(
                    VehicleSample(
                        timestampMs = now,
                        speedMps = v,
                        yawRateRadPs = steeringDeg?.let { steering.yawRate(v, it) },
                        brakePedalOn = brakeOn,
                        turnSignal = signal,
                    ),
                )
                if (now - lastLog >= 2000) {
                    lastLog = now
                    Log.i(TAG, "t=${now / 1000}s v=${"%.1f".format(v * 3.6)}km/h brake=$brakeOn signal=$signal steer=$steeringDeg odo=$odometerNowKm | ${channelSummary()}")
                }
                delay(100)
            }
        }
    }

    /** 이번 주행 거리(km). 주행거리 채널이 없으면 null → 엔진이 속도 적분값을 쓴다. */
    fun tripDistanceKm(): Double? {
        val a = odometerStartKm ?: return null
        val b = odometerNowKm ?: return null
        return (b - a).takeIf { it >= 0 }
    }

    private fun connectSpeed() {
        try {
            val c = Car.createCar(context)
            car = c
            val pm = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            propertyManager = pm
            available = pm.registerCallback(speedCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_FASTEST)
        } catch (e: Exception) {
            Log.w(TAG, "AAOS speed unavailable", e)
            available = false
        }
    }

    /**
     * 채널마다 ① checkXxxCapabilities로 차종 지원 여부를 확인하고 ② 지원하면 리스너를 등록한다.
     * Pleos 서비스 연결은 비동기라 initialize 직후 호출이 실패할 수 있어, 응답이 없거나 실패한 채널은
     * 1초 간격으로 최대 10번 다시 시도한다. 미지원으로 확인된 채널은 다시 시도하지 않는다.
     */
    private fun connectPleos() {
        val v = try {
            Vehicle(context).also { it.initialize() }
        } catch (e: Throwable) {
            Log.w(TAG, "Pleos Vehicle SDK unavailable (일반 AAOS 에뮬레이터?)", e)
            return
        }
        vehicle = v
        // 이름 → (지원 여부 확인, 등록)
        val channelsToConnect: Map<String, Pair<(ok: () -> Unit, fail: (Exception) -> Unit) -> Unit, () -> Unit>> = linkedMapOf(
            "brake" to Pair({ ok, fail -> v.getBrake().checkBrakePedalStatusCapabilities({ ok() }, fail) }, { v.getBrake().registerBrakePedalStatus(brakeListener) }),
            "turnSignal" to Pair({ ok, fail -> v.getTurnSignal().checkTurnSignalStateCapabilities({ ok() }, fail) }, { v.getTurnSignal().registerTurnSignalState(turnListener) }),
            "steering" to Pair({ ok, fail -> v.getSteeringWheel().checkSteeringWheelAngleCapabilities({ _, _ -> ok() }, fail) }, { v.getSteeringWheel().registerSteeringWheelAngle(steeringListener) }),
            "odometer" to Pair({ ok, fail -> v.getOdometer().checkCurrentDistanceDrivenCapabilities({ ok() }, fail) }, { v.getOdometer().registerDistanceDriven(odometerListener) }),
        )
        val done = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        scope.launch(Dispatchers.Default) {
            repeat(10) { attempt ->
                channelsToConnect.filterKeys { it !in done }.forEach { (name, pair) ->
                    val (check, register) = pair
                    try {
                        check({
                            try {
                                register()
                                done += name
                                Log.i(TAG, "registered $name (attempt ${attempt + 1})")
                            } catch (e: Throwable) {
                                Log.w(TAG, "register $name failed: ${e.javaClass.simpleName} ${e.message}")
                            }
                        }, { e ->
                            Log.w(TAG, "$name capability check failed (attempt ${attempt + 1}): ${e.javaClass.simpleName} ${e.message}")
                            if (e.isUnsupported()) {
                                done += name
                                unsupported += name
                            }
                        })
                    } catch (e: Throwable) {
                        Log.w(TAG, "$name not ready (attempt ${attempt + 1}): ${e.javaClass.simpleName} ${e.message}")
                    }
                }
                delay(1000)
                if (done.size == channelsToConnect.size) {
                    Log.i(TAG, "pleos channels settled; unsupported=$unsupported")
                    return@launch
                }
            }
            Log.w(TAG, "gave up: ${channelsToConnect.keys - done}")
        }
    }

    /** 차종 미지원으로 확인된 채널 (요약에 ✗로 표시) */
    private val unsupported = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private fun Exception.isUnsupported(): Boolean {
        val m = (message ?: "") + javaClass.simpleName
        return listOf("not support", "unsupported", "NotSupported", "NOT_AVAILABLE", "not available").any { m.contains(it, ignoreCase = true) }
    }

    override fun stop() {
        job?.cancel()
        runCatching { propertyManager?.unregisterCallback(speedCallback) }
        runCatching { car?.disconnect() }
        vehicle?.let { v ->
            runCatching { v.getBrake().unregisterBrakePedalStatus(brakeListener) }
            runCatching { v.getTurnSignal().unregisterTurnSignalState(turnListener) }
            runCatching { v.getSteeringWheel().unregisterSteeringWheelAngle(steeringListener) }
            runCatching { v.getOdometer().unregisterDistanceDriven(odometerListener) }
            runCatching { v.release() }
        }
        vehicle = null
        car = null
        propertyManager = null
    }

    private fun failed(channel: String, e: Exception) {
        // 차종 미지원·권한 없음 등. 해당 채널만 비워 두고 나머지는 계속 동작한다.
        Log.w(TAG, "$channel failed: ${e.javaClass.simpleName} ${e.message}")
    }

    private inline fun mark(f: Channels.() -> Channels) {
        val next = channels.f()
        if (next != channels) {
            channels = next
            Log.i(TAG, "channels: ${next.summary()}")
        }
    }

    private companion object {
        const val TAG = "DataYouPleos"
    }
}
