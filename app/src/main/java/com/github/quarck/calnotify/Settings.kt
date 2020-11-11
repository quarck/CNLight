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
import android.os.Build
import com.github.quarck.calnotify.prefs.PreferenceUtils
import com.github.quarck.calnotify.utils.PersistentStorageBase
import com.github.quarck.calnotify.utils.toIntOrNull


enum class NotificationSwipeBehavior(val code: Int)
{
    DismissEvent(0),
    SnoozeEvent(1),
    SwipeDisallowed(2);

    companion object {
        @JvmStatic
        fun fromInt(v: Int) = values()[v]
    }
}

//data class NotificationSettingsSnapshot
//(
//        val notificationSwipeBehavior: NotificationSwipeBehavior,
//        val groupNotificationSwipeBehavior: NotificationSwipeBehavior
//        // val postGroupNotification: Boolean, // false
//) {
//    val ongoingIndividual: Boolean
//        get() = notificationSwipeBehavior == NotificationSwipeBehavior.SwipeDisallowed
//
//    val ongoingGroup: Boolean
//        get() = groupNotificationSwipeBehavior == NotificationSwipeBehavior.SwipeDisallowed
//
//    val swipeSnoozeIndividual: Boolean
//        get() = notificationSwipeBehavior == NotificationSwipeBehavior.SnoozeEvent
//
//    val swipeSnoozeGroup: Boolean
//        get() = groupNotificationSwipeBehavior == NotificationSwipeBehavior.SnoozeEvent
//}


class Settings(context: Context) : PersistentStorageBase(context, "settings") {

    var devModeEnabled: Boolean
        get() = getBoolean(DEVELOPER_MODE_KEY, false)
        set(value) = setBoolean(DEVELOPER_MODE_KEY, value)

//    var notificationAddEmptyAction: Boolean
//        get() = getBoolean(NOTIFICATION_ADD_EMPTY_ACTION_KEY, false)
//        set(value) = setBoolean(NOTIFICATION_ADD_EMPTY_ACTION_KEY, value)

    val viewAfterEdit: Boolean = false

    val snoozePresetsRaw: String = DEFAULT_SNOOZE_PRESET

    val snoozePresets: LongArray
        get() {
            var ret = PreferenceUtils.parseSnoozePresets(snoozePresetsRaw)

            if (ret == null)
                ret = PreferenceUtils.parseSnoozePresets(DEFAULT_SNOOZE_PRESET)

            if (ret == null || ret.size == 0)
                ret = Consts.DEFAULT_SNOOZE_PRESETS

            return ret;
        }

    val firstNonNegativeSnoozeTime: Long
        get() {
            val result = snoozePresets.firstOrNull { snoozeTimeInMillis -> snoozeTimeInMillis >= 0 }
            return result ?: Consts.DEFAULT_SNOOZE_TIME
        }

//    var notificationSwipeDoesSnooze: Boolean
//        get() = getBoolean(NOTIFICATION_SWIPE_DOES_SNOOZE_KEY, false)
//        set(value) = setBoolean(NOTIFICATION_SWIPE_DOES_SNOOZE_KEY, value)

//    var notificationUseAlarmStream: Boolean
//        get() = getBoolean(USE_ALARM_STREAM_FOR_NOTIFICATION_KEY, false)
//        set(value) = setBoolean(USE_ALARM_STREAM_FOR_NOTIFICATION_KEY, value)

//    var remindersEnabled: Boolean
//        get() = getBoolean(ENABLE_REMINDERS_KEY, false)
//        set(value) = setBoolean(ENABLE_REMINDERS_KEY, value)
//
//    var remindersIntervalMillisPatternRaw
//        get() = getString(REMINDER_INTERVAL_PATTERN_KEY, "")
//        set(value) = setString(REMINDER_INTERVAL_PATTERN_KEY, value)
//
//    var remindersIntervalMillisPattern: LongArray
//        get() {
//            val raw = remindersIntervalMillisPatternRaw
//
//            val ret: LongArray?
//
//            if (!raw.isEmpty()) {
//                ret = PreferenceUtils.parseSnoozePresets(raw)
//            } else {
//                val intervalSeconds = getInt(REMIND_INTERVAL_SECONDS_KEY, 0)
//                if (intervalSeconds != 0) {
//                    ret = longArrayOf(intervalSeconds * 1000L)
//                }
//                else {
//                    val intervalMinutes = getInt(REMIND_INTERVAL_MINUTES_KEY, DEFAULT_REMINDER_INTERVAL_MINUTES)
//                    ret = longArrayOf(intervalMinutes * 60L * 1000L)
//                }
//            }
//
//            return ret ?: longArrayOf(DEFAULT_REMINDER_INTERVAL_SECONDS * 1000L)
//        }
//        set(value) {
//            remindersIntervalMillisPatternRaw = PreferenceUtils.formatPattern(value)
//        }
//
//    fun reminderIntervalMillisForIndex(index: Int): Long {
//        val pattern = remindersIntervalMillisPattern
//        val value = pattern[index % pattern.size]
//        return Math.max(value, Consts.MIN_REMINDER_INTERVAL_SECONDS * 1000L)
//    }
//
//    fun currentAndNextReminderIntervalsMillis(indexCurrent: Int): Pair<Long, Long> {
//        val pattern = remindersIntervalMillisPattern
//        val minInterval = Consts.MIN_REMINDER_INTERVAL_SECONDS * 1000L
//
//        val current = Math.max(pattern[indexCurrent % pattern.size], minInterval)
//        val next = Math.max(pattern[(indexCurrent + 1) % pattern.size], minInterval)
//
//        return Pair(current, next)
//    }
//
//    var maxNumberOfReminders: Int
//        get() = getString(MAX_REMINDERS_KEY, DEFAULT_MAX_REMINDERS).toIntOrNull() ?: 0
//        set(value) = setString(MAX_REMINDERS_KEY, "$value")
//
//    var quietHoursEnabled: Boolean
//        get() = getBoolean(ENABLE_QUIET_HOURS_KEY, false)
//        set(value) = setBoolean(ENABLE_QUIET_HOURS_KEY, value)
//
//    var quietHoursFrom: Pair<Int, Int>
//        get() = PreferenceUtils.unpackTime(getInt(QUIET_HOURS_FROM_KEY, 0))
//        set(value) = setInt(QUIET_HOURS_FROM_KEY, PreferenceUtils.packTime(value))
//
//    var quietHoursTo: Pair<Int, Int>
//        get() = PreferenceUtils.unpackTime(getInt(QUIET_HOURS_TO_KEY, 0))
//        set(value) = setInt(QUIET_HOURS_TO_KEY, PreferenceUtils.packTime(value))

