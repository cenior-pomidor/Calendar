package io.github.ceniorpomidor.workcalendar.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.ceniorpomidor.workcalendar.AppContainer
import io.github.ceniorpomidor.workcalendar.domain.absence.InsuranceExperience
import io.github.ceniorpomidor.workcalendar.domain.model.AppSettings
import io.github.ceniorpomidor.workcalendar.domain.model.Money
import io.github.ceniorpomidor.workcalendar.domain.model.ThemeMode
import io.github.ceniorpomidor.workcalendar.domain.notify.NotificationKind
import io.github.ceniorpomidor.workcalendar.domain.notify.PlannedNotification
import io.github.ceniorpomidor.workcalendar.domain.util.Formats
import io.github.ceniorpomidor.workcalendar.notifications.Notifications
import io.github.ceniorpomidor.workcalendar.ui.components.Banner
import io.github.ceniorpomidor.workcalendar.ui.components.BannerKind
import io.github.ceniorpomidor.workcalendar.ui.components.DateField
import io.github.ceniorpomidor.workcalendar.ui.components.LocalSnackbar
import io.github.ceniorpomidor.workcalendar.ui.components.NumberField
import io.github.ceniorpomidor.workcalendar.ui.components.ScrollColumn
import io.github.ceniorpomidor.workcalendar.ui.components.SectionCard
import io.github.ceniorpomidor.workcalendar.ui.components.StepperField
import io.github.ceniorpomidor.workcalendar.ui.components.SubScreen
import io.github.ceniorpomidor.workcalendar.ui.components.SwitchRow
import io.github.ceniorpomidor.workcalendar.ui.components.TextInput
import io.github.ceniorpomidor.workcalendar.ui.components.TimeField
import io.github.ceniorpomidor.workcalendar.ui.components.ValueRow
import io.github.ceniorpomidor.workcalendar.ui.components.launchSafely
import java.time.DayOfWeek

/** Saves a settings change and reports errors. */
@Composable
private fun rememberSettingsUpdater(container: AppContainer): ((AppSettings) -> AppSettings) -> Unit {
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    return { transform -> scope.launchSafely(snackbar) { container.settings.update(transform) } }
}

@Composable
fun ProfileScreen(container: AppContainer, settings: AppSettings, onBack: () -> Unit) {
    val update = rememberSettingsUpdater(container)
    var currency by rememberSaveable { mutableStateOf(settings.currency) }
    val months = InsuranceExperience.months(settings.employmentStartDate, settings.priorExperienceMonths, container.clock.today())
    SubScreen(title = "Профиль и стаж", onBack = onBack) { padding ->
        ScrollColumn(padding) {
            SectionCard(title = "Трудоустройство") {
                DateField(
                    "Дата трудоустройства",
                    settings.employmentStartDate,
                    { date -> update { it.copy(employmentStartDate = date) } },
                    supportingText = "Используется для стажа (больничный) и расчётного периода отпускных",
                )
                StepperField(
                    "Стаж до этой работы, лет",
                    settings.priorExperienceMonths / 12,
                    { years -> update { it.copy(priorExperienceMonths = years * 12 + it.priorExperienceMonths % 12) } },
                    0..60,
                )
                StepperField(
                    "и месяцев",
                    settings.priorExperienceMonths % 12,
                    { m -> update { it.copy(priorExperienceMonths = it.priorExperienceMonths / 12 * 12 + m) } },
                    0..11,
                )
                ValueRow("Страховой стаж сегодня", InsuranceExperience.describe(months), emphasize = true)
                ValueRow("Процент пособия по больничному", "${InsuranceExperience.sickPercent(months)}%")
            }
            SectionCard(title = "Учёт в приложении") {
                DateField(
                    "Все смены и выплаты внесены начиная с",
                    settings.trackingStartDate,
                    { date -> update { it.copy(trackingStartDate = date) } },
                    supportingText = "Заработок до этой даты нужно указать в «Отпуск и больничный → Заработок прошлых периодов»",
                )
            }
            SectionCard(title = "Валюта") {
                TextInput("Символ валюты", currency, {
                    currency = it.take(4)
                    update { s -> s.copy(currency = currency.ifBlank { "₽" }) }
                })
            }
        }
    }
}

