@echo off
chcp 65001 >nul
rem DataYou - AAOS emulator install fix
rem See docs/guides/troubleshooting-aaos-install.md
cd /d "%~dp0..\.."
if not exist dist mkdir dist
set LOG=%CD%\dist\fix_install_log.txt
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
if not exist "%ADB%" set ADB=adb
set APK=%~1
if "%APK%"=="" set APK=app\build\outputs\apk\debug\app-debug.apk
if not exist "%APK%" set APK=dist\datayou-m3-debug.apk
if not exist "%APK%" set APK=dist\datayou-m2-debug.apk

echo ==== DataYou install fix %DATE% %TIME% ==== > "%LOG%"
echo APK=%APK% >> "%LOG%"
echo. >> "%LOG%" & echo [devices] >> "%LOG%"
"%ADB%" devices -l >> "%LOG%" 2>&1
echo. >> "%LOG%" & echo [users] >> "%LOG%"
"%ADB%" shell pm list users >> "%LOG%" 2>&1
echo. >> "%LOG%" & echo [current user] >> "%LOG%"
"%ADB%" shell am get-current-user >> "%LOG%" 2>&1
echo. >> "%LOG%" & echo [before: install state per user] >> "%LOG%"
"%ADB%" shell "dumpsys package team.datayou.app | grep -E 'User [0-9]+:|versionName'" >> "%LOG%" 2>&1
echo. >> "%LOG%" & echo [uninstall all users] >> "%LOG%"
"%ADB%" uninstall team.datayou.app >> "%LOG%" 2>&1
echo. >> "%LOG%" & echo [install --user all] >> "%LOG%"
"%ADB%" install -r -g --user all "%APK%" >> "%LOG%" 2>&1
echo. >> "%LOG%" & echo [after: install state per user] >> "%LOG%"
"%ADB%" shell "dumpsys package team.datayou.app | grep -E 'User [0-9]+:|versionName'" >> "%LOG%" 2>&1
echo. >> "%LOG%" & echo [resolve activity] >> "%LOG%"
"%ADB%" shell cmd package resolve-activity --user current --brief -c android.intent.category.LAUNCHER team.datayou.app >> "%LOG%" 2>&1
echo. >> "%LOG%" & echo [launch] >> "%LOG%"
"%ADB%" shell am start -W --user current -n team.datayou.app/.MainActivity >> "%LOG%" 2>&1
timeout /t 4 >nul
echo. >> "%LOG%" & echo [crash log] >> "%LOG%"
"%ADB%" logcat -d -t 300 AndroidRuntime:E ActivityTaskManager:I PackageManager:W *:S >> "%LOG%" 2>&1

type "%LOG%"
echo.
echo ---- Done: saved to dist\fix_install_log.txt ----
pause
