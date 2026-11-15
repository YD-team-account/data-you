package team.datayou.app.ui.anim

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.json.JSONObject
import team.datayou.app.ui.Avatar
import team.datayou.app.ui.FaceMood
import team.datayou.engine.model.GaugeLevel
import team.datayou.engine.model.PassengerType
import team.datayou.engine.model.ReactionKind
import java.io.File

/*
 * 캐릭터 애니메이션 주입 지점
 *
 *  엔진은 "무슨 반응인가"(ReactionKind)만 정하고, "어떻게 움직이나"는 여기서 주입받는다.
 *
 *  ① 데이터:  KeyframeReactionMotion  ← JSON (animations/reactions.json)
 *             우선순위: 기기 파일(외부 파일 폴더) > 앱 assets > 내장 기본값
 *  ② 코드:    ReactionMotion 구현을 LocalReactionMotion으로 제공
 *  ③ 그림:    CharacterRenderer(얼굴 그림 자체)를 LocalCharacterRenderer로 교체 (Lottie·이미지 등)
 *
 *  주의분산 정책 (Android for Cars 품질 기준 SA-1 참고):
 *   - 주행 중: 움직이지 않는다. 반응이 생기면 holdPose(정지 자세)로 바뀌어 잠시 유지됐다가 돌아온다.
 *   - 정차 중: 정차 카드에서 그 반응 동작 전체(pose 0..1)를 재생해 "방금 이렇게 쏠렸어요"를 보여준다.
 *
 *  형식과 예시: docs/design/character-animation.md
 */

/** 한 순간의 자세. 아바타에 graphicsLayer로 적용된다. */
data class MotionPose(
    /** 앞뒤로 숙임(도). + 앞으로 쏠림, − 뒤로 젖힘 */
    val rotationX: Float = 0f,
    /** 좌우 기울기(도). + 오른쪽 */
    val rotationZ: Float = 0f,
    val dxDp: Float = 0f,
    val dyDp: Float = 0f,
    val scale: Float = 1f,
) {
    fun lerp(o: MotionPose, f: Float) = MotionPose(
        rotationX + (o.rotationX - rotationX) * f,
        rotationZ + (o.rotationZ - rotationZ) * f,
        dxDp + (o.dxDp - dxDp) * f,
        dyDp + (o.dyDp - dyDp) * f,
        scale + (o.scale - scale) * f,
    )
}

/** 반응 동작 정의. 구현해서 LocalReactionMotion으로 주입한다. */
interface ReactionMotion {
    /** 동작 길이(ms). 0이면 움직이지 않음 */
    fun durationMs(kind: ReactionKind): Int
    /** progress 0..1 에서의 자세 */
    fun pose(kind: ReactionKind, progress: Float): MotionPose
    /** 동작 중 표정 */
    fun mood(kind: ReactionKind): FaceMood = FaceMood.SURPRISED

    /** 주행 중에 보여줄 정지 자세. 기본값: 동작 중 가장 크게 쏠린 순간 */
    fun holdPose(kind: ReactionKind): MotionPose =
        (0..50).map { pose(kind, it / 50f) }.maxBy { kotlin.math.abs(it.rotationX) + kotlin.math.abs(it.rotationZ) + kotlin.math.abs(it.dxDp) + kotlin.math.abs(it.dyDp) }
}

/** 키프레임 기반 구현. JSON으로 만들 수 있다. 구간 사이는 부드럽게(smoothstep) 보간한다. */
class KeyframeReactionMotion(val clips: Map<ReactionKind, Clip>, val source: String = "code") : ReactionMotion {

    data class Keyframe(val t: Float, val pose: MotionPose)
    /** @param hold 주행 중 정지 자세. null이면 가장 크게 쏠린 키프레임 */
    data class Clip(val durationMs: Int, val mood: FaceMood, val keyframes: List<Keyframe>, val hold: MotionPose? = null)

    override fun durationMs(kind: ReactionKind) = clips[kind]?.durationMs ?: 0
    override fun holdPose(kind: ReactionKind): MotionPose = clips[kind]?.hold ?: super.holdPose(kind)
    override fun mood(kind: ReactionKind) = clips[kind]?.mood ?: FaceMood.SURPRISED

    override fun pose(kind: ReactionKind, progress: Float): MotionPose {
        val kf = clips[kind]?.keyframes ?: return MotionPose()
        if (kf.isEmpty()) return MotionPose()
        val p = progress.coerceIn(0f, 1f)
        if (p <= kf.first().t) return kf.first().pose
        for (i in 0 until kf.size - 1) {
            val a = kf[i]; val b = kf[i + 1]
            if (p <= b.t) {
                val f = if (b.t > a.t) (p - a.t) / (b.t - a.t) else 1f
                return a.pose.lerp(b.pose, f * f * (3 - 2 * f))
            }
        }
        return kf.last().pose
    }

