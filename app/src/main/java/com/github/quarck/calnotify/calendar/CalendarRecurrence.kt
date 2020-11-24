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

import java.util.*

sealed class CalendarRecurrenceLimit {
    abstract fun serializeInto(rrule: RRule)

    class NoLimit : CalendarRecurrenceLimit() {
        override fun serializeInto(rrule: RRule) {
            rrule.count = null
            rrule.until = null
        }
    }

    class Count(var count: Int): CalendarRecurrenceLimit() {
        override fun serializeInto(rrule: RRule) {
            rrule.count = RRuleVal.COUNT(count)
            rrule.until = null
        }
    }

    class Until(var until: Long): CalendarRecurrenceLimit() {
        override fun serializeInto(rrule: RRule) {
            rrule.until = RRuleVal.UNTIL(until)
            rrule.count = null
        }
    }

    companion object {
        fun interpretRRuleLimits(rrule: RRule): CalendarRecurrenceLimit {

            val count = rrule.count
            val until = rrule.until

            if (count != null && until != null)
                throw Exception("Setting both COUNT and UNTIL is not supported, rrule: ${rrule.serialize()}")

            if (count != null)
                return Count(count.value)

            if (until != null)
                return Until(until.value)

            return NoLimit()
        }
    }
}

sealed class CalendarRecurrence(
        var firstInstance: Long,
        var eventTimeZone: String,
        var interval: Int,
        var limit: CalendarRecurrenceLimit,
        var weekStart: WeekDay?
) {
    abstract fun serialize(): RRule

    fun setCount(v: Int) {
        limit = CalendarRecurrenceLimit.Count(v)
    }

    fun setUntil(v: Long) {
        limit = CalendarRecurrenceLimit.Until(v)
    }

    override fun toString(): String =
            serialize().toString(java.util.TimeZone.getTimeZone(eventTimeZone).getOffset(firstInstance))

    class Daily(
            firstInstance: Long,
            eventTimeZone: String,
            interval: Int,
            limit: CalendarRecurrenceLimit,
            weekStart: WeekDay?
    ) : CalendarRecurrence(firstInstance, eventTimeZone, interval, limit, weekStart) {
        override fun serialize(): RRule {
            return RRule(
                    freq = RRuleVal.FREQ(FreqType.DAILY),
                    interval = if (interval != 1) RRuleVal.INTERVAL(interval) else null,
                    wkst = RRuleVal.WKST(weekStart ?: WeekDay.MO )
            ).apply {
                limit.serializeInto(this)
            }
        }

        companion object {
            fun createDefaultForDate(firstInstance: Long, eventTimeZone: String, weekStart: WeekDay): Daily {
                return Daily(
                        firstInstance,
                        eventTimeZone,
                        interval = 1,
                        limit = CalendarRecurrenceLimit.NoLimit(),
                        weekStart = weekStart
                )
            }
        }
    }

    class Weekly(
            firstInstance: Long,
            eventTimeZone: String,
            interval: Int,
            limit: CalendarRecurrenceLimit,
            weekStart: WeekDay?,
            var weekDays: List<WeekDay>? // if not given - determined by the weekday of the instance start
    ) : CalendarRecurrence(firstInstance, eventTimeZone, interval, limit, weekStart) {

        override fun serialize(): RRule {
            return RRule(
                    freq = RRuleVal.FREQ(FreqType.WEEKLY),
                    interval = if (interval != 1) RRuleVal.INTERVAL(interval) else null,
                    byDay = weekDays?.let { RRuleVal.BYDAY(it.map { NthWeekDay(it) }.toList()) },
                    wkst = RRuleVal.WKST(weekStart ?: WeekDay.MO )
            ).apply {
                limit.serializeInto(this)
            }
        }

        companion object {
            fun createDefaultForDate(firstInstance: Long, eventTimeZone: String, weekStart: WeekDay): Weekly {
                return Weekly(
                        firstInstance,
                        eventTimeZone,
                        interval = 1,
                        limit = CalendarRecurrenceLimit.NoLimit(),
                        weekStart = weekStart,
                        weekDays = null
                )
            }
        }
    }

    class MonthlyByWeekDay(
            firstInstance: Long,
            eventTimeZone: String,
            interval: Int,
            limit: CalendarRecurrenceLimit,
            weekStart: WeekDay?,
            var weekDay: WeekDay,
            var weekDayNum: Int // here 1 means 1st
    ) : CalendarRecurrence(firstInstance, eventTimeZone, interval, limit, weekStart) {

        override fun serialize(): RRule {
            return RRule(
                    freq = RRuleVal.FREQ(FreqType.MONTHLY),
                    interval = if (interval != 1) RRuleVal.INTERVAL(interval) else null,
                    byDay = RRuleVal.BYDAY(listOf(NthWeekDay(weekDay, weekDayNum))),
                    wkst = RRuleVal.WKST(weekStart ?: WeekDay.MO )
            ).apply {
                limit.serializeInto(this)
            }
        }

        companion object {
            fun createDefaultForDate(firstInstance: Long, eventTimeZone: String, weekStart: WeekDay): MonthlyByWeekDay {
                val timeZone = TimeZone.getTimeZone(eventTimeZone)
                val cal = Calendar.getInstance(timeZone)
                cal.timeInMillis = firstInstance
                cal.firstDayOfWeek = weekStart.javaCalendarDayOfWeek
                return MonthlyByWeekDay(
                        firstInstance,
                        eventTimeZone,
                        interval = 1,
                        limit = CalendarRecurrenceLimit.NoLimit(),
                        weekStart = weekStart,
                        weekDay = WeekDay.fromJavaCalendarDayOfWeek(cal.get(Calendar.DAY_OF_WEEK)),
                        weekDayNum = cal.get(Calendar.DAY_OF_WEEK_IN_MONTH)
                )
            }
        }
    }

    class Monthly(
            firstInstance: Long,
            eventTimeZone: String,
            interval: Int,
            limit: CalendarRecurrenceLimit,
            weekStart: WeekDay?,
            var monthDay: Int
    ) : CalendarRecurrence(firstInstance, eventTimeZone, interval, limit, weekStart) {

        override fun serialize(): RRule {
            return RRule(
                    freq = RRuleVal.FREQ(FreqType.MONTHLY),
                    interval = if (interval != 1) RRuleVal.INTERVAL(interval) else null,
                    byMonthDay = RRuleVal.BYMONTHDAY(listOf(monthDay)),
                    wkst = RRuleVal.WKST(weekStart ?: WeekDay.MO )
            ).apply {
                limit.serializeInto(this)
            }
        }

        companion object {
            fun createDefaultForDate(firstInstance: Long, eventTimeZone: String, weekStart: WeekDay): Monthly {
                val timeZone = TimeZone.getTimeZone(eventTimeZone)
                val cal = Calendar.getInstance(timeZone)
                cal.timeInMillis = firstInstance
                cal.firstDayOfWeek = weekStart.javaCalendarDayOfWeek
                return Monthly(
                        firstInstance,
                        eventTimeZone,
                        interval = 1,
                        limit = CalendarRecurrenceLimit.NoLimit(),
                        weekStart = weekStart,
                        monthDay = cal.get(Calendar.DAY_OF_MONTH)
                )
            }
        }
    }

    class Yearly(
            firstInstance: Long,
            eventTimeZone: String,
            interval: Int,
            limit: CalendarRecurrenceLimit,
            weekStart: WeekDay?,
            var month: Int,
            var monthDay: Int
    ) : CalendarRecurrence(firstInstance, eventTimeZone, interval, limit, weekStart) {

        override fun serialize(): RRule {
            return RRule(
                    freq = RRuleVal.FREQ(FreqType.YEARLY),
                    interval = if (interval != 1) RRuleVal.INTERVAL(interval) else null,
                    byMonth = RRuleVal.BYMONTH(listOf(month)),
                    byMonthDay = RRuleVal.BYMONTHDAY(listOf(monthDay)),
                    wkst = RRuleVal.WKST(weekStart ?: WeekDay.MO )
            ).apply {
                limit.serializeInto(this)
            }
        }

        companion object {
            fun createDefaultForDate(firstInstance: Long, eventTimeZone: String, weekStart: WeekDay): Yearly {
                val timeZone = TimeZone.getTimeZone(eventTimeZone)
                val cal = Calendar.getInstance(timeZone)
                cal.timeInMillis = firstInstance
                cal.firstDayOfWeek = weekStart.javaCalendarDayOfWeek
                return Yearly(
                        firstInstance,
                        eventTimeZone,
                        interval = 1,
                        limit = CalendarRecurrenceLimit.NoLimit(),
                        weekStart = weekStart,
                        month = cal.get(Calendar.MONTH) + 1,
                        monthDay = cal.get(Calendar.DAY_OF_MONTH)
                )
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

            return Daily(
                    firstInstance = instanceStart,
                    eventTimeZone = eventTimeZone,
                    interval = rrule.interval?.value ?: 1,
                    limit = CalendarRecurrenceLimit.interpretRRuleLimits(rrule),
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

            return Weekly(
                    firstInstance = instanceStart,
                    eventTimeZone = eventTimeZone,
                    interval = rrule.interval?.value ?: 1,
                    limit = CalendarRecurrenceLimit.interpretRRuleLimits(rrule),
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
                return MonthlyByWeekDay(
                        firstInstance = instanceStart,
                        eventTimeZone = eventTimeZone,
                        interval = rrule.interval?.value ?: 1,
                        limit = CalendarRecurrenceLimit.interpretRRuleLimits(rrule),
                        weekStart = rrule.wkst?.value,
                        weekDay = it.first().weekDay,
                        weekDayNum = it.first().n ?: 1
                )
            }

            monthDays?.let {
                if (it.count() != 1)
                    throw Exception("BYMONTHDAY must contain exactly one element for monthly events")
                return Monthly(
                        firstInstance = instanceStart,
                        eventTimeZone = eventTimeZone,
                        interval = rrule.interval?.value ?: 1,
                        limit = CalendarRecurrenceLimit.interpretRRuleLimits(rrule),
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

            return Yearly(
                    firstInstance = instanceStart,
                    eventTimeZone = eventTimeZone,
                    interval = rrule.interval?.value ?: 1,
                    limit = CalendarRecurrenceLimit.interpretRRuleLimits(rrule),
                    weekStart = rrule.wkst?.value,
                    month = month.first(),
                    monthDay = monthDay.first()
            )
        }
    }
}
