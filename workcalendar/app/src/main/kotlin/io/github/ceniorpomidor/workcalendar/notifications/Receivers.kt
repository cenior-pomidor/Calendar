package io.github.ceniorpomidor.workcalendar.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import io.github.ceniorpomidor.workcalendar.appContainer
import io.github.ceniorpomidor.workcalendar.domain.util.Formats
import io.github.ceniorpomidor.workcalendar.domain.util.HoursParser
import io.github.ceniorpomidor.workcalendar.work.MaintenanceWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Runs [block] off the main thread while keeping the broadcast alive. */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            block()
        } catch (e: Exception) {
            android.util.Log.e("WorkCalendar", "Broadcast handling failed", e)
        } finally {
            pending.finish()
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val snooze = intent.getBooleanExtra(EXTRA_SNOOZE, false)
        runAsync { context.appContainer.notifications.onAlarm(key, snooze) }
    }

    companion object {
        const val ACTION_ALARM = "io.github.ceniorpomidor.workcalendar.ALARM"
        const val EXTRA_KEY = "key"
        const val EXTRA_SNOOZE = "snooze"
    }
}

/** Actions from notifications: confirm the planned hours, enter hours inline, snooze. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val shiftId = intent.getLongExtra(EXTRA_SHIFT_ID, -1L)
        val container = context.appContainer
        // Results are shown as a separate short notification; the original one is removed.
        val savedKey = "$key:saved"
        when (intent.action) {
            ACTION_SNOOZE -> runAsync { container.notifications.snooze(key) }
            ACTION_CONFIRM_FULL -> runAsync {
                if (shiftId <= 0) return@runAsync
                try {
                    val shift = container.shifts.confirmAsPlanned(shiftId)
                    container.notifications.dismiss(key)
                    val amount = shift.pay?.total?.let { " · ${Formats.money(it)}" } ?: ""
                    Notifications.show(
                        context,
                        savedKey,
                        Notifications.buildSaved(
                            context,
                            savedKey,
                            "Часы сохранены",
                            "${Formats.shortDate(shift.date)}: ${Formats.hours((shift.workedMinutes ?: 0).toLong())}$amount",
                            "confirm/$shiftId",
                        ),
                    )
                } catch (e: Exception) {
                    Notifications.show(context, savedKey, Notifications.buildSaved(context, savedKey, "Не удалось сохранить", e.message ?: "Ошибка", "confirm/$shiftId"))
                }
            }
            ACTION_REPLY_HOURS -> runAsync {
                if (shiftId <= 0) return@runAsync
                val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifications.KEY_HOURS)?.toString().orEmpty()
                val minutes = HoursParser.parse(text)
                if (minutes == null) {
                    val current = container.notifications.current(key)
                    if (current != null) {
                        Notifications.show(context, key, Notifications.build(context, current, "Не удалось распознать «$text». Введите часы, например 7,5 или 7:30"))
                    }
                    return@runAsync
                }
                try {
                    val shift = if (minutes == 0) {
                        container.shifts.markMissed(shiftId)
                        null
                    } else {
                        container.shifts.confirm(shiftId, minutes)
                    }
                    container.notifications.dismiss(key)
                    val body = if (shift == null) {
                        "Смена отмечена как несостоявшаяся"
                    } else {
                        "${Formats.shortDate(shift.date)}: ${Formats.hours(minutes.toLong())}" + (shift.pay?.total?.let { " · ${Formats.money(it)}" } ?: "")
                    }
                    Notifications.show(context, savedKey, Notifications.buildSaved(context, savedKey, "Часы сохранены", body, "confirm/$shiftId"))
                } catch (e: Exception) {
                    Notifications.show(context, savedKey, Notifications.buildSaved(context, savedKey, "Не удалось сохранить", e.message ?: "Ошибка", "confirm/$shiftId"))
                }
            }
        }
    }

    companion object {
        const val ACTION_CONFIRM_FULL = "io.github.ceniorpomidor.workcalendar.CONFIRM_FULL"
        const val ACTION_REPLY_HOURS = "io.github.ceniorpomidor.workcalendar.REPLY_HOURS"
        const val ACTION_SNOOZE = "io.github.ceniorpomidor.workcalendar.SNOOZE"
        const val EXTRA_KEY = "key"
        const val EXTRA_SHIFT_ID = "shiftId"
    }
}

/** Restores alarms after a reboot, an app update or a change of the system time / time zone. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = context.appContainer
        runAsync {
            container.schedule.ensureHorizon()
            container.notifications.reschedule()
        }
        MaintenanceWorker.schedule(context)
    }
}
