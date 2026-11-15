package team.datayou.app.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
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
 * 주행 화면. 실제 차량에서는 내비게이션이 이 자리에 있고, 다 타유는 오른쪽 아래 칩만 그린다.
 * M2에서는 내비 대신 추상 지도를 그려 시연한다.
 */
@Composable
fun DriveScreen(s: Screen.Driving, vm: DataYouActions) {
    Box(Modifier.fillMaxSize().background(Palette.bg)) {
        when {
            !s.isReplay -> LiveMap(s.geo)
            s.route != null -> ScenarioMap(s.route, s.tMs)
            else -> MapBackdrop(s.elapsedSec)
        }

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
                    TurnArrow(Modifier.size(34.dp), tbt.left)
                    Column {
                        Text("${(tbt.distanceM / 10).roundToInt() * 10}m", color = Palette.text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                        Text(tbt.text, color = Palette.muted, fontSize = 15.sp)
                    }
                }
            }
        } else if (!s.passengerPanel) {
            Row(
                Modifier.align(Alignment.TopStart).padding(20.dp).background(Palette.chip, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(10.dp).background(Palette.discomfort, CircleShape))
                Text("정차 중", color = Color(0xFFC9D2DB), fontSize = 16.sp)
            }
        }

        // 속도 (동승자 팝업이 떠 있는 출발 전에는 숨김)
        if (!s.passengerPanel) Row(
            Modifier.align(Alignment.BottomStart).padding(20.dp).background(Palette.chip, RoundedCornerShape(14.dp)).padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("${s.speedKmh}", color = Palette.text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Text("km/h", color = Palette.muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
        }

        // 다 타유 칩: 주행 중 유일한 표시. 편안하면 숨김.
        val focus = s.focus
        // 편안 단계에서도 반응 직후나 부드러운 주행(휴식 표정) 중에는 보인다
        AnimatedVisibility(
            visible = focus != null && (focus.level != GaugeLevel.COMFORT || s.reactionShowing || s.resting),
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut(),
        ) {
            if (focus != null) DataYouChip(
                focus.profile.type, focus.level,
                badge = if (focus.level != GaugeLevel.COMFORT) s.badge else null,
                highlight = s.preAlert,
                holding = if (s.reactionShowing) s.reaction?.kind else null,
                resting = s.resting,
            )
        }

        // 출발 전 동승자 팝업 (운전석 쪽 왼쪽). 움직이면 효과 없이 바로 사라진다.
        if (s.passengerPanel) PassengerPanel(s.passengers, vm, Modifier.align(Alignment.TopStart).padding(20.dp))

        // 데모 조작 (재생 데이터일 때만)
        DemoBar(s, vm)

        // 정차 카드
        AnimatedVisibility(
            visible = s.stopCard != null,
            modifier = Modifier.align(Alignment.CenterEnd).padding(28.dp),
            enter = fadeIn() + slideInVertically { it / 6 },
            exit = fadeOut(),
        ) { s.stopCard?.let { StopCardView(it, s.feedbackNote, vm, s.stopCardReplay) } }

        s.liveProblem?.let { problem ->
            Text(
                problem,
                color = Palette.caution, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp).background(Palette.chip, RoundedCornerShape(10.dp)).padding(10.dp),
            )
        }
    }
}

@Composable
private fun DataYouChip(
    type: team.datayou.engine.model.PassengerType,
    level: GaugeLevel,
    badge: team.datayou.engine.model.Cause?,
    highlight: Boolean,
    holding: team.datayou.engine.model.ReactionKind? = null,
    resting: Boolean = false,
) {
    // 주행 중 칩은 움직이지 않는다 (Android for Cars 품질 기준 SA-1: 주행 중 움직이는 그래픽 금지).
    //  - 반응: 동작 대신 정지 자세(holdPose)로 바뀌었다가 돌아온다 — 아이콘이 바뀌는 것과 같은 수준의 변화
    //  - 예측 안내: 펄스 대신 파란 테두리 강조
    val motion = team.datayou.app.ui.anim.LocalReactionMotion.current
    val renderer = team.datayou.app.ui.anim.LocalCharacterRenderer.current
    val pose = holding?.let { motion.holdPose(it) } ?: team.datayou.app.ui.anim.MotionPose()
    val mood = when {
        holding != null -> motion.mood(holding)
        resting && level == GaugeLevel.COMFORT -> FaceMood.RESTING
        else -> FaceMood.NORMAL
    }
    val edge = if (highlight) Palette.link else Color(0xFF34404D)

    Row(
        Modifier
            .background(Palette.chip, RoundedCornerShape(50))
            .border(if (highlight) 3.dp else 1.dp, edge, RoundedCornerShape(50))
            .padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = "${type.label} ${level.label()}" + (badge?.let { ", ${it.label}" } ?: "") +
                    (if (mood == FaceMood.RESTING) ", 편안히 쉬는 중" else "") + (if (highlight) ", 곧 회전" else "")
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.poseLayer(pose)) { renderer(type, level, mood, 64.dp, Modifier) }
        badge?.let { CauseBadge(it, 44.dp) }
    }
}