@Composable
fun PaySettingsScreen(container: AppContainer, settings: AppSettings, onBack: () -> Unit) {
    val update = rememberSettingsUpdater(container)
    var taxText by rememberSaveable { mutableStateOf(if (settings.pay.taxPercent > 0) settings.pay.taxPercent.toString() else "") }
    var targetText by rememberSaveable { mutableStateOf(settings.pay.monthlyTarget?.toString()?.replace('.', ',') ?: "") }
    SubScreen(title = "Правила расчёта", onBack = onBack) { padding ->
        ScrollColumn(padding) {
            SectionCard(title = "Предварительный заработок") {
                SwitchRow(
                    "Показывать прогноз по плану",
                    "Сумма по запланированным часам. Окончательный заработок считается только по подтверждённым часам",
                    settings.pay.showForecast,
                ) { v -> update { it.copy(pay = it.pay.copy(showForecast = v)) } }
            }
            SectionCard(title = "Ночное время") {
                Text("Часы в этом интервале оплачиваются с ночной надбавкой из ставки (по ТК РФ ночь — 22:00–06:00).", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeField("Начало ночи", settings.pay.nightStartMinute, { m -> update { it.copy(pay = it.pay.copy(nightStartMinute = m)) } }, modifier = Modifier.weight(1f))
                    TimeField("Конец ночи", settings.pay.nightEndMinute, { m -> update { it.copy(pay = it.pay.copy(nightEndMinute = m)) } }, modifier = Modifier.weight(1f))
                }
            }
            SectionCard(title = "Налоги") {
                NumberField(
                    "НДФЛ, %",
                    taxText,
                    { text ->
                        taxText = text.filter { it.isDigit() }.take(2)
                        update { it.copy(pay = it.pay.copy(taxPercent = taxText.toIntOrNull()?.coerceIn(0, 50) ?: 0)) }
                    },
                    decimal = false,
                    supportingText = "Пусто или 0 — суммы показываются как расчётный заработок без вычетов. Обычно 13",
                )
            }
            SectionCard(title = "Цель на месяц") {
                NumberField(
                    "Минимальный целевой заработок",
                    targetText,
                    { text ->
                        targetText = text
                        val value = Money.parse(text)
                        update { it.copy(pay = it.pay.copy(monthlyTarget = value?.takeIf { v -> v.isPositive })) }
                    },
                    suffix = "₽",
                    supportingText = "Прогресс показывается в разделе «Финансы»",
                )
            }
        }
    }
}

@Composable
fun DisplaySettingsScreen(container: AppContainer, settings: AppSettings, onBack: () -> Unit) {
    val update = rememberSettingsUpdater(container)
    val c = settings.calendar
    SubScreen(title = "Календарь и оформление", onBack = onBack) { padding ->
        ScrollColumn(padding) {
            SectionCard(title = "Тема") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ThemeMode.SYSTEM to "Как в системе", ThemeMode.LIGHT to "Светлая", ThemeMode.DARK to "Тёмная").forEach { (mode, label) ->
                        FilterChip(selected = settings.theme == mode, onClick = { update { it.copy(theme = mode) } }, label = { Text(label) })
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchRow("Цвета обоев (Material You)", null, settings.dynamicColor) { v -> update { it.copy(dynamicColor = v) } }
                }
            }
            SectionCard(title = "Календарь") {
                Text("Первый день недели", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY, DayOfWeek.SATURDAY).forEach { day ->
                        FilterChip(
                            selected = c.firstDayOfWeek == day,
                            onClick = { update { it.copy(calendar = it.calendar.copy(firstDayOfWeek = day)) } },
                            label = { Text(Formats.weekdayFull(day).replaceFirstChar { ch -> ch.uppercase() }) },
                        )
                    }
                }
                SwitchRow("Время смен в ячейках", null, c.showShiftTimes) { v -> update { it.copy(calendar = it.calendar.copy(showShiftTimes = v)) } }
                SwitchRow("Отработанные часы после подтверждения", null, c.showWorkedHours) { v -> update { it.copy(calendar = it.calendar.copy(showWorkedHours = v)) } }
                SwitchRow("Заработок за день в ячейках", null, c.showDayEarnings) { v -> update { it.copy(calendar = it.calendar.copy(showDayEarnings = v)) } }
                SwitchRow("Выделять праздники", null, c.showHolidays) { v -> update { it.copy(calendar = it.calendar.copy(showHolidays = v)) } }
                SwitchRow("Сводка месяца над календарём", null, c.showMonthSummary) { v -> update { it.copy(calendar = it.calendar.copy(showMonthSummary = v)) } }
            }
        }
    }
}

