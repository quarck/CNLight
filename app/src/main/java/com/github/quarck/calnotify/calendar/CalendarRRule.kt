package com.github.quarck.calnotify.calendar

import com.github.quarck.calnotify.utils.DateTimeUtils
import com.github.quarck.calnotify.utils.hourOfDay
import com.github.quarck.calnotify.utils.minute
import com.github.quarck.calnotify.utils.second
import java.lang.Exception
import java.lang.StringBuilder
import java.util.*

enum class FreqType {
    DAILY, WEEKLY, MONTHLY, YEARLY;

    companion object {
        fun parse(v: String): FreqType {
            return when (v) {
                "DAILY" -> DAILY; "WEEKLY" -> WEEKLY; "MONTHLY" -> MONTHLY; "YEARLY" -> YEARLY
                else -> throw Exception("Unknown value $v for Frequency Type")
            }
        }
    }
}

enum class WeekDay(val code: Int) {
    MO(1), TU(2), WE(3), TH(4), FR(5),  SA(6), SU(7); // take this, Usono!

    override fun toString(): String {
        return when (this) {
            MO -> "Monday"
            TU -> "Tuesday"
            WE -> "Wednesday"
            TH -> "Thursday"
            FR -> "Friday"
            SA -> "Saturday"
            SU -> "Sunday"
        }
    }

    fun serialize(): String {
        return when (this) {
            MO -> "MO"; TU -> "TU"; WE -> "WE"; TH -> "TH"
            FR -> "FR"; SA -> "SA"; SU -> "SU"
        }
    }

    val javaCalendarDayOfWeek: Int
        get() = when(this) {
            MO -> Calendar.MONDAY
            TU -> Calendar.TUESDAY
            WE -> Calendar.WEDNESDAY
            TH -> Calendar.THURSDAY
            FR -> Calendar.FRIDAY
            SA -> Calendar.SATURDAY
            SU -> Calendar.SUNDAY
        }

    companion object {
        fun parse(v: String): WeekDay {
            return when (v) {
                "MO" -> MO; "TU" -> TU; "WE" -> WE;  "TH" -> TH
                "FR" -> FR  // Friday is always special!
                "SA" -> SA; "SU" -> SU
                else -> throw Exception("Failed to parse WeekDay $v")
            }
        }

        fun fromJavaCalendarDayOfWeek(day: Int): WeekDay {
            return when (day) {
                Calendar.MONDAY -> MO
                Calendar.TUESDAY -> TU
                Calendar.WEDNESDAY -> WE
                Calendar.THURSDAY -> TH
                Calendar.FRIDAY -> FR
                Calendar.SATURDAY -> SA
                Calendar.SUNDAY -> SU
                else ->
                    throw Exception("Invalid day of week code $day")
            }
        }

        @JvmStatic
        fun fromZeroBasedIndex(v: Int) = values()[v]
    }
}

class WeekDays {
    private val days = Array<Boolean>(7, {false} )

    operator fun get(idx: Int): Boolean = days[idx]
    operator fun set(idx: Int, value: Boolean) { days[idx] = value }

    operator fun get(day: WeekDay): Boolean = get(day.code - 1)
    operator fun set(day: WeekDay, value: Boolean) { days[day.code - 1] = value }

    fun toList(): List<WeekDay> {
        val ret = mutableListOf<WeekDay>()
        for (i in 0 until 7) {
            if (days[i])
                ret.add(WeekDay.fromZeroBasedIndex(i))
        }
        return ret
    }

    fun toRRule(): RRuleVal.BYDAY {
        val values = mutableListOf<NthWeekDay>()
        for (i in 0 until 7) {
            if (days[i])
                values.add(NthWeekDay(WeekDay.fromZeroBasedIndex(i)))
        }
        return RRuleVal.BYDAY(values)
    }

    fun clone(): WeekDays {
        val ret = WeekDays()
        for (i in 0 until 8) {
            ret[i] = get(i)
        }
        return ret
    }

    companion object {
        fun fromList(days: List<WeekDay>): WeekDays {
            val ret = WeekDays()
            for (d in days) {
                ret[d] = true
            }
            return ret
        }
    }
}

data class NthWeekDay(val weekDay: WeekDay, val n: Int?=null) {
    fun serialize(): String = if (n == null) weekDay.serialize() else "$n${weekDay.serialize()}"

    override fun toString(): String {
        if (n == null)
            return weekDay.toString()
        else
            return when (n) {
                1 -> "1st $weekDay"
                2 -> "2nd $weekDay"
                3 -> "3rd $weekDay"
                4, 5 -> "${n}th $weekDay"
                -1 -> "last $weekDay"
                -2 -> "2nd last $weekDay"
                -3 -> "3rd last $weekDay"
                -4, -5 -> "${-n}th last $weekDay"
                else -> "Invalid rule: ${serialize()}"
            }
    }

