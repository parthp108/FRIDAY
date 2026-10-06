
package com.health.friday.reminders

import com.health.friday.data.local.Reminder
import com.health.friday.data.local.ReminderDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ReminderRepository(
    private val reminderDao: ReminderDao,
    private val scheduler: ReminderScheduler
) {

    fun getReminders(): Flow<List<Reminder>> {
        return reminderDao.getAll()
    }

    suspend fun getRemindersNow(): List<Reminder> {
        return reminderDao.getAll().first()
    }

    suspend fun addReminder(
        title: String,
        timeMillis: Long,
        repeatDaily: Boolean
    ): Reminder? {

        val cleanTitle =
            title.trim()

        if (cleanTitle.isEmpty()) {
            return null
        }

        val reminder =
            Reminder(
                title = cleanTitle,
                timeMillis = timeMillis,
                enabled = true,
                repeatDaily = repeatDaily
            )

        val id =
            reminderDao.insert(
                reminder
            )

        val savedReminder =
            reminder.copy(
                id = id
            )

        if (!scheduler.canScheduleExactAlarms()) {

            scheduler.openExactAlarmSettings()

            reminderDao.update(
                savedReminder.copy(
                    enabled = false
                )
            )

            return savedReminder.copy(
                enabled = false
            )
        }

        val scheduled =
            scheduler.scheduleReminder(
                reminderId =
                    savedReminder.id,

                title =
                    savedReminder.title,

                message =
                    savedReminder.title,

                triggerAtMillis =
                    savedReminder.timeMillis,

                repeatDaily =
                    savedReminder.repeatDaily
            )

        if (!scheduled) {

            reminderDao.update(
                savedReminder.copy(
                    enabled = false
                )
            )

            return savedReminder.copy(
                enabled = false
            )
        }

        return savedReminder
    }

    suspend fun enableReminder(
        reminder: Reminder
    ): Boolean {

        if (!scheduler.canScheduleExactAlarms()) {

            scheduler.openExactAlarmSettings()

            return false
        }

        val scheduled =
            scheduler.scheduleReminder(
                reminderId =
                    reminder.id,

                title =
                    reminder.title,

                message =
                    reminder.title,

                triggerAtMillis =
                    reminder.timeMillis,

                repeatDaily =
                    reminder.repeatDaily
            )

        if (!scheduled) {
            return false
        }

        reminderDao.update(
            reminder.copy(
                enabled = true
            )
        )

        return true
    }

    suspend fun disableReminder(
        reminder: Reminder
    ) {

        scheduler.cancelReminder(
            reminder.id
        )

        reminderDao.update(
            reminder.copy(
                enabled = false
            )
        )
    }

    suspend fun deleteReminder(
        reminder: Reminder
    ) {

        scheduler.cancelReminder(
            reminder.id
        )

        reminderDao.delete(
            reminder
        )
    }

    /*
     * Called after a one-time reminder fires.
     *
     * We disable it rather than deleting it so the reminder
     * remains visible in FRIDAY's Tasks screen as completed/expired.
     */
    suspend fun completeOneTimeReminder(
        reminderId: Long
    ) {

        reminderDao.disable(
            reminderId
        )
    }
}

