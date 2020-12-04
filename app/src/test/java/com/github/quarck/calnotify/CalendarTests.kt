package com.github.quarck.calnotify

import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.utils.*
import org.junit.Test

import org.junit.Assert.*


class CalendarTests {
    @Test
    fun parsingRrulesIntoValueLinesTest() {
        // Here we are just expecting code to run with no exceptions, as any parsing error will raise one
        val rrules = listOf(
                "FREQ=DAILY;COUNT=10",
                "FREQ=DAILY;UNTIL=19971224T000000Z",
                "FREQ=DAILY;INTERVAL=2",
                "FREQ=DAILY;INTERVAL=10;COUNT=5",
                "FREQ=YEARLY;UNTIL=20000131T140000Z;BYMONTH=1;BYDAY=SU,MO,TU,WE,TH,FR,SA",
                "FREQ=DAILY;UNTIL=20000131T140000Z;BYMONTH=1",
                "FREQ=WEEKLY;COUNT=10",
                "FREQ=WEEKLY;UNTIL=19971224T000000Z",
                "FREQ=WEEKLY;INTERVAL=2;WKST=SU",
                "FREQ=WEEKLY;UNTIL=19971007T000000Z;WKST=SU;BYDAY=TU,TH",
                "FREQ=WEEKLY;COUNT=10;WKST=SU;BYDAY=TU,TH",
                "FREQ=WEEKLY;INTERVAL=2;UNTIL=19971224T000000Z;WKST=SU;BYDAY=MO,WE,FR",
                "FREQ=WEEKLY;INTERVAL=2;COUNT=8;WKST=SU;BYDAY=TU,TH",
                "FREQ=MONTHLY;COUNT=10;BYDAY=1FR",
                "FREQ=MONTHLY;UNTIL=19971224T000000Z;BYDAY=1FR",
                "FREQ=MONTHLY;INTERVAL=2;COUNT=10;BYDAY=1SU,-1SU",
                "FREQ=MONTHLY;COUNT=6;BYDAY=-2MO",
                "FREQ=MONTHLY;BYMONTHDAY=-3",
                "FREQ=MONTHLY;COUNT=10;BYMONTHDAY=2,15",
                "FREQ=MONTHLY;COUNT=10;BYMONTHDAY=1,-1",
                "FREQ=MONTHLY;INTERVAL=18;COUNT=10;BYMONTHDAY=10,11,12,13,14,15",
                "FREQ=MONTHLY;INTERVAL=2;BYDAY=TU",
                "FREQ=YEARLY;COUNT=10;BYMONTH=6,7",
                "FREQ=YEARLY;INTERVAL=2;COUNT=10;BYMONTH=1,2,3",
                "FREQ=YEARLY;INTERVAL=3;COUNT=10;BYYEARDAY=1,100,200",
                "FREQ=YEARLY;BYDAY=20MO",
                "FREQ=YEARLY;BYWEEKNO=20;BYDAY=MO",
                "FREQ=YEARLY;BYMONTH=3;BYDAY=TH",
                "FREQ=YEARLY;BYDAY=TH;BYMONTH=6,7,8",
                "FREQ=MONTHLY;BYDAY=FR;BYMONTHDAY=13",
                "FREQ=MONTHLY;BYDAY=SA;BYMONTHDAY=7,8,9,10,11,12,13",
                "FREQ=YEARLY;INTERVAL=4;BYMONTH=11;BYDAY=TU;BYMONTHDAY=2,3,4,5,6,7,8",
                "FREQ=MONTHLY;COUNT=3;BYDAY=TU,WE,TH;BYSETPOS=3",
                "FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-2",
                "FREQ=WEEKLY;INTERVAL=2;COUNT=4;BYDAY=TU,SU;WKST=MO",
                "FREQ=WEEKLY;INTERVAL=2;COUNT=4;BYDAY=TU,SU;WKST=SU",
                "FREQ=MONTHLY;BYMONTHDAY=15,30;COUNT=5",
                "FREQ=MONTHLY",
                "FREQ=YEARLY"
        )

        for (rrule in rrules) {
            println("Original rule:")
            println(rrule)
            val parsed = RRule.parse(rrule)
            println("Parsed: $parsed")
            val serialized = parsed.serialize()
            println("Serialized: $serialized")
            val parsed2 = RRule.parse(serialized)
            assertEquals(parsed, parsed2)
            println()
        }

    }

    @Test
    fun interpretingRRuleTest() {

        val rrules = listOf(
                "FREQ=DAILY;COUNT=10",
                "FREQ=DAILY;UNTIL=19971224T000000Z",
                "FREQ=DAILY;INTERVAL=2",
                "FREQ=DAILY;INTERVAL=10;COUNT=5",
                "FREQ=WEEKLY;COUNT=10",
                "FREQ=WEEKLY;INTERVAL=2;WKST=SU",
                "FREQ=WEEKLY;COUNT=10;WKST=SU;BYDAY=TU,TH",
                "FREQ=WEEKLY;INTERVAL=2;UNTIL=19971224T000000Z;WKST=SU;BYDAY=MO,WE,FR",
                "FREQ=MONTHLY;UNTIL=19971224T000000Z;BYDAY=1FR",
                "FREQ=MONTHLY;COUNT=10;BYDAY=1FR",
                "FREQ=MONTHLY;COUNT=6;BYDAY=-2MO",
                "FREQ=MONTHLY;BYMONTHDAY=3",
                "FREQ=MONTHLY;INTERVAL=18;COUNT=10;BYMONTHDAY=15",
                "FREQ=YEARLY;COUNT=10;BYMONTH=6;BYMONTHDAY=15",
                "FREQ=MONTHLY",
                "FREQ=YEARLY"
        )

        val now = System.currentTimeMillis()

        for (rrule in rrules) {
            val interpreted = CalendarRecurrence.interpretRecurrence(now, "UTC", rrule, "", "", "");
            println(rrule)
            println(interpreted.serialize().serialize())
            println(interpreted.toString())
            println()
        }


        /*interpretRecurrence(
                instanceStart: Long,
                eventTimeZone: String,
                rRule: String,
                rDate: String,
                exRRule: String,
                exRDate: String,
        )*/
    }

    fun alignUntilTime(t: Long): Long {
        val day = t / (24 * 3600 * 1000L)
        return (day * 24 * 3600 * 1000L) + 24 * 3600 * 1000L -1L
    }

