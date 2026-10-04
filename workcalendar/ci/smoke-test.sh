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

# ---- Notifications: shift end -> quick actions (needs adb root to move the clock) ----
next_weekday() {
  python3 -c "import datetime,sys
d = datetime.date.fromisoformat(sys.argv[1]) + datetime.timedelta(days=1)
while d.weekday() >= 5:
    d += datetime.timedelta(days=1)
print(d)" "$1"
}
echo "===== ALARMS ====="
adb shell dumpsys alarm | grep -A2 "Alarm{.*$PKG" | grep -E "Alarm\{|origWhen=" | head -20
adb root > /dev/null 2>&1 || true
sleep 3
adb wait-for-device
TODAY=$(adb shell date +%F | tr -d '\r')
D1=$(next_weekday "$TODAY")
D2=$(next_weekday "$D1")
adb shell input keyevent 3
$UI set-time "${D1}T18:05:00"
if [ "$(adb shell date +%F | tr -d '\r')" = "$D1" ]; then
  # The shift of D1 (09:00-18:00) has just ended: the notification must appear by itself.
  $UI notifications "Смена завершена" || exit 1
  adb shell cmd statusbar expand-notifications
  sleep 2
  $UI dump shade-shift-end 1 || exit 1
  $UI notification-action "Полностью" "Смена завершена" || exit 1
  $UI notifications "Часы сохранены" 10 || exit 1
  adb shell cmd statusbar collapse
  # Next working day: enter the hours right in the notification.
  $UI set-time "${D2}T18:05:00"
  $UI notifications "Смена завершена" || exit 1
  adb shell cmd statusbar expand-notifications
  sleep 2
  if $UI notification-action "Ввести часы" "Смена завершена"; then
    $UI type "7,5"
    $UI dump shade-reply 1 || exit 1
    adb shell input keyevent 66
    $UI notifications "7,5" 10 || echo "!! inline reply was not confirmed"
  fi
  adb shell cmd statusbar collapse
  $UI route "day/$D2"
  $UI dump day-after-reply 1 || exit 1
else
  echo "!! the emulator clock could not be changed, notification flow skipped"
fi

echo "===== LOGCAT (app) ====="
adb logcat -d | grep -E "WorkCalendar|AndroidRuntime|FATAL" | tail -80
if adb logcat -d -b crash | grep -q "FATAL EXCEPTION"; then
  echo "App crashed"
  exit 1
fi
echo "SMOKE TEST PASSED"