    companion object {
        const val FILE_NAME = "animations/reactions.json"

        private fun clip(ms: Int, vararg k: Pair<Float, MotionPose>) = Clip(ms, FaceMood.SURPRISED, k.map { Keyframe(it.first, it.second) })

        /** 내장 기본값. assets/animations/reactions.json 과 같은 내용. */
        val Default = KeyframeReactionMotion(
            mapOf(
                ReactionKind.LEAN_BACK to clip(650, 0f to MotionPose(), .2f to MotionPose(rotationX = -30f, dyDp = -6f), .55f to MotionPose(rotationX = 8f, dyDp = 2f), 1f to MotionPose()),
                ReactionKind.LEAN_FORWARD to clip(650, 0f to MotionPose(), .2f to MotionPose(rotationX = 30f, dyDp = 7f), .55f to MotionPose(rotationX = -8f, dyDp = -2f), 1f to MotionPose()),
                ReactionKind.TILT_LEFT to clip(700, 0f to MotionPose(), .22f to MotionPose(rotationZ = -18f, dxDp = -7f), .58f to MotionPose(rotationZ = 5f, dxDp = 2f), 1f to MotionPose()),
                ReactionKind.TILT_RIGHT to clip(700, 0f to MotionPose(), .22f to MotionPose(rotationZ = 18f, dxDp = 7f), .58f to MotionPose(rotationZ = -5f, dxDp = -2f), 1f to MotionPose()),
            ),
            source = "built-in",
        )

        /** JSON → 동작. 빠진 반응은 [fallback]의 것을 쓴다. 형식이 틀리면 예외. */
        fun fromJson(json: String, fallback: KeyframeReactionMotion = Default, source: String = "json"): KeyframeReactionMotion {
            val root = JSONObject(json)
            val reactions = root.getJSONObject("reactions")
            val clips = fallback.clips.toMutableMap()
            for (name in reactions.keys()) {
                val kind = ReactionKind.valueOf(name)
                val c = reactions.getJSONObject(name)
                val arr = c.getJSONArray("keyframes")
                val kfs = (0 until arr.length()).map { i ->
                    val k = arr.getJSONObject(i)
                    Keyframe(
                        k.getDouble("t").toFloat(),
                        MotionPose(
                            k.optDouble("rotationX", 0.0).toFloat(), k.optDouble("rotationZ", 0.0).toFloat(),
                            k.optDouble("dx", 0.0).toFloat(), k.optDouble("dy", 0.0).toFloat(), k.optDouble("scale", 1.0).toFloat(),
                        ),
                    )
                }.sortedBy { it.t }
                require(kfs.isNotEmpty()) { "$name: keyframes가 비어 있음" }
                val hold = c.optJSONObject("hold")?.let { h ->
                    MotionPose(
                        h.optDouble("rotationX", 0.0).toFloat(), h.optDouble("rotationZ", 0.0).toFloat(),
                        h.optDouble("dx", 0.0).toFloat(), h.optDouble("dy", 0.0).toFloat(), h.optDouble("scale", 1.0).toFloat(),
                    )
                }
                clips[kind] = Clip(c.optInt("durationMs", 650).coerceIn(0, 3000), FaceMood.valueOf(c.optString("mood", "SURPRISED")), kfs, hold)
            }
            return KeyframeReactionMotion(clips, source)
        }

        /**
         * 주입된 동작을 찾는다. 우선순위:
         *  1. 기기 파일  <외부 파일 폴더>/animations/reactions.json  (adb push로 재빌드 없이 교체)
         *  2. 앱 assets  animations/reactions.json
         *  3. 내장 기본값
         * 읽기·형식 오류는 로그만 남기고 다음 순위로 넘어간다.
         */
        fun load(context: Context): KeyframeReactionMotion {
            val file = context.getExternalFilesDir(null)?.let { File(it, FILE_NAME) }
            if (file != null && file.isFile) {
                runCatching { return fromJson(file.readText(), source = "file:${file.path}") }
                    .onFailure { Log.w(TAG, "기기 애니메이션 파일 오류, 무시함: ${it.message}") }
            }
            runCatching { context.assets.open(FILE_NAME).bufferedReader().use { it.readText() } }
                .mapCatching { fromJson(it, source = "assets") }
                .onSuccess { return it }
                .onFailure { Log.i(TAG, "assets 애니메이션 없음/오류 → 내장 기본값 (${it.message})") }
            return Default
        }

        private const val TAG = "DataYouAnim"
    }
}

/** 얼굴 그림을 그리는 함수. Lottie·이미지 등으로 바꾸려면 LocalCharacterRenderer로 주입한다. */
typealias CharacterRenderer = @Composable (type: PassengerType, level: GaugeLevel, mood: FaceMood, size: Dp, modifier: Modifier) -> Unit

val DefaultCharacterRenderer: CharacterRenderer = { type, level, mood, size, modifier ->
    Avatar(type, level, size, mood = mood, modifier = modifier)
}

/** 정차 카드에서 다시 보여줄 반응: 같은 원인의 최근 반응이 있으면 그 방향, 없으면 원인으로 정한다. */
fun replayKindFor(cause: team.datayou.engine.model.Cause, last: team.datayou.engine.model.Reaction?): ReactionKind? {
    if (last != null && last.cause == cause) return last.kind
    return when (cause) {
        team.datayou.engine.model.Cause.HARD_ACCEL -> ReactionKind.LEAN_BACK
        team.datayou.engine.model.Cause.HARD_BRAKE, team.datayou.engine.model.Cause.REGEN_DECEL,
        team.datayou.engine.model.Cause.BRAKE_PUMPING -> ReactionKind.LEAN_FORWARD
        team.datayou.engine.model.Cause.SHARP_LATERAL, team.datayou.engine.model.Cause.CONTINUOUS_CURVES -> ReactionKind.TILT_RIGHT
        team.datayou.engine.model.Cause.STOP_AND_GO -> null
    }
}

val LocalReactionMotion = staticCompositionLocalOf<ReactionMotion> { KeyframeReactionMotion.Default }
val LocalCharacterRenderer = staticCompositionLocalOf<CharacterRenderer> { DefaultCharacterRenderer }
