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
import team.datayou.app.data.RouteGeometry
import team.datayou.app.data.RouteNavigation
import team.datayou.app.ui.DataYouTheme
import team.datayou.app.ui.DriveScreen
import team.datayou.app.ui.ReportScreen
import team.datayou.app.ui.StartScreen
import team.datayou.engine.DataYouEngine
import team.datayou.engine.EngineUpdate
import team.datayou.engine.model.PassengerProfile
import team.datayou.engine.model.PassengerType
import team.datayou.engine.replay.Scenarios

/**
 * 앱 화면 4종을 에뮬레이터 없이 렌더링해 PNG로 남긴다.
 * 화면에 들어가는 값은 엔진으로 '가족 나들이' 시나리오를 실제로 돌려서 얻는다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h560dp-land-mdpi")
class ScreenshotTest {

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

    private val passengers = listOf(PassengerProfile("CHILD", PassengerType.CHILD), PassengerProfile("DOG", PassengerType.DOG))

    /** 가족 나들이를 재생하며 모든 업데이트를 모은다. */
    private fun runTrip(): Pair<List<EngineUpdate>, DataYouEngine> {
        val e = DataYouEngine(passengers)
        return Scenarios.familyTrip().mapNotNull { e.process(it) } to e
    }

    private val familySamples = Scenarios.familyTrip()
    private val familyRoute = RouteGeometry.from(familySamples)

    private fun driving(u: EngineUpdate, pre: Boolean = false): Screen.Driving {
        val sample = familySamples.last { it.timestampMs <= u.timestampMs }
        return Screen.Driving(
            sourceLabel = "가족 나들이",
            focus = u.focusPassenger,
            badge = u.badge,
            speedKmh = Math.round(u.speedMps * 3.6).toInt(),
            stopped = u.vehicleStopped,
            tbt = RouteNavigation(familyRoute).update(sample),
            preAlert = pre,
            stopCard = u.stopCard,
            elapsedSec = u.timestampMs / 1000.0,
            speedFactor = 5,
            route = familyRoute,
            tMs = u.timestampMs,
        )
    }

    @Test
    fun start() {
        compose.setContent { DataYouTheme { StartScreen(Screen.Start(), Noop) } }
        compose.onRoot().captureRoboImage("screenshots/1_start.png")
    }

    /** 출발 전 동승자 팝업: 왼쪽(운전석 쪽)에 좁게 떠서 내비 화면을 가리지 않는다 */
    @Test
    fun passengerPanel() {
        val s = Screen.Driving(
            sourceLabel = "가족 나들이", stopped = true, speedFactor = 5, route = familyRoute, tMs = 0,
            passengerPanel = true, passengers = setOf(PassengerType.CHILD, PassengerType.DOG),
        )
        compose.setContent { DataYouTheme { DriveScreen(s, Noop) } }
        compose.onRoot().captureRoboImage("screenshots/1b_passenger_panel.png")
    }

    @Test
    fun drivingCaution() {
        val (updates, _) = runTrip()
        val u = updates.first { it.focusPassenger.level.name == "CAUTION" && it.badge != null && !it.vehicleStopped && it.speedMps > 8 }
        compose.setContent { DataYouTheme { DriveScreen(driving(u), Noop) } }
        compose.onRoot().captureRoboImage("screenshots/2_driving_caution.png")
    }

    @Test
    fun drivingDiscomfortCurves() {
        val (updates, _) = runTrip()
        val u = updates.first { it.focusPassenger.level.name == "DISCOMFORT" && it.badge?.name == "CONTINUOUS_CURVES" }
        compose.setContent { DataYouTheme { DriveScreen(driving(u, pre = true), Noop) } }
        compose.onRoot().captureRoboImage("screenshots/3_driving_discomfort.png")
    }

    @Test
    fun stopCard() {
        val (updates, _) = runTrip()
        val u = updates.first { it.stopCard != null }
        compose.setContent { DataYouTheme { DriveScreen(driving(u), Noop) } }
        compose.onRoot().captureRoboImage("screenshots/4_stop_card.png")
    }

    @Test
    fun report() {
        val (_, e) = runTrip()
        val r = e.finishTrip()
        compose.setContent { DataYouTheme { ReportScreen(Screen.Report(r, "가족 나들이"), Noop) } }
        compose.onRoot().captureRoboImage("screenshots/5_report.png")
    }
}
