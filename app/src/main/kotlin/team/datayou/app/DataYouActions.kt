package team.datayou.app

import team.datayou.engine.model.PassengerType

/** 화면에서 호출하는 동작. ViewModel이 구현하고, 화면 스크린샷 테스트에서는 빈 구현을 쓴다. */
interface DataYouActions {
    fun togglePassenger(type: PassengerType)
    fun selectScenario(asset: String)
    fun setLive(live: Boolean)
    fun startTrip()
    fun setSpeedFactor(f: Int)
    fun feedback(uncomfortable: Boolean)
    fun finishTrip()
    fun backToStart()
}
