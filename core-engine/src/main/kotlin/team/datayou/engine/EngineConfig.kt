package team.datayou.engine

/**
 * 엔진 파라미터. 모든 값은 초기값이며 실주행 데이터로 보정할 대상이다.
 * 가속도 단위 m/s² (참고: 1g ≈ 9.81 m/s²).
 */
data class EngineConfig(
    // ── 신호 처리 ─────────────────────────────
    /** 속도 저역통과 시상수(초). 속도 센서 노이즈를 미분하기 전에 줄인다. */
    val speedSmoothingTauSec: Double = 0.25,
    /** 가속도 저역통과 시상수(초). jerk 계산 전 평활화. */
    val accelSmoothingTauSec: Double = 0.3,

    // ── 멀미 게이지 (ISO 2631-1을 출발점으로 한 단순화 모델) ──
    /** 멀미 유발 대역 하한/상한 (Hz). 문헌상 약 0.2Hz 부근이 가장 유발적. */
    val bandLowHz: Double = 0.1,
    val bandHighHz: Double = 0.5,
    /** 대역 통과 가속도 제곱(파워)을 게이지 점수/초로 바꾸는 계수. */
    val doseGain: Double = 0.18,
    /** 이 파워 미만이면 "조용한 주행"으로 보고 게이지를 회복시킨다. */
    val quietPowerThreshold: Double = 0.05,
    /** 조용한 주행 시 초당 게이지 회복량. 100 → 0까지 약 10분. */
    val recoveryPerSec: Double = 0.17,
    /** 단계 경계 (0~100). 내려올 때는 hysteresis만큼 더 내려와야 단계가 바뀐다. */
    val cautionAt: Double = 40.0,
    val discomfortAt: Double = 70.0,
    val levelHysteresis: Double = 8.0,

    // ── 순간 이벤트 (프로필 eventThresholdScale이 곱해짐) ──
    val hardBrakeDecel: Double = 3.0,
    val hardAccel: Double = 2.5,
    val sharpLateral: Double = 3.0,
    /** 임계값을 이 시간 이상 넘어야 이벤트로 인정 (노이즈 제거). */
    val eventMinDurationSec: Double = 0.3,
    /** 같은 종류 이벤트 사이 최소 간격. */
    val eventCooldownSec: Double = 3.0,
    /** 깜빡이와 좌우 가속 사이 허용 시간차 (차선변경 판별). */
    val turnSignalWindowSec: Double = 4.0,
    /** 이벤트 1건이 게이지에 더하는 점수 (프로필 doseSensitivity가 곱해짐). */
    val momentEventBonus: Double = 6.0,
    val patternEventBonus: Double = 4.0,

    // ── 반복 패턴 ─────────────────────────────
    /** 브레이크 끊어 밟기: 창(초) 안에서 브레이크 ON 전환 횟수. */
    val pumpingWindowSec: Double = 60.0,
    val pumpingMinPresses: Int = 6,
    /** 끊어 밟기 판정 시 창 안 최저 속도가 이 이상이어야 함 (정체와 구분). 15km/h. */
    val pumpingMinSpeedMps: Double = 15.0 / 3.6,
    /** 정체: 창(초) 안에서 정지(1m/s 미만) 진입 횟수. */
    val stopAndGoWindowSec: Double = 120.0,
    val stopAndGoMinStops: Int = 3,
    val stopSpeedMps: Double = 1.0,
    /** 정체 판정: 창 안 최고속도가 이 이하일 때만 (30km/h). 신호 대기 많은 시내 주행과 구분. */
    val stopAndGoMaxSpeedMps: Double = 30.0 / 3.6,
    val movingSpeedMps: Double = 3.0,
    /** 연속 커브: 창(초) 안 좌우 가속 부호 전환 횟수와 최소 크기. */
    val curvesWindowSec: Double = 30.0,
    val curvesMinSignChanges: Int = 4,
    val curvesMinLatAccel: Double = 1.0,
    /** 반복 패턴 판정 후 재판정까지 대기. */
    val patternCooldownSec: Double = 60.0,

    // ── 원인 배지 (주행 중 UI) ─────────────────
    /** 원인별 기여도 감쇠 시상수(초). 최근 1분 정도의 원인을 반영. */
    val attributionTauSec: Double = 60.0,
    /** 배지를 한 번 띄우면 최소 유지 시간. 깜빡임 방지. */
    val badgeMinHoldSec: Double = 30.0,
    /** 새 원인이 현재 배지보다 이만큼 커야 교체. */
    val badgeSwitchRatio: Double = 1.2,

    // ── 정차 판정 ─────────────────────────────
    val stoppedSpeedMps: Double = 0.5,
    val stoppedMinSec: Double = 2.0,
    /** 정차 카드 최소 간격. 신호마다 같은 말을 반복하지 않도록. */
    val stopCardCooldownSec: Double = 90.0,

    // ── 예측 안내 (NaviHelper TBT) ─────────────
    val preAlertDistanceM: Double = 300.0,

    // ── 원안 모드: N분 내 M회 ─────────────────
    val legacyWindowMin: Double = 10.0,
    val legacyMinEvents: Int = 3,
)