    @Test
    fun creatingRecurrenceTest() {
        val tz = "UTC"
        val wkst = WeekDay.MO

        val time = 1606038313290L  // 2020/11/22 09:45:13 4-th week, day: 1
        val daily = CalendarRecurrence.Daily.createDefaultForDate(time, tz, wkst)
        assertEquals(daily.serialize().serialize(), "FREQ=DAILY;WKST=MO")
        daily.setCount(2)
        assertEquals(daily.serialize().serialize(), "FREQ=DAILY;COUNT=2;WKST=MO")
        daily.interval = 3
        assertEquals(daily.serialize().serialize(), "FREQ=DAILY;COUNT=2;INTERVAL=3;WKST=MO")
        daily.setUntil(alignUntilTime(time + 3 * 24 * 3600 * 1000L))
        assertEquals(daily.serialize().serialize(), "FREQ=DAILY;INTERVAL=3;UNTIL=20201125T235959Z;WKST=MO")

        val weekly = CalendarRecurrence.Weekly.createDefaultForDate(time, tz, wkst)
        assertEquals(weekly.serialize().serialize(), "FREQ=WEEKLY;WKST=MO")
        weekly.setCount(2)
        assertEquals(weekly.serialize().serialize(), "FREQ=WEEKLY;COUNT=2;WKST=MO")
        weekly.interval = 3
        assertEquals(weekly.serialize().serialize(), "FREQ=WEEKLY;COUNT=2;INTERVAL=3;WKST=MO")
        weekly.setUntil(alignUntilTime(time + 30 * 24 * 3600 * 1000L))
        assertEquals(weekly.serialize().serialize(), "FREQ=WEEKLY;INTERVAL=3;UNTIL=20201222T235959Z;WKST=MO")
        weekly.weekDays = WeekDays.fromList(listOf(WeekDay.MO, WeekDay.FR))
        assertEquals(weekly.serialize().serialize(), "FREQ=WEEKLY;INTERVAL=3;UNTIL=20201222T235959Z;WKST=MO;BYDAY=MO,FR")


        val monthly0 = CalendarRecurrence.Monthly.createDefaultForDate(time, tz, wkst)
        assertEquals(monthly0.serialize().serialize(), "FREQ=MONTHLY;WKST=MO;BYMONTHDAY=22")
        monthly0.setCount(2)
        assertEquals(monthly0.serialize().serialize(), "FREQ=MONTHLY;COUNT=2;WKST=MO;BYMONTHDAY=22")
        monthly0.interval = 3
        assertEquals(monthly0.serialize().serialize(), "FREQ=MONTHLY;COUNT=2;INTERVAL=3;WKST=MO;BYMONTHDAY=22")
        monthly0.setUntil(alignUntilTime(time + 395 * 24 * 3600 * 1000L))
        assertEquals(monthly0.serialize().serialize(), "FREQ=MONTHLY;INTERVAL=3;UNTIL=20211222T235959Z;WKST=MO;BYMONTHDAY=22")


        val monthly1 = CalendarRecurrence.MonthlyByWeekDay.createDefaultForDate(time, tz, wkst, false)
        assertEquals(monthly1.serialize().serialize(), "FREQ=MONTHLY;WKST=MO;BYDAY=4SU")
        monthly1.setCount(2)
        assertEquals(monthly1.serialize().serialize(), "FREQ=MONTHLY;COUNT=2;WKST=MO;BYDAY=4SU")
        monthly1.interval = 3
        assertEquals(monthly1.serialize().serialize(), "FREQ=MONTHLY;COUNT=2;INTERVAL=3;WKST=MO;BYDAY=4SU")
        monthly1.setUntil(alignUntilTime(time + 395 * 24 * 3600 * 1000L))
        assertEquals(monthly1.serialize().serialize(), "FREQ=MONTHLY;INTERVAL=3;UNTIL=20211222T235959Z;WKST=MO;BYDAY=4SU")


        val monthly1r = CalendarRecurrence.MonthlyByWeekDay.createDefaultForDate(time, tz, wkst, true)
        assertEquals(monthly1r.serialize().serialize(), "FREQ=MONTHLY;WKST=MO;BYDAY=-2SU")
        monthly1r.setCount(2)
        assertEquals(monthly1r.serialize().serialize(), "FREQ=MONTHLY;COUNT=2;WKST=MO;BYDAY=-2SU")
        monthly1r.interval = 3
        assertEquals(monthly1r.serialize().serialize(), "FREQ=MONTHLY;COUNT=2;INTERVAL=3;WKST=MO;BYDAY=-2SU")
        monthly1r.setUntil(alignUntilTime(time + 395 * 24 * 3600 * 1000L))
        assertEquals(monthly1r.serialize().serialize(), "FREQ=MONTHLY;INTERVAL=3;UNTIL=20211222T235959Z;WKST=MO;BYDAY=-2SU")


        val time1 = 1605001513290L  // 2020/11/10 09:45:13 3-th week, day: 3
        val monthly2 = CalendarRecurrence.MonthlyByWeekDay.createDefaultForDate(time1, tz, wkst, false)
        assertEquals(monthly2.serialize().serialize(), "FREQ=MONTHLY;WKST=MO;BYDAY=2TU")
        monthly2.setCount(2)
        assertEquals(monthly2.serialize().serialize(), "FREQ=MONTHLY;COUNT=2;WKST=MO;BYDAY=2TU")
        monthly2.interval = 3
        assertEquals(monthly2.serialize().serialize(), "FREQ=MONTHLY;COUNT=2;INTERVAL=3;WKST=MO;BYDAY=2TU")
        monthly2.setUntil(alignUntilTime(time + 395 * 24 * 3600 * 1000L))
        assertEquals(monthly2.serialize().serialize(), "FREQ=MONTHLY;INTERVAL=3;UNTIL=20211222T235959Z;WKST=MO;BYDAY=2TU")

        val monthly2r = CalendarRecurrence.MonthlyByWeekDay.createDefaultForDate(time1, tz, wkst, true)
        assertEquals(monthly2r.serialize().serialize(), "FREQ=MONTHLY;WKST=MO;BYDAY=-3TU")
        monthly2r.setCount(2)
        assertEquals(monthly2r.serialize().serialize(), "FREQ=MONTHLY;COUNT=2;WKST=MO;BYDAY=-3TU")
        monthly2r.interval = 3
        assertEquals(monthly2r.serialize().serialize(), "FREQ=MONTHLY;COUNT=2;INTERVAL=3;WKST=MO;BYDAY=-3TU")
        monthly2r.setUntil(alignUntilTime(time + 395 * 24 * 3600 * 1000L))
        assertEquals(monthly2r.serialize().serialize(), "FREQ=MONTHLY;INTERVAL=3;UNTIL=20211222T235959Z;WKST=MO;BYDAY=-3TU")

        val monthly2r2 = CalendarRecurrence.MonthlyByWeekDay.createDefaultForDate(time1 - 24*3600*1000L, tz, wkst, true)
        assertEquals(monthly2r2.serialize().serialize(), "FREQ=MONTHLY;WKST=MO;BYDAY=-4MO")
        monthly2r2.setCount(2)
        assertEquals(monthly2r2.serialize().serialize(), "FREQ=MONTHLY;COUNT=2;WKST=MO;BYDAY=-4MO")
        monthly2r2.interval = 3
        assertEquals(monthly2r2.serialize().serialize(), "FREQ=MONTHLY;COUNT=2;INTERVAL=3;WKST=MO;BYDAY=-4MO")
        monthly2r2.setUntil(alignUntilTime(time + 395 * 24 * 3600 * 1000L))
        assertEquals(monthly2r2.serialize().serialize(), "FREQ=MONTHLY;INTERVAL=3;UNTIL=20211222T235959Z;WKST=MO;BYDAY=-4MO")

        val monthly2r3 = CalendarRecurrence.MonthlyByWeekDay.createDefaultForDate(time1 - 8*24*3600*1000L, tz, wkst, true)
        assertEquals(monthly2r3.serialize().serialize(), "FREQ=MONTHLY;WKST=MO;BYDAY=-5MO")
        monthly2r3.setCount(2)
        assertEquals(monthly2r3.serialize().serialize(), "FREQ=MONTHLY;COUNT=2;WKST=MO;BYDAY=-5MO")
        monthly2r3.interval = 3
        assertEquals(monthly2r3.serialize().serialize(), "FREQ=MONTHLY;COUNT=2;INTERVAL=3;WKST=MO;BYDAY=-5MO")
        monthly2r3.setUntil(alignUntilTime(time + 395 * 24 * 3600 * 1000L))
        assertEquals(monthly2r3.serialize().serialize(), "FREQ=MONTHLY;INTERVAL=3;UNTIL=20211222T235959Z;WKST=MO;BYDAY=-5MO")


        val yearly = CalendarRecurrence.Yearly.createDefaultForDate(time, tz, wkst)
        assertEquals(yearly.serialize().serialize(), "FREQ=YEARLY;WKST=MO;BYMONTH=11;BYMONTHDAY=22")
        yearly.setCount(2)
        assertEquals(yearly.serialize().serialize(), "FREQ=YEARLY;COUNT=2;WKST=MO;BYMONTH=11;BYMONTHDAY=22")
        yearly.interval = 3
        assertEquals(yearly.serialize().serialize(), "FREQ=YEARLY;COUNT=2;INTERVAL=3;WKST=MO;BYMONTH=11;BYMONTHDAY=22")
        yearly.setUntil(alignUntilTime(time + (395 + 365) * 24 * 3600 * 1000L))
        assertEquals(yearly.serialize().serialize(), "FREQ=YEARLY;INTERVAL=3;UNTIL=20221222T235959Z;WKST=MO;BYMONTH=11;BYMONTHDAY=22")
    }

