# 도구

## `emulator/` — 에뮬레이터 설치·진단·시연
Windows는 `.bat`을 더블클릭하면 됩니다. 모든 스크립트는 저장소 루트 기준으로 동작하고, 결과 로그는 `dist/`에 남깁니다(Git 제외).

| 파일 | 하는 일 |
|---|---|
| `fix_install.bat` / `fix_install.sh` | Automotive 멀티유저 설치 문제 해결: 모든 사용자에서 지우고 다시 설치한 뒤 운전자 사용자로 실행 |
| `pleos_probe.bat` | 최신 APK 설치, 차량 신호 주입 방식 3가지 시험, Pleos 패키지 확인, 앱 로그 수집 |
| `sway_demo.bat` | 속도·핸들·깜빡이를 약 2분 주입해 연속 커브와 급차선변경 재현 (동승자 '아이' 권장) |
| `sway_demo.sh` | 위가 에뮬레이터 안으로 보내 실행하는 주입 스크립트 (`dumpsys IVehicle --set`) |
| `gen_sway_demo.py` | 주입 프로필 생성기. `sway_demo.sh`와 엔진 검증용 `sample-data/sway_demo.csv`를 함께 만듦 |

사용하는 APK: `fix_install`은 직접 빌드한 `app/build/outputs/apk/debug/app-debug.apk`를 먼저 쓰고, `pleos_probe`·`sway_demo`는 `dist/datayou-m3-debug.apk`를 씁니다. 저장소에는 APK가 없으니 GitHub Releases에서 받아 `dist/`에 넣거나 직접 빌드하세요.

## `prototype/`
| 파일 | 하는 일 |
|---|---|
| `build_prototype.py` | `docs/prototype/template.html` + `timeline.json` → 파일 하나짜리 `index.html` |
