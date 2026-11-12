package team.datayou.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import team.datayou.engine.model.Cause
import team.datayou.engine.model.GaugeLevel
import team.datayou.engine.model.PassengerType

/*
 * 화면 시안(SVG)과 같은 선 아이콘을 Compose Canvas로 그린다.
 * 동승자 얼굴은 40×40, 원인 아이콘은 24×24 좌표계 기준.
 */

private val pathCache = HashMap<String, androidx.compose.ui.graphics.Path>()
private fun p(d: String) = pathCache.getOrPut(d) { PathParser().parsePathString(d).toPath() }

private fun DrawScope.inViewBox(box: Float, block: DrawScope.() -> Unit) {
    val s = size.minDimension / box
    withTransform({ scale(s, s, Offset.Zero) }) { block() }
}

private fun DrawScope.line(d: String, color: Color, w: Float = 2f) =
    drawPath(p(d), color, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))

private fun DrawScope.ring(cx: Float, cy: Float, r: Float, color: Color, w: Float = 2f) =
    drawCircle(color, r, Offset(cx, cy), style = Stroke(width = w))

private fun DrawScope.dot(cx: Float, cy: Float, r: Float, color: Color) = drawCircle(color, r, Offset(cx, cy))

/** 동승자 얼굴. 단계에 따라 표정이 바뀐다 (색약 운전자도 구분 가능하도록). */
@Composable
fun PassengerFace(type: PassengerType, level: GaugeLevel, modifier: Modifier = Modifier, color: Color = Palette.text) {
    Canvas(modifier.semantics { contentDescription = "${type.label} ${level.label()}" }) {
        inViewBox(40f) {
            val mouth = when (level) {
                GaugeLevel.DISCOMFORT -> "M14 29c2-2 4 2 6 0s4 2 6 0"
                GaugeLevel.CAUTION -> "M15 28h10"
                GaugeLevel.COMFORT -> "M15 27c3 3 7 3 10 0"
            }
            fun eyes() {
                if (level == GaugeLevel.DISCOMFORT) line("M13 20.5l3 1.2M27 20.5l-3 1.2", color)
                else { dot(15f, 21f, 1.4f, color); dot(25f, 21f, 1.4f, color) }
            }
            when (type) {
                PassengerType.CHILD -> { ring(20f, 22f, 13f, color); line("M17 9c0-3 4-4 5-1", color); eyes(); line(mouth, color) }
                PassengerType.PREGNANT -> { ring(18f, 9f, 5f, color); line("M18 14v6c0 0 9 1 9 8s-6 7-9 7", color); line("M18 20c-3 0-5 3-5 7v8", color) }
                PassengerType.SENIOR -> {
                    ring(20f, 21f, 13f, color); ring(15f, 20f, 3.2f, color); ring(25f, 20f, 3.2f, color)
                    line("M18.2 20h3.6", color); line("M12 11c4-3 12-3 16 0", color); line(mouth, color)
                }
                PassengerType.DOG -> {
                    line("M11 12c-5 0-7 9-4 13l4-3", color); line("M29 12c5 0 7 9 4 13l-4-3", color)
                    line("M11 12c3-3 15-3 18 0v12c0 6-4 9-9 9s-9-3-9-9z", color)
                    if (level == GaugeLevel.DISCOMFORT) line("M14 19l3 1.2M26 19l-3 1.2", color)
                    else { dot(16f, 20f, 1.4f, color); dot(24f, 20f, 1.4f, color) }
                    drawPath(p("M18 26h4l-2 2z"), color)
                }
                PassengerType.SENSITIVE_ADULT, PassengerType.ADULT -> {
                    ring(20f, 21f, 13f, color); line("M13 19h4M23 19h4", color)
                    line(if (level == GaugeLevel.COMFORT) "M15 28h10" else mouth, color)
                }
            }
        }
    }
}

/** 원인 아이콘 (배지 안쪽). */
@Composable
fun CauseGlyph(cause: Cause, modifier: Modifier = Modifier, color: Color = Palette.onAccent) {
    Canvas(modifier.semantics { contentDescription = cause.label }) {
        inViewBox(24f) {
            when (cause) {
                Cause.HARD_BRAKE -> { line("M8 3h8l5 5v8l-5 5H8l-5-5V8z", color, 2.4f); line("M12 8v5", color, 2.4f); dot(12f, 16.5f, 1f, color) }
                Cause.REGEN_DECEL -> { line("M5 7h11a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2z", color, 2.2f); line("M21 10v4", color, 2.2f); line("M10.5 9v6M8 12.5l2.5 2.5 2.5-2.5", color, 2.2f) }
                Cause.HARD_ACCEL -> line("M4 6l6 6-6 6M12 6l6 6-6 6", color, 2.4f)
                Cause.SHARP_LATERAL -> line("M6 21V13c0-4 3-7 7-7h5M15 3l3 3-3 3", color, 2.4f)
                Cause.BRAKE_PUMPING -> line("M5 8v8M10 6v12M15 8v8M20 10v4", color, 2.4f)
                Cause.CONTINUOUS_CURVES -> line("M2 12c2.5-6 5-6 7.5 0s5 6 7.5 0 3.5-4 5-2", color, 2.4f)
                Cause.STOP_AND_GO -> line("M3.5 9h5a1.5 1.5 0 0 1 1.5 1.5v3a1.5 1.5 0 0 1-1.5 1.5h-5A1.5 1.5 0 0 1 2 13.5v-3A1.5 1.5 0 0 1 3.5 9zM15.5 9h5a1.5 1.5 0 0 1 1.5 1.5v3a1.5 1.5 0 0 1-1.5 1.5h-5a1.5 1.5 0 0 1-1.5-1.5v-3a1.5 1.5 0 0 1 1.5-1.5z", color, 2.2f)
            }
        }
    }
}

/** 원형 배지 = 원인 색 배경 + 아이콘 */
@Composable
fun CauseBadge(cause: Cause, size: Dp) {
    Box(
        Modifier.size(size).background(cause.badgeColor(), CircleShape),
        contentAlignment = Alignment.Center,
    ) { CauseGlyph(cause, Modifier.size(size * 0.58f)) }
}

/** 단계 색 테두리를 두른 동승자 아바타 */
@Composable
fun Avatar(type: PassengerType, level: GaugeLevel, size: Dp, ring: Color = level.color()) {
    Box(
        Modifier.size(size).background(Palette.surface2, CircleShape).border(size * 0.08f, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) { PassengerFace(type, level, Modifier.padding(size * 0.18f).fillMaxSize()) }
}
