package team.datayou.app.data

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
import team.datayou.engine.model.VehicleSample

/**
 * AAOS 표준 차량 속성으로 실시간 샘플을 만든다.
 * Pleos SDK 정책(표준 API가 있으면 표준 우선)에 따라 속도는 AAOS PERF_VEHICLE_SPEED로 받는다.
 *
 * - 속도 : PERF_VEHICLE_SPEED (Pleos Connect 차량 지원 'O')
 * - 브레이크·깜빡이 : M3에서 Pleos Vehicle SDK(Brake, TurnSignal)로 채움 → 지금은 null/OFF
 * - 좌우 가속 : M3에서 자이로 또는 GPS 방위각 추가
 *
 * 콜백은 불규칙하게 오므로, 마지막 값을 들고 있다가 10Hz로 샘플을 만든다.
 * Pleos 에뮬레이터에서는 VHAL 속도 주입으로 테스트할 수 있다.
 */
class CarPropertyDataSource(
    private val context: Context,
    private val scope: CoroutineScope,
) : VehicleDataSource {

    override val label: String = "실시간 차량 데이터 (AAOS)"

    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null
    private var job: Job? = null

    @Volatile private var speedMps: Double = 0.0
    @Volatile var available: Boolean = false
        private set

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            if (value.propertyId == VehiclePropertyIds.PERF_VEHICLE_SPEED) {
                (value.value as? Float)?.let { speedMps = it.toDouble() }
            }
        }

        override fun onErrorEvent(propertyId: Int, zone: Int) {
            Log.w(TAG, "property error id=$propertyId zone=$zone")
        }
    }

    override fun start(onSample: (VehicleSample) -> Unit, onEnd: () -> Unit) {
        try {
            val c = Car.createCar(context)
            car = c
            val pm = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            propertyManager = pm
            available = pm.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_FASTEST)
        } catch (e: Exception) {
            // 권한 없음, Car 서비스 미연결 등 → 속도 0으로 계속 (화면에 상태 표시)
            Log.w(TAG, "Car API unavailable", e)
            available = false
        }
        val t0 = System.currentTimeMillis()
        job = scope.launch(Dispatchers.Default) {
            while (isActive) {
                onSample(VehicleSample(timestampMs = System.currentTimeMillis() - t0, speedMps = speedMps))
                delay(100)
            }
        }
    }

    override fun stop() {
        job?.cancel()
        try {
            propertyManager?.unregisterCallback(callback)
            car?.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "stop", e)
        }
        car = null
        propertyManager = null
    }

    private companion object {
        const val TAG = "DataYouCar"
    }
}
