package com.health.friday.proactive

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationActionReceiverTest {

    @Test
    fun testActionConstants() {
        assertEquals("ACTION_ADD_WATER_250", NotificationActionReceiver.ACTION_ADD_WATER_250)
        assertEquals("ACTION_SNOOZE_15M", NotificationActionReceiver.ACTION_SNOOZE_15M)
        assertEquals("ACTION_MARK_TASKS_DONE", NotificationActionReceiver.ACTION_MARK_TASKS_DONE)
        assertEquals("ACTION_RUN_PROTOCOL", NotificationActionReceiver.ACTION_RUN_PROTOCOL)
        assertEquals("ACTION_DISMISS", NotificationActionReceiver.ACTION_DISMISS)

        assertEquals("NOTIFICATION_ID", NotificationActionReceiver.EXTRA_NOTIFICATION_ID)
        assertEquals("NUDGE_TYPE", NotificationActionReceiver.EXTRA_NUDGE_TYPE)
        assertEquals("NUDGE_ID", NotificationActionReceiver.EXTRA_NUDGE_ID)
        assertEquals("PROTOCOL_ID", NotificationActionReceiver.EXTRA_PROTOCOL_ID)
    }
}
