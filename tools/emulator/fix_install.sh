#!/usr/bin/env bash
# DataYou - AAOS emulator install fix (macOS / Linux)
# See docs/guides/troubleshooting-aaos-install.md
set -u
cd "$(dirname "$0")/../.."
ADB="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}/platform-tools/adb"
[ -x "$ADB" ] || ADB=adb
APK=app/build/outputs/apk/debug/app-debug.apk
[ -f "$APK" ] || APK=dist/datayou-m2-debug.apk
PKG=team.datayou.app

echo "APK=$APK"
echo "[users]";        "$ADB" shell pm list users
echo "[current user]"; "$ADB" shell am get-current-user
echo "[uninstall]";    "$ADB" uninstall "$PKG"
echo "[install]";      "$ADB" install -r -g --user all "$APK"
echo "[state]";        "$ADB" shell "dumpsys package $PKG | grep -E 'User [0-9]+:|versionName'"
echo "[launch]";       "$ADB" shell am start -W --user current -n "$PKG/.MainActivity"
