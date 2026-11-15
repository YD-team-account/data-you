package team.datayou.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import team.datayou.engine.PassengerState
import team.datayou.engine.model.GaugeLevel
import team.datayou.engine.model.PassengerProfile
import team.datayou.engine.model.PassengerType

class AlertSpeechTest {
    private fun s(t: PassengerType, l: GaugeLevel) = PassengerState(PassengerProfile(t.name, t), 0.0, l)

    @Test
    fun `모두 편안하면 말하지 않는다`() {
        assertNull(AlertSpeech.of(listOf(s(PassengerType.CHILD, GaugeLevel.COMFORT), s(PassengerType.DOG, GaugeLevel.COMFORT))))
    }

    @Test
    fun `한 유형만 불편하면 그 유형을 짚어 말한다`() {
        assertEquals("임산부가 불편해해요", AlertSpeech.of(listOf(s(PassengerType.PREGNANT, GaugeLevel.CAUTION), s(PassengerType.DOG, GaugeLevel.COMFORT))))
        assertEquals("어르신이 불편해해요", AlertSpeech.of(listOf(s(PassengerType.SENIOR, GaugeLevel.DISCOMFORT))))
        assertEquals("반려견이 불편해해요", AlertSpeech.of(listOf(s(PassengerType.DOG, GaugeLevel.CAUTION))))
    }

    @Test
    fun `여러 유형이 불편하면 묶어서 말한다`() {
        assertEquals("동승자들이 불편해해요", AlertSpeech.of(listOf(s(PassengerType.CHILD, GaugeLevel.CAUTION), s(PassengerType.SENIOR, GaugeLevel.DISCOMFORT))))
    }
}
