package team.datayou.app

import team.datayou.engine.PassengerState
import team.datayou.engine.model.GaugeLevel

/**
 * 주행 중 단계가 오를 때 효과음 다음에 읽어 줄 짧은 음성.
 *  - 한 유형만 불편하면 그 유형을 짚어서: "임산부가 불편해해요"
 *  - 여러 유형이 불편하면 묶어서: "동승자들이 불편해해요"
 * 화면을 보지 않아도 되도록 음성으로만 알리고, 원인 설명은 정차 카드로 미룬다.
 */
object AlertSpeech {
    fun of(riders: List<PassengerState>): String? {
        val uneasy = riders.filter { it.level != GaugeLevel.COMFORT }.map { it.profile.type }.distinct()
        return when (uneasy.size) {
            0 -> null
            1 -> withSubject(uneasy[0].label) + " 불편해해요"
            else -> "동승자들이 불편해해요"
        }
    }

    /** 받침이 있으면 '이', 없으면 '가' */
    internal fun withSubject(word: String): String {
        val c = word.last()
        if (c !in '가'..'힣') return "$word(이)가"
        return if ((c - '가') % 28 != 0) "${word}이" else "${word}가"
    }
}