@Composable
fun NotificationSettingsScreen(container: AppContainer, settings: AppSettings, onBack: () -> Unit) {
    val update = rememberSettingsUpdater(container)
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val canPost = remember(refresh) { Notifications.canPost(context) }
    val exact = remember(refresh) { container.notifications.canScheduleExact() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val n = settings.notifications
    SubScreen(title = "Уведомления", onBack = onBack) { padding ->
        ScrollColumn(padding) {
            if (!canPost) {
                Banner(
                    "Уведомления запрещены — напоминания не будут показаны.",
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
            if (!exact) {
                Banner(
                    "Точные будильники недоступны — уведомления могут приходить с задержкой.",
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
            SectionCard(title = "Окончание смены") {
                SwitchRow("Предлагать отметить часы", "С кнопками «Полностью», «Ввести часы» и «Отложить»", n.shiftEnd) { v ->
                    update { it.copy(notifications = it.notifications.copy(shiftEnd = v)) }
                }
                StepperField("Через минут после окончания", n.shiftEndDelayMinutes, { v -> update { it.copy(notifications = it.notifications.copy(shiftEndDelayMinutes = v)) } }, 0..240, step = 5)
            }
            SectionCard(title = "Неподтверждённые часы") {
                SwitchRow("Ежедневное напоминание", "Если есть смены без отмеченных часов", n.unconfirmedReminder) { v ->
                    update { it.copy(notifications = it.notifications.copy(unconfirmedReminder = v)) }
                }
                TimeField("Время напоминания", n.unconfirmedReminderMinute, { m -> update { it.copy(notifications = it.notifications.copy(unconfirmedReminderMinute = m)) } })
            }
            SectionCard(title = "Аванс и зарплата") {
                SwitchRow("Напоминать о выплатах", "За сколько дней — в настройках каждой выплаты", n.payouts) { v ->
                    update { it.copy(notifications = it.notifications.copy(payouts = v)) }
                }
                TimeField("Время уведомления", n.payoutMinute, { m -> update { it.copy(notifications = it.notifications.copy(payoutMinute = m)) } })
            }
            SectionCard(title = "Отпускные и больничные") {
                SwitchRow("Отпускные", "За ${settings.absence.vacationNotifyBusinessDaysBefore} раб. дня до отпуска, с суммой", n.vacationPay) { v ->
                    update { it.copy(notifications = it.notifications.copy(vacationPay = v)) }
                }
                SwitchRow("Пособие по больничному", "Утром в дни ожидаемых выплат", n.sickPay) { v ->
                    update { it.copy(notifications = it.notifications.copy(sickPay = v)) }
                }
                TimeField("Время уведомления", n.absenceMinute, { m -> update { it.copy(notifications = it.notifications.copy(absenceMinute = m)) } })
            }
            SectionCard(title = "Отложить") {
                StepperField("Отложить на, минут", n.snoozeMinutes, { v -> update { it.copy(notifications = it.notifications.copy(snoozeMinutes = v)) } }, 5..720, step = 15)
            }
            OutlinedButton(onClick = {
                val test = PlannedNotification(
                    key = "test:${System.currentTimeMillis()}",
                    kind = NotificationKind.PAYOUT,
                    triggerAt = container.clock.now(),
                    title = "Проверка уведомлений",
                    text = "Так будут выглядеть напоминания о выплатах.",
                )
                if (Notifications.canPost(context)) {
                    Notifications.show(context, test.key, Notifications.build(context, test))
                } else {
                    scope.launchSafely(snackbar) { snackbar.showSnackbar("Сначала разрешите уведомления") }
                }
            }) { Text("Отправить тестовое уведомление") }
        }
    }
}
