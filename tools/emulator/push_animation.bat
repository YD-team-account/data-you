@echo off
chcp 65001 >nul
rem DataYou - 캐릭터 반응 동작(JSON)을 재빌드 없이 에뮬레이터에 넣는다
rem 사용: push_animation.bat [json 경로]   (생략하면 app\src\main\assets\animations\reactions.json)
rem 형식: docs/design/character-animation.md
cd /d "%~dp0..\.."
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
if not exist "%ADB%" set ADB=adb
set JSON=%~1
if "%JSON%"=="" set JSON=app\src\main\assets\animations\reactions.json

"%ADB%" root >nul 2>&1
"%ADB%" wait-for-device
rem Automotive는 운전자 사용자(보통 10번)로 앱이 돈다 → 그 사용자의 앱 파일 폴더에 넣어야 한다
for /f %%u in ('"%ADB%" shell am get-current-user') do set U=%%u
set DEST=/storage/emulated/%U%/Android/data/team.datayou.app/files/animations
"%ADB%" shell mkdir -p %DEST%
"%ADB%" push "%JSON%" %DEST%/reactions.json
"%ADB%" shell am force-stop --user %U% team.datayou.app
"%ADB%" shell am start --user %U% -n team.datayou.app/.MainActivity >nul
echo.
echo 적용 완료: 앱을 다시 시작했습니다. 로그 확인: adb logcat -s DataYouAnim
echo 되돌리기: adb shell rm %DEST%/reactions.json
pause
