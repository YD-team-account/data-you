package team.datayou.engine.signal

import kotlin.math.PI
import kotlin.math.exp

/**
 * 1차 저역통과 필터. 샘플 간격(dt)이 들쭉날쭉해도 동작하도록
 * 매 스텝 alpha = 1 - exp(-dt/tau) 로 계산한다.
 */
class LowPass(private val tauSec: Double) {
    private var y: Double? = null

    fun update(x: Double, dtSec: Double): Double {
        val prev = y
        val out = if (prev == null || dtSec <= 0.0) x else {
            val alpha = 1.0 - exp(-dtSec / tauSec)
            prev + alpha * (x - prev)
        }
        y = out
        return out
    }

    fun value(): Double = y ?: 0.0

    companion object {
        fun ofCutoffHz(hz: Double) = LowPass(1.0 / (2.0 * PI * hz))
    }
}

/**
 * 간이 대역통과 필터 = LP(상한) − LP(하한).
 * 멀미 유발 대역(기본 0.1~0.5Hz)만 남긴다.
 * 엄밀한 ISO 2631-1 Wf 가중치는 아니지만, 같은 대역을 강조하는 근사다.
 */
class BandPass(lowHz: Double, highHz: Double) {
    private val lpHigh = LowPass.ofCutoffHz(highHz)
    private val lpLow = LowPass.ofCutoffHz(lowHz)

    fun update(x: Double, dtSec: Double): Double {
        val a = lpHigh.update(x, dtSec)
        val b = lpLow.update(x, dtSec)
        return a - b
    }
}
