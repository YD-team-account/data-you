# 실행·시연 가이드

차 없이 ‘다 타유!’를 확인하는 방법을 쉬운 것부터 정리했습니다.

| 방법 | 필요한 것 | 걸리는 시간 | 확인되는 것 |
|---|---|---|---|
| [1. 브라우저로 보기](#1-브라우저로-보기) | 브라우저 | 1분 | 화면 흐름, 아키텍처, 게이지 시뮬레이터 |
| [2. 엔진만 돌려 보기](#2-엔진만-돌려-보기) | JDK 17 | 3분 | 시나리오별 원인 판별, 테스트 |
| [3. 에뮬레이터에서 앱 실행](#3-에뮬레이터에서-앱-실행) | Android Studio | 15분 | 실제 앱, 시나리오 지도 |
| [4. 실시간 모드 시연](#4-실시간-모드-시연) | 3번 + adb | 5분 | Pleos SDK·AAOS 연동, 실시간 지도 |

---

## 1. 브라우저로 보기
- [`docs/overview/index.html`](../overview/index.html) — 아키텍처·기능 개요 (주제별 10페이지, 게이지 시뮬레이터·경로 뷰어 포함)
- [`docs/prototype/index.html`](../prototype/index.html) — 엔진이 계산한 주행을 차량 화면으로 재생하는 프로토타입

저장소를 내려받아 파일을 브라우저로 열거나, GitHub Pages가 켜져 있으면 웹 주소로 바로 열 수 있습니다.

## 2. 엔진만 돌려 보기
필요한 것: JDK 17 이상(Android Studio에 포함된 JBR이면 됩니다). Windows에서는 `./gradlew` 대신 `gradlew.bat`을 씁니다.

```bash
./gradlew :core-engine:test                                        # 엔진 테스트 24개
./gradlew :core-engine:run --args="summary CHILD"                   # 9개 시나리오 결과 표
./gradlew :core-engine:run --args="scenario mountain_curves CHILD"  # 산길을 아이가 탄 상태로 재생
./gradlew :core-engine:run --args="scenario city_aggressive PREGNANT,DOG"
./gradlew :core-engine:run --args="csv sample-data/ev_strong_regen.csv CHILD"
```

동승자: `CHILD` 아이, `PREGNANT` 임산부, `SENIOR` 어르신, `DOG` 반려견, `SENSITIVE_ADULT` 멀미 민감, `ADULT` 일반.

<details>
<summary>출력 예시 — 산길 연속 커브, 아이 동승</summary>

```
── 다 타유! 주행 리포트 ──
[아이] 최고 게이지 93
  - 좌우 흔들림(연속 커브): 100%
다음 주행 팁: 커브 앞에서 미리 속도를 줄여보세요
```
</details>

실주행 데이터도 같은 CSV 형식으로 바꾸면 그대로 재생할 수 있습니다.
```
timestamp_ms,speed_mps,yaw_rate_rad_s,heading_deg,brake,turn_signal
0,0.0123,0.00041,90.00,0,OFF
```
빈 칸은 해당 센서가 없다는 뜻(`null`)입니다.

## 3. 에뮬레이터에서 앱 실행
1. Android Studio에서 저장소 폴더를 엽니다. Pleos SDK는 공개 Maven 저장소에서 자동으로 받습니다(인증 불필요).
2. AVD를 만듭니다. 둘 중 하나면 됩니다.
   - **Pleos Connect Emulator** (권장): SDK Manager ▸ SDK Update Sites에 Pleos 시스템 이미지 주소를 추가하고, Device Manager ▸ Automotive ▸ Pleos Connect로 만듭니다. Boot는 **Cold boot**, RAM은 4GB 이상을 권장합니다. 절차는 Pleos Playground 문서 "Setting up Pleos Connect Emulator"를 따릅니다.
   - Android Automotive 시스템 이미지(API 34·35)
3. `app` ▾ ▸ Edit Configurations ▸ **Install for all users**를 체크하고 실행합니다.
4. 처음 실행하면 권한 창이 뜹니다. 차량 속도·핸들·위치 권한을 모두 **허용**합니다.
5. 동승자를 고르고 시나리오(예: 가족 나들이)를 선택해 출발합니다. 오른쪽 위 데모 막대에서 1·5·15배속을 바꿀 수 있습니다.

> ⚠️ `Activity class {team.datayou.app/...MainActivity} does not exist`가 뜨면 Automotive 멀티유저 설치 문제입니다. 앱 코드 문제가 아닙니다. → [설치 문제 해결](troubleshooting-aaos-install.md) 또는 `tools\emulator\fix_install.bat`

빌드만 할 때: `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`

## 4. 실시간 모드 시연
시작 화면에서 **실시간 차량 데이터**를 고르면 에뮬레이터의 차량 신호를 받습니다. 오른쪽 위에 채널별 수신 상태(`속도✓ 브레이크– 깜빡이✓ 조향✓ 주행거리– 내비– GPS✓`, 미지원은 `✗`)가 보입니다.

| 방법 | 하는 법 | 보이는 것 |
|---|---|---|
| 신호 주입 (권장) | `tools\emulator\sway_demo.bat` 더블클릭 → 안내대로 동승자 '아이'·실시간·출발 → 아무 키 | 약 2분간 속도·핸들·깜빡이 주입. 연속 커브 → 급차선변경 → 정차 카드 |
| 실제 도로 주행 | 에뮬레이터 `⋯ ▸ Location ▸ Routes`에서 경로를 만들어 재생 | 실제 지도 위를 GPS로 이동. 핸들 신호가 없으면 GPS 방위로 좌우 판단 |
| 수동 | 에뮬레이터 `⋯ ▸ Car data`에서 속도 변경 | 앞뒤 원인(급출발·급제동) |

- `sway_demo`는 지도 시작 위치를 서울시청 앞으로 맞추고, GPS가 멈춰 있으면 차량 신호로 위치를 추정 이동합니다. 이때 지도 위 경로는 실제 도로를 따라가지 않습니다.
- 실시간 지도 타일을 받으려면 에뮬레이터가 인터넷에 연결돼 있어야 합니다.
- Pleos 서비스가 있는지, 어떤 주입 방식이 되는지는 `tools\emulator\pleos_probe.bat`로 확인할 수 있습니다(결과: `dist\pleos_probe_log.txt`).