    fun getCalendarIsHandled(calendarId: Long) =
            getBoolean("$CALENDAR_IS_HANDLED_KEY_PREFIX.$calendarId", true)

    fun setCalendarIsHandled(calendarId: Long, enabled: Boolean) =
            setBoolean("$CALENDAR_IS_HANDLED_KEY_PREFIX.$calendarId", enabled)

    var shouldRemindForEventsWithNoReminders: Boolean
        get() = getBoolean(SHOULD_REMIND_FOR_EVENTS_WITH_NO_REMINDERS_KEY, false)
        set(value) = setBoolean(SHOULD_REMIND_FOR_EVENTS_WITH_NO_REMINDERS_KEY, value)

    var defaultReminderTimeForEventWithNoReminderMinutes: Int
        get() = getInt(DEFAULT_REMINDER_TIME_FOR_EVENTS_WITH_NO_REMINDER_KEY, 15)
        set(value) = setInt(DEFAULT_REMINDER_TIME_FOR_EVENTS_WITH_NO_REMINDER_KEY, value)

    val defaultReminderTimeForEventWithNoReminderMillis: Long
        get() = defaultReminderTimeForEventWithNoReminderMinutes * 60L * 1000L

    var defaultReminderTimeForAllDayEventWithNoreminderMinutes: Int
        get() = getInt(DEFAULT_REMINDER_TIME_FOR_ALL_DAY_EVENTS_WITH_NO_REMINDER, -480)
        set(value) = setInt(DEFAULT_REMINDER_TIME_FOR_ALL_DAY_EVENTS_WITH_NO_REMINDER, value)

    val defaultReminderTimeForAllDayEventWithNoreminderMillis: Long
        get() = defaultReminderTimeForAllDayEventWithNoreminderMinutes * 60L * 1000L

    var enableMonitorDebug: Boolean
        get() = getBoolean(ENABLE_MONITOR_DEBUGGING_KEY, false)
        set(value) = setBoolean(ENABLE_MONITOR_DEBUGGING_KEY, value)

    var firstDayOfWeek: Int
        get() = getInt(FIRST_DAY_OF_WEEK_KEY, 1)
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

        private const val SHOULD_REMIND_FOR_EVENTS_WITH_NO_REMINDERS_KEY = "remind_events_no_rmdnrs"
        private const val DEFAULT_REMINDER_TIME_FOR_EVENTS_WITH_NO_REMINDER_KEY = "default_rminder_time"
        private const val DEFAULT_REMINDER_TIME_FOR_ALL_DAY_EVENTS_WITH_NO_REMINDER = "default_all_day_rminder_time"

        private const val ENABLE_MONITOR_DEBUGGING_KEY = "enableMonitorDebug"

        private const val FIRST_DAY_OF_WEEK_KEY = "first_day_of_week_2"

        private const val NOTIFY_ON_EMAIL_ONLY_EVENTS_KEY = "notify_on_email_only_events"

        private const val DEVELOPER_MODE_KEY = "dev"

        private const val ALWAYS_USE_EXTERNAL_EDITOR = "always_use_external_editor_0001"

        private const val DO_NOT_SHOW_BATTERY_OPTIMISATION = "dormi_mi_volas_"

        private const val NOTIFICATIONS_ALWAYS_COLLAPSED = "cxiam_kolapsita"

        private const val MANUAL_QUIET_PERIOD_UNTIL = "manual_quiet_until"

        // Default values
        internal const val DEFAULT_SNOOZE_PRESET = "15m ,1h, 4h, 8h, 1d, -5m"
    }
}
