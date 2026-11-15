package team.datayou.app.data

import android.content.Context
import team.datayou.engine.model.PassengerType

/** 마지막으로 고른 동승자를 기억한다. 다음 주행의 동승자 팝업이 이 값으로 열린다. */
class PassengerStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("datayou_passengers", Context.MODE_PRIVATE)

    fun load(): Set<PassengerType> =
        prefs.getString(KEY, null).orEmpty().split(',')
            .mapNotNull { n -> PassengerType.entries.firstOrNull { it.name == n } }
            .toSet()

    fun save(selected: Set<PassengerType>) {
        prefs.edit().putString(KEY, selected.sortedBy { it.ordinal }.joinToString(",") { it.name }).apply()
    }

    private companion object { const val KEY = "selected" }
}
