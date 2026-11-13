@echo off
chcp 65001 >nul
rem DataYou - 좌우 흔들림 시연 (연속 커브 + 급차선변경)
rem 에뮬레이터 차량 신호(속도·핸들 각도·방향지시등)를 약 2분간 주입합니다.
rem 신호 프로필: tools/gen_sway_demo.py  /  주입 스크립트: tools/sway_demo.sh
cd /d "%~dp0.."
if not exist dist mkdir dist
set LOG=%CD%\dist\sway_demo_log.txt
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
if not exist "%ADB%" set ADB=adb

echo ===================================================================
echo  1. 다타유 앱에서 동승자 "아이" 하나만 선택
echo  2. "실시간 차량 데이터" 선택 후 "출발"
echo  3. 주행 화면이 보이면 이 창에서 아무 키나 누르세요 (약 2분 진행)
echo ===================================================================
pause

"%ADB%" root >nul 2>&1
"%ADB%" wait-for-device
"%ADB%" push tools\sway_demo.sh /data/local/tmp/sway_demo.sh >nul
"%ADB%" logcat -c
echo 주입 시작: 0~12초 출발, 17~62초 연속 커브, 72~77초 급차선변경, 85초~ 정차
"%ADB%" shell sh /data/local/tmp/sway_demo.sh

echo ==== DataYou sway demo %DATE% %TIME% ==== > "%LOG%"
"%ADB%" shell getprop ro.build.fingerprint >> "%LOG%" 2>&1
"%ADB%" logcat -d -s DataYouPleos:* DataYouNavi:* DataYouPerm:* AndroidRuntime:E >> "%LOG%" 2>&1
echo.
echo ---- 완료: 정차 카드와 리포트를 확인하세요. 로그: dist\sway_demo_log.txt ----
pause
