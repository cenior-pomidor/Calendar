package io.github.ceniorpomidor.workcalendar.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.ceniorpomidor.workcalendar.AppContainer
import io.github.ceniorpomidor.workcalendar.domain.alarm.AlarmPlanner
import io.github.ceniorpomidor.workcalendar.domain.alarm.PlannedAlarm
import io.github.ceniorpomidor.workcalendar.domain.model.AlarmSettings
import io.github.ceniorpomidor.workcalendar.domain.model.AlarmTimeMode
import io.github.ceniorpomidor.workcalendar.domain.model.AppSettings
import io.github.ceniorpomidor.workcalendar.domain.time.TimeMath
import io.github.ceniorpomidor.workcalendar.domain.util.Formats
import io.github.ceniorpomidor.workcalendar.notifications.Notifications
import io.github.ceniorpomidor.workcalendar.ui.components.Banner
import io.github.ceniorpomidor.workcalendar.ui.components.BannerKind
import io.github.ceniorpomidor.workcalendar.ui.components.LocalSnackbar
import io.github.ceniorpomidor.workcalendar.ui.components.ScrollColumn
import io.github.ceniorpomidor.workcalendar.ui.components.SectionCard
import io.github.ceniorpomidor.workcalendar.ui.components.SettingRow
import io.github.ceniorpomidor.workcalendar.ui.components.SubScreen
import io.github.ceniorpomidor.workcalendar.ui.components.SwitchRow
import io.github.ceniorpomidor.workcalendar.ui.components.TimeField
import io.github.ceniorpomidor.workcalendar.ui.components.launchSafely
import io.github.ceniorpomidor.workcalendar.ui.theme.AppIcons

/** One line about the alarm for the settings list. */
fun alarmSummary(alarm: AlarmSettings): String {
    val singles = alarm.days.count { it.minute != null }
    val base = when {
        !alarm.workDays -> "Выключен"
        alarm.mode == AlarmTimeMode.BEFORE_SHIFT -> "В рабочие дни за ${Formats.hoursMinutes(alarm.minutesBefore.toLong())} до смены"
        else -> "В рабочие дни в ${Formats.time(TimeMath.timeOfMinute(alarm.fixedMinute))}"
    }
    return if (singles > 0 && !alarm.workDays) "Только в выбранные дни" else base
}

