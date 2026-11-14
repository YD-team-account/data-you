# 문서 색인

| 문서 | 내용 | 누구에게 |
|---|---|---|
| [아키텍처·기능 개요](overview/index.html) | 주제별 10페이지 인터랙티브 문서: 개요, 아키텍처, 데이터 흐름, 게이지 시뮬레이터, 원인 7종, 화면 규칙, 데이터 채널과 SDK, 지도, 검증, 로드맵 | 처음 보는 사람, 심사위원 |
| [소프트웨어 설계 문서 (SDD)](design/SDD.md) | 요구사항, 구조, 데이터 모델, 알고리즘, 대체 경로, 권한, 설정값, 테스트 추적성 | 개발자, 기술 심사 |
| [실행·시연 가이드](guides/run-and-demo.md) | 브라우저 → 엔진 → 에뮬레이터 → 실시간 시연 순서 | 직접 돌려 볼 사람 |
| [설치 문제 해결](guides/troubleshooting-aaos-install.md) | Automotive 에뮬레이터 멀티유저 설치 오류 원인과 대처 | 에뮬레이터 사용자 |
| [인터랙티브 프로토타입](prototype/index.html) | 엔진이 계산한 '가족 나들이' 주행을 차량 화면으로 재생 | 화면 흐름 확인 |
| [스크린샷](screenshots/) | 앱 화면 5장, 시나리오 지도 6장 (Robolectric 렌더링) | 첨부·미리보기 |

`overview`와 `prototype`은 HTML 파일입니다. 저장소를 내려받아 브라우저로 열거나 GitHub Pages로 보면 됩니다.

## 다시 만들기
| 결과물 | 명령 |
|---|---|
| 스크린샷 | `./gradlew :app:testDebugUnitTest` → `app/screenshots/` |
| 시나리오 CSV | `./gradlew :core-engine:run --args="export sample-data"` |
| 프로토타입 | `./gradlew :core-engine:run --args="timeline docs/prototype/timeline.json"` 후 `python3 tools/prototype/build_prototype.py` |
| 좌우 흔들림 주입 스크립트 | `python3 tools/emulator/gen_sway_demo.py` |
