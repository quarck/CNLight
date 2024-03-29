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

package com.github.quarck.calnotify

object Consts {
    const val DEV_MODE_ENABLED = true

    object JobIDS {
        const val CALENDAR_RESCAN = 0
        const val CALENDAR_RESCAN_ONCE = 1
    }

    const val DATA_UPDATED_BROADCAST = BuildConfig.APPLICATION_ID + ".dataupdated"

    const val ALARM_REMINDER_INTERVAL: Long = 45L * 1000L

    const val DAY_IN_MILLISECONDS = 24L * 3600L * 1000L
    const val DAY_IN_SECONDS: Long = 3600L * 24
    const val DAY_IN_MINUTES: Int = 60*24
    const val HOUR_IN_SECONDS: Long = 3600L
    const val HOUR_IN_MILLISECONDS: Long = 3600L * 1000L
    const val MINUTE_IN_SECONDS: Long = 60L
    const val MINUTE_IN_MILLISECONDS: Long = 60L * 1000L

    const val NOTIFICATION_ID_COLLAPSED = 0
    const val NOTIFICATION_ID_DYNAMIC_FROM = 1

    //
    const val INTENT_NOTIFICATION_ID_KEY = "notificationId"
    const val INTENT_EVENT_ID_KEY = "eventId"
    const val INTENT_INSTANCE_START_TIME_KEY = "instanceStartTime"
    const val INTENT_ALERT_TIME = "alert_time"
    const val INTENT_SNOOZE_ALL_IS_CHANGE = "snooze_all_is_change"

    const val INTENT_IS_USER_ACTION = "causedByUser"

    // max number of notifications displayed on the screen at all the times
    const val MAX_NOTIFICATIONS = 6

    val VIBRATION_PATTERN_DEFAULT = longArrayOf(0,
            // on off
            60, 60,
            60, 60,
            60, 60,
            60, 200,
            1200
    )

    val VIBRATION_PATTERN_ALARM = longArrayOf(0,
            // on off time
            240, 240,
            240, 240,
            240, 400,
            1200, 2000,

            240, 240,
            240, 240,
            240, 400,
            1200, 2000,

            240, 240,
            240, 240,
            240, 400,
            1200,
    )

    const val DEFAULT_LED_COLOR = 0x7f0000ff

    const val ALARM_THRESHOLD = 24 * 1000L // multiple of both, 2 and 3

    val DEFAULT_SNOOZE_PRESETS = longArrayOf(
            15 * MINUTE_IN_MILLISECONDS,
            1 * HOUR_IN_MILLISECONDS,
            4 * HOUR_IN_MILLISECONDS,
            8 * HOUR_IN_MILLISECONDS,
            24 * HOUR_IN_MILLISECONDS,
            -5 * MINUTE_IN_MILLISECONDS)

    const val DEFAULT_CALENDAR_EVENT_COLOR = 0xff0000ff.toInt()

    const val EVENT_MOVE_THRESHOLD = 15 * MINUTE_IN_MILLISECONDS

    const val MANUAL_SCAN_WINDOW: Long = 3L * 24L * HOUR_IN_MILLISECONDS
    const val UPCOMING_EVENTS_WINDOW: Long = 2L * 24L * HOUR_IN_MILLISECONDS
    const val ALERTS_DB_REMOVE_AFTER = 2L * MANUAL_SCAN_WINDOW

    const val CALENDAR_RESCAN_INTERVAL = 30L * MINUTE_IN_MILLISECONDS

    const val MAX_DUE_ALERTS_FOR_MANUAL_SCAN = 512
    const val MAX_SCAN_BACKWARD_DAYS = 31L

    const val FAILBACK_SHORT_SNOOZE = 1 * MINUTE_IN_MILLISECONDS

    const val MAX_USER_ACTION_DELAY: Long = 3500L

    const val NEW_EVENT_DEFAULT_NEW_EVENT_REMINDER: Long = 15 * MINUTE_IN_MILLISECONDS
    const val NEW_EVENT_DEFAULT_ALL_DAY_REMINDER: Long = 6 * HOUR_IN_MILLISECONDS // 18:00 on the day before
    const val NEW_EVENT_DEFAULT_ADD_HOURS: Int = 0
    const val NEW_EVENT_MAX_ALL_DAY_REMINDER_DAYS_BEFORE: Int = 28
    const val NEW_EVENT_MAX_REMINDER_MILLISECONDS_BEFORE: Long = 28L * DAY_IN_MILLISECONDS

    const val DEFAULT_NEW_EVENT_DURATION_MINUTES: Int = 30

    private const val BIN_KEEP_HISTORY_DAYS = 7L
    const val BIN_KEEP_HISTORY_MILLISECONDS = BIN_KEEP_HISTORY_DAYS * DAY_IN_MILLISECONDS
}