    companion object {
        fun parse(v: String): NthWeekDay {
            if (v.length == 2)
                return NthWeekDay(WeekDay.parse(v), null)
            val nStr = v.substring(0, v.length - 2)
            val wdStr = v.substring(v.length - 2)
            return NthWeekDay(
                    WeekDay.parse(wdStr),
                    n = nStr.toIntOrNull() ?: throw Exception("Failed to parse NthWeekDay $v"))
        }
    }
}

sealed class RRuleVal {

    abstract fun serialize(): String

    data class FREQ(val value: FreqType): RRuleVal() {
        override fun serialize(): String = "FREQ=$value"

        private fun plural(n: Int, unit: String): String = when (n) {
            2 -> "every 2nd $unit"
            3 -> "every 3rd $unit"
            else -> "every ${n}th $unit"
        }

        fun toStringWithInterval(v: INTERVAL?): String = when (value) {
            FreqType.DAILY ->
                if (v?.value ?: 1 == 1) "daily"
                else plural(v?.value ?: 0, "day")
            FreqType.WEEKLY ->
                if (v?.value ?: 1 == 1) "weekly"
                else plural(v?.value ?: 0, "week")
            FreqType.MONTHLY ->
                if (v?.value ?: 1 == 1) "monthly"
                else plural(v?.value ?: 0, "month")
            FreqType.YEARLY ->
                if (v?.value ?: 1 == 1) "yearly"
                else plural(v?.value ?: 0, "year")
        }

        companion object {
            fun parse(v: String): FREQ = FREQ(FreqType.parse(v))
        }
    }

    data class INTERVAL(val value: Int): RRuleVal() {
        override fun serialize(): String = "INTERVAL=$value"
        companion object {
            fun parse(v: String): INTERVAL =
                    INTERVAL(v.toIntOrNull() ?: throw Exception("Failed to parse count $v"))
        }
    }

    data class COUNT(val value: Int): RRuleVal() {
        override fun serialize(): String = "COUNT=$value"
        override fun toString(): String = "for $value occasions"
        companion object {
            fun parse(v: String): COUNT =
                    COUNT(v.toIntOrNull() ?: throw Exception("Failed to parse count $v"))
        }
    }

    data class UNTIL(val value: Long): RRuleVal() {
        override fun serialize(): String {
            val ret = StringBuilder(128)
            val c = DateTimeUtils.createUTCCalendarTime(value)
            ret.append("UNTIL=%04d%02d%02dT%02d%02d%02dZ".format(
                    c.get(Calendar.YEAR),
                    c.get(Calendar.MONTH) + 1,
                    c.get(Calendar.DAY_OF_MONTH),
                    c.get(Calendar.HOUR_OF_DAY),
                    c.get(Calendar.MINUTE),
                    c.get(Calendar.SECOND))
            )
            return ret.toString()
        }

        fun toString(tzOffset: Int): String {
            val c = DateTimeUtils.createUTCCalendarTime(value + tzOffset)

            val h = c.hourOfDay
            val m = c.minute
            val s = c.second

            if (h == 23 && m == 59 && s == 59) {
                return "until %04d/%02d/%02d".format(
                        c.get(Calendar.YEAR),
                        c.get(Calendar.MONTH) + 1,
                        c.get(Calendar.DAY_OF_MONTH))

            }
            else {
                return "until %04d/%02d/%02d %02d:%02d:%02d".format(
                        c.get(Calendar.YEAR),
                        c.get(Calendar.MONTH) + 1,
                        c.get(Calendar.DAY_OF_MONTH),
                        c.get(Calendar.HOUR_OF_DAY),
                        c.get(Calendar.MINUTE),
                        c.get(Calendar.SECOND))
            }
        }

        companion object {
            fun parse(v: String): UNTIL {
                if (v.length != 16)
                    throw Exception("UNTIL date '$v' is invalid")

                val year = v.substring(0, 4).toIntOrNull()
                val month = v.substring(4, 6).toIntOrNull()
                val day = v.substring(6, 8).toIntOrNull()
                val t = v.substring(8, 9).toLowerCase(Locale.ROOT) == "t"
                val hour = v.substring(9, 11).toIntOrNull()
                val minute = v.substring(11, 13).toIntOrNull()
                val second = v.substring(13, 15).toIntOrNull()
                val z = v.substring(15, 16).toLowerCase(Locale.ROOT) == "z"

                if (year == null || month == null || day == null ||
                        hour == null || minute == null || second == null || !t || !z) // format check
                    throw Exception("UNTIL date '$v' is invalid (2)")

                val c = Calendar.getInstance(DateTimeUtils.utcTimeZone)

                c.set(Calendar.YEAR, year)
                c.set(Calendar.MONTH, month-1)
                c.set(Calendar.DAY_OF_MONTH, day)
                c.set(Calendar.HOUR_OF_DAY, hour)
                c.set(Calendar.MINUTE, minute)
                c.set(Calendar.SECOND, second)
                c.set(Calendar.MILLISECOND, 0)

                return UNTIL(c.timeInMillis)
            }
        }
    }

    data class WKST(val value: WeekDay): RRuleVal() {
        override fun serialize(): String = "WKST=${value.serialize()}"

        override fun toString(): String = "(week starts on $value)"

        companion object {
            fun parse(v: String): WKST {
                return WKST(WeekDay.parse(v))
            }
        }
    }

    data class BYDAY(val value: List<NthWeekDay>): RRuleVal() { // list preserves an order
        override fun serialize(): String =
                "BYDAY=${value.map{ it.serialize() }.joinToString(",")}"

        override fun toString(): String =
                "on ${value.map{ it.toString() }.joinToString(",")}"

        companion object {
            fun parse(v: String): BYDAY {
                if (v.isBlank())
                    throw Exception("Failed to parse BYDATE $v")
                return BYDAY(v.split(',').map{ NthWeekDay.parse(it) }.toList())
            }
        }
    }
    data class BYMONTH(val value: List<Int>): RRuleVal() {
        override fun serialize(): String =
                "BYMONTH=${value.joinToString(",")}"

        override fun toString(): String =
                "on months: ${value.joinToString(",")}"

        companion object {
            fun parse(v: String): BYMONTH {
                if (v.isBlank())
                    throw Exception("BYMONTH value is empty")
                return BYMONTH(v.split(',').map { it.toIntOrNull() ?: throw Exception("Failed to BYMONTH entry $it") }.toList())
            }
        }
    }

    data class BYMONTHDAY(val value: List<Int>): RRuleVal() {
        override fun serialize(): String =
                "BYMONTHDAY=${value.joinToString(",")}"

        override fun toString(): String =
                "on month days: ${value.joinToString(",")}"

        companion object {
            fun parse(v: String): BYMONTHDAY {
                if (v.isBlank())
                    throw Exception("BYMONTHDAY value is empty")
                return BYMONTHDAY(v.split(',').map { it.toIntOrNull() ?: throw Exception("Failed to parse BYMONTHDAY entry '$it'") }.toList())
            }
        }
    }

    data class BYYEARDAY(val value: List<Int>): RRuleVal() {
        override fun serialize(): String =
                "BYYEARDAY=${value.joinToString(",")}"

        override fun toString(): String =
                "on year days: ${value.joinToString(",")}"

        companion object {
            fun parse(v: String): BYYEARDAY {
                if (v.isBlank())
                    throw Exception("BYYEARDAY value is empty")
                return BYYEARDAY(v.split(',').map { it.toIntOrNull() ?: throw Exception("Failed to BYYEARDAY entry $it") }.toList())
            }
        }
    }

    data class BYWEEKNO(val value: List<Int>): RRuleVal() {
        override fun serialize(): String =
                "BYWEEKNO=${value.joinToString(",")}"

        override fun toString(): String =
                "on weeks number: ${value.joinToString(",")}"

        companion object {
            fun parse(v: String): BYWEEKNO {
                if (v.isBlank())
                    throw Exception("BYWEEKNO value is empty")
                return BYWEEKNO(v.split(',').map { it.toIntOrNull() ?: throw Exception("Failed to BYWEEKNO entry $it") }.toList())
            }
        }
    }

    data class BYSETPOS(val value: Int): RRuleVal() {
        override fun serialize(): String = "BYSETPOS=$value"

        override fun toString(): String =
                "by set pos $value"

        companion object {
            fun parse(v: String): BYSETPOS {
                return BYSETPOS(v.toIntOrNull() ?: throw Exception("Failed to parse BYSETPOS $v"))
            }
        }
    }
}

