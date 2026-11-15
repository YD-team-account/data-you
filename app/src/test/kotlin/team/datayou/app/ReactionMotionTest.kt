package team.datayou.app

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import team.datayou.app.ui.FaceMood
import team.datayou.app.ui.anim.KeyframeReactionMotion
import team.datayou.engine.model.ReactionKind
import java.io.File

/** 캐릭터 반응 동작 주입: JSON 형식, 보간, 우선순위(기기 파일 > assets > 내장). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReactionMotionTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `JSON에 없는 반응은 내장 기본값을 쓴다`() {
        val m = KeyframeReactionMotion.fromJson("""{"reactions":{"LEAN_BACK":{"durationMs":400,"mood":"RESTING","keyframes":[{"t":0},{"t":0.5,"rotationX":-40},{"t":1}]}}}""")
        assertEquals(400, m.durationMs(ReactionKind.LEAN_BACK))
        assertEquals(FaceMood.RESTING, m.mood(ReactionKind.LEAN_BACK))
        assertEquals(-40f, m.pose(ReactionKind.LEAN_BACK, 0.5f).rotationX, 1e-3f)
        assertEquals(KeyframeReactionMotion.Default.durationMs(ReactionKind.TILT_LEFT), m.durationMs(ReactionKind.TILT_LEFT))
    }

    @Test
    fun `처음과 끝은 원래 자세이고 중간에 쏠린다`() {
        val m = KeyframeReactionMotion.Default
        for (k in ReactionKind.values()) {
            assertEquals(0f, m.pose(k, 0f).rotationX + m.pose(k, 0f).rotationZ, 1e-3f)
            assertEquals(0f, m.pose(k, 1f).rotationX + m.pose(k, 1f).rotationZ, 1e-3f)
        }
        assertTrue(m.pose(ReactionKind.LEAN_FORWARD, 0.2f).rotationX > 20f)
        assertTrue(m.pose(ReactionKind.LEAN_BACK, 0.2f).rotationX < -20f)
        assertTrue(m.pose(ReactionKind.TILT_RIGHT, 0.22f).rotationZ > 10f)
        assertTrue(m.pose(ReactionKind.TILT_LEFT, 0.22f).rotationZ < -10f)
    }

    @Test(expected = Exception::class)
    fun `알 수 없는 반응 이름은 거부한다`() {
        KeyframeReactionMotion.fromJson("""{"reactions":{"JUMP":{"keyframes":[{"t":0}]}}}""")
    }

    @Test
    fun `앱에 포함된 기본 JSON은 내장값과 같다`() {
        val json = ctx.assets.open(KeyframeReactionMotion.FILE_NAME).bufferedReader().use { it.readText() }
        val m = KeyframeReactionMotion.fromJson(json)
        for (k in ReactionKind.values()) for (p in listOf(0.1f, 0.2f, 0.4f, 0.7f)) {
            assertEquals(KeyframeReactionMotion.Default.pose(k, p), m.pose(k, p))
        }
    }

    @Test
    fun `기기 파일이 있으면 assets보다 먼저 쓴다 · 깨진 파일은 무시한다`() {
        val f = File(ctx.getExternalFilesDir(null), KeyframeReactionMotion.FILE_NAME)
        f.parentFile!!.mkdirs()
        f.writeText("""{"reactions":{"TILT_LEFT":{"durationMs":900,"keyframes":[{"t":0},{"t":1}]}}}""")
        assertEquals(900, KeyframeReactionMotion.load(ctx).durationMs(ReactionKind.TILT_LEFT))
        assertTrue(KeyframeReactionMotion.load(ctx).source.startsWith("file:"))
        f.writeText("{ 깨진 json")
        assertEquals("assets", KeyframeReactionMotion.load(ctx).source)
        f.delete()
    }
}
