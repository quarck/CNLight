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

import android.content.Context
import com.github.quarck.calnotify.utils.PersistentStorageBase
import java.util.*

class Settings(context: Context) : PersistentStorageBase(context, "settings") {

    fun getCalendarIsHandled(calendarId: Long): Boolean
            = getBoolean("$CALENDAR_IS_HANDLED_KEY_PREFIX.$calendarId", true)

    fun setCalendarIsHandled(calendarId: Long, enabled: Boolean)
            = setBoolean("$CALENDAR_IS_HANDLED_KEY_PREFIX.$calendarId", enabled)

    var handleEventsWithNoReminders: Boolean
        get() = getBoolean(HANDLE_EVENTS_WITH_NO_REMINDERS, false)
        set(value) = setBoolean(HANDLE_EVENTS_WITH_NO_REMINDERS, value)

    var defaultReminderTimeMinutes: Int
        get() = getInt(DEFAULT_REMINDER_TIME, 15)
        set(value) = setInt(DEFAULT_REMINDER_TIME, value)

    val defaultReminderTime: Long
        get() = defaultReminderTimeMinutes * 60L * 1000L

    var defaultAllDayReminderTimeMinutes: Int
        get() = getInt(DEFAULT_REMINDER_TIME_ALL_DAY, -480)
        set(value) = setInt(DEFAULT_REMINDER_TIME_ALL_DAY, value)

    val defaultAllDayReminderTime: Long
        get() = defaultAllDayReminderTimeMinutes * 60L * 1000L

    // in the API format - 1 - Sun, 2 - Mon, etc.. crazy
    var firstDayOfWeek: Int
        get() = getInt(FIRST_DAY_OF_WEEK_KEY, Calendar.MONDAY)
        set(value) = setInt(FIRST_DAY_OF_WEEK_KEY, value)

    var notifyOnEmailOnlyEvents: Boolean
        get() = getBoolean(NOTIFY_ON_EMAIL_ONLY_EVENTS_KEY, false)
        set(value) = setBoolean(NOTIFY_ON_EMAIL_ONLY_EVENTS_KEY, value)

    var doNotShowBatteryOptimisationWarning: Boolean
        get() = getBoolean(DO_NOT_SHOW_BATTERY_OPTIMISATION, false)
        set(value) = setBoolean(DO_NOT_SHOW_BATTERY_OPTIMISATION, value)

    companion object {
        // Preferences keys
        private const val CALENDAR_IS_HANDLED_KEY_PREFIX = "calendar_handled_"
        private const val HANDLE_EVENTS_WITH_NO_REMINDERS = "failback_reminder"
        private const val DEFAULT_REMINDER_TIME = "failback_reminder_time"
        private const val DEFAULT_REMINDER_TIME_ALL_DAY = "failback_reminder_time_all_day"
        private const val FIRST_DAY_OF_WEEK_KEY = "first_day_of_week"
        private const val NOTIFY_ON_EMAIL_ONLY_EVENTS_KEY = "notify_on_email_only_events"
        private const val DO_NOT_SHOW_BATTERY_OPTIMISATION = "dormi_mi_ne_volas"
    }
}
