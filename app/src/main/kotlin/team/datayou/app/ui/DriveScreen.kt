package team.datayou.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.datayou.app.DataYouActions
import team.datayou.app.Screen
import team.datayou.engine.model.GaugeLevel
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 주행 화면. 실제 차량에서는 내비게이션이 이 자리에 있고, 다타유는 오른쪽 아래 칩만 그린다.
 * M2에서는 내비 대신 추상 지도를 그려 시연한다.
 */
@Composable
fun DriveScreen(s: Screen.Driving, vm: DataYouActions) {
    Box(Modifier.fillMaxSize().background(Palette.bg)) {
        MapBackdrop(s.elapsedSec)

        // 다음 안내 (M3: NaviHelper getTBTInfo)
        if (!s.stopped) {
            s.tbt?.let { tbt ->
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(20.dp)
                        .background(Palette.chip, RoundedCornerShape(16.dp))
                        .border(2.dp, if (s.preAlert) Palette.caution else Color.Transparent, RoundedCornerShape(16.dp))
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    TurnArrow(Modifier.size(34.dp))
                    Column {
                        Text("${(tbt.distanceM / 10).roundToInt() * 10}m", color = Palette.text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                        Text(tbt.text, color = Palette.muted, fontSize = 15.sp)
                    }
                }
            }
        } else {
            Row(
                Modifier.align(Alignment.TopStart).padding(20.dp).background(Palette.chip, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(10.dp).background(Palette.discomfort, CircleShape))
                Text("정차 중", color = Color(0xFFC9D2DB), fontSize = 16.sp)
            }
        }

        // 속도
        Row(
            Modifier.align(Alignment.BottomStart).padding(20.dp).background(Palette.chip, RoundedCornerShape(14.dp)).padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("${s.speedKmh}", color = Palette.text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Text("km/h", color = Palette.muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
        }

        // 다타유 칩: 주행 중 유일한 표시. 편안하면 숨김.
        val focus = s.focus
        AnimatedVisibility(
            visible = focus != null && focus.level != GaugeLevel.COMFORT,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut(),
        ) {
            if (focus != null) DataYouChip(focus.profile.type, focus.level, s.badge, s.preAlert)
        }

        // 데모 조작 (재생 데이터일 때만)
        DemoBar(s, vm)

        // 정차 카드
        AnimatedVisibility(
            visible = s.stopCard != null,
            modifier = Modifier.align(Alignment.CenterEnd).padding(28.dp),
            enter = fadeIn() + slideInVertically { it / 6 },
            exit = fadeOut(),
        ) { s.stopCard?.let { StopCardView(it, s.feedbackNote, vm) } }

        if (s.liveUnavailable) {
            Text(
                "차량 속도를 받을 수 없어요 · Car 서비스/권한을 확인하세요",
                color = Palette.caution, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp).background(Palette.chip, RoundedCornerShape(10.dp)).padding(10.dp),
            )
        }
    }
}

@Composable
private fun DataYouChip(type: team.datayou.engine.model.PassengerType, level: GaugeLevel, badge: team.datayou.engine.model.Cause?, pulse: Boolean) {
    // 예측 안내 중에만 아바타가 숨쉬듯 커졌다 작아진다 (무한 애니메이션은 이때만 돌린다)
    val k = if (pulse) {
        val t = rememberInfiniteTransition(label = "pulse")
        t.animateFloat(1f, 1.08f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "k").value
    } else 1f
    Row(
        Modifier
            .background(Palette.chip, RoundedCornerShape(50))
            .border(1.dp, Color(0xFF34404D), RoundedCornerShape(50))
            .padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "${type.label} ${level.label()}" + (badge?.let { ", ${it.label}" } ?: "")
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.scale(k)) { Avatar(type, level, 64.dp) }
        badge?.let { CauseBadge(it, 44.dp) }
    }
}

