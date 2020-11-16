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

package com.github.quarck.calnotify.broadcastreceivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.globalState
import com.github.quarck.calnotify.utils.logs.DevLog
//import com.github.quarck.calnotify.utils.logs.Logger
import com.github.quarck.calnotify.persistentState
import com.github.quarck.calnotify.reminders.ReminderState
import com.github.quarck.calnotify.ui.MainActivityNG
import com.github.quarck.calnotify.utils.alarmManager
import com.github.quarck.calnotify.utils.powerManager
import com.github.quarck.calnotify.utils.setExactAndAlarm
import com.github.quarck.calnotify.utils.wakeLocked

open class ReminderAlarmGenericBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {

        DevLog.debug(LOG_TAG, "Alarm received")

        if (context == null || intent == null) {
            return;
        }

        context.globalState?.lastTimerBroadcastReceived = System.currentTimeMillis()

        if (!ApplicationController.hasActiveEventsToRemind(context)) {
            DevLog.info(LOG_TAG, "Reminder broadcast alarm received: no active requests")
            return
        }

        wakeLocked(context.powerManager, PowerManager.PARTIAL_WAKE_LOCK, REMINDER_WAKE_LOCK_NAME) {

            val reminderState = ReminderState(context)

            val currentTime = System.currentTimeMillis()

            val nextFireAt: Long
            var shouldFire = false

            val lastFireTime = Math.max(
                    context.persistentState.notificationLastFireTime,
                    reminderState.reminderLastFireTime)

            val sinceLastFire = currentTime - lastFireTime;

            DevLog.info(LOG_TAG, "Reminders are enabled, lastFire=$lastFireTime, sinceLastFire=$sinceLastFire")

            if (Consts.ALARM_REMINDER_INTERVAL - sinceLastFire > Consts.ALARM_THRESHOLD) {
                // Schedule actual time to fire based on how long ago we have fired
                val leftMillis = Consts.ALARM_REMINDER_INTERVAL - sinceLastFire;
                nextFireAt = currentTime + leftMillis

                DevLog.info(LOG_TAG, "Early alarm: since last: ${sinceLastFire}, interval[current]: ${Consts.ALARM_REMINDER_INTERVAL}, thr: ${Consts.ALARM_THRESHOLD}, left: ${leftMillis}, moving alarm to $nextFireAt");
            }
            else {
                nextFireAt = currentTime + Consts.ALARM_REMINDER_INTERVAL
                shouldFire = true

                DevLog.info(LOG_TAG, "Good to fire, since last: ${sinceLastFire}, interval[next]: ${Consts.ALARM_REMINDER_INTERVAL}, next fire expected at $nextFireAt")
            }


            if (nextFireAt != 0L) {
                context.alarmManager.setExactAndAlarm(
                        context,
                        true,
                        nextFireAt,
                        ReminderAlarmBroadcastReceiver::class.java, // ignored on KitKat and below
                        ReminderExactAlarmBroadcastReceiver::class.java,
                        MainActivityNG::class.java)

                reminderState.nextFireExpectedAt = nextFireAt
            }

            if (shouldFire) {
                fireReminder(
                        context = context,
                        currentTime = currentTime
                )
            }
        }
    }

    private fun fireReminder(
            context: Context,
            currentTime: Long
    ) {
        DevLog.info(LOG_TAG, "Firing reminder, current time ${System.currentTimeMillis()}")
        ApplicationController.fireEventReminder(context);
        ReminderState(context).onReminderFired(currentTime)
    }

    companion object {
        private const val LOG_TAG = "BroadcastReceiverReminderAlarm"
        private const val REMINDER_WAKE_LOCK_NAME = "ReminderWakeLock"
    }
}

open class ReminderAlarmBroadcastReceiver : ReminderAlarmGenericBroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) = super.onReceive(context, intent)
}

open class ReminderExactAlarmBroadcastReceiver : ReminderAlarmGenericBroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) = super.onReceive(context, intent)
}
