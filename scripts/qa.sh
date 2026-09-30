#!/usr/bin/env bash
# Runs the on-device QA suite on the emulator and collects everything into qa-out/.
set -x
OUT=qa-out
mkdir -p "$OUT"

adb wait-for-device
adb shell getprop ro.build.version.release > "$OUT/device.txt"
adb shell getprop ro.build.version.sdk >> "$OUT/device.txt"
adb shell wm size >> "$OUT/device.txt"
adb shell wm density >> "$OUT/device.txt"
adb shell dumpsys package com.google.android.webview | grep -m1 versionName >> "$OUT/device.txt" || true

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

adb shell svc power stayon true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard
adb shell settings put system screen_off_timeout 1800000
adb logcat -c

# The whole suite, in a fixed order.
CLASSES="app.offgrow.qa.A1_OnboardingTest,app.offgrow.qa.A2_HomeStatesTest,app.offgrow.qa.A3_WeeksSimulationTest,app.offgrow.qa.A4_FocusTest,app.offgrow.qa.A5_ScreensTest,app.offgrow.qa.A6_WidgetTest,app.offgrow.qa.A7_BackgroundTest,app.offgrow.qa.A8_RealUsageTest,app.offgrow.qa.A9_DeviceConditionsTest"
for c in ${CLASSES//,/ }; do
  echo "=== $c" >> "$OUT/instrument.txt"
  timeout 1500 adb shell am instrument -w -e class "$c" app.offgrow.test/androidx.test.runner.AndroidJUnitRunner >> "$OUT/instrument.txt" 2>&1
done

# Pull the report and screenshots the tests wrote inside the app.
adb exec-out run-as app.offgrow tar -cf - files/qa > "$OUT/qa.tar" 2>/dev/null
(cd "$OUT" && tar -xf qa.tar && rm -f qa.tar && mv files/qa/* . && rm -rf files) || echo "pull failed" >> "$OUT/instrument.txt"

# Random tapping through the real app (real clock, real usage data).
adb shell monkey -p app.offgrow --throttle 80 --pct-syskeys 0 --pct-appswitch 5 -s 20261007 -v 3000 > "$OUT/monkey.txt" 2>&1
adb shell screencap -p /sdcard/after_monkey.png && adb pull /sdcard/after_monkey.png "$OUT/after_monkey.png"

adb logcat -d > "$OUT/logcat.txt"
adb logcat -d -b crash > "$OUT/crash.txt"
grep -n -E "FATAL EXCEPTION|AndroidRuntime" -A 25 "$OUT/logcat.txt" > "$OUT/fatal.txt" || true
grep -E "OffgrowQA|GardenRenderer|GardenEngine|WidgetUpdater|RefreshWorker|AppViewModel|UsageReader" "$OUT/logcat.txt" > "$OUT/app-log.txt" || true
ls -la "$OUT"
exit 0