@Composable
private fun BoxScope.DemoBar(s: Screen.Driving, vm: DataYouActions) {
    Column(
        Modifier.align(Alignment.TopEnd).padding(20.dp).background(Palette.chip.copy(alpha = 0.92f), RoundedCornerShape(12.dp)).padding(6.dp),
        horizontalAlignment = Alignment.End,
    ) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("데모 · ${s.sourceLabel} · ${"%d:%02d".format((s.elapsedSec / 60).toInt(), (s.elapsedSec % 60).toInt())}", color = Palette.faint, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp))
        if (s.isReplay) listOf(1, 5, 15).forEach { f -> Pill("${f}배", selected = s.speedFactor == f) { vm.setSpeedFactor(f) } }
        Pill("주행 종료", selected = false) { vm.finishTrip() }
    }
    // 실시간 모드: Pleos 채널별 수신 상태 (시연·디버그용)
    s.channels?.let { Text(it, color = Palette.faint, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
    }
}

@Composable
private fun StopCardView(card: team.datayou.engine.StopCard, note: String?, vm: DataYouActions) {
    val parts = card.message.split(" · ")
    Row(
        Modifier
            .width(720.dp)
            .background(Palette.surface, RoundedCornerShape(28.dp))
            .border(1.dp, Palette.line, RoundedCornerShape(28.dp))
            .padding(28.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(card.passenger.type, card.level, 150.dp)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(parts.first(), color = Palette.text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CauseBadge(card.cause, 38.dp)
                Text(if (parts.size > 2) parts[1] else card.cause.label, color = Palette.text, fontSize = 19.sp, fontWeight = FontWeight.Medium)
            }
            Text(parts.last(), color = Color(0xFFDCE3EA), fontSize = 21.sp, lineHeight = 30.sp)
            Text("음성으로 읽어드리는 중", color = Palette.link, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FeedbackButton("괜찮아요", Palette.surface2, Palette.text) { vm.feedback(false) }
                FeedbackButton("속 안 좋아요", Color(0xFF3A2A24), Color(0xFFFFC2B0)) { vm.feedback(true) }
            }
            Text(note ?: "동승자가 누르면 다음부터 민감도가 맞춰져요", color = Palette.faint, fontSize = 13.sp)
        }
    }
}

@Composable
private fun FeedbackButton(text: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 52.dp).background(bg, RoundedCornerShape(14.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg, fontSize = 17.sp) }
}

@Composable
private fun TurnArrow(modifier: Modifier) {
    Canvas(modifier) {
        val s = size.minDimension / 24f
        val st = Stroke(width = 2.2f * s, cap = StrokeCap.Round)
        val p = Path().apply {
            moveTo(7 * s, 20 * s); lineTo(7 * s, 11 * s)
            quadraticBezierTo(7 * s, 7 * s, 11 * s, 7 * s); lineTo(19 * s, 7 * s)
            moveTo(15 * s, 3 * s); lineTo(19 * s, 7 * s); lineTo(15 * s, 11 * s)
        }
        drawPath(p, Palette.text, style = st)
    }
}

/** 시간에 따라 흐르는 추상 지도 (실차에서는 Pleos 내비게이션이 대신함) */
@Composable
private fun MapBackdrop(elapsedSec: Double) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val travel = (elapsedSec * 60).toFloat() // 화면 픽셀 기준 이동량
        fun roadY(x: Float) = h * 0.62f + sin(((x + travel) / w * 6.0)).toFloat() * h * 0.08f
        // 교차로 (이동에 따라 흘러감)
        val gap = w / 5f
        var x0 = -(travel % gap)
        while (x0 < w + gap) {
            drawLine(Color(0xFF1B232C), Offset(x0, 0f), Offset(x0 + w * 0.05f, h), strokeWidth = 18f)
            x0 += gap
        }
        val road = Path()
        var x = 0f
        road.moveTo(0f, roadY(0f))
        while (x <= w) { x += 16f; road.lineTo(x, roadY(x)) }
        drawPath(road, Palette.road, style = Stroke(width = 40f, cap = StrokeCap.Round))
        drawPath(road, Palette.route, style = Stroke(width = 11f, cap = StrokeCap.Round))
        val cx = w * 0.33f
        drawCircle(Palette.route, 18f, Offset(cx, roadY(cx)))
        drawCircle(Color.White, 6f, Offset(cx, roadY(cx)))
    }
}
