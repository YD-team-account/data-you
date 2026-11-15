package team.datayou.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import team.datayou.engine.model.VehicleSample
import kotlin.math.cos

/** 실시간 지도에 쓸 현재 위치 */
data class GeoFix(
    val lat: Double,
    val lon: Double,
    /** 나침반 방위(도, 북 0·시계방향) */
    val bearingDeg: Double,
    /** GPS로 실제 움직이는 중인지 (false면 차량 신호로 추정 이동 중) */
    val gpsMoving: Boolean,
)

/**
 * 실시간 모드의 위치.
 *  - GPS가 움직이면(실차, 에뮬레이터 Location ▸ Routes 재생) GPS 위치·방위를 그대로 쓴다.
 *  - GPS가 멈춰 있는데 차량 속도가 있으면(adb로 차량 신호만 주입) 마지막 위치에서 속도·회전으로 추정 이동한다.
 * 또, 핸들 각도를 못 받는 차량을 위해 GPS 방위를 엔진의 좌우 판단 대체 경로로 넘겨준다.
 */
class LiveLocator(context: Context) {
    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    @Volatile private var last: Location? = null
    @Volatile private var lastFixWallMs = 0L
    @Volatile private var gpsBearing: Double? = null
    @Volatile private var gpsSpeed = 0.0
    @Volatile private var gpsMoving = false

    private var lat = Double.NaN
    private var lon = Double.NaN
    private var bearing = 0.0
    private var lastSampleMs: Long? = null

    @Volatile var available = false
        private set

    private val listener = LocationListener { loc -> onFix(loc) }

    @SuppressLint("MissingPermission")
    fun start() {
        val m = lm ?: return
        try {
            m.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { onFix(it) }
            m.requestLocationUpdates(LocationManager.GPS_PROVIDER, 500L, 0f, listener, Looper.getMainLooper())
            available = true
        } catch (e: SecurityException) {
            Log.w(TAG, "location permission denied")
        } catch (e: Exception) {
            Log.w(TAG, "GPS unavailable: ${e.message}")
        }
    }

    fun stop() {
        runCatching { lm?.removeUpdates(listener) }
    }

    private fun onFix(loc: Location) {
        val prev = last
        val moved = prev?.distanceTo(loc) ?: 0f
        gpsMoving = loc.speed > 1f || moved > 2f
        gpsSpeed = if (loc.hasSpeed() && loc.speed > 0) loc.speed.toDouble() else {
            val dt = prev?.let { (loc.time - it.time) / 1000.0 } ?: 0.0
            if (dt > 0) moved / dt else 0.0
        }
        gpsBearing = when {
            loc.hasBearing() && loc.speed > 1f -> loc.bearing.toDouble()
            prev != null && moved > 2f -> prev.bearingTo(loc).toDouble().let { (it + 360) % 360 }
            else -> gpsBearing
        }
        last = loc
        lastFixWallMs = System.currentTimeMillis()
        if (gpsMoving || lat.isNaN()) {
            lat = loc.latitude
            lon = loc.longitude
            gpsBearing?.let { bearing = it }
        }
    }

    /** GPS가 움직이는 중이면 GPS 속도(m/s), 아니면 null */
    fun gpsSpeedIfMoving(): Double? = if (isGpsFresh() && gpsMoving) gpsSpeed else null

    /** GPS 방위(도). 엔진의 좌우 판단 대체 경로 */
    fun gpsHeadingIfMoving(): Double? = if (isGpsFresh() && gpsMoving) gpsBearing else null

    private fun isGpsFresh() = System.currentTimeMillis() - lastFixWallMs < 2500

    /** 샘플마다 호출: 위치를 갱신하고 돌려준다. 위치를 아직 모르면 null */
    fun update(sample: VehicleSample): GeoFix? {
        val prevMs = lastSampleMs
        lastSampleMs = sample.timestampMs
        if (lat.isNaN()) return null
        val moving = isGpsFresh() && gpsMoving
        if (!moving && prevMs != null) {
            // 추측 항법: 왼쪽 회전(+yaw)이면 방위가 줄어든다
            val dt = (sample.timestampMs - prevMs) / 1000.0
            sample.yawRateRadPs?.let { bearing = (bearing - Math.toDegrees(it) * dt + 360) % 360 }
            val d = sample.speedMps * dt
            val rad = Math.toRadians(bearing)
            lat += d * cos(rad) / 111_320.0
            lon += d * kotlin.math.sin(rad) / (111_320.0 * cos(Math.toRadians(lat)))
        }
        return GeoFix(lat, lon, bearing, moving)
    }

    /** 차량 샘플이 오지 않을 때(속도 권한·신호 없음)도 지도를 GPS 위치에 맞추기 위한 현재 위치 */
    fun currentFix(): GeoFix? = if (lat.isNaN()) null else GeoFix(lat, lon, bearing, isGpsFresh() && gpsMoving)

    private companion object {
        const val TAG = "DataYouLoc"
    }
}
