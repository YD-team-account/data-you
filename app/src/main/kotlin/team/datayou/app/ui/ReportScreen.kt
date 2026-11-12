package team.datayou.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import team.datayou.engine.model.GaugeLevel

@Composable
fun ReportScreen(s: Screen.Report, vm: DataYouActions) {
    val r = s.report
    Column(
        Modifier.fillMaxSize().background(Palette.bg).padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text("주행 리포트", color = Palette.comfort, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text("오늘의 동승자 컨디션", color = Palette.text, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Text("%.1f분 · %.1fkm · %s".format(r.durationMin, r.distanceKm, s.sourceLabel), color = Palette.muted, fontSize = 16.sp)
            }
            Box(
                Modifier.heightIn(min = 52.dp).background(Palette.surface2, RoundedCornerShape(12.dp)).clickable(role = Role.Button) { vm.backToStart() }.padding(horizontal = 22.dp, vertical = 14.dp),
            ) { Text("다시 출발", color = Palette.text, fontSize = 17.sp) }
        }

        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            // 동승자별
            Column(
                Modifier.weight(0.9f).fillMaxHeight().background(Palette.surface, RoundedCornerShape(20.dp)).padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("동승자별", color = Palette.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                r.passengers.sortedByDescending { it.peakGauge }.forEach { p ->
                    val lv = when { p.peakGauge >= 70 -> GaugeLevel.DISCOMFORT; p.peakGauge >= 40 -> GaugeLevel.CAUTION; else -> GaugeLevel.COMFORT }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(p.profile.type, lv, 56.dp)
                        Column {
                            Text(p.profile.displayName, color = Palette.text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Text("최고 게이지 %.0f".format(p.peakGauge), color = Palette.muted, fontSize = 14.sp)
                        }
                    }
                    val c = p.minutesInLevel[GaugeLevel.COMFORT] ?: 0.0
                    val m = p.minutesInLevel[GaugeLevel.CAUTION] ?: 0.0
                    val d = p.minutesInLevel[GaugeLevel.DISCOMFORT] ?: 0.0
                    val tot = (c + m + d).coerceAtLeast(0.01)
                    Row(Modifier.fillMaxWidth().height(12.dp).background(Palette.surface2, RoundedCornerShape(6.dp))) {
                        if (c > 0) Box(Modifier.weight((c / tot).toFloat()).fillMaxHeight().background(Palette.comfort))
                        if (m > 0) Box(Modifier.weight((m / tot).toFloat()).fillMaxHeight().background(Palette.caution))
                        if (d > 0) Box(Modifier.weight((d / tot).toFloat()).fillMaxHeight().background(Palette.discomfort))
                    }
                    Text("편안 %.1f분 · 주의 %.1f분 · 힘듦 %.1f분".format(c, m, d), color = Palette.faint, fontSize = 13.sp)
                }
            }

            // 원인 비율 (가장 힘들었던 동승자 기준)
            val focus = r.passengers.maxBy { it.peakGauge }
            Column(
                Modifier.weight(1.3f).fillMaxHeight().background(Palette.surface, RoundedCornerShape(20.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("무엇 때문에 힘들었을까요?", color = Palette.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                val shares = focus.causeShare.entries.filter { it.value >= 0.01 }.take(4)
                if (r.smoothTrip || shares.isEmpty()) {
                    Text("부드러운 주행이었어요. 게이지를 올린 원인이 거의 없어요.", color = Palette.muted, fontSize = 16.sp)
                }
                shares.forEach { (cause, share) ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CauseBadge(cause, 28.dp)
                                Text(cause.label + if (cause.actionable) "" else " · 운전자 탓 아님", color = if (cause.actionable) Palette.text else Palette.muted, fontSize = 16.sp)
                            }
                            Text("%.0f%%".format(share * 100), color = Color(0xFFC9D2DB), fontSize = 16.sp)
                        }
                        Row(Modifier.fillMaxWidth().height(10.dp).background(Palette.surface2, RoundedCornerShape(5.dp))) {
                            Box(Modifier.weight(share.toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(if (cause.actionable) cause.badgeColor() else Color(0xFF6B7785), RoundedCornerShape(5.dp)))
                            if (share < 1.0) Box(Modifier.weight((1 - share).toFloat().coerceAtLeast(0.001f)))
                        }
                    }
                }
                Text("회색은 운전자가 바꿀 수 없는 상황이라 코칭에서 빠져요.", color = Palette.faint, fontSize = 13.sp)
            }

            // 다음 주행
            Column(
                Modifier.weight(0.9f).fillMaxHeight().background(Palette.surface, RoundedCornerShape(20.dp)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("다음 주행", color = Palette.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Column {
                    Text("10km당 개선할 이벤트", color = Palette.faint, fontSize = 13.sp)
                    Text("%.1f회".format(r.actionableEventsPer10Km), color = Palette.text, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
                }
                Column {
                    Text("다음 주행 팁", color = Palette.faint, fontSize = 13.sp)
                    Text(r.topImprovement?.coaching ?: "지금처럼 부드럽게 운전해 주세요", color = Palette.text, fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium)
                }
                if (r.eventCounts.isNotEmpty()) {
                    Text(r.eventCounts.entries.joinToString("\n") { "${it.key.label} ${it.value}회" }, color = Palette.muted, fontSize = 14.sp, lineHeight = 22.sp)
                }
            }
        }
    }
}