data class RRule(
        var freq: RRuleVal.FREQ? = null,
        var count: RRuleVal.COUNT? = null,
        var interval: RRuleVal.INTERVAL? = null,
        var until: RRuleVal.UNTIL? = null,
        var wkst: RRuleVal.WKST? = null,
        var byDay: RRuleVal.BYDAY? = null,
        var byMonth: RRuleVal.BYMONTH? = null,
        var byMonthDay: RRuleVal.BYMONTHDAY? = null,
        var byYearDay: RRuleVal.BYYEARDAY? = null,
        var byWeekNo: RRuleVal.BYWEEKNO? = null,
        var bySetPos: RRuleVal.BYSETPOS? = null
) {
    fun parsePair(name: String, value: String) {
        when (name) {
            "FREQ" ->
                if (freq == null) freq = RRuleVal.FREQ.parse(value)
                else throw Exception("Failed to parse rule - FREQ appears twice!")
            "COUNT" ->
                if (count == null) count = RRuleVal.COUNT.parse(value)
                else throw Exception("Failed to parse rule - COUNT appears twice!")
            "INTERVAL" ->
                if (interval == null) interval = RRuleVal.INTERVAL.parse(value)
                else throw Exception("Failed to parse rule - INTERVAL appears twice!")
            "UNTIL" ->
                if (until == null) until = RRuleVal.UNTIL.parse(value)
                else throw Exception("Failed to parse rule - UNTIL appears twice!")
            "WKST" ->
                if (wkst == null) wkst = RRuleVal.WKST.parse(value)
                else throw Exception("Failed to parse rule - WKST appears twice!")
            "BYDAY" ->
                if (byDay == null) byDay = RRuleVal.BYDAY.parse(value)
                else throw Exception("Failed to parse rule - BYDAY appears twice!")
            "BYMONTH" ->
                if (byMonth == null) byMonth = RRuleVal.BYMONTH.parse(value)
                else throw Exception("Failed to parse rule - BYMONTH appears twice!")
            "BYMONTHDAY" ->
                if (byMonthDay == null) byMonthDay = RRuleVal.BYMONTHDAY.parse(value)
                else throw Exception("Failed to parse rule - BYMONTHDAY appears twice!")
            "BYYEARDAY" ->
                if (byYearDay == null) byYearDay = RRuleVal.BYYEARDAY.parse(value)
                else throw Exception("Failed to parse rule - BYYEARDAY appears twice!")
            "BYWEEKNO" ->
                if (byWeekNo == null) byWeekNo = RRuleVal.BYWEEKNO.parse(value)
                else throw Exception("Failed to parse rule - BYWEEKNO appears twice!")
            "BYSETPOS" ->
                if (bySetPos == null) bySetPos = RRuleVal.BYSETPOS.parse(value)
                else throw Exception("Failed to parse rule - BYSETPOS appears twice!")
            else ->
                throw Exception("Unknown value $name with value '$value'")
        }
    }

    fun serialize(): String {
        val items = mutableListOf<String>()

        freq?.let { items.add(it.serialize()) }
        count?.let { items.add(it.serialize()) }
        interval?.let { items.add(it.serialize()) }
        until?.let { items.add(it.serialize()) }
        wkst?.let { items.add(it.serialize()) }
        byDay?.let { items.add(it.serialize()) }
        byMonth?.let { items.add(it.serialize()) }
        byMonthDay?.let { items.add(it.serialize()) }
        byYearDay?.let { items.add(it.serialize()) }
        byWeekNo?.let { items.add(it.serialize()) }
        bySetPos?.let { items.add(it.serialize()) }

        return items.joinToString(";")
    }

    fun toString(eventTimeZoneOffset: Int): String {
        val sb = StringBuilder(128)

        freq?.let { sb.append("Repeats "); sb.append(it.toStringWithInterval(interval)) }
        count?.let { sb.append(" "); sb.append(it.toString())}
        byDay?.let { sb.append(" "); sb.append(it.toString())}
        byMonth?.let { sb.append(" "); sb.append(it.toString())}
        byMonthDay?.let { sb.append(" "); sb.append(it.toString())}
        byYearDay?.let { sb.append(" "); sb.append(it.toString())}
        byWeekNo?.let { sb.append(" "); sb.append(it.toString())}
        bySetPos?.let { sb.append(" "); sb.append(it.toString())}
        until?.let { sb.append(" "); sb.append(it.toString(eventTimeZoneOffset))}
//        wkst?.let {
//            if (it.value.code != weekStartDefault) {
//                sb.append(" ")
//                sb.append(it.toString())
//            }
//        }

        return sb.toString()
    }

    fun isEmpty(): Boolean =
            (freq == null) && (count == null) && (byDay == null) &&
            (byMonth == null) && (byMonthDay == null) &&
            (byYearDay == null) && (byWeekNo == null) &&
            (bySetPos == null) && (until == null) && (wkst == null)

    companion object {
        fun parse(rRule: String): RRule {
            val ret = RRule()

            if (rRule.isNotBlank()) {
                for (item in rRule.split(';')) {
                    val eqPos = item.indexOf('=')
                    if (eqPos == -1)
                        throw Exception("Error when parsing the rrRule $rRule: token $item has no '=' sign")
                    ret.parsePair(
                            name = item.substring(0, eqPos),
                            value = item.substring(startIndex = eqPos + 1)
                    )
                }
            }
            return ret
        }

        fun tryParse(rRule: String): RRule? {
            try {
                return parse(rRule)
            }
            catch (ex: Exception) {
            }
            return null
        }
    }
}

