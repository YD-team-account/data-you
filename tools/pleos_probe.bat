@echo off
chcp 65001 >nul
rem DataYou - Pleos Connect Emulator probe (M3)
rem 1) installs the M3 APK  2) checks how vehicle data can be injected  3) runs the app in live mode
rem Output: dist\pleos_probe_log.txt, dist\pleos_props.txt
cd /d "%~dp0.."
if not exist dist mkdir dist
set LOG=%CD%\dist\pleos_probe_log.txt
set PROPS=%CD%\dist\pleos_props.txt
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
if not exist "%ADB%" set ADB=adb
set APK=%~1
if "%APK%"=="" set APK=dist\datayou-m3-debug.apk

echo ==== DataYou Pleos probe %DATE% %TIME% ==== > "%LOG%"
echo [device] >> "%LOG%"
"%ADB%" devices -l >> "%LOG%" 2>&1
"%ADB%" shell getprop ro.build.fingerprint >> "%LOG%" 2>&1
"%ADB%" shell getprop ro.build.type >> "%LOG%" 2>&1
echo. >> "%LOG%" & echo [root] >> "%LOG%"
"%ADB%" root >> "%LOG%" 2>&1
timeout /t 3 >nul
"%ADB%" wait-for-device
"%ADB%" shell id >> "%LOG%" 2>&1
echo. >> "%LOG%" & echo [pleos packages] >> "%LOG%"
"%ADB%" shell "pm list packages | grep -i -E 'pleos|navi|vehicle'" >> "%LOG%" 2>&1
echo. >> "%LOG%" & echo [vsomeip fifo] >> "%LOG%"
"%ADB%" shell "ls -l /data/vendor/vsomeip/" >> "%LOG%" 2>&1

echo. >> "%LOG%" & echo [install %APK%] >> "%LOG%"
"%ADB%" uninstall team.datayou.app >> "%LOG%" 2>&1
"%ADB%" install -r -g --user all "%APK%" >> "%LOG%" 2>&1

echo. >> "%LOG%" & echo [speed before] >> "%LOG%"
"%ADB%" shell cmd car_service get-property-value 0x11600207 >> "%LOG%" 2>&1
echo. >> "%LOG%" & echo [inject A: cmd car_service inject-vhal-event speed=10] >> "%LOG%"
"%ADB%" shell cmd car_service inject-vhal-event 0x11600207 0 10 >> "%LOG%" 2>&1
"%ADB%" shell cmd car_service get-property-value 0x11600207 >> "%LOG%" 2>&1
echo. >> "%LOG%" & echo [inject B: vhal_fifo speed=12] >> "%LOG%"
"%ADB%" shell "echo 'propId: 291504647 areaId: 0 values: 12' > /data/vendor/vsomeip/vhal_fifo" >> "%LOG%" 2>&1
timeout /t 1 >nul
"%ADB%" shell cmd car_service get-property-value 0x11600207 >> "%LOG%" 2>&1
echo. >> "%LOG%" & echo [inject C: dumpsys IVehicle --set speed=14] >> "%LOG%"
"%ADB%" shell dumpsys android.hardware.automotive.vehicle.IVehicle/default --set 0x11600207 -f 14 >> "%LOG%" 2>&1
"%ADB%" shell cmd car_service get-property-value 0x11600207 >> "%LOG%" 2>&1
echo. >> "%LOG%" & echo [steering / turn signal current] >> "%LOG%"
"%ADB%" shell cmd car_service get-property-value 0x11600209 >> "%LOG%" 2>&1
"%ADB%" shell cmd car_service get-property-value 0x11400408 >> "%LOG%" 2>&1

echo. >> "%LOG%" & echo [launch app] >> "%LOG%"
"%ADB%" logcat -c
"%ADB%" shell am start -W --user current -n team.datayou.app/.MainActivity >> "%LOG%" 2>&1

echo ==== property configs ==== > "%PROPS%"
"%ADB%" shell cmd car_service get-property-config >> "%PROPS%" 2>&1
echo ==== IVehicle list ==== >> "%PROPS%"
"%ADB%" shell dumpsys android.hardware.automotive.vehicle.IVehicle/default --list >> "%PROPS%" 2>&1

echo.
echo ===================================================================
echo  앱이 떴으면: 동승자 선택 - "실시간 차량 데이터" 선택 - 출발 을 누르세요.
echo  주행 화면이 나오면 이 창에서 아무 키나 누르세요. (20초 기록)
echo ===================================================================
pause
"%ADB%" shell cmd car_service inject-vhal-event 0x11600207 0 15 >nul 2>&1
"%ADB%" shell "echo 'propId: 291504647 areaId: 0 values: 15' > /data/vendor/vsomeip/vhal_fifo" >nul 2>&1
timeout /t 20 >nul
echo. >> "%LOG%" & echo [app log] >> "%LOG%"
"%ADB%" logcat -d -s DataYouPleos:* DataYouNavi:* DataYouCar:* AndroidRuntime:E >> "%LOG%" 2>&1

echo.
echo ---- 완료: dist\pleos_probe_log.txt, dist\pleos_props.txt ----
pause
