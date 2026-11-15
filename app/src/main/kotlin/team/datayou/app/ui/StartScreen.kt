package team.datayou.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.datayou.app.DataYouActions
import team.datayou.app.Screen
import team.datayou.app.data.ReplayDataSource
import team.datayou.engine.model.GaugeLevel
import team.datayou.engine.model.PassengerType

private val SELECTABLE = listOf(
    PassengerType.CHILD, PassengerType.PREGNANT, PassengerType.SENIOR, PassengerType.DOG, PassengerType.SENSITIVE_ADULT,
)

@Composable
fun StartScreen(s: Screen.Start, vm: DataYouActions) {
    Column(
        Modifier.fillMaxSize().background(Palette.bg).padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("다 타유!", color = Palette.comfort, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text("오늘은 누가 함께 타나요?", color = Palette.text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Text("선택한 동승자 기준으로 흔들림을 살펴볼게요. 운전 중에는 작은 아이콘으로만 알려드려요.", color = Palette.muted, fontSize = 16.sp)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SELECTABLE.forEach { type ->
                val on = type in s.selected
                Column(
                    Modifier
                        .weight(1f)
                        .background(if (on) Color(0xFF1E2C2A) else Palette.surface, RoundedCornerShape(20.dp))
                        .border(2.dp, if (on) Palette.comfort else Color(0xFF2A3440), RoundedCornerShape(20.dp))
                        .toggleable(value = on, role = Role.Checkbox) { vm.togglePassenger(type) }
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(64.dp).background(Palette.surface2, CircleShape), contentAlignment = Alignment.Center) {
                        PassengerFace(type, GaugeLevel.COMFORT, Modifier.size(40.dp))
                    }
                    Text(type.label.removeSuffix(" 동승자"), color = Palette.text, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                    Text(type.note(), color = Palette.muted, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // 데모 데이터 선택 (실차에서는 숨김)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("데이터", color = Palette.faint, fontSize = 14.sp)
            ReplayDataSource.SCENARIOS.forEach { (asset, name) ->
                Pill(name, selected = !s.live && s.scenarioAsset == asset) { vm.setLive(false); vm.selectScenario(asset) }
            }
            Pill("실시간 차량 데이터", selected = s.live) { vm.setLive(true) }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (s.selected.isEmpty()) "동승자를 한 명 이상 골라주세요"
                else "${s.selected.size}명 선택됨 · 가장 힘든 동승자 한 명만 운전 화면에 표시돼요",
                color = Palette.muted, fontSize = 15.sp,
            )
            val enabled = s.selected.isNotEmpty()
            Box(
                Modifier
                    .heightIn(min = 56.dp)
                    .background(if (enabled) Palette.comfort else Palette.surface2, RoundedCornerShape(14.dp))
                    .clickable(enabled = enabled, role = Role.Button) { vm.startTrip() }
                    .padding(horizontal = 36.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("이대로 출발", color = if (enabled) Palette.onComfort else Palette.faint, fontSize = 19.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .background(if (selected) Palette.surface2 else Palette.surface, RoundedCornerShape(10.dp))
            .border(1.dp, if (selected) Palette.comfort else Palette.line, RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (selected) Palette.text else Palette.muted, fontSize = 14.sp) }
}
