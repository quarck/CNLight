package com.github.quarck.calnotify

import com.github.quarck.calnotify.calendar.CalendarRecurrence
import com.github.quarck.calnotify.calendar.RRule
import org.junit.Test

import org.junit.Assert.*


class CalendareRecurrenceParsingTest {
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
                "FREQ=MONTHLY;BYMONTHDAY=15,30;COUNT=5"
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
}