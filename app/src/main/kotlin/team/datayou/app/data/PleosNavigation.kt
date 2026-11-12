package team.datayou.app.data

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import team.datayou.engine.model.VehicleSample

/** 다음 회전 안내(TBT)를 주는 쪽. 재생 모드는 [FakeNavigation], 실시간 모드는 [PleosNavigation]. */
interface NavigationSource {
    /** 샘플마다 호출. 안내가 없으면 null (경로 안내 중이 아님). */
    fun update(sample: VehicleSample): TbtInfo?
    fun release() {}
}

/**
 * Pleos NaviHelper SDK로 내장 내비게이션의 다음 회전 안내를 받는다 (M3).
 * - addListener → onTBTInfo(List<TBTInfo>) 로 갱신 수신
 * - 리스너가 조용하면 1초마다 getTBTInfo()로 다시 요청
 * - 회전 종류(TurnType)가 좌·우회전/급회전/유턴/로터리면 isTurn=true → 엔진의 예측 안내(300m 전)에 쓰인다
 * NaviHelper는 경로(폴리라인)를 주지 않으므로, 커브 예측은 하지 않고 회전 지점만 예고한다.
 */
class PleosNavigation(context: Context) : NavigationSource {

    @Volatile private var latest: TbtInfo? = null
    @Volatile private var lastUpdateMs = 0L
    @Volatile var connected = false
        private set
    @Volatile var received = false
        private set
    private var lastRequestMs = 0L

    private val listener = object : NaviHelperEventListener {
        override fun onTBTInfo(list: List<TBTInfo>) {
            val first = list.firstOrNull()
            latest = first?.let { TbtInfo(distanceM = it.distance, isTurn = it.type.isTurn(), text = it.description.ifBlank { it.type.label() }) }
            lastUpdateMs = System.currentTimeMillis()
            if (!received) Log.i(TAG, "first TBT: ${first?.type} ${first?.distance}m ${first?.description}")
            received = true
        }

        override fun onNavigationStatus(status: NavigationStatus) {
            Log.i(TAG, "navigation status: $status")
        }

        override fun onRouteEnded() {
            latest = null
        }

        override fun onRouteCancelled() {
            latest = null
        }

        override fun onError(code: NaviErrorCode) {
            Log.w(TAG, "navi error: $code")
        }
    }

    private val helper: NaviHelper? = try {
        NaviHelper(context).also { h ->
            h.initialize(
                { connected = true; Log.i(TAG, "NaviHelper connected"); runCatching { h.addListener(listener); h.getTBTInfo() } },
                { e -> Log.w(TAG, "NaviHelper init failed", e) },
            )
        }
    } catch (e: Throwable) {
        Log.w(TAG, "NaviHelper unavailable", e)
        null
    }

    override fun update(sample: VehicleSample): TbtInfo? {
        val now = System.currentTimeMillis()
        if (connected && now - lastUpdateMs > 1000 && now - lastRequestMs > 1000) {
            lastRequestMs = now
            runCatching { helper?.getTBTInfo() }
        }
        // 5초 넘게 갱신이 없으면 안내가 끝난 것으로 본다
        return latest?.takeIf { now - lastUpdateMs < 5000 }
    }

    override fun release() {
        runCatching { helper?.removeListener(listener) }
        runCatching { helper?.release() }
    }

    private companion object {
        const val TAG = "DataYouNavi"

        fun TurnType.isTurn() = when (this) {
            TurnType.TURN_STRAIGHT, TurnType.TURN_UNKNOWN, TurnType.TURN_ARRIVE, TurnType.TURN_TUNNEL,
            TurnType.TURN_TOLL, TurnType.TURN_WAY, TurnType.TURN_ENTER_SERVICEAREA, TurnType.TURN_RESTAREA -> false
            else -> true
        }

        fun TurnType.label() = when (this) {
            TurnType.TURN_LEFT, TurnType.TURN_SHARP_L, TurnType.TURN_KEEP_L -> "좌회전"
            TurnType.TURN_RIGHT, TurnType.TURN_SHARP_R, TurnType.TURN_KEEP_R -> "우회전"
            TurnType.TURN_UTURN -> "유턴"
            else -> if (name.contains("ROUNDABOUT")) "회전교차로" else "안내 지점"
        }
    }
}