/** 자세를 아바타에 적용 */
private fun Modifier.poseLayer(pose: team.datayou.app.ui.anim.MotionPose) = graphicsLayer {
    rotationX = pose.rotationX
    rotationZ = pose.rotationZ
    translationX = pose.dxDp * density
    translationY = pose.dyDp * density
    scaleX = pose.scale
    scaleY = pose.scale
    cameraDistance = 12f * density
}

/**
 * 정차 카드의 캐릭터: 차가 멈춘 뒤에만 "방금 이렇게 쏠렸어요" 동작을 두 번 재생한다.
 * 동작은 주입된 ReactionMotion, 그림은 CharacterRenderer를 쓴다.
 */
@Composable
private fun ReplayAvatar(type: team.datayou.engine.model.PassengerType, level: GaugeLevel, kind: team.datayou.engine.model.ReactionKind?, size: androidx.compose.ui.unit.Dp) {
    val motion = team.datayou.app.ui.anim.LocalReactionMotion.current
    val renderer = team.datayou.app.ui.anim.LocalCharacterRenderer.current
    val progress = remember { Animatable(1f) }
    val reduceMotion = rememberReduceMotion()
    LaunchedEffect(kind) {
        if (kind == null || reduceMotion) return@LaunchedEffect
        val ms = motion.durationMs(kind)
        if (ms <= 0) return@LaunchedEffect
        kotlinx.coroutines.delay(400)
        repeat(2) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(ms, easing = LinearEasing))
            kotlinx.coroutines.delay(500)
        }
    }
    val playing = kind != null && progress.value < 1f
    val pose = if (playing) motion.pose(kind!!, progress.value) else team.datayou.app.ui.anim.MotionPose()
    Box(Modifier.poseLayer(pose)) { renderer(type, level, if (playing) motion.mood(kind!!) else FaceMood.NORMAL, size, Modifier) }
}

/** 시스템 '애니메이션 줄이기'(애니메이터 배율 0)면 반응 동작을 생략한다. */
@Composable
private fun rememberReduceMotion(): Boolean {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return remember {
        runCatching { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
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
private fun StopCardView(card: team.datayou.engine.StopCard, note: String?, vm: DataYouActions, replay: team.datayou.engine.model.ReactionKind? = null) {
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
        ReplayAvatar(card.passenger.type, card.level, replay, 150.dp)
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
private fun TurnArrow(modifier: Modifier, left: Boolean?) {
    Canvas(modifier) {
        val s = size.minDimension / 24f
        val st = Stroke(width = 2.2f * s, cap = StrokeCap.Round)
        val p = Path().apply {
            if (left == null) {
                // 직진(목적지)
                moveTo(12 * s, 21 * s); lineTo(12 * s, 4 * s)
                moveTo(8 * s, 8 * s); lineTo(12 * s, 4 * s); lineTo(16 * s, 8 * s)
            } else {
                moveTo(7 * s, 20 * s); lineTo(7 * s, 11 * s)
                quadraticBezierTo(7 * s, 7 * s, 11 * s, 7 * s); lineTo(19 * s, 7 * s)
                moveTo(15 * s, 3 * s); lineTo(19 * s, 7 * s); lineTo(15 * s, 11 * s)
            }
        }
        if (left == true) {
            // 좌회전은 좌우 반전
            withTransform({ scale(-1f, 1f, center) }) { drawPath(p, Palette.text, style = st) }
        } else {
            drawPath(p, Palette.text, style = st)
        }
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
