package team.datayou.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
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

/** 캐릭터 반응: 반응 순간의 자세와 휴식 표정, 그리고 바깥에서 주입한 동작·그림이 쓰이는지. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h560dp-land-mdpi")
class ReactionScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val noop = object : DataYouActions {
        override fun togglePassenger(type: PassengerType) {}
        override fun selectScenario(asset: String) {}
        override fun setLive(live: Boolean) {}
        override fun startTrip() {}
        override fun setSpeedFactor(f: Int) {}
        override fun feedback(uncomfortable: Boolean) {}
        override fun finishTrip() {}
        override fun backToStart() {}
    }

    private fun state(kind: team.datayou.engine.model.ReactionKind?, resting: Boolean = false, level: team.datayou.engine.model.GaugeLevel = team.datayou.engine.model.GaugeLevel.CAUTION): Screen.Driving {
        val samples = Scenarios.cityAggressive()
        val route = RouteGeometry.from(samples)
        val s = samples[300]
        return Screen.Driving(
            sourceLabel = "반응 확인",
            focus = team.datayou.engine.PassengerState(PassengerProfile("c", PassengerType.CHILD), if (level == team.datayou.engine.model.GaugeLevel.COMFORT) 10.0 else 52.0, level),
            badge = team.datayou.engine.model.Cause.HARD_BRAKE,
            speedKmh = 38,
            route = route,
            tMs = s.timestampMs,
            reaction = kind?.let { team.datayou.engine.model.Reaction(it, team.datayou.engine.model.Cause.HARD_BRAKE, 1000L) },
            reactionShowing = kind != null,
            resting = resting,
        )
    }

    /** 반응 시작 후 [ms] 시점의 장면을 찍는다 */
    private fun shotAt(name: String, d: Screen.Driving, ms: Long, content: @androidx.compose.runtime.Composable () -> Unit = { DataYouTheme { DriveScreen(d, noop) } }) {
        compose.mainClock.autoAdvance = false
        compose.setContent(content)
        compose.mainClock.advanceTimeBy(ms)
        compose.onRoot().captureRoboImage("screenshots/reaction_$name.png")
    }

    @Test
    fun leanForward() = shotAt("lean_forward", state(team.datayou.engine.model.ReactionKind.LEAN_FORWARD), 0)

    @Test
    fun leanBack() = shotAt("lean_back", state(team.datayou.engine.model.ReactionKind.LEAN_BACK), 0)

    @Test
    fun tiltRight() = shotAt("tilt_right", state(team.datayou.engine.model.ReactionKind.TILT_RIGHT), 0)

    @Test
    fun resting() = shotAt("resting", state(null, resting = true, level = team.datayou.engine.model.GaugeLevel.COMFORT), 0)

    private fun frame(name: String): java.io.File {
        val f = java.io.File("build/frames/$name.png")
        compose.onRoot().captureRoboImage(f.path)
        return f
    }

    private fun samePixels(a: java.io.File, b: java.io.File): Boolean {
        val x = android.graphics.BitmapFactory.decodeFile(a.path)
        val y = android.graphics.BitmapFactory.decodeFile(b.path)
        return x.sameAs(y)
    }

    /** 주행 중 칩은 시간이 지나도 화면이 그대로여야 한다 (움직이는 요소 없음) */
    @Test
    fun drivingChipDoesNotMove() {
        val d = state(team.datayou.engine.model.ReactionKind.LEAN_FORWARD).copy(preAlert = true)
        compose.mainClock.autoAdvance = false
        compose.setContent { DataYouTheme { DriveScreen(d, noop) } }
        compose.mainClock.advanceTimeBy(50)
        val a = frame("static_0")
        for (t in listOf(150L, 300L, 600L, 1200L)) {
            compose.mainClock.advanceTimeBy(t)
            org.junit.Assert.assertTrue("주행 중 화면이 ${t}ms 뒤 바뀜", samePixels(a, frame("static_$t")))
        }
        compose.onRoot().captureRoboImage("screenshots/reaction_prealert_static.png")
    }

    /** 정차 카드에서만 반응 동작을 다시 보여준다 */
    @Test
    fun stopCardReplays() {
        val card = team.datayou.engine.StopCard(PassengerProfile("c", PassengerType.CHILD), team.datayou.engine.model.GaugeLevel.CAUTION, team.datayou.engine.model.Cause.HARD_BRAKE, 2)
        val d = state(null).copy(stopped = true, speedKmh = 0, stopCard = card, stopCardReplay = team.datayou.engine.model.ReactionKind.LEAN_FORWARD)
        compose.mainClock.autoAdvance = false
        compose.setContent { DataYouTheme { DriveScreen(d, noop) } }
        compose.mainClock.advanceTimeBy(100)
        val before = frame("replay_before")
        compose.mainClock.advanceTimeBy(400 + 130)
        val mid = frame("replay_mid")
        org.junit.Assert.assertFalse("정차 카드 캐릭터가 움직여야 함", samePixels(before, mid))
        compose.onRoot().captureRoboImage("screenshots/reaction_stopcard_replay.png")
    }

    @Test
    fun injectedMotionAndRenderer() {
        // 바깥에서 주입: 과장된 동작 + 다른 그림(사각형 캐릭터)
        val motion = team.datayou.app.ui.anim.KeyframeReactionMotion.fromJson(
            """{"reactions":{"LEAN_FORWARD":{"durationMs":1000,"keyframes":[{"t":0},{"t":0.5,"rotationZ":40,"dy":20,"scale":1.3},{"t":1}]}}}""",
        )
        val renderer: team.datayou.app.ui.anim.CharacterRenderer = { _, _, _, size, modifier ->
            androidx.compose.foundation.layout.Box(modifier.size(size).background(androidx.compose.ui.graphics.Color(0xFFFF9B80)))
        }
        val d = state(team.datayou.engine.model.ReactionKind.LEAN_FORWARD)
        shotAt("injected", d, 0) {
            androidx.compose.runtime.CompositionLocalProvider(
                team.datayou.app.ui.anim.LocalReactionMotion provides motion,
                team.datayou.app.ui.anim.LocalCharacterRenderer provides renderer,
            ) { DataYouTheme { DriveScreen(d, noop) } }
        }
    }
}
