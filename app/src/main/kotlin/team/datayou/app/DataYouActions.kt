package team.datayou.app

import team.datayou.engine.model.PassengerType

/** 화면에서 호출하는 동작. ViewModel이 구현하고, 화면 스크린샷 테스트에서는 빈 구현을 쓴다. */
interface DataYouActions {
    /** 동승자 팝업(주행 화면 왼쪽)에서 동승자 켜고 끄기. 바로 저장된다. */
    fun togglePassenger(type: PassengerType)
    /** 동승자 팝업 닫기 (확인·건너뛰기). 차가 움직이면 자동으로도 닫힌다. */
    fun closePassengerPanel() {}
    fun selectScenario(asset: String)
    fun setLive(live: Boolean)
    fun startTrip()
    fun setSpeedFactor(f: Int)
    fun feedback(uncomfortable: Boolean)
    fun finishTrip()
    fun backToStart()
}
