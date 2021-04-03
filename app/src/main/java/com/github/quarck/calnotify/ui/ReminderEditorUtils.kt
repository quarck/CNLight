//
//   Calendar Notifications Plus
//   Copyright (C) 2018 Sergey Parshin (s.parshin.sc@gmail.com)
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

package com.github.quarck.calnotify.ui

import android.content.Context
import android.provider.CalendarContract
import android.text.format.DateUtils
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.calendar.EventReminderRecord
import com.github.quarck.calnotify.utils.DateTimeUtils
import com.github.quarck.calnotify.utils.textutils.EventFormatter
import com.github.quarck.calnotify.R

object ReminderEditorUtils {
    @JvmStatic
    fun getAllDayReminderDaysBefore(millisecondsBefore: Long): Int =
            ((millisecondsBefore + Consts.DAY_IN_MILLISECONDS) / Consts.DAY_IN_MILLISECONDS).toInt()

    @JvmStatic
    fun getAllDayReminderHourOfDayAndMinute(millisecondsBefore: Long): Pair<Int, Int> {
        val timeOfDayMillis =
                if (millisecondsBefore >= 0L) { // on the day of event
                    Consts.DAY_IN_MILLISECONDS - millisecondsBefore % Consts.DAY_IN_MILLISECONDS
                } else {
                    -millisecondsBefore
                }

        val timeOfDayMinutes = timeOfDayMillis.toInt() / 1000 / 60

        val minute = timeOfDayMinutes % 60
        val hourOfDay = timeOfDayMinutes / 60

        return Pair(hourOfDay, minute)
    }

    @JvmStatic
    fun eventReminderMillisToLocalizedString(ctx: Context, isAllDay: Boolean, millisecondsBefore: Long, method: Int): String {
        val ret = StringBuilder()

        if (!isAllDay) {
            val duration = EventFormatter(ctx).formatTimeDuration(millisecondsBefore, 60L)

            ret.append(
                    ctx.resources.getString(R.string.add_event_fmt_before).format(duration)
            )
        } else {
            val fullDaysBefore = getAllDayReminderDaysBefore(millisecondsBefore)
            val (hr, min) = getAllDayReminderHourOfDayAndMinute(millisecondsBefore)

            val cal = DateTimeUtils.createCalendarTime(System.currentTimeMillis(), hr, min)

            val time = DateUtils.formatDateTime(ctx, cal.timeInMillis, DateUtils.FORMAT_SHOW_TIME)

            when (fullDaysBefore) {
                0 ->
                    ret.append(
                            ctx.resources.getString(R.string.add_event_zero_days_before).format(time)
                    )
                1 ->
                    ret.append(
                            ctx.resources.getString(R.string.add_event_one_day_before).format(time)
                    )
                else ->
                    ret.append(
                            ctx.resources.getString(R.string.add_event_n_days_before).format(fullDaysBefore, time)
                    )
            }
        }

        when (method) {
            CalendarContract.Reminders.METHOD_EMAIL -> {
                ret.append(" ")
                ret.append(ctx.resources.getString(R.string.add_event_as_email_suffix))
            }
            CalendarContract.Reminders.METHOD_SMS -> {
                ret.append(" ")
                ret.append(ctx.resources.getString(R.string.add_event_as_sms_suffix))
            }
            CalendarContract.Reminders.METHOD_ALARM -> {
                ret.append(" ")
                ret.append(ctx.resources.getString(R.string.add_event_as_alarm_suffix))
            }
        }

        return ret.toString()
    }

    @JvmStatic
    fun eventReminderMinutesToLocalizedString(ctx: Context, isAllDay: Boolean, minutesBefore: Int, method: Int): String {
        return eventReminderMillisToLocalizedString(ctx, isAllDay, minutesBefore * Consts.MINUTE_IN_MILLISECONDS, method)
    }

}

fun EventReminderRecord.toLocalizedString(ctx: Context, isAllDay: Boolean): String =
    ReminderEditorUtils.eventReminderMillisToLocalizedString(ctx, isAllDay, this.millisecondsBefore, this.method)

