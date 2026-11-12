package team.datayou.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import team.datayou.engine.model.Cause
import team.datayou.engine.model.CauseKind
import team.datayou.engine.model.GaugeLevel
import team.datayou.engine.model.PassengerType

/** 화면 시안과 같은 야간 차량 디스플레이 팔레트 */
object Palette {
    val bg = Color(0xFF121820)
    val chip = Color(0xFF1C2530)
    val surface = Color(0xFF1A2129)
    val surface2 = Color(0xFF26313D)
    val line = Color(0xFF2E3945)
    val text = Color(0xFFF2F4F6)
    val muted = Color(0xFFA9B4C0)
    val faint = Color(0xFF8794A1)
    val comfort = Color(0xFF3CC4A0)
    val caution = Color(0xFFF2B33D)
    val discomfort = Color(0xFFFF7A59)
    val moment = Color(0xFFFF9B80)
    val situation = Color(0xFFA9B4C0)
    val route = Color(0xFF3D8BFF)
    val link = Color(0xFF7CC4FF)
    val onAccent = Color(0xFF1A1206)
    val onComfort = Color(0xFF0B2A22)
    val road = Color(0xFF232D38)
}

fun GaugeLevel.color(): Color = when (this) {
    GaugeLevel.COMFORT -> Palette.comfort
    GaugeLevel.CAUTION -> Palette.caution
    GaugeLevel.DISCOMFORT -> Palette.discomfort
}

fun GaugeLevel.label(): String = when (this) {
    GaugeLevel.COMFORT -> "편안"
    GaugeLevel.CAUTION -> "주의"
    GaugeLevel.DISCOMFORT -> "힘듦"
}

fun Cause.badgeColor(): Color = when (kind) {
    CauseKind.MOMENT -> Palette.moment
    CauseKind.PATTERN -> Palette.caution
    CauseKind.SITUATION -> Palette.situation
}

fun PassengerType.note(): String = when (this) {
    PassengerType.CHILD -> "가다 서다, 연속 커브에 민감해요"
    PassengerType.PREGNANT -> "급제동, 급출발에 가장 민감해요"
    PassengerType.SENIOR -> "급회전 때 몸 쏠림을 줄여요"
    PassengerType.DOG -> "어린 강아지일수록 민감해요"
    PassengerType.SENSITIVE_ADULT -> "타면서 개인에 맞게 보정돼요"
    PassengerType.ADULT -> "기본 민감도"
}

@Composable
fun DataYouTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Palette.bg,
            surface = Palette.surface,
            primary = Palette.comfort,
            onPrimary = Palette.onComfort,
            onBackground = Palette.text,
            onSurface = Palette.text,
        ),
        content = content,
    )
}