    @Test
    fun parsingDurationTest() {
        val S = 1000L
        val M = 60 * S
        val H = 60 * M
        val D = 24 * H
        val W = 7 * D
        val durations = listOf(
                Pair("P15DT5H0M20S", 15*D + 5*H + 0*M + 20*S),
                Pair("+P15DT5H0M20S", 15*D + 5*H + 0*M + 20*S),
                Pair("-P15DT5H0M20S", -(15*D + 5*H + 0*M + 20*S)),
                Pair("P15D5H0M20S", 15*D + 5*H + 0*M + 20*S),
                Pair("P7W", 7*W),
                Pair("+P7W", 7*W),
                Pair("-P7W", -7*W),
                Pair("P3600S", 3600*S),
        )

        for (d in durations) {
            val parsed = CalendarProviderHelper.parseRfc2445Duration(d.first)
            println("${d.first} -> $parsed")
            assertEquals(parsed, d.second)
        }

        assertEquals(CalendarProviderHelper.encodeRfc2445Duration(3600*1000L), "P3600S")
        assertEquals(CalendarProviderHelper.encodeRfc2445Duration(1*1000L), "P1S")
        assertEquals(CalendarProviderHelper.encodeRfc2445Duration(232323*1000L), "P232323S")

    }

    @Test
    fun moveToNextPrevMonthTst() {
        val cal = java.util.Calendar.getInstance()
        cal.set(2020, 6, 15, 1, 2, 3)
        cal.millisecond = 456

        assertEquals(cal.toISO8601String(), "2020-07-15T01:02:03.456")
        assertEquals(cal.toAdjacentMonth(1).toISO8601String(), "2020-08-15T01:02:03.456")
        assertEquals(cal.toAdjacentMonth(-1).toISO8601String(), "2020-06-15T01:02:03.456")

        val cal2 = cal.clone() as java.util.Calendar
        cal2.dayOfMonth = 30

        assertEquals(cal2.toISO8601String(), "2020-07-30T01:02:03.456")
        assertEquals(cal2.toAdjacentMonth(1).toISO8601String(), "2020-08-28T01:02:03.456")
        assertEquals(cal2.toAdjacentMonth(-1).toISO8601String(), "2020-06-28T01:02:03.456")
    }
}