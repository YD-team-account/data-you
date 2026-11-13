package team.datayou.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import team.datayou.app.data.RoadKind
import team.datayou.app.data.RouteGeometry
import team.datayou.app.data.RoutePoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 재생 시나리오용 지도. 주행 데이터로 복원한 실제 경로([RouteGeometry])를 그대로 그린다.
 * 진행 방향이 위로 오도록 회전하고, 도로 유형마다 배경·도로 폭·차선·주변 표현을 바꾼다.
 *  - 시내: 건물 블록, 교차로, 정지선·신호등, 정체 시 앞차
 *  - 간선도로: 넓은 2차로, 건물 듬성듬성
 *  - 고속도로: 3차로 점선, 가드레일, 들판 / 차선 변경이 차선 단위로 보이도록 강조
 *  - 굽이길(산길·강변): 숲, 2차로 중앙선, 확대해서 커브가 잘 보이게
 */
@Composable
fun ScenarioMap(route: RouteGeometry, tMs: Long, modifier: Modifier = Modifier) {
    val style = remember { MapStyles() }
    Canvas(modifier.fillMaxSize()) {
        if (route.points.isEmpty()) {
            drawRect(Palette.bg)
            return@Canvas
        }
        drawRoute(route, route.indexAt(tMs), style)
    }
}

private class MapStyles {
    val bg = mapOf(
        RoadKind.CITY to Color(0xFF151B22),
        RoadKind.ARTERIAL to Color(0xFF161D24),
        RoadKind.HIGHWAY to Color(0xFF16201C),
        RoadKind.WINDING to Color(0xFF13201A),
    )
    val building = Color(0xFF222B35)
    val buildingEdge = Color(0xFF2B3642)
    val field = Color(0xFF1B2820)
    val tree = Color(0xFF1E3326)
    val tree2 = Color(0xFF243D2D)
    val laneMark = Color(0xFF55616E)
    val center = Color(0xFF8C7A3E)
    val guard = Color(0xFF3A4652)
    val car = Color(0xFF5B6773)
}

/** 도로 유형별 확대 배율(dp/m)과 도로 폭(dp) */
private fun scaleDpPerM(k: RoadKind) = when (k) {
    RoadKind.CITY -> 1.5f
    RoadKind.ARTERIAL -> 1.15f
    RoadKind.HIGHWAY -> 0.85f
    RoadKind.WINDING -> 1.3f
}

private fun roadWidthDp(k: RoadKind) = when (k) {
    RoadKind.CITY -> 26f
    RoadKind.ARTERIAL -> 36f
    RoadKind.HIGHWAY -> 58f
    RoadKind.WINDING -> 24f
}

private fun hash(i: Int): Float {
    var x = i * 374761393 + 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return ((x xor (x ushr 16)) and 0xFFFF) / 65535f
}

