package team.datayou.app.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import team.datayou.app.data.GeoFix
import java.io.File

/**
 * 실시간 모드 지도: OpenStreetMap 데이터의 실제 도로 지도(어두운 스타일, CARTO 타일).
 * 구글 서비스·API 키 없이 Pleos 에뮬레이터와 실차에서 동작한다.
 * 진행 방향이 위로 오도록 회전하고, 내 차는 화면 가운데 화살표로 표시한다.
 */
@Composable
fun LiveMap(fix: GeoFix?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val map = remember { createMap(ctx) }
    DisposableEffect(Unit) {
        map.onResume()
        onDispose {
            map.onPause()
            map.onDetach()
        }
    }
    Box(modifier.fillMaxSize().background(Color(0xFF0E1216))) {
        AndroidView(factory = { map }, modifier = Modifier.fillMaxSize(), update = { mv ->
            fix?.let {
                mv.controller.setCenter(GeoPoint(it.lat, it.lon))
                mv.mapOrientation = (-it.bearingDeg).toFloat()
            }
        })
        if (fix == null) {
            Text(
                "현재 위치를 기다리는 중 · 에뮬레이터 ⋯ ▸ Location에서 위치를 지정하세요",
                color = Palette.muted, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.Center).background(Palette.chip, RoundedCornerShape(10.dp)).padding(10.dp),
            )
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2, size.height / 2)
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
        // 지도 출처 표기 (OpenStreetMap·CARTO 이용 조건)
        Text(
            "© OpenStreetMap contributors © CARTO",
            color = Color(0xFF8794A1), fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).background(Color(0x991C2530), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun createMap(ctx: Context): MapView {
    Configuration.getInstance().apply {
        userAgentValue = ctx.packageName
        osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
        osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")
    }
    val dark = XYTileSource(
        "CartoDarkMatter", 0, 19, 256, ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/dark_all/",
            "https://b.basemaps.cartocdn.com/dark_all/",
            "https://c.basemaps.cartocdn.com/dark_all/",
        ),
        "© OpenStreetMap contributors © CARTO",
    )
    return MapView(ctx).apply {
        setTileSource(dark)
        isTilesScaledToDpi = true
        setMultiTouchControls(false)
        zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        controller.setZoom(17.0)
        isFlingEnabled = false
        // 타일을 받기 전 바탕도 어둡게 (밝은 격자가 번쩍이지 않게)
        overlayManager.tilesOverlay.loadingBackgroundColor = android.graphics.Color.rgb(14, 18, 22)
        overlayManager.tilesOverlay.loadingLineColor = android.graphics.Color.rgb(28, 37, 48)
    }
}
