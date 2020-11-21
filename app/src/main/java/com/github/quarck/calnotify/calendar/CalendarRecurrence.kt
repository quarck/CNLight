//
//   Calendar Notifications Plus
//   Copyright (C) 2020 Sergey Parshin (s.parshin.sc@gmail.com)
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

package com.github.quarck.calnotify.calendar

import android.content.Context

abstract class CalendarRecurrence(
        var firstInstance: Long,
        var calendarTimeZone: String?, // defaults to UTC if not given. Defines how to handle 'firstInstance'
        var interval: Int?,
        var count: Int?,
        var until: Long?
) {
    /**
     * @returns pair of rRule and rDate
     */
    abstract fun format(): Pair<String, String>

    abstract fun toLocalizedString(ctx: Context): String

    companion object {
        fun parse(rRule: String, rDate: String): CalendarRecurrence {
            TODO("Implement me")
        }
    }
}

class CalendarRecurrenceDaily(
        firstInstance: Long,
        calendarTimeZone: String?,
        interval: Int?,
        count: Int?,
        until: Long?
) : CalendarRecurrence(firstInstance, calendarTimeZone, interval, count, until) {
    override fun format(): Pair<String, String> {
        TODO("Not yet implemented")
    }

    override fun toLocalizedString(ctx: Context): String {
        TODO("Not yet implemented")
    }

    companion object {
        fun parse(rRule: Collection<String>, rDate: String): CalendarRecurrenceDaily {
            TODO("Implement me")
        }
    }
}

// weekDays and weekStart - Monday is 0, and up
class CalendarRecurrenceWeekly(
        var weekDays: List<Int>,
        var weekStart: Int,
        firstInstance: Long,
        calendarTimeZone: String?,
        interval: Int?,
        count: Int?,
        until: Long?
) : CalendarRecurrence(firstInstance, calendarTimeZone, interval, count, until) {
    override fun format(): Pair<String, String> {
        TODO("Not yet implemented")
    }

    override fun toLocalizedString(ctx: Context): String {
        TODO("Not yet implemented")
    }

    companion object {
        fun parse(rRule: Collection<String>, rDate: String): CalendarRecurrenceWeekly {
            TODO("Implement me")
        }
    }
}

class CalendarRecurrenceMonthly(
        var monthDay: Int?,
        var nthWeek: Int?,              // for events like
        var nthWeekDayOfWeek: Int?,     // 'every third Wednesday'
        firstInstance: Long,
        calendarTimeZone: String?,
        interval: Int?,
        count: Int?,
        until: Long?
) : CalendarRecurrence(firstInstance, calendarTimeZone, interval, count, until) {
    override fun format(): Pair<String, String> {
        TODO("Not yet implemented")
    }

    override fun toLocalizedString(ctx: Context): String {
        TODO("Not yet implemented")
    }

    companion object {
        fun parse(rRule: Collection<String>, rDate: String): CalendarRecurrenceMonthly {
            TODO("Implement me")
        }
    }
}

class CalendarRecurrenceAnnually(
        firstInstance: Long,
        calendarTimeZone: String?,
        interval: Int?,
        count: Int?,
        until: Long?
) : CalendarRecurrence(firstInstance, calendarTimeZone, interval, count, until) {
    override fun format(): Pair<String, String> {
        TODO("Not yet implemented")
    }

    override fun toLocalizedString(ctx: Context): String {
        TODO("Not yet implemented")
    }

    companion object {
        fun parse(rRule: Collection<String>, rDate: String): CalendarRecurrenceAnnually {
            TODO("Implement me")
        }
    }
}