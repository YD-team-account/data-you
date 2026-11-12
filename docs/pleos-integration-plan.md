# Pleos Connect 연동 설계 (M2 · M3)

## 1. 데이터 소스 추상화

앱은 엔진에 `VehicleSample`만 넣습니다. 데이터를 어디서 가져오는지는 `VehicleDataSource` 구현체가 정합니다.

```kotlin
interface VehicleDataSource {
    fun start(onSample: (VehicleSample) -> Unit)
    fun stop()
}
```

| 구현체 | 단계 | 설명 |
|---|---|---|
| `ReplayDataSource` | M2 | `sample-data/*.csv`를 10Hz로 재생. 일반 AAOS 에뮬레이터·시연용 |
| `PleosVehicleDataSource` | M3 | AAOS `CarPropertyManager` + Pleos Vehicle SDK 실시간 구독 |

## 2. 필드 매핑 (M3)

| `VehicleSample` 필드 | 출처 | 없을 때 |
|---|---|---|
| `speedMps` | AAOS `PERF_VEHICLE_SPEED` (m/s) | 필수 |
| `brakePedalOn` | Pleos Vehicle SDK `Brake` (브레이크 페달 상태) | `null` → 급감속은 급제동으로 판정 |
| `turnSignal` | Pleos Vehicle SDK `TurnSignal` | `OFF` → 좌우 이벤트는 모두 '급회전'으로 표기 |
| `yawRateRadPs` | `SensorManager` 자이로 (헤드유닛에 있을 때) | GPS 방위각으로 대체 |
| `headingDeg` | `LocationManager` GPS bearing | 좌우 추정 불가 → 0 |
| (리포트) 주행거리 | Pleos Vehicle SDK `Odometer` | 속도 적분값 사용 |

- 모든 Vehicle SDK 호출 전에 해당 인터페이스의 `checkXXXCapability`로 지원 여부를 확인합니다.
- Pleos 정책에 따라 VHAL Property ID를 차종별로 분기하지 않습니다.

## 3. 화면 상태

| 상태 | 판단 | 표시 |
|---|---|---|
| 주행 중 | `CarUxRestrictionsManager`가 제약 활성 | 동승자 아이콘 + 원인 배지 1개, 텍스트 없음 |
| 정차 | 엔진 `vehicleStopped`(속도 0.5m/s 미만 2초) | 캐릭터 애니메이션 + `StopCard.message` + Gleo TTS |
| 하차 | `IGNITION_STATE` OFF 또는 기어 P 유지 | `TripReport` 화면 |

## 4. 예측 안내

1. NaviHelper `addListener`로 TBT 갱신을 받습니다.
2. `getTBTInfo`에서 다음 안내 지점까지의 거리와 회전 종류를 꺼냅니다.
3. 엔진의 `shouldPreAlert(distance, isTurn)`가 true면 아이콘을 강조하고 효과음을 1회 냅니다.

## 5. 에뮬레이터 시연 방법 (M3)

1. Pleos Playground에서 My Project를 만들고 CRN을 발급받습니다. 이 CRN을 에뮬레이터에 주입합니다.
2. 앱을 설치하고, 동승자 프로필에서 '아이'를 선택합니다.
3. `tools/inject_vhal.py`로 `sample-data/mountain_curves.csv`의 속도를 VHAL에 주입합니다. 브레이크·깜빡이는 에뮬레이터 Vehicle 설정으로 조작합니다.
4. 아이콘이 '주의' → '힘듦'으로 바뀌고, 정차 시 코칭 카드가 뜨는 장면을 녹화합니다.

> 3번의 주입 방식(adb 명령, 에뮬레이터 extended controls)은 Pleos Connect Emulator에서 직접 확인한 뒤 확정합니다.
