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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import team.datayou.app.DataYouActions
import team.datayou.app.Screen
import team.datayou.app.data.ReplayDataSource

/**
 * 출발 화면. 시연용 데이터만 고르고 바로 주행 화면으로 간다.
 * 동승자는 주행 화면 왼쪽 팝업에서 고르며(고르지 않아도 됨), 지난번 선택을 기억한다.
 */
@Composable
fun StartScreen(s: Screen.Start, vm: DataYouActions) {
    Column(
        Modifier.fillMaxSize().background(Palette.bg).padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("다 타유", color = Palette.comfort, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    "시연용 화면 · 실차에서는 이 화면 없이 바로 주행 화면으로 시작해요",
                    color = Palette.caution, fontSize = 13.sp,
                    modifier = Modifier.border(1.dp, Palette.caution, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Text("함께 탄 사람까지 편안하게", color = Palette.text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Text("동승자는 주행 화면 왼쪽에서 고를 수 있어요. 고르지 않아도 바로 출발할 수 있고, 지난번 선택을 기억해요.", color = Palette.muted, fontSize = 16.sp)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf(
                "1" to "동승자 고르기 (선택)",
                "2" to "달리면 팝업이 저절로 닫혀요",
                "3" to "흔들림 원인은 정차할 때 알려줘요",
            ).forEach { (n, t) ->
                Row(
                    Modifier.weight(1f).background(Palette.surface, RoundedCornerShape(16.dp)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(32.dp).background(Palette.surface2, CircleShape), contentAlignment = Alignment.Center) {
                        Text(n, color = Palette.comfort, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(t, color = Palette.text, fontSize = 16.sp)
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

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .heightIn(min = 56.dp)
                    .background(Palette.comfort, RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button) { vm.startTrip() }
                    .padding(horizontal = 36.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("주행 시작", color = Palette.onComfort, fontSize = 19.sp, fontWeight = FontWeight.SemiBold) }
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
