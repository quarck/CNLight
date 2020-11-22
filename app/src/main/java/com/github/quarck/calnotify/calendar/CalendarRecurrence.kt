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
import java.util.*

sealed class CalendarRecurrence(
        var firstInstance: Long,
        var eventTimeZone: String,
        var interval: Int,
        var count: Int?,
        var until: Long?,
        var weekStart: WeekDay?
) {
    abstract fun serialize(): RRule

    override fun toString(): String =
            serialize().toString(java.util.TimeZone.getTimeZone(eventTimeZone).getOffset(firstInstance))

    class CalendarRecurrenceDaily(
            firstInstance: Long,
            eventTimeZone: String,
            interval: Int,
            count: Int?,
            until: Long?,
            weekStart: WeekDay?
    ) : CalendarRecurrence(firstInstance, eventTimeZone, interval, count, until, weekStart) {
        override fun serialize(): RRule {
            return RRule(
                    freq = RRuleVal.FREQ(FreqType.DAILY),
                    interval = if (interval != 1) RRuleVal.INTERVAL(interval) else null,
                    count = count?.let { RRuleVal.COUNT(it) },
                    until = until?.let { RRuleVal.UNTIL(it) },
                    wkst = RRuleVal.WKST(weekStart ?: WeekDay.MO )
            )
        }

        companion object {
            fun createDefaultForDate(firstInstance: Long, eventTimeZone: String): CalendarRecurrenceDaily {
                TODO("Implement me")
            }
        }
    }

    class CalendarRecurrenceWeekly(
            firstInstance: Long,
            eventTimeZone: String,
            interval: Int,
            count: Int?,
            until: Long?,
            weekStart: WeekDay?,
            var weekDays: List<WeekDay>? // if not given - determined by the weekday of the instance start
    ) : CalendarRecurrence(firstInstance, eventTimeZone, interval, count, until, weekStart) {

        override fun serialize(): RRule {
            return RRule(
                    freq = RRuleVal.FREQ(FreqType.WEEKLY),
                    interval = if (interval != 1) RRuleVal.INTERVAL(interval) else null,
                    count = count?.let { RRuleVal.COUNT(it) },
                    until = until?.let { RRuleVal.UNTIL(it) },
                    byDay = weekDays?.let { RRuleVal.BYDAY(it.map { NthWeekDay(it) }.toList()) },
                    wkst = RRuleVal.WKST(weekStart ?: WeekDay.MO )
            )
        }

        companion object {
            fun createDefaultForDate(firstInstance: Long, eventTimeZone: String): CalendarRecurrenceWeekly {
                TODO("Implement me")
            }
        }
    }

    class CalendarRecurrenceMonthlyByNthWeekDay(
            firstInstance: Long,
            eventTimeZone: String,
            interval: Int,
            count: Int?,
            until: Long?,
            weekStart: WeekDay?,
            var weekDay: WeekDay,
            var weekDayNum: Int // here 1 means 1st
    ) : CalendarRecurrence(firstInstance, eventTimeZone, interval, count, until, weekStart) {

        override fun serialize(): RRule {
            return RRule(
                    freq = RRuleVal.FREQ(FreqType.WEEKLY),
                    interval = if (interval != 1) RRuleVal.INTERVAL(interval) else null,
                    count = count?.let { RRuleVal.COUNT(it) },
                    until = until?.let { RRuleVal.UNTIL(it) },
                    byDay = RRuleVal.BYDAY(listOf(NthWeekDay(weekDay, weekDayNum))),
                    wkst = RRuleVal.WKST(weekStart ?: WeekDay.MO )
            )
        }

        companion object {
            fun createDefaultForDate(firstInstance: Long, eventTimeZone: String): CalendarRecurrenceMonthlyByNthWeekDay {
                TODO("Implement me")
            }
        }
    }

    class CalendarRecurrenceMonthlyByMonthDay(
            firstInstance: Long,
            eventTimeZone: String,
            interval: Int,
            count: Int?,
            until: Long?,
            weekStart: WeekDay?,
            var monthDay: Int
    ) : CalendarRecurrence(firstInstance, eventTimeZone, interval, count, until, weekStart) {

        override fun serialize(): RRule {
            return RRule(
                    freq = RRuleVal.FREQ(FreqType.WEEKLY),
                    interval = if (interval != 1) RRuleVal.INTERVAL(interval) else null,
                    count = count?.let { RRuleVal.COUNT(it) },
                    until = until?.let { RRuleVal.UNTIL(it) },
                    byMonthDay = RRuleVal.BYMONTHDAY(listOf(monthDay)),
                    wkst = RRuleVal.WKST(weekStart ?: WeekDay.MO )
            )
        }

        companion object {
            fun createDefaultForDate(firstInstance: Long, eventTimeZone: String): CalendarRecurrenceMonthlyByMonthDay {
                TODO("Implement me")
            }
        }
    }

    class CalendarRecurrenceYearly(
            firstInstance: Long,
            eventTimeZone: String,
            interval: Int,
            count: Int?,
            until: Long?,
            weekStart: WeekDay?,
            var month: Int,
            var monthDay: Int
    ) : CalendarRecurrence(firstInstance, eventTimeZone, interval, count, until, weekStart) {

        override fun serialize(): RRule {
            return RRule(
                    freq = RRuleVal.FREQ(FreqType.WEEKLY),
                    interval = if (interval != 1) RRuleVal.INTERVAL(interval) else null,
                    count = count?.let { RRuleVal.COUNT(it) },
                    until = until?.let { RRuleVal.UNTIL(it) },
                    byMonth = RRuleVal.BYMONTH(listOf(month)),
                    byMonthDay = RRuleVal.BYMONTHDAY(listOf(monthDay)),
                    wkst = RRuleVal.WKST(weekStart ?: WeekDay.MO )
            )
        }

        companion object {
            fun createDefaultForDate(firstInstance: Long, eventTimeZone: String): CalendarRecurrenceYearly {
                TODO("Implement me")
            }
        }
    }

    companion object {
        fun interpretRecurrence(
                instanceStart: Long,
                eventTimeZone: String,
                rRule: String,
                rDate: String,
                exRRule: String,
                exRDate: String,
        ): CalendarRecurrence {
            if (rDate.isNotEmpty())
                throw Exception("non-empty RDATE is not supported")
            if (exRRule.isNotEmpty())
                throw Exception("Non-empty EXRRULE is not supported")
            if (exRDate.isNotEmpty())
                throw Exception("Non-empty EXRDATE is not supported")

            val rrule = RRule.parse(rRule)
            if (rrule.isEmpty())
                throw Exception("RRULE is empty")

            val rruleVal = rrule.freq?.value ?: throw Exception("RRULE has no FREQ tag")

            return when (rruleVal) {
                FreqType.DAILY -> interpretDailyRecurrence(
                        instanceStart,
                        eventTimeZone,
                        rrule
                )
                FreqType.WEEKLY -> interpretWeeklyRecurrence(
                        instanceStart,
                        eventTimeZone,
                        rrule
                )
                FreqType.MONTHLY -> interpretMonthlyRecurrence(
                        instanceStart,
                        eventTimeZone,
                        rrule
                )
                FreqType.YEARLY -> interpretYearlyRecurrence(
                        instanceStart,
                        eventTimeZone,
                        rrule
                )
            }
        }

        fun tryInterpretRecurrence(
                instanceStart: Long,
                eventTimeZone: String,
                rRule: String,
                rDate: String,
                exRRule: String,
                exRDate: String,
        ): CalendarRecurrence? {
            try {
                return interpretRecurrence(instanceStart, eventTimeZone, rRule, rDate, exRRule, exRDate)
            }
            catch (ex:Exception) {
            }
            return null
        }

        private fun interpretDailyRecurrence(instanceStart: Long, eventTimeZone: String, rrule: RRule): CalendarRecurrence {
            if (rrule.byDay != null)
                throw Exception("BYDAY is not supported for DAILY recurrent events")
            if (rrule.byMonth != null)
                throw Exception("BYMONTH is not supported for DAILY recurrent events")
            if (rrule.byMonthDay != null)
                throw Exception("BYMONTHDAY is not supported for DAILY recurrent events")
            if (rrule.byYearDay != null)
                throw Exception("BYYEARDAY is not supported for DAILY recurrent events")
            if (rrule.byWeekNo != null)
                throw Exception("BYWEEKNO is not supported for DAILY recurrent events")
            if (rrule.bySetPos != null)
                throw Exception("BYSETPOS is not supported for DAILY recurrent events")

            return CalendarRecurrenceDaily(
                    firstInstance = instanceStart,
                    eventTimeZone = eventTimeZone,
                    interval = rrule.interval?.value ?: 1,
                    count = rrule.count?.value,
                    until = rrule.until?.value,
                    weekStart = rrule.wkst?.value
                )
        }

        private fun interpretWeeklyRecurrence(instanceStart: Long, eventTimeZone: String, rrule: RRule): CalendarRecurrence {
            if (rrule.byMonth != null)
                throw Exception("BYMONTH is not supported for WEEKLY recurrent events")
            if (rrule.byMonthDay != null)
                throw Exception("BYMONTHDAY is not supported for WEEKLY recurrent events")
            if (rrule.byYearDay != null)
                throw Exception("BYYEARDAY is not supported for WEEKLY recurrent events")
            if (rrule.byWeekNo != null)
                throw Exception("BYWEEKNO is not supported for WEEKLY recurrent events")
            if (rrule.bySetPos != null)
                throw Exception("BYSETPOS is not supported for WEEKLY recurrent events")

            val weekDays =
                    rrule.byDay?.let {
                        byday ->
                        byday.value.map{
                            if (it.n != null)
                                throw Exception("BYDAY value ${it.serialize()} is not supported")
                            it.weekDay
                        }
                }

            return CalendarRecurrenceWeekly(
                    firstInstance = instanceStart,
                    eventTimeZone = eventTimeZone,
                    interval = rrule.interval?.value ?: 1,
                    count = rrule.count?.value,
                    until = rrule.until?.value,
                    weekStart = rrule.wkst?.value,
                    weekDays = weekDays
                )
        }

        private fun interpretMonthlyRecurrence(instanceStart: Long, eventTimeZone: String, rrule: RRule): CalendarRecurrence {
            if (rrule.byMonth != null)
                throw Exception("BYMONTH is not supported for MONTHLY recurrent events")
            if (rrule.byYearDay != null)
                throw Exception("BYYEARDAY is not supported for MONTHLY recurrent events")
            if (rrule.byWeekNo != null)
                throw Exception("BYWEEKNO is not supported for MONTHLY recurrent events")
            if (rrule.bySetPos != null)
                throw Exception("BYSETPOS is not supported for MONTHLY recurrent events")

            val weekDays = rrule.byDay?.value
            val monthDays = rrule.byMonthDay?.value

            if (weekDays != null && monthDays != null)
                throw Exception("Setting both BYDAY and BYMONTHDAY is not supported for monthly events")

            weekDays?.let {
                if (it.count() != 1)
                    throw Exception("BYDAY must contain exactly one element for monthly events")
                return CalendarRecurrenceMonthlyByNthWeekDay(
                        firstInstance = instanceStart,
                        eventTimeZone = eventTimeZone,
                        interval = rrule.interval?.value ?: 1,
                        count = rrule.count?.value,
                        until = rrule.until?.value,
                        weekStart = rrule.wkst?.value,
                        weekDay = it.first().weekDay,
                        weekDayNum = it.first().n ?: 1
                )
            }

            monthDays?.let {
                if (it.count() != 1)
                    throw Exception("BYMONTHDAY must contain exactly one element for monthly events")
                return CalendarRecurrenceMonthlyByMonthDay(
                        firstInstance = instanceStart,
                        eventTimeZone = eventTimeZone,
                        interval = rrule.interval?.value ?: 1,
                        count = rrule.count?.value,
                        until = rrule.until?.value,
                        weekStart = rrule.wkst?.value,
                        monthDay = it.first()
                )
            }

            throw Exception("At least one of BYDAY or BYMONTHDAY must be provided")
        }

        private fun interpretYearlyRecurrence(instanceStart: Long, eventTimeZone: String, rrule: RRule): CalendarRecurrence {
            if (rrule.byDay != null)
                throw Exception("BYDAY is not supported for YEARLY recurrent events")
            if (rrule.byYearDay != null)
                throw Exception("BYYEARDAY is not supported for YEARLY recurrent events")
            if (rrule.byWeekNo != null)
                throw Exception("BYWEEKNO is not supported for YEARLY recurrent events")
            if (rrule.bySetPos != null)
                throw Exception("BYSETPOS is not supported for YEARLY recurrent events")

            val month = rrule.byMonth?.value ?: throw Exception("BYMONTH must be giveg for YEARLY events")
            val monthDay = rrule.byMonthDay?.value ?: throw Exception("BYMONTHDAY must be given YEARLY events")

            if (month.count() != 1)
                throw Exception("BYMONTH must contain exactly one month for YEARLY events")
            if (monthDay.count() != 1)
                throw Exception("BYMONTHDAY must contain exactly one month for YEARLY events")

            return CalendarRecurrenceYearly(
                    firstInstance = instanceStart,
                    eventTimeZone = eventTimeZone,
                    interval = rrule.interval?.value ?: 1,
                    count = rrule.count?.value,
                    until = rrule.until?.value,
                    weekStart = rrule.wkst?.value,
                    month = month.first(),
                    monthDay = monthDay.first()
            )
        }
    }
}
