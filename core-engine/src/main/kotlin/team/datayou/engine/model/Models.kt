package team.datayou.engine.model

/**
 * 차량에서 들어오는 원시 샘플 1개.
 *
 * 데이터 출처(앱 모듈에서 채움):
 * - speedMps       : AAOS CarPropertyManager PERF_VEHICLE_SPEED (m/s)
 * - yawRateRadPs   : 헤드유닛 자이로 z축 (지원 차량만, 없으면 null)
 * - headingDeg     : GPS 방위각 (자이로가 없을 때 좌우 가속 추정용)
 * - brakePedalOn   : Pleos Vehicle SDK Brake API (미지원 시 null)
 * - turnSignal     : Pleos Vehicle SDK TurnSignal API
 */
data class VehicleSample(
    val timestampMs: Long,
    val speedMps: Double,
    val yawRateRadPs: Double? = null,
    val headingDeg: Double? = null,
    val brakePedalOn: Boolean? = null,
    val turnSignal: TurnSignal = TurnSignal.OFF,
)

enum class TurnSignal { OFF, LEFT, RIGHT, HAZARD;
    val isOn: Boolean get() = this != OFF
}

/** 신호 처리 후 1프레임. 가속도 단위는 m/s², jerk는 m/s³. */
data class MotionFrame(
    val timestampMs: Long,
    val dtSec: Double,
    val speedMps: Double,
    val longAccel: Double,
    val latAccel: Double,
    val jerk: Double,
    val brakePedalOn: Boolean?,
    val turnSignal: TurnSignal,
)

/**
 * 멀미 게이지를 올리는 원인 7종.
 * actionable=false 인 원인(정체)은 운전자 탓으로 표시하지 않는다.
 */
enum class Cause(
    val label: String,
    val coaching: String,
    val kind: CauseKind,
    val actionable: Boolean = true,
) {
    HARD_BRAKE("브레이크 급제동", "브레이크를 조금 일찍, 약하게 밟아보세요", CauseKind.MOMENT),
    REGEN_DECEL("회생·관성 급감속", "회생제동 단계를 한 단계 낮춰보세요", CauseKind.MOMENT),
    HARD_ACCEL("급출발", "출발은 천천히 해주세요", CauseKind.MOMENT),
    SHARP_LATERAL("급차선변경·급회전", "차선 변경과 회전은 여유 있게 해주세요", CauseKind.MOMENT),
    BRAKE_PUMPING("브레이크 끊어 밟기", "한 번에 부드럽게 밟아보세요", CauseKind.PATTERN),
    CONTINUOUS_CURVES("좌우 흔들림(연속 커브)", "커브 앞에서 미리 속도를 줄여보세요", CauseKind.PATTERN),
    STOP_AND_GO("정체 가다 서다", "정체 구간이에요. 차간거리를 넉넉히 두면 덜 출렁여요", CauseKind.SITUATION, actionable = false),
}

enum class CauseKind { MOMENT, PATTERN, SITUATION }

/** 게이지 단계. 주행 중 아이콘의 테두리 색·표정으로 표시된다. */
enum class GaugeLevel { COMFORT, CAUTION, DISCOMFORT }

/** 원인 이벤트 1건. 리포트 지도·통계에 쓰인다. */
data class CauseEvent(
    val cause: Cause,
    val timestampMs: Long,
    val peakValue: Double,
    val detail: String = "",
)