@Composable
fun AlarmScreen(container: AppContainer, settings: AppSettings, onBack: (() -> Unit)?) {
    val update = rememberSettingsUpdater(container)
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val canPost = remember(refresh) { Notifications.canPost(context) }
    val fullScreen = remember(refresh) { container.wakeAlarms.canUseFullScreen() }
    val exact = remember(refresh) { container.notifications.canScheduleExact() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val alarm = settings.alarm
    val today = container.clock.today()
    val upcoming by produceState(emptyList<PlannedAlarm>(), alarm, refresh) { value = container.wakeAlarms.upcoming(UPCOMING_COUNT) }
    val snoozedUntil = remember(refresh, upcoming) { container.wakeAlarms.snoozedUntil() }
    fun change(transform: (AlarmSettings) -> AlarmSettings) = update { it.copy(alarm = transform(it.alarm)) }

    SubScreen(title = "Будильник", onBack = onBack) { padding ->
        ScrollColumn(padding) {
            if (!canPost) {
                Banner(
                    "Уведомления запрещены — будильник не сможет зазвонить.",
                    BannerKind.ERROR,
                    action = {
                        TextButton(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                            }
                        }) { Text("Разрешить") }
                    },
                )
            }
            if (!fullScreen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Banner(
                    "Нет разрешения на показ во весь экран — при заблокированном телефоне будильник появится только уведомлением.",
                    BannerKind.WARNING,
                    action = {
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
                            }
                        }) { Text("Разрешить") }
                    },
                )
            }
            if (!exact) {
                Banner(
                    "Точные будильники запрещены — звонок может опоздать на несколько минут.",
                    BannerKind.WARNING,
                    action = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            TextButton(onClick = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                                }
                            }) { Text("Настроить") }
                        }
                    },
                )
            }
            snoozedUntil?.let { at ->
                Banner(
                    "Будильник отложен до ${Formats.time(at)}",
                    BannerKind.INFO,
                    action = {
                        TextButton(onClick = {
                            scope.launchSafely(snackbar, success = "Отложенный будильник выключен") {
                                container.wakeAlarms.cancelSnooze()
                                refresh++
                            }
                        }) { Text("Выключить") }
                    },
                )
            }
            SectionCard(title = "Рабочие дни", icon = AppIcons.Alarm) {
                SwitchRow("Будильник во все рабочие дни", "Звонит перед каждой сменой по графику", alarm.workDays) { v -> change { it.copy(workDays = v) } }
                if (alarm.workDays) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = alarm.mode == AlarmTimeMode.BEFORE_SHIFT,
                            onClick = { change { it.copy(mode = AlarmTimeMode.BEFORE_SHIFT) } },
                            label = { Text("До начала смены") },
                        )
                        FilterChip(
                            selected = alarm.mode == AlarmTimeMode.FIXED_TIME,
                            onClick = { change { it.copy(mode = AlarmTimeMode.FIXED_TIME) } },
                            label = { Text("В одно время") },
                        )
                    }
                    if (alarm.mode == AlarmTimeMode.BEFORE_SHIFT) {
                        TimeField(
                            "За сколько до начала смены (ч:мин)",
                            alarm.minutesBefore,
                            { m -> change { it.copy(minutesBefore = m.coerceIn(0, MAX_BEFORE_MINUTES)) } },
                            supportingText = "Сейчас: за ${Formats.hoursMinutes(alarm.minutesBefore.toLong())}. Для ранних смен будильник может зазвонить накануне вечером",
                        )
                    } else {
                        TimeField("Время будильника", alarm.fixedMinute, { m -> change { it.copy(fixedMinute = m) } }, supportingText = "В день смены, когда бы она ни начиналась")
                    }
                    SwitchRow("И в дни доп. смен", "Смены, добавленные вручную", alarm.includeExtraShifts) { v -> change { it.copy(includeExtraShifts = v) } }
                }
                Text(
                    "Будильник на один день: выберите день в календаре и включите «Будильник» в панели дня. Там же его можно выключить или сдвинуть только на этот день.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SectionCard(title = "Ближайшие будильники", icon = AppIcons.Event) {
                if (upcoming.isEmpty()) {
                    Text(
                        if (alarm.workDays) "В ближайшие два месяца рабочих дней нет" else "Будильник не заведён",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                for (a in upcoming) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${Formats.time(a.at)} · ${Formats.dayTitle(a.at.toLocalDate())}", style = MaterialTheme.typography.bodyLarge)
                            Text(a.description(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            scope.launchSafely(snackbar, success = "Будильник на ${Formats.shortDate(a.date)} выключен") {
                                container.settings.update { s ->
                                    // A working day is switched off; an alarm set for a single day is removed.
                                    val workDay = AlarmPlanner.regular(s.alarm, a.date, listOfNotNull(a.shift)) != null
                                    s.copy(alarm = if (workDay) s.alarm.withDay(a.date, null, today) else s.alarm.withoutDay(a.date, today))
                                }
                            }
                        }) { Icon(AppIcons.AlarmOff, contentDescription = "Не будить в этот день") }
                    }
                }
            }
            val offDays = alarm.days.filter { it.minute == null && !it.date.isBefore(today) }
            if (offDays.isNotEmpty()) {
                SectionCard(title = "Выключен в дни", icon = AppIcons.AlarmOff) {
                    for (day in offDays) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(Formats.dayTitle(day.date).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            TextButton(onClick = { change { it.withoutDay(day.date, today) } }) { Text("Вернуть") }
                        }
                    }
                }
            }
            SectionCard(title = "Звонок", icon = AppIcons.NotificationsActive) {
                Text("Отложить на", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 20, 30).forEach { m ->
                        FilterChip(selected = alarm.snoozeMinutes == m, onClick = { change { it.copy(snoozeMinutes = m) } }, label = { Text("$m мин") })
                    }
                }
                Text("Звонит не дольше", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 5, 10, 15, 30).forEach { m ->
                        FilterChip(selected = alarm.ringMinutes == m, onClick = { change { it.copy(ringMinutes = m) } }, label = { Text("$m мин") })
                    }
                }
                SettingRow(
                    "Мелодия и вибрация",
                    "Системные настройки звука будильника. Громкость — как у будильника телефона",
                    AppIcons.Tune,
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    .putExtra(Settings.EXTRA_CHANNEL_ID, Notifications.CHANNEL_ALARM),
                            )
                        }
                    },
                )
                OutlinedButton(onClick = { scope.launchSafely(snackbar) { container.wakeAlarms.test() } }, modifier = Modifier.fillMaxWidth()) {
                    Text("Проверить будильник")
                }
            }
        }
    }
}

private const val UPCOMING_COUNT = 10

/** The alarm can ring at most 12 hours before the shift. */
private const val MAX_BEFORE_MINUTES = 12 * 60
