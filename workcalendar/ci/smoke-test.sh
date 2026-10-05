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

# Month swipes: the title follows the swipe and the calendar does not stop between two months.
month_title() {
  python3 -c "import datetime,sys
names = ['Январь', 'Февраль', 'Март', 'Апрель', 'Май', 'Июнь', 'Июль', 'Август', 'Сентябрь', 'Октябрь', 'Ноябрь', 'Декабрь']
t = datetime.date.today()
m = t.month - 1 + int(sys.argv[1])
print(names[m % 12], t.year + m // 12)" "$1"
}
# Title of today in the day panel: the selection stays on today while months are swiped.
today_title() {
  python3 -c "import datetime
names = ['января', 'февраля', 'марта', 'апреля', 'мая', 'июня', 'июля', 'августа', 'сентября', 'октября', 'ноября', 'декабря']
t = datetime.date.today()
print(t.day, names[t.month - 1])"
}
# `input swipe` waits until the app has handled each event: while the app is still busy after the
# setup wizard (generating the schedule), a quick swipe can turn into a plain touch without moves.
# Let it settle first and log how long the swipe command takes.
sleep 4
timed_swipe() {
  local start end
  start=$(date +%s%N)
  adb shell input swipe "$@"
  end=$(date +%s%N)
  echo ">> swipe $* took $(( (end - start) / 1000000 )) ms"
}
timed_swipe 950 1000 150 1000 300
if ! $UI dump swipe-fast 0 "$(month_title 1)"; then
  echo "!! WARNING: the first swipe did not change the month; trying once more"
  # Which events the system delivered to the app: shows whether the moves of the swipe arrived.
  adb shell dumpsys input | sed -n '/RecentQueue/,/PendingEvent/p' | cut -c1-240 | head -30
  timed_swipe 950 1000 150 1000 300
  $UI dump swipe-fast-retry 1 "$(month_title 1)" || exit 1
fi
# A slow drag over 60% of the width, slightly diagonal.
adb shell input swipe 950 950 300 1150 900
$UI dump swipe-slow 1 "$(month_title 2)" "$(today_title)" || exit 1
adb shell input swipe 150 1000 950 1000 250
$UI dump swipe-back 0 "$(month_title 1)" || exit 1
$UI tap "Сегодня"
$UI dump swipe-today 0 "$(month_title 0)" || exit 1

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
VACATION=$(date -d '+20 days' +%F)
$UI route "absence/new?type=VACATION&start=$VACATION"
sleep 2
$UI dump absence 1 || exit 1
$UI tap "Сохранить" 1
sleep 2
$UI route "day/$VACATION"
$UI dump vacation-day 1 "Сумма" || exit 1
$UI route "settings/backup"
$UI dump backup 0 || exit 1
$UI route "settings/notifications"
$UI dump notifications 0 || exit 1
$UI route "search"
$UI dump search 0 || exit 1

# ---- Appearance: another palette and the alarm in the bottom bar ----
$UI route "settings/display"
$UI dump appearance 1 "Цветовая палитра" || exit 1
$UI tap "Зелёный" 1
$UI tap "Будильник" 1
sleep 2
$UI dump appearance-after 0 || exit 1
$UI route "calendar"
$UI dump calendar-green 1 "=Будильник" || exit 1

# ---- Alarm: every working day, one more on a day off, test ring ----
$UI tap "Будильник" 1
$UI dump alarm 0 "Будильник во все рабочие дни" || exit 1
$UI tap "Будильник во все рабочие дни" 1
sleep 2
$UI show "Ближайшие будильники"
$UI dump alarm-on 1 "Ближайшие будильники" "07:30" || exit 1
SATURDAY=$(python3 -c "import datetime
d = datetime.date.today() + datetime.timedelta(days=1)
while d.weekday() != 5:
    d += datetime.timedelta(days=1)
print(d)")
$UI route "day/$SATURDAY"
$UI show "Включите, чтобы разбудить в этот день"
$UI dump day-no-alarm 0 "Включите, чтобы разбудить в этот день" || exit 1
$UI tap "Включите, чтобы разбудить в этот день"
$UI tap "Готово" 1
sleep 4
$UI show "Только в этот день"
$UI dump day-alarm 1 "Будильник 07:00" "Только в этот день" || exit 1
$UI route "alarm"
$UI tap "Проверить будильник"
$UI wait alarm-test "Отложить на 10 мин" 30 || exit 1
$UI notifications "Проверка будильника" 10 || exit 1
$UI tap "Выключить" 1
sleep 2
$UI show "Ближайшие будильники"
$UI dump alarm-after-test 0 "Ближайшие будильники" || exit 1

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

  # ---- The alarm of the next working day rings by itself over the lock screen ----
  D3=$(next_weekday "$D2")
  adb shell appops set $PKG USE_FULL_SCREEN_INTENT allow || true
  adb shell input keyevent 223
  $UI set-time "${D3}T07:29:30"
  if $UI wait alarm-ring "Отложить на 10 мин" 90; then
    $UI tap "Отложить на 10 мин" 1
    $UI notifications "Будильник отложен до 07:4" 15 || exit 1
    adb shell input keyevent 223
    $UI set-time "${D3}T07:39:30"
    $UI wait alarm-snoozed-ring "Будильник 07:4" 150 || exit 1
    $UI tap "Выключить" 1
  else
    echo "!! WARNING: the alarm screen did not open over the lock screen"
    $UI notifications "Будильник 07:30" 5 || exit 1
  fi
  echo "===== NEXT ALARM CLOCK ====="
  adb shell dumpsys alarm | grep -i -A3 "next alarm clock" | head -12
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
