package team.datayou.app.data

import team.datayou.engine.model.VehicleSample
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** 도로 유형. 지도 배경·차선 수·확대 배율을 정한다. */
enum class RoadKind { CITY, ARTERIAL, HIGHWAY, WINDING }

/** 경로 위 한 점 (미터, x 동쪽·y 북쪽, heading은 x축 기준 반시계 라디안). */
data class RoutePoint(
    val tMs: Long,
    val x: Double,
    val y: Double,
    val heading: Double,
    val s: Double,
    val speed: Double,
    val kind: RoadKind,
    /** 차선 변경으로 생긴 중심선 대비 좌우 위치(m, 왼쪽 +). 고속도로·간선도로에서만 의미 있음 */
    val laneOffset: Double,
    /** 주변에 정차가 잦음(정체) */
    val congested: Boolean,
)

/** 다음 안내 지점 */
data class Maneuver(val s: Double, val left: Boolean, val label: String, val isTurn: Boolean)

/**
 * 주행 샘플(속도·회전 속도·방위각)로 실제 지나간 길을 복원한다(추측 항법).
 * 시나리오 지도와 회전 안내(TBT)가 데이터와 정확히 맞도록, 지도는 이 경로로만 그린다.
 */
class RouteGeometry private constructor(
    val points: List<RoutePoint>,
    val stops: List<RoutePoint>,
    val maneuvers: List<Maneuver>,
) {
    val totalM: Double get() = points.lastOrNull()?.s ?: 0.0

    /** 시각으로 현재 위치 찾기 (이진 탐색) */
    fun indexAt(tMs: Long): Int {
        var lo = 0
        var hi = points.size - 1
        if (hi < 0) return 0
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (points[mid].tMs <= tMs) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun nextManeuver(s: Double): Maneuver? = maneuvers.firstOrNull { it.s > s + 5 }

    companion object {
        fun from(samples: List<VehicleSample>): RouteGeometry {
            if (samples.isEmpty()) return RouteGeometry(emptyList(), emptyList(), emptyList())
            // 1) 추측 항법
            val n = samples.size
            val xs = DoubleArray(n)
            val ys = DoubleArray(n)
            val hs = DoubleArray(n)
            val ss = DoubleArray(n)
            val yaws = DoubleArray(n)
            var x = 0.0
            var y = 0.0
            var h = 0.0
            var s = 0.0
            val h0 = samples.firstOrNull { it.headingDeg != null }?.headingDeg
            for (i in 0 until n) {
                val cur = samples[i]
                if (i > 0) {
                    val dt = (cur.timestampMs - samples[i - 1].timestampMs) / 1000.0
                    val yaw = cur.yawRateRadPs
                    if (yaw != null) {
                        h += yaw * dt
                        yaws[i] = yaw
                    } else if (cur.headingDeg != null && h0 != null) {
                        val nh = Math.toRadians(cur.headingDeg!! - h0)
                        yaws[i] = if (dt > 0) wrap(nh - h) / dt else 0.0
                        h = nh
                    }
                    val d = cur.speedMps * dt
                    x += d * cos(h)
                    y += d * sin(h)
                    s += d
                }
                xs[i] = x; ys[i] = y; hs[i] = h; ss[i] = s
            }

            // 2) 도로 유형: 앞뒤 약 15초 창의 평균 속도와 회전량
            val win = 150
            val kinds = Array(n) { RoadKind.CITY }
            run {
                var sumV = 0.0
                var sumYaw = 0.0
                var lo = 0
                var hi = -1
                for (i in 0 until n) {
                    while (hi < minOf(n - 1, i + win)) { hi++; sumV += samples[hi].speedMps; sumYaw += abs(yaws[hi]) }
                    while (lo < i - win) { sumV -= samples[lo].speedMps; sumYaw -= abs(yaws[lo]); lo++ }
                    val cnt = (hi - lo + 1).coerceAtLeast(1)
                    val v = sumV / cnt * 3.6
                    val yawAvg = sumYaw / cnt
                    kinds[i] = when {
                        yawAvg > 0.09 && v > 20 -> RoadKind.WINDING
                        v > 70 -> RoadKind.HIGHWAY
                        v > 42 -> RoadKind.ARTERIAL
                        else -> RoadKind.CITY
                    }
                }
            }

            // 3) 차선 위치: 도로 방향(거리 ±150m 평균 방향)에 대해 옆으로 얼마나 이동했는지 누적한다.
            //    차선 변경은 한 차로(약 3.5m) 이동으로 남고, 커브는 도로 방향이 따라가므로 쌓이지 않는다.
            val offsets = DoubleArray(n)
            run {
                // 방향을 펼쳐서(unwrap) 평균
                val un = DoubleArray(n)
                for (i in 0 until n) un[i] = if (i == 0) hs[0] else un[i - 1] + wrap(hs[i] - hs[i - 1])
                // 핸들을 크게 돌리는 순간(차선 변경 중)은 도로 방향 평균에서 뺀다
                val straight = BooleanArray(n) { abs(yaws[it]) < 0.03 }
                var lo = 0
                var hi = 0
                var sum = 0.0
                var cnt = 0
                var off = 0.0
                for (i in 0 until n) {
                    while (hi < n && ss[hi] <= ss[i] + 150) { if (straight[hi]) { sum += un[hi]; cnt++ }; hi++ }
                    while (ss[lo] < ss[i] - 150) { if (straight[lo]) { sum -= un[lo]; cnt-- }; lo++ }
                    val road = if (cnt > 0) sum / cnt else un[i]
                    val k = kinds[i]
                    if (i > 0 && (k == RoadKind.HIGHWAY || k == RoadKind.ARTERIAL)) {
                        val ds = ss[i] - ss[i - 1]
                        off += ds * sin(un[i] - road)
                        off = off.coerceIn(-3.5, 3.5) * kotlin.math.exp(-ds / 3000.0)
                    } else if (k == RoadKind.CITY || k == RoadKind.WINDING) {
                        off = 0.0
                    }
                    offsets[i] = off
                }
            }

            // 4) 정차 지점
            val stopIdx = mutableListOf<Int>()
            var wasMoving = false
            for (i in 0 until n) {
                val moving = samples[i].speedMps > 0.3
                if (wasMoving && !moving) stopIdx += i
                wasMoving = moving
            }
            val congested = BooleanArray(n)
            for (i in 0 until n) congested[i] = stopIdx.count { abs(ss[it] - ss[i]) < 150 } >= 3

            val pts = List(n) { i ->
                RoutePoint(samples[i].timestampMs, xs[i], ys[i], hs[i], ss[i], samples[i].speedMps, kinds[i], offsets[i], congested[i])
            }

            // 5) 안내 지점: 50m 안에 60도 이상 방향이 바뀌면 교차로 회전, 굽이길 구간은 시작점에 한 번
            val man = mutableListOf<Maneuver>()
            var i = 0
            while (i < n) {
                var j = i
                while (j < n && ss[j] - ss[i] < 50) j++
                if (j >= n) break
                val dh = wrap(hs[j] - hs[i])
                if (abs(dh) > Math.toRadians(60.0) && kinds[i] != RoadKind.WINDING) {
                    man += Maneuver(ss[i] + 25, dh > 0, if (dh > 0) "좌회전" else "우회전", true)
                    while (i < n && ss[i] < ss[j] + 30) i++
                    continue
                }
                if (kinds[i] == RoadKind.WINDING && (i == 0 || kinds[i - 1] != RoadKind.WINDING)) {
                    man += Maneuver(ss[i], dh > 0, "굽은 길 시작", true)
                }
                i++
            }
            man += Maneuver(s, false, "목적지", false)
            return RouteGeometry(pts, stopIdx.map { pts[it] }, man)
        }

        private fun wrap(a: Double): Double {
            var r = a
            while (r > PI) r -= 2 * PI
            while (r < -PI) r += 2 * PI
            return r
        }
    }
}

/** 재생 모드 회전 안내: 복원한 경로의 실제 회전 지점까지 거리를 알려준다. */
class RouteNavigation(private val route: RouteGeometry) : NavigationSource {
    override fun update(sample: VehicleSample): TbtInfo? {
        val p = route.points.getOrNull(route.indexAt(sample.timestampMs)) ?: return null
        val m = route.nextManeuver(p.s) ?: return null
        val dist = m.s - p.s
        return TbtInfo(distanceM = dist, isTurn = m.isTurn, text = m.label, left = if (m.isTurn) m.left else null)
    }
}
