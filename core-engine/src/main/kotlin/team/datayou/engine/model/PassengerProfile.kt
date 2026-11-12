package team.datayou.engine.model

/**
 * 동승자 프로필.
 *
 * - doseSensitivity : 저주파 흔들림 누적량을 게이지로 바꿀 때의 배율 (클수록 빨리 참)
 * - eventThresholdScale : 순간 이벤트 판별 임계값 배율 (작을수록 약한 급제동도 이벤트로 봄)
 *
 * 프리셋 값은 "출발값"이다. 동승자 피드백(feedback)으로 개인별로 보정된다.
 * 어르신은 멀미보다 승차감(몸 쏠림) 중심이므로 누적 민감도는 낮고 순간 이벤트 민감도는 높다.
 */
data class PassengerProfile(
    val id: String,
    val type: PassengerType,
    val displayName: String = type.label,
    val doseSensitivity: Double = type.defaultDoseSensitivity,
    val eventThresholdScale: Double = type.defaultEventThresholdScale,
) {
    /** "속 안 좋아" → 민감도 상승, "괜찮아" → 민감도 소폭 하락. 범위는 프리셋의 0.5~2배로 제한. */
    fun withFeedback(uncomfortable: Boolean): PassengerProfile {
        val factor = if (uncomfortable) 1.10 else 0.95
        val base = type.defaultDoseSensitivity
        val next = (doseSensitivity * factor).coerceIn(base * 0.5, base * 2.0)
        return copy(doseSensitivity = next)
    }
}

enum class PassengerType(
    val label: String,
    val defaultDoseSensitivity: Double,
    val defaultEventThresholdScale: Double,
) {
    CHILD("아이", 1.4, 0.9),
    PREGNANT("임산부", 1.5, 0.75),
    SENIOR("어르신", 0.8, 0.8),
    DOG("반려견", 1.3, 0.9),
    SENSITIVE_ADULT("멀미 민감 동승자", 1.3, 1.0),
    ADULT("동승자", 1.0, 1.0),
}