private fun DrawScope.drawRoute(route: RouteGeometry, idx: Int, st: MapStyles) {
    val pts = route.points
    val cur = pts[idx]
    val w = size.width
    val h = size.height

    // ── 카메라: 앞쪽 40m 방향을 위로, 배율은 주변 도로 유형 평균 ──
    val ahead = pointAtS(pts, idx, cur.s + 40.0)
    val camHeading = if (ahead != null && (ahead.x != cur.x || ahead.y != cur.y)) atan2(ahead.y - cur.y, ahead.x - cur.x) else cur.heading
    var scaleSum = 0f
    var scaleN = 0
    var j = idx
    while (j < pts.size && pts[j].s < cur.s + 120) { scaleSum += scaleDpPerM(pts[j].kind); scaleN++; j += 5 }
    j = idx
    while (j >= 0 && pts[j].s > cur.s - 60) { scaleSum += scaleDpPerM(pts[j].kind); scaleN++; j -= 5 }
    val scale = (if (scaleN > 0) scaleSum / scaleN else scaleDpPerM(cur.kind)) * density
    val anchor = Offset(w * 0.42f, h * 0.68f)
    val a = PI / 2 - camHeading
    val ca = cos(a).toFloat()
    val sa = sin(a).toFloat()
    fun toScreen(x: Double, y: Double): Offset {
        val dx = (x - cur.x).toFloat()
        val dy = (y - cur.y).toFloat()
        val rx = dx * ca - dy * sa
        val ry = dx * sa + dy * ca
        return Offset(anchor.x + rx * scale, anchor.y - ry * scale)
    }
    fun normalOffset(p: RoutePoint, meters: Double) = toScreen(p.x - sin(p.heading) * meters, p.y + cos(p.heading) * meters)

    // 배경
    drawRect(st.bg.getValue(cur.kind))

    // 보이는 범위의 경로 점 (3m 간격으로 솎음)
    val fromS = cur.s - 400
    val toS = cur.s + 1100
    val visible = ArrayList<RoutePoint>()
    run {
        // 경로 시작 전 구간 (곧게 늘림)
        var es = fromS
        while (es < pts.first().s) { pointAtS(pts, 0, es)?.let { visible += it }; es += 10.0 }
        var k = idx
        while (k > 0 && pts[k].s > fromS) k--
        var lastS = -1e9
        while (k < pts.size && pts[k].s < toS) {
            if (pts[k].s - lastS >= 3.0) { visible += pts[k]; lastS = pts[k].s }
            k++
        }
        // 경로 끝 이후 구간
        es = maxOf(pts.last().s + 10.0, lastS + 10.0)
        while (es < toS) { pointAtS(pts, pts.size - 1, es)?.let { visible += it }; es += 10.0 }
    }
    val pxPerM = scale

    // ── 주변 표현 (도로 아래) ──
    val step = 30.0
    var mark = (fromS / step).toInt()
    while (mark * step < toS) {
        val p = pointAtS(pts, idx, mark * step)
        if (p != null) {
            val r = hash(mark)
            when (p.kind) {
                RoadKind.CITY, RoadKind.ARTERIAL -> {
                    // 교차로(약 120m마다): 길을 가로지르는 도로
                    if (mark % 4 == 0) {
                        val a1 = normalOffset(p, 170.0)
                        val a2 = normalOffset(p, -170.0)
                        drawLine(Palette.road.copy(alpha = 0.75f), a1, a2, strokeWidth = roadWidthDp(RoadKind.CITY) * density * 0.8f)
                    } else {
                        // 건물 블록: 길 양쪽으로 여러 줄 (시내는 빽빽하게, 간선도로는 듬성듬성)
                        val deg = (-(p.heading - camHeading) * 180 / PI).toFloat()
                        val bands = if (p.kind == RoadKind.CITY) listOf(24.0, 58.0, 95.0, 135.0) else listOf(30.0, 80.0)
                        for (side in listOf(1, -1)) for ((bi, base) in bands.withIndex()) {
                            val key = mark * 31 + side * 7 + bi * 101
                            if (hash(key) < (if (p.kind == RoadKind.CITY) 0.15f else 0.45f)) continue
                            val c = normalOffset(p, side * (base + 8 * hash(key + 1)))
                            val bw = (16 + 10 * hash(key + 2)) * pxPerM
                            val bh = (14 + 14 * hash(key + 3)) * pxPerM
                            rotate(deg, c) {
                                drawRect(st.building, Offset(c.x - bw / 2, c.y - bh / 2), androidx.compose.ui.geometry.Size(bw, bh))
                                drawRect(st.buildingEdge, Offset(c.x - bw / 2, c.y - bh / 2), androidx.compose.ui.geometry.Size(bw, bh), style = Stroke(1.5f))
                            }
                        }
                    }
                }
                RoadKind.HIGHWAY -> {
                    for (side in listOf(1.0, -1.0)) {
                        if (hash(mark * 11 + side.toInt()) < 0.5f) {
                            val c = normalOffset(p, side * (60 + 80 * r))
                            val fw = (40 + 50 * hash(mark * 13)) * pxPerM
                            drawRect(st.field, Offset(c.x - fw / 2, c.y - fw / 3), androidx.compose.ui.geometry.Size(fw, fw * 0.66f))
                        }
                    }
                }
                RoadKind.WINDING -> {
                    for (t in 0 until 5) {
                        val side = if (hash(mark * 17 + t) < 0.5f) 1.0 else -1.0
                        val c = normalOffset(p, side * (16 + 70 * hash(mark * 19 + t)))
                        val rr = (4 + 6 * hash(mark * 23 + t)) * pxPerM
                        drawCircle(if (t % 2 == 0) st.tree else st.tree2, rr, c)
                    }
                }
            }
        }
        mark++
    }

    // ── 도로 ──
    var seg = 0
    while (seg < visible.size - 1) {
        val kind = visible[seg].kind
        val path = Path()
        val o = toScreen(visible[seg].x, visible[seg].y)
        path.moveTo(o.x, o.y)
        var e = seg + 1
        while (e < visible.size) {
            val q = toScreen(visible[e].x, visible[e].y)
            path.lineTo(q.x, q.y)
            if (visible[e].kind != kind) break
            e++
        }
        val rw = roadWidthDp(kind) * density
        if (kind == RoadKind.HIGHWAY) drawPath(path, st.guard, style = Stroke(rw + 6 * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, Palette.road, style = Stroke(rw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        seg = e.coerceAtMost(visible.size - 1).let { if (it == seg) seg + 1 else it }
    }

    // ── 차선 표시 ──
    val dash = PathEffect.dashPathEffect(floatArrayOf(10 * density, 10 * density))
    fun offsetLine(kinds: Set<RoadKind>, metersFn: (RoadKind) -> Double, color: Color, width: Float) {
        var started = false
        val path = Path()
        for (p in visible) {
            if (p.kind !in kinds) { started = false; continue }
            val px = metersFn(p.kind) / pxPerM * density // dp → m
            val q = normalOffset(p, px)
            if (!started) { path.moveTo(q.x, q.y); started = true } else path.lineTo(q.x, q.y)
        }
        drawPath(path, color, style = Stroke(width, pathEffect = dash))
    }
    // 고속도로 3차로: 차로 경계 2줄
    val hwLane = roadWidthDp(RoadKind.HIGHWAY) / 3.0
    offsetLine(setOf(RoadKind.HIGHWAY), { hwLane / 2 }, st.laneMark, 2 * density)
    offsetLine(setOf(RoadKind.HIGHWAY), { -hwLane / 2 }, st.laneMark, 2 * density)
    // 간선도로 2차로: 가운데 점선
    offsetLine(setOf(RoadKind.ARTERIAL), { 0.0 }, st.laneMark, 1.5f * density)
    // 굽이길·시내: 중앙선
    offsetLine(setOf(RoadKind.WINDING, RoadKind.CITY), { 0.0 }, st.center, 1.5f * density)

    // 차선 변경 강조: 실제 3.5m 이동을 화면에서 한 차로 폭만큼 옮겨 그린다
    fun laneShiftM(p: RoutePoint): Double {
        val laneDp = when (p.kind) {
            RoadKind.HIGHWAY -> roadWidthDp(RoadKind.HIGHWAY) / 3f
            RoadKind.ARTERIAL -> roadWidthDp(RoadKind.ARTERIAL) / 2f
            else -> 0f
        }
        val px = (p.laneOffset / 3.5).coerceIn(-1.0, 1.0) * laneDp * density
        return px / pxPerM - p.laneOffset // 실제 이동량은 이미 경로에 들어 있으므로 차이만
    }

    // ── 남은 경로 (파란 선) ──
    run {
        val path = Path()
        var started = false
        for (p in visible) {
            if (p.s < cur.s || p.s > pts.last().s) continue
            val q = normalOffset(p, laneShiftM(p))
            if (!started) { path.moveTo(q.x, q.y); started = true } else path.lineTo(q.x, q.y)
        }
        drawPath(path, Palette.route.copy(alpha = 0.85f), style = Stroke(6 * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    // ── 정지선·신호등 (시내 정차 지점) ──
    for (stop in route.stops) {
        if (stop.s < fromS || stop.s > toS || stop.kind == RoadKind.HIGHWAY || stop.congested) continue
        val a1 = normalOffset(stop, 6.0)
        val a2 = normalOffset(stop, -6.0)
        val lineA = toScreen(stop.x + cos(stop.heading) * 4, stop.y + sin(stop.heading) * 4)
        val shift = lineA - toScreen(stop.x, stop.y)
        drawLine(Color(0xFFDDE3E8), a1 + shift, a2 + shift, strokeWidth = 3 * density)
        val nearStopped = abs(stop.s - cur.s) < 15 && cur.speed < 0.3
        val light = normalOffset(stop, 9.0) + shift
        drawCircle(Color(0xFF0E1318), 7 * density, light)
        drawCircle(if (nearStopped) Palette.discomfort else Palette.comfort, 4.5f * density, light)
    }

    // ── 목적지 ──
    if (pts.last().s in fromS..toS) {
        val d = toScreen(pts.last().x, pts.last().y)
        drawCircle(Color(0xFF0E1318), 11 * density, d)
        drawCircle(Palette.comfort, 7 * density, d)
    }

    // ── 정체: 앞차 ──
    if (cur.congested) {
        val gap = maxOf(9.0 + cur.speed * 1.2, (30 * density / pxPerM).toDouble())
        for (n in 1..3) {
            val p = pointAtS(pts, idx, cur.s + gap * n) ?: break
            drawCar(toScreen(p.x, p.y), (-(p.heading - camHeading) * 180 / PI).toFloat(), st.car, pxPerM)
        }
    }

    // ── 내 차: 고속도로·간선도로에서는 차선 변경이 보이게 좌우 위치를 강조 ──
    val ego = normalOffset(cur, laneShiftM(cur))
    drawEgo(ego, (-(cur.heading - camHeading) * 180 / PI).toFloat())
}

/** 경로 위 거리 s의 점. 경로 밖이면 시작·끝 방향으로 곧게 늘린 점(도로가 화면 끝까지 이어지게). */
private fun pointAtS(pts: List<RoutePoint>, near: Int, s: Double): RoutePoint? {
    if (pts.isEmpty()) return null
    val first = pts.first()
    val last = pts.last()
    if (s > last.s) {
        val d = s - last.s
        return last.copy(x = last.x + cos(last.heading) * d, y = last.y + sin(last.heading) * d, s = s, congested = false, laneOffset = last.laneOffset)
    }
    if (s < first.s) {
        val d = first.s - s
        return first.copy(x = first.x - cos(first.heading) * d, y = first.y - sin(first.heading) * d, s = s, congested = false)
    }
    var k = near.coerceIn(0, pts.size - 1)
    if (pts[k].s <= s) {
        while (k < pts.size - 1 && pts[k + 1].s <= s) k++
    } else {
        while (k > 0 && pts[k].s > s) k--
    }
    return pts[k]
}

private fun DrawScope.drawCar(c: Offset, deg: Float, color: Color, pxPerM: Float) {
    val l = maxOf(4.6f * pxPerM, 24 * density)
    val wd = maxOf(2.0f * pxPerM, 12 * density)
    rotate(deg, c) {
        drawRoundRect(color, Offset(c.x - wd / 2, c.y - l / 2), androidx.compose.ui.geometry.Size(wd, l), androidx.compose.ui.geometry.CornerRadius(3 * density))
        drawRoundRect(Color(0xFF8C98A5), Offset(c.x - wd / 2 + 1.5f * density, c.y - l / 2 + 4 * density), androidx.compose.ui.geometry.Size(wd - 3 * density, l * 0.35f), androidx.compose.ui.geometry.CornerRadius(2 * density))
        // 브레이크등
        drawRect(Palette.discomfort, Offset(c.x - wd / 2, c.y + l / 2 - 2 * density), androidx.compose.ui.geometry.Size(wd, 2 * density))
    }
}

private fun DrawScope.drawEgo(c: Offset, deg: Float) {
    rotate(deg, c) {
        drawCircle(Palette.route.copy(alpha = 0.25f), 26 * density, c)
        val p = Path().apply {
            moveTo(c.x, c.y - 14 * density)
            lineTo(c.x + 10 * density, c.y + 10 * density)
            lineTo(c.x, c.y + 5 * density)
            lineTo(c.x - 10 * density, c.y + 10 * density)
            close()
        }
        drawPath(p, Color.White)
        drawPath(p, Palette.route, style = Stroke(2.5f * density, join = StrokeJoin.Round))
    }
}

