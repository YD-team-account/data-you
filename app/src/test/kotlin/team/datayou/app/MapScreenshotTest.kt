package team.datayou.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import team.datayou.app.data.RoadKind
import team.datayou.app.data.RouteGeometry
import team.datayou.app.data.RouteNavigation
import team.datayou.app.ui.DataYouTheme
import team.datayou.app.ui.DriveScreen
import team.datayou.engine.DataYouEngine
import team.datayou.engine.model.PassengerProfile
import team.datayou.engine.model.PassengerType
import team.datayou.engine.model.VehicleSample
import team.datayou.engine.replay.Scenarios
import org.junit.Assert.assertTrue

/** 시나리오마다 지도가 데이터에 맞는 도로 형태로 그려지는지 확인한다. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h560dp-land-mdpi")
class MapScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private object Noop : DataYouActions {
        override fun togglePassenger(type: PassengerType) {}
        override fun selectScenario(asset: String) {}
        override fun setLive(live: Boolean) {}
        override fun startTrip() {}
        override fun setSpeedFactor(f: Int) {}
        override fun feedback(uncomfortable: Boolean) {}
        override fun finishTrip() {}
        override fun backToStart() {}
    }

    private fun shot(name: String, label: String, samples: List<VehicleSample>, pick: (List<VehicleSample>, RouteGeometry) -> Int) {
        val route = RouteGeometry.from(samples)
        val i = pick(samples, route)
        val e = DataYouEngine(listOf(PassengerProfile("c", PassengerType.CHILD)))
        val u = samples.take(i + 1).mapNotNull { e.process(it) }.last()
        val s = samples[i]
        val d = Screen.Driving(
            sourceLabel = label,
            focus = u.focusPassenger,
            badge = u.badge,
            speedKmh = Math.round(s.speedMps * 3.6).toInt(),
            stopped = u.vehicleStopped,
            tbt = RouteNavigation(route).update(s),
            elapsedSec = s.timestampMs / 1000.0,
            speedFactor = 5,
            route = route,
            tMs = s.timestampMs,
        )
        compose.setContent { DataYouTheme { DriveScreen(d, Noop) } }
        compose.onRoot().captureRoboImage("screenshots/map_$name.png")
    }

    @Test
    fun mountain() = shot("mountain", "산길 연속 커브", Scenarios.mountainCurves()) { s, _ -> s.indexOfFirst { it.timestampMs >= 120_000 } }

    @Test
    fun highwayLaneChange() = shot("highway_lane_change", "급차선변경", Scenarios.abruptLaneChange(false)) { s, r ->
        // 차선 변경 직후
        val k = r.points.indexOfFirst { kotlin.math.abs(it.laneOffset) > 1.5 }
        assertTrue("차선 변경이 경로에 보여야 함", k > 0)
        k + 10
    }

    @Test
    fun cityStop() = shot("city_stop", "시내 급가감속", Scenarios.cityAggressive()) { s, r ->
        assertTrue(r.points.all { it.kind == RoadKind.CITY || it.kind == RoadKind.ARTERIAL })
        s.indexOfFirst { it.timestampMs > 40_000 && it.speedMps < 0.2 }
    }

    @Test
    fun traffic() = shot("traffic", "정체 가다 서다", Scenarios.trafficStopAndGo()) { s, r ->
        assertTrue(r.points.any { it.congested })
        s.indexOfFirst { it.timestampMs > 60_000 && it.speedMps > 3 }
    }

    @Test
    fun familyTurn() = shot("family_turn", "가족 나들이", Scenarios.familyTrip()) { s, r ->
        assertTrue("우회전이 안내돼야 함", r.maneuvers.any { it.isTurn && !it.left })
        val m = r.maneuvers.first { it.isTurn }
        r.points.indexOfFirst { it.s > m.s - 120 }
    }

    @Test
    fun familyRiverCurves() = shot("family_curves", "가족 나들이", Scenarios.familyTrip()) { _, r ->
        r.points.indexOfFirst { it.kind == RoadKind.WINDING } + 400
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h560dp-land-mdpi")
class LiveMapSmokeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun liveMapRendersWithoutCrash() {
        val d = Screen.Driving(
            sourceLabel = "실시간 · Pleos 차량",
            isReplay = false,
            speedKmh = 38,
            channels = "속도✓ 브레이크– 깜빡이✓ 조향✓ 주행거리– 내비– GPS✓",
            geo = team.datayou.app.data.GeoFix(37.5512, 126.9882, 30.0, true),
        )
        compose.setContent { DataYouTheme { DriveScreen(d, object : DataYouActions {
            override fun togglePassenger(type: PassengerType) {}
            override fun selectScenario(asset: String) {}
            override fun setLive(live: Boolean) {}
            override fun startTrip() {}
            override fun setSpeedFactor(f: Int) {}
            override fun feedback(uncomfortable: Boolean) {}
            override fun finishTrip() {}
            override fun backToStart() {}
        }) } }
        compose.onRoot().captureRoboImage("screenshots/map_live_offline.png")
    }
}
