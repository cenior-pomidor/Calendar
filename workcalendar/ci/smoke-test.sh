#!/usr/bin/env bash
# Installs the APK on the emulator, walks through the main screens and fails on crashes.
# Prints UI texts of every screen and small inline screenshots of key screens to the log.
set -u
APK=$(ls "$1"/*.apk | head -1)
PKG=io.github.ceniorpomidor.workcalendar
UI="python3 workcalendar/ci/ui.py"
export SMOKE_OUT=smoke
mkdir -p smoke

adb install -r "$APK" || { echo "install failed"; exit 1; }
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb logcat -c
adb shell am start -W -n $PKG/.MainActivity
sleep 8
$UI dump onboarding-1 1 || exit 1
$UI tap "Далее"
$UI dump onboarding-rate 0 || exit 1
$UI tap "Ставка в час"
$UI type 350
$UI hide-keyboard
$UI tap "Далее"
$UI dump onboarding-schedule 1 "График работы" || exit 1
$UI tap "Далее"
$UI dump onboarding-payouts 0 "Аванс и зарплата" || exit 1
$UI tap "Далее"
$UI dump onboarding-last 0 || exit 1
$UI tap "Готово"
sleep 5
# The wizard must be finished: the bottom navigation is visible.
$UI dump calendar 1 "Финансы" || exit 1

YESTERDAY=$(date -d yesterday +%F)
$UI route "shift/new?date=$YESTERDAY&extra=false"
$UI dump shift-new 1 || exit 1
$UI tap "Сохранить" 1
sleep 2
$UI route "unconfirmed"
$UI dump unconfirmed 1 || exit 1
$UI tap "Полностью" 1
sleep 2
$UI dump unconfirmed-after 0 || exit 1
$UI route "day/$YESTERDAY"
$UI dump calendar-day 1 || exit 1

$UI tap "Финансы" 1
sleep 2
$UI dump finance 1 || exit 1
$UI route "finance/stats"
$UI dump stats 1 || exit 1
$UI route "finance/history"
$UI dump history 0 || exit 1

$UI tap "Настройки" 1
$UI route "settings"
$UI dump settings 1 || exit 1
$UI route "settings/templates"
$UI dump templates 0 || exit 1
$UI route "settings/rates"
$UI dump rates 0 || exit 1
$UI route "settings/payouts"
$UI dump payouts 0 || exit 1
$UI route "absence/new?type=VACATION&start=$(date -d '+20 days' +%F)"
sleep 2
$UI dump absence 1 || exit 1
$UI route "settings/backup"
$UI dump backup 0 || exit 1
$UI route "settings/notifications"
$UI dump notifications 0 || exit 1
$UI route "search"
$UI dump search 0 || exit 1

echo "===== LOGCAT (app) ====="
adb logcat -d | grep -E "WorkCalendar|AndroidRuntime|FATAL" | tail -80
if adb logcat -d -b crash | grep -q "FATAL EXCEPTION"; then
  echo "App crashed"
  exit 1
fi
echo "SMOKE TEST PASSED"
