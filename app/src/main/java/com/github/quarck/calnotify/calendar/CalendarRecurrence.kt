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
    abstract fun copy(): CalendarRecurrenceLimit

    class NoLimit : CalendarRecurrenceLimit() {
        override fun serializeInto(rrule: RRule) {
            rrule.count = null
            rrule.until = null
        }

        override fun copy(): CalendarRecurrenceLimit {
            return NoLimit()
        }
    }

    class Count(var count: Int): CalendarRecurrenceLimit() {
        override fun serializeInto(rrule: RRule) {
            rrule.count = RRuleVal.COUNT(count)
            rrule.until = null
        }

        override fun copy(): CalendarRecurrenceLimit {
            return Count(count)
        }
    }

    class Until(var until: Long): CalendarRecurrenceLimit() {
        override fun serializeInto(rrule: RRule) {
            rrule.until = RRuleVal.UNTIL(until)
            rrule.count = null
        }

        override fun copy(): CalendarRecurrenceLimit {
            return Until(until)
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
    open fun serialize(): RRule {
        val ret = RRule()
        limit.serializeInto(ret)
        ret.interval = if (interval != 1) RRuleVal.INTERVAL(interval) else null
        ret.wkst = RRuleVal.WKST(weekStart ?: WeekDay.MO )
        return ret
    }

    fun setCount(v: Int) {
        limit = CalendarRecurrenceLimit.Count(v)
    }

    fun setUntil(v: Long) {
        limit = CalendarRecurrenceLimit.Until(v)
    }

    override fun toString(): String =
            serialize().toString(TimeZone.getTimeZone(eventTimeZone).getOffset(firstInstance))

    abstract fun copy(): CalendarRecurrence

    class Daily(
            firstInstance: Long,
            eventTimeZone: String,
            interval: Int,
            limit: CalendarRecurrenceLimit,
            weekStart: WeekDay?
    ) : CalendarRecurrence(firstInstance, eventTimeZone, interval, limit, weekStart) {

        override fun serialize(): RRule = super.serialize().apply {
            freq = RRuleVal.FREQ(FreqType.DAILY)
        }

        override fun copy(): CalendarRecurrence {
            return Daily(
                    firstInstance,
                    eventTimeZone,
                    interval,
                    limit.copy(),
                    weekStart
            )
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
            var weekDays: WeekDays? // if not given - determined by the weekday of the instance start
    ) : CalendarRecurrence(firstInstance, eventTimeZone, interval, limit, weekStart) {

        override fun serialize(): RRule = super.serialize().apply {
            freq = RRuleVal.FREQ(FreqType.WEEKLY)
            byDay = weekDays?.toRRule()
        }

        override fun copy(): CalendarRecurrence {
            return Weekly(
                    firstInstance,
                    eventTimeZone,
                    interval,
                    limit.copy(),
                    weekStart,
                    weekDays?.clone()
            )
        }

        fun generateStartTimeAdjust(): Long {
            val wkd = weekDays
            if (wkd == null || !wkd.any())
                return 0L

            val cal = Calendar.getInstance(TimeZone.getTimeZone(eventTimeZone))
            cal.timeInMillis = firstInstance

            for (max_try in 0 until 7) {
                val weekDay = WeekDay.fromJavaCalendarDayOfWeek(cal.get(Calendar.DAY_OF_WEEK))
                if (wkd[weekDay])
                    return cal.timeInMillis - firstInstance
                cal.timeInMillis += 24 * 3600 * 1000L
            }

            return 0L
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

            fun getDefaultValuesFor(eventStart: Long, timeZone: TimeZone, weekStart: WeekDay): WeekDays {

                val cal = Calendar.getInstance(timeZone)
                cal.timeInMillis = eventStart
                cal.firstDayOfWeek = weekStart.javaCalendarDayOfWeek

                val weekDays = WeekDays()
                weekDays[WeekDay.fromJavaCalendarDayOfWeek(cal.get(Calendar.DAY_OF_WEEK))] = true
                return weekDays
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
            var weekDayNum: Int // here 1 means 1st, -1 means last, -2 - 2nd last
    ) : CalendarRecurrence(firstInstance, eventTimeZone, interval, limit, weekStart) {

        override fun serialize(): RRule = super.serialize().apply {
            freq = RRuleVal.FREQ(FreqType.MONTHLY)
            byDay = RRuleVal.BYDAY(listOf(NthWeekDay(weekDay, weekDayNum)))
        }

        override fun copy(): CalendarRecurrence {
            return MonthlyByWeekDay(
                    firstInstance,
                    eventTimeZone,
                    interval,
                    limit.copy(),
                    weekStart,
                    weekDay,
                    weekDayNum
            )
        }

        companion object {

            fun getDefaultValuesFor(firstInstance: Long, timeZone: TimeZone, weekStart: WeekDay, takeLast: Boolean): Pair<WeekDay, Int> {

                val cal = Calendar.getInstance(timeZone)
                cal.timeInMillis = firstInstance
                cal.firstDayOfWeek = weekStart.javaCalendarDayOfWeek

                val weekDay = WeekDay.fromJavaCalendarDayOfWeek(cal.get(Calendar.DAY_OF_WEEK))
                var weekDayNum = cal.get(Calendar.DAY_OF_WEEK_IN_MONTH)

                if (takeLast) {
                    weekDayNum = -1
                    val currentMonth = cal.get(Calendar.MONTH)
                    while (true) {
                        cal.timeInMillis += 7 * 24 * 3600 * 1000L // jump next week
                        if (cal.get(Calendar.MONTH) != currentMonth)  // this week is in the next month already -- we are done
                            break
                        // still in the same month -- add 1 to "weekDayNum"
                        weekDayNum -= 1
                    }
                }

                return Pair(weekDay, weekDayNum)
            }

            fun createDefaultForDate(firstInstance: Long, eventTimeZone: String, weekStart: WeekDay, takeLast: Boolean): MonthlyByWeekDay {
                val timeZone = TimeZone.getTimeZone(eventTimeZone)
                val (weekDay, weekDayNum) = getDefaultValuesFor(firstInstance, timeZone, weekStart, takeLast)

                return MonthlyByWeekDay(
                        firstInstance,
                        eventTimeZone,
                        interval = 1,
                        limit = CalendarRecurrenceLimit.NoLimit(),
                        weekStart = weekStart,
                        weekDay = weekDay,
                        weekDayNum = weekDayNum
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
            var monthDay: Int?
    ) : CalendarRecurrence(firstInstance, eventTimeZone, interval, limit, weekStart) {

        override fun serialize(): RRule  = super.serialize().apply {
            freq = RRuleVal.FREQ(FreqType.MONTHLY)
            byMonthDay = monthDay?.let { RRuleVal.BYMONTHDAY(listOf(it)) }
        }

        override fun copy(): CalendarRecurrence {
            return Monthly(
                    firstInstance,
                    eventTimeZone,
                    interval,
                    limit.copy(),
                    weekStart,
                    monthDay
            )
        }

        companion object {

            fun getDefaultValuesFor(firstInstance: Long, timeZone: TimeZone, weekStart: WeekDay): Int {
                val cal = Calendar.getInstance(timeZone)
                cal.timeInMillis = firstInstance
                cal.firstDayOfWeek = weekStart.javaCalendarDayOfWeek
                return cal.get(Calendar.DAY_OF_MONTH)
            }

            fun createDefaultForDate(firstInstance: Long, eventTimeZone: String, weekStart: WeekDay): Monthly {
                val timeZone = TimeZone.getTimeZone(eventTimeZone)
                return Monthly(
                        firstInstance,
                        eventTimeZone,
                        interval = 1,
                        limit = CalendarRecurrenceLimit.NoLimit(),
                        weekStart = weekStart,
                        monthDay = getDefaultValuesFor(firstInstance, timeZone, weekStart)
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
            var month: Int?,
            var monthDay: Int?
    ) : CalendarRecurrence(firstInstance, eventTimeZone, interval, limit, weekStart) {

        override fun serialize(): RRule  = super.serialize().apply {
            freq = RRuleVal.FREQ(FreqType.YEARLY)
            byMonth = month?.let { RRuleVal.BYMONTH(listOf(it)) }
            byMonthDay = monthDay?.let{ RRuleVal.BYMONTHDAY(listOf(it)) }
        }

        override fun copy(): CalendarRecurrence {
            return Yearly(
                    firstInstance,
                    eventTimeZone,
                    interval,
                    limit.copy(),
                    weekStart,
                    month,
                    monthDay
            )
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
                    weekDays = weekDays?.let{ WeekDays.fromList(it) }
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

            return Monthly(
                    firstInstance = instanceStart,
                    eventTimeZone = eventTimeZone,
                    interval = rrule.interval?.value ?: 1,
                    limit = CalendarRecurrenceLimit.interpretRRuleLimits(rrule),
                    weekStart = rrule.wkst?.value,
                    monthDay = null
            )
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

            val month = rrule.byMonth?.value
            val monthDay = rrule.byMonthDay?.value

            if ((month != null && monthDay == null) ||(month == null && monthDay != null)) {
                throw Exception("Yearly recurrence must either have both BYMONTH & BYMONTHDAY or none")
            }

            if ((month?.count() ?: 1) != 1)
                throw Exception("BYMONTH must contain exactly one month for YEARLY events")
            if ((monthDay?.count() ?: 1) != 1)
                throw Exception("BYMONTHDAY must contain exactly one month for YEARLY events")

            return Yearly(
                    firstInstance = instanceStart,
                    eventTimeZone = eventTimeZone,
                    interval = rrule.interval?.value ?: 1,
                    limit = CalendarRecurrenceLimit.interpretRRuleLimits(rrule),
                    weekStart = rrule.wkst?.value,
                    month = month?.first() ?: null,
                    monthDay = monthDay?.first() ?: null
            )
        }
    }
}
