package com.health.friday.alarms

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class AlarmRepository(
    private val alarmDao: AlarmDao,
    private val scheduler: AlarmScheduler
) {


    fun getAlarms(): Flow<List<Alarm>> =
        alarmDao.getAll()

    suspend fun getAlarmsNow(): List<Alarm> =
        alarmDao.getAll().first()

    suspend fun addAlarm(
        title: String,
        timeMillis: Long,
        repeatDaily: Boolean
    ): Alarm? {

        val cleanTitle = title.trim()

        if (cleanTitle.isEmpty()) {
            return null
        }

        val alarm = Alarm(
            title = cleanTitle,
            timeMillis = timeMillis,
            enabled = true,
            repeatDaily = repeatDaily
        )

        val id = alarmDao.insert(alarm)

        val savedAlarm = alarm.copy(id = id)

        val scheduled = scheduler.scheduleAlarm(
            alarmId = savedAlarm.id,
            title = savedAlarm.title,
            triggerAtMillis = savedAlarm.timeMillis,
            repeatDaily = savedAlarm.repeatDaily
        )

        if (!scheduled) {
            alarmDao.update(
                savedAlarm.copy(
                    enabled = false
                )
            )

            return savedAlarm.copy(
                enabled = false
            )
        }

        return savedAlarm
    }

    suspend fun enableAlarm(
        alarm: Alarm
    ): Boolean {

        val scheduled = scheduler.scheduleAlarm(
            alarmId = alarm.id,
            title = alarm.title,
            triggerAtMillis = alarm.timeMillis,
            repeatDaily = alarm.repeatDaily
        )

        if (!scheduled) {
            return false
        }

        alarmDao.update(
            alarm.copy(
                enabled = true
            )
        )

        return true
    }

    suspend fun disableAlarm(
        alarm: Alarm
    ) {

        scheduler.cancelAlarm(
            alarmId = alarm.id
        )

        alarmDao.update(
            alarm.copy(
                enabled = false
            )
        )
    }

    suspend fun deleteAlarm(
        alarm: Alarm
    ) {

        scheduler.cancelAlarm(
            alarmId = alarm.id
        )

        alarmDao.delete(alarm)
    }


}
