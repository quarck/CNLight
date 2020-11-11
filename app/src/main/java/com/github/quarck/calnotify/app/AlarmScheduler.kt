//
//   Calendar Notifications Plus
//   Copyright (C) 2016 Sergey Parshin (s.parshin.sc@gmail.com)
//
//   This program is free software; you can redistribute it and/or modify
//   it under the terms of the GNU General Public License as published by
//   the Free Software Foundation; either version 3 of the License, or
//   (at your option) any later version.
//
//   This program is distributed in the hope that it will be useful,
//   but WITHOUT ANY WARRANTY; without even the implied warranty of
//   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//   GNU General Public License for more details.
//
//   You should have received a copy of the GNU General Public License
//   along with this program; if not, write to the Free Software Foundation,
//   Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
//

package com.github.quarck.calnotify.app

import android.content.Context
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.broadcastreceivers.ReminderAlarmBroadcastReceiver
import com.github.quarck.calnotify.broadcastreceivers.ReminderExactAlarmBroadcastReceiver
import com.github.quarck.calnotify.broadcastreceivers.SnoozeAlarmBroadcastReceiver
import com.github.quarck.calnotify.broadcastreceivers.SnoozeExactAlarmBroadcastReceiver
import com.github.quarck.calnotify.calendar.isNotSnoozed
import com.github.quarck.calnotify.calendar.isSnoozed
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.persistentState
import com.github.quarck.calnotify.reminders.ReminderState
import com.github.quarck.calnotify.ui.MainActivity
import com.github.quarck.calnotify.utils.alarmManager
import com.github.quarck.calnotify.utils.cancelExactAndAlarm
import com.github.quarck.calnotify.utils.setExactAndAlarm


object AlarmScheduler  {

    const val LOG_TAG = "AlarmScheduler"

    fun rescheduleAlarms(context: Context) {

        DevLog.debug(LOG_TAG, "rescheduleAlarms called");

        EventsStorage(context).use {
            db ->

            val events = db.events

            // Schedule event (snooze) alarm
            var nextEventAlarm =
                    events.filter { it.isSnoozed }.map { it.snoozedUntil }.min()

            if (nextEventAlarm != null) {

                val currentTime = System.currentTimeMillis()

                if (nextEventAlarm < currentTime) {
                    DevLog.error(LOG_TAG, "CRITICAL: rescheduleAlarms: nextAlarm=$nextEventAlarm is less than currentTime $currentTime");
                    nextEventAlarm = currentTime + Consts.MINUTE_IN_SECONDS * 5 * 1000L;
                }

                DevLog.info(LOG_TAG, "next alarm at ${nextEventAlarm} (T+${(nextEventAlarm - currentTime) / 1000L}s)");

                context.alarmManager.setExactAndAlarm(
                        context,
                        true,
                        nextEventAlarm,
                        SnoozeAlarmBroadcastReceiver::class.java, // ignored on KitKat and below
                        SnoozeExactAlarmBroadcastReceiver::class.java,
                        MainActivity::class.java)

                context.persistentState.nextSnoozeAlarmExpectedAt = nextEventAlarm

            }
            else { // if (nextEventAlarm != null) {

                DevLog.info(LOG_TAG, "Cancelling alarms (snooze and reminder)");

                context.alarmManager.cancelExactAndAlarm(
                        context,
                        SnoozeAlarmBroadcastReceiver::class.java,
                        SnoozeExactAlarmBroadcastReceiver::class.java)
            }

            val reminderState = ReminderState(context)

            // Schedule reminders alarm
            var reminderAlarmNextFire: Long? = null

            val activeAlarmEvents = events.filter {
                it.isNotSnoozed && it.isAlarm
            }

            if (activeAlarmEvents.any()) {
                reminderAlarmNextFire = System.currentTimeMillis() + Consts.ALARM_REMINDER_INTERVAL
                DevLog.info(LOG_TAG, "Reminder Alarm next fire: $reminderAlarmNextFire")
            }
            else {  // if (hasActiveNotifications)
                DevLog.info(LOG_TAG, "no active requests")
            }

            if (reminderAlarmNextFire != null) {
                context.alarmManager.setExactAndAlarm(
                        context,
                        true,
                        reminderAlarmNextFire,
                        ReminderAlarmBroadcastReceiver::class.java, // ignored on KitKat and below
                        ReminderExactAlarmBroadcastReceiver::class.java,
                        MainActivity::class.java)

                reminderState.nextFireExpectedAt = reminderAlarmNextFire

            }
            else {
                context.alarmManager.cancelExactAndAlarm(
                        context,
                        ReminderAlarmBroadcastReceiver::class.java,
                        ReminderExactAlarmBroadcastReceiver::class.java)
            }
        }
    }
}
