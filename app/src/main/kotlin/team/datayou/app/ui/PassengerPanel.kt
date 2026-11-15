package team.datayou.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import team.datayou.engine.model.GaugeLevel
import team.datayou.engine.model.PassengerType

val SELECTABLE_PASSENGERS = listOf(
    PassengerType.CHILD, PassengerType.PREGNANT, PassengerType.SENIOR, PassengerType.DOG, PassengerType.SENSITIVE_ADULT,
)

/**
 * 출발 전 동승자 팝업. 내비 화면을 가리지 않도록 운전석 쪽(왼쪽)에 좁게 뜬다.
 * - 고르지 않아도 출발할 수 있다(일반 동승자 기준으로 계산).
 * - 고른 값은 바로 저장되어 다음 주행의 팝업이 같은 값으로 열린다.
 * - 차가 움직이기 시작하면 자동으로 닫힌다. 닫힐 때도 움직이는 효과를 쓰지 않는다.
 */
@Composable
fun PassengerPanel(selected: Set<PassengerType>, vm: DataYouActions, modifier: Modifier = Modifier) {
    Column(
        modifier
            .width(340.dp)
            .background(Palette.chip, RoundedCornerShape(20.dp))
            .border(1.dp, Color(0xFF34404D), RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("누가 함께 타나요?", color = Palette.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text("고르지 않아도 돼요 · 출발하면 자동으로 닫혀요", color = Palette.muted, fontSize = 13.sp)
        SELECTABLE_PASSENGERS.forEach { type ->
            val on = type in selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .background(if (on) Color(0xFF1E2C2A) else Palette.surface, RoundedCornerShape(12.dp))
                    .border(1.5.dp, if (on) Palette.comfort else Color.Transparent, RoundedCornerShape(12.dp))
                    .toggleable(value = on, role = Role.Checkbox) { vm.togglePassenger(type) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PassengerFace(type, GaugeLevel.COMFORT, Modifier.size(34.dp))
                Text(type.label.removeSuffix(" 동승자"), color = Palette.text, fontSize = 18.sp, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(22.dp)
                        .background(if (on) Palette.comfort else Color.Transparent, CircleShape)
                        .border(2.dp, if (on) Palette.comfort else Palette.faint, CircleShape),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (selected.isEmpty()) "선택 없음 · 일반 동승자 기준" else "${selected.size}명 · 다음에도 기억해요",
                color = Palette.faint, fontSize = 13.sp, modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .heightIn(min = 48.dp)
                    .background(Palette.comfort, RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button) { vm.closePassengerPanel() }
                    .padding(horizontal = 26.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (selected.isEmpty()) "건너뛰기" else "확인", color = Palette.onComfort, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}
