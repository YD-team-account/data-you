package team.datayou.app

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import team.datayou.app.data.FakeNavigation
import team.datayou.app.data.NavigationSource
import team.datayou.app.data.PleosNavigation
import team.datayou.app.data.PleosVehicleDataSource
import team.datayou.app.data.ReplayDataSource
import team.datayou.app.data.TbtInfo
import team.datayou.app.data.VehicleDataSource
import team.datayou.engine.DataYouEngine
import team.datayou.engine.PassengerState
import team.datayou.engine.StopCard
import team.datayou.engine.model.Cause
import team.datayou.engine.model.GaugeLevel
import team.datayou.engine.model.PassengerProfile
import team.datayou.engine.model.PassengerType
import team.datayou.engine.report.TripReport
import java.util.Locale

/** 앱 화면 상태. 출발 → 주행 → 리포트. */
sealed interface Screen {
    data class Start(
        val selected: Set<PassengerType> = setOf(PassengerType.CHILD, PassengerType.DOG),
        val scenarioAsset: String = ReplayDataSource.SCENARIOS.keys.first(),
        val live: Boolean = false,
    ) : Screen

    data class Driving(
        val sourceLabel: String,
        val focus: PassengerState? = null,
        val badge: Cause? = null,
        val speedKmh: Int = 0,
        val stopped: Boolean = false,
        val tbt: TbtInfo? = null,
        val preAlert: Boolean = false,
        val stopCard: StopCard? = null,
        val feedbackNote: String? = null,
        val elapsedSec: Double = 0.0,
        val speedFactor: Int = 1,
        val isReplay: Boolean = true,
        val liveUnavailable: Boolean = false,
        /** 실시간 모드의 채널 수신 상태 (데모 막대에 표시) */
        val channels: String? = null,
    ) : Screen

    data class Report(val report: TripReport, val sourceLabel: String) : Screen
}

class DataYouViewModel(app: Application) : AndroidViewModel(app), DataYouActions {

    private val _screen = MutableStateFlow<Screen>(Screen.Start())
    val screen: StateFlow<Screen> = _screen

    private var engine: DataYouEngine? = null
    private var source: VehicleDataSource? = null
    private var nav: NavigationSource = FakeNavigation()
    private var cardShownAtMs: Long? = null

    // ── 효과음·음성 (M3에서 음성은 Gleo AI TTS로 교체) ──
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }.getOrNull()
    private var ttsReady = false
    private val tts: TextToSpeech = TextToSpeech(app) { status ->
        ttsReady = status == TextToSpeech.SUCCESS
    }.also { it.language = Locale.KOREAN }

    // ── 출발 화면 ──
    override fun togglePassenger(type: PassengerType) = _screen.update { s ->
        if (s !is Screen.Start) s else s.copy(selected = if (type in s.selected) s.selected - type else s.selected + type)
    }

    override fun selectScenario(asset: String) = _screen.update { s -> if (s is Screen.Start) s.copy(scenarioAsset = asset) else s }

    override fun setLive(live: Boolean) = _screen.update { s -> if (s is Screen.Start) s.copy(live = live) else s }

    override fun startTrip() {
        val s = _screen.value as? Screen.Start ?: return
        if (s.selected.isEmpty()) return
        val profiles = s.selected.sortedBy { it.ordinal }.map { PassengerProfile(id = it.name, type = it) }
        engine = DataYouEngine(profiles)
        nav.release()
        nav = if (s.live) PleosNavigation(getApplication()) else FakeNavigation()
        cardShownAtMs = null
        val src: VehicleDataSource = if (s.live) {
            PleosVehicleDataSource(getApplication(), viewModelScope)
        } else {
            ReplayDataSource(getApplication(), s.scenarioAsset, ReplayDataSource.SCENARIOS.getValue(s.scenarioAsset), viewModelScope, speedFactor = 5)
        }
        source = src
        _screen.value = Screen.Driving(sourceLabel = src.label, isReplay = !s.live, speedFactor = if (s.live) 1 else 5)
        src.start(onSample = ::onSample, onEnd = ::finishTrip)
    }

    override fun setSpeedFactor(f: Int) {
        (source as? ReplayDataSource)?.speedFactor = f
        _screen.update { s -> if (s is Screen.Driving) s.copy(speedFactor = f) else s }
    }

    /** 정차 카드의 동승자 피드백. 엔진 민감도에 반영. (M3: Gleo STT "속 안 좋아"와 연결) */
    override fun feedback(uncomfortable: Boolean) {
        val d = _screen.value as? Screen.Driving ?: return
        val who = d.stopCard?.passenger ?: d.focus?.profile ?: return
        engine?.feedback(who.id, uncomfortable)
        _screen.value = d.copy(feedbackNote = if (uncomfortable) "기록했어요 · 다음부터 더 민감하게 볼게요" else "기록했어요 · 민감도를 조금 낮췄어요")
    }

    override fun finishTrip() {
        val src = source
        src?.stop()
        nav.release()
        val e = engine ?: return
        val label = (screen.value as? Screen.Driving)?.sourceLabel ?: ""
        // 실시간 모드는 Pleos 주행거리(Odometer)를 쓰고, 없으면 엔진의 속도 적분값을 쓴다
        _screen.value = Screen.Report(e.finishTrip((src as? PleosVehicleDataSource)?.tripDistanceKm()), label)
        engine = null
        source = null
    }

    override fun backToStart() {
        source?.stop()
        nav.release()
        engine = null
        _screen.value = Screen.Start()
    }

    // ── 엔진 연결 ──
    private fun onSample(sample: team.datayou.engine.model.VehicleSample) {
        val e = engine ?: return
        val u = e.process(sample) ?: return
        val tbt = nav.update(sample)
        val pre = tbt != null && !u.vehicleStopped && e.shouldPreAlert(tbt.distanceM, tbt.isTurn)

        if (u.playAlertSound) beep(u.focusPassenger.level)

        _screen.update { s ->
            if (s !is Screen.Driving) return@update s
            var card = s.stopCard
            var note = s.feedbackNote
            u.stopCard?.let {
                card = it
                note = null
                cardShownAtMs = u.timestampMs
                speak(it.message)
            }
            // 차가 다시 움직이거나 12초가 지나면 카드를 닫는다
            val shownAt = cardShownAtMs
            if (card != null && (!u.vehicleStopped || (shownAt != null && u.timestampMs - shownAt > 12_000))) {
                card = null
                note = null
            }
            s.copy(
                focus = u.focusPassenger,
                badge = u.badge,
                speedKmh = Math.round(u.speedMps * 3.6).toInt(),
                stopped = u.vehicleStopped,
                tbt = tbt,
                preAlert = pre,
                stopCard = card,
                feedbackNote = note,
                elapsedSec = u.timestampMs / 1000.0,
                liveUnavailable = (source as? PleosVehicleDataSource)?.available == false,
                channels = (source as? PleosVehicleDataSource)?.let { p ->
                    p.channels.summary() + " 내비" + if ((nav as? PleosNavigation)?.received == true) "✓" else "–"
                },
            )
        }
    }

    private fun beep(level: GaugeLevel) {
        val t = if (level == GaugeLevel.DISCOMFORT) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_BEEP
        tone?.startTone(t, 300)
    }

    private fun speak(text: String) {
        if (!ttsReady) return
        tts.speak(text.replace("·", ","), TextToSpeech.QUEUE_FLUSH, null, "stopcard")
    }

    override fun onCleared() {
        source?.stop()
        nav.release()
        tts.shutdown()
        tone?.release()
    }
}
