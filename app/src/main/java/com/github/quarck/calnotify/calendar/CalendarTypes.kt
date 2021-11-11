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

import android.provider.CalendarContract
import android.util.Log
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.utils.MD5
import com.github.quarck.calnotify.utils.md5state

data class CalendarRecord(
        val calendarId: Long,
        val owner: String,
        val displayName: String,
        val name: String,
        val accountName: String,
        val accountType: String,
        val timeZone: String,
        val color: Int,
        val isVisible: Boolean,
        val isPrimary: Boolean,
        val isReadOnly: Boolean,
        val isSynced: Boolean
)

data class EventReminderRecord(
        val millisecondsBefore: Long,
        val method: Int = CalendarContract.Reminders.METHOD_DEFAULT
) {
    fun serialize() = "$millisecondsBefore,$method"

    companion object {
        fun deserialize(str: String): EventReminderRecord {
            val (time, method) = str.split(',')
            return EventReminderRecord(
                    millisecondsBefore = time.toLong(),
                    method = method.toInt()
            )
        }

        fun minutes(mins: Int) = EventReminderRecord(mins * Consts.MINUTE_IN_MILLISECONDS)
    }
}

val EventReminderRecord.allDayDaysBefore: Int
    get() = ((this.millisecondsBefore + Consts.DAY_IN_MILLISECONDS) / Consts.DAY_IN_MILLISECONDS).toInt()

val EventReminderRecord.allDayHourOfDayAndMinute: Pair<Int, Int>
    get() {
        val timeOfDayMillis =
                if (this.millisecondsBefore >= 0L) { // on the day of event
                    Consts.DAY_IN_MILLISECONDS - this.millisecondsBefore % Consts.DAY_IN_MILLISECONDS
                }
                else  {
                    -this.millisecondsBefore
                }

        val timeOfDayMinutes = timeOfDayMillis.toInt() / 1000 / 60

        val minute = timeOfDayMinutes % 60
        val hourOfDay = timeOfDayMinutes / 60

        return Pair(hourOfDay, minute)
    }


fun List<EventReminderRecord>.serialize()
        = this.map { it.serialize() }.joinToString(separator = ";")

fun String.deserializeCalendarEventReminders()
        = this.split(";").filter { it != "" }.map { EventReminderRecord.deserialize(it) }.toList()

data class CalendarEventDetails(
        val title: String,
        val desc: String,
        val location: String,
        val timezone: String,
        val startTime: Long,
        val endTime: Long,
        val isAllDay: Boolean,
        var reminders: List<EventReminderRecord>,
        val rRule: String = "", // empty if not repeating
        val rDate: String = "", // empty if not repeating
        val exRRule: String = "", // empty if not repeating
        val exRDate: String = "", // empty if not repeating
        val lastDate: Long? = null,
        val duration: Long? = null,
        val color: Int = 0,
        val colorKey: String? = null
) {
}

@Suppress("UNUSED_PARAMETER")
fun eventContentHash(calendarId: Long, isAllDay: Boolean, color: Int,
                     startTime: Long, endTime: Long,
                     eventStatus: EventStatus, attendanceStatus: AttendanceStatus,
                     title: String, desc: String, location: String, timeZone: String,
                     rRule: String, rDate: String, exRRule: String, exRDate: String
): md5state {
    val text = "$calendarId,$isAllDay,$color," +
            "$startTime,$endTime," +
            "$eventStatus,$attendanceStatus," +
            "${title.length},${desc.length},${location.length},${timeZone.length}," +
            "$title,$desc,$location,$timeZone,"// +
    return MD5.compute(text.toByteArray())
}

data class EventRecord(
        val calendarId: Long,
        val eventId: Long,
        val details: CalendarEventDetails,
        var eventStatus: EventStatus = EventStatus.Confirmed,
        var attendanceStatus: AttendanceStatus = AttendanceStatus.None
) {
    val contentMd5: md5state by lazy {
        eventContentHash(
                calendarId, isAllDay, color,
                startTime, endTime,
                eventStatus, attendanceStatus,
                title, desc, location, timeZone,
                rRule, rDate, exRRule, exRDate
        )
    }

    val title: String get() = details.title
    val desc: String get() = details.desc

    val location: String get() = details.location

    val timezone: String get() = details.timezone

    val startTime: Long get() = details.startTime
    val endTime: Long get() = details.endTime

    val isAllDay: Boolean get() = details.isAllDay

    val timeZone: String get() = details.timezone

    var reminders: List<EventReminderRecord>
        get() = details.reminders
        set(value) {
            details.reminders = value
        }

    val rRule: String get() = details.rRule
    val rDate: String get() = details.rDate
    val exRRule: String get() = details.exRRule
    val exRDate: String get() = details.exRDate

    val color: Int get() = details.color
}


fun EventRecord.nextAlarmTime(currentTime: Long): Long {
    var ret = 0L

    for (reminder in reminders) {
        val reminderTime = startTime - reminder.millisecondsBefore

        if (ret == 0L) {
            // First entry - simply store
            ret = reminderTime
        }
        else if ((ret > currentTime) && (reminderTime > currentTime)) {
            // Both in the future - look for the closest time
            if (reminderTime < ret)
                ret = reminderTime
        }
        else if ((ret <= currentTime) && (reminderTime > currentTime)) {
            // current in the future, 'ret' in the past - update ret
            ret = reminderTime
        }
        else if ((ret > currentTime) && (reminderTime <= currentTime)) {
            //  'ret' is in the future, current in the past - ignore
        }
        else if ((ret <= currentTime) && (reminderTime <= currentTime)) {
            // both 'ret' and current are in the past - pick most recent
            if (reminderTime > ret)
                ret = reminderTime
        }
    }

    return ret
}



enum class EventOrigin(val code: Int) {
    ProviderBroadcast(0),
    ProviderManual(1),
    ProviderBroadcastFollowingManual(2),
    FullManual(3);

    override fun toString(): String
            = when (this) {
        ProviderBroadcast -> "PB"
        ProviderManual -> "PM"
        ProviderBroadcastFollowingManual -> "pbPM"
        FullManual -> "FM"
    }

    companion object {
        @JvmStatic
        fun fromInt(v: Int) = values()[v]
    }
}

enum class EventStatus(val code: Int) {
    Tentative(CalendarContract.Events.STATUS_TENTATIVE),
    Confirmed(CalendarContract.Events.STATUS_CONFIRMED),
    Cancelled(CalendarContract.Events.STATUS_CANCELED),
    Unknown(-1);

    companion object {
        @JvmStatic
        fun fromInt(v: Int?): EventStatus {

            if (v == null)
                return Confirmed

            return when (v) {
                CalendarContract.Events.STATUS_TENTATIVE -> Tentative
                CalendarContract.Events.STATUS_CONFIRMED -> Confirmed
                CalendarContract.Events.STATUS_CANCELED -> Cancelled
                else -> Unknown
            }
        }
    }
}

enum class AttendanceStatus(val code: Int) {

    None(CalendarContract.Attendees.ATTENDEE_STATUS_NONE),
    Accepted(CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED),
    Declined(CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED),
    Invited(CalendarContract.Attendees.ATTENDEE_STATUS_INVITED),
    Tentative(CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE),
    Unknown(-1);

    companion object {
        @JvmStatic
        fun fromInt(v: Int?): AttendanceStatus {

            if (v == null)
                return Accepted

            return when (v) {
                CalendarContract.Attendees.ATTENDEE_STATUS_NONE -> None
                CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED -> Accepted
                CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED -> Declined
                CalendarContract.Attendees.ATTENDEE_STATUS_INVITED -> Invited
                CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE -> Tentative
                else -> Unknown
            }
        }
    }
}

object EventAlertFlags {
    const val IS_ALARM = 4L
}

fun Long.isFlagSet(flag: Long)
        = (this and flag) != 0L

fun Long.setFlag(flag: Long, value: Boolean)
        = if (value)
    this or flag
else
    this and flag.inv()

data class EventAlertRecordKey(val eventId: Long, val instanceStartTime: Long)

data class EventAlertRecord(
        // non-changeable
        val calendarId: Long,
        val eventId: Long,
        val isAllDay: Boolean,
        val rRule: String,
        val rDate: String,
        val exRRule: String,
        val exRDate: String,
        val alertTime: Long,
        val title: String,
        val desc: String,
        val startTime: Long,
        val endTime: Long,
        val instanceStartTime: Long,
        val instanceEndTime: Long,
        val location: String,
        val timeZone: String,
        val color: Int = 0,
        val eventStatus: EventStatus = EventStatus.Confirmed,
        val attendanceStatus: AttendanceStatus = AttendanceStatus.None,

        // dynamic
        var notificationId: Int  = 0,
        var lastStatusChangeTime: Long = 0,
        var snoozedUntil: Long = 0,
        var displayStatus: EventDisplayStatus = EventDisplayStatus.Hidden,
        var origin: EventOrigin = EventOrigin.ProviderBroadcast,
        var timeFirstSeen: Long = 0L,
        var flags: Long = 0
) {
    fun monitorEntryKeyEquas(monitorEventAlertEntry: MonitorEventAlertEntry): Boolean {
        return monitorEventAlertEntry.keyEquals(this)
    }

    val monitorEntryKey: MonitorEventAlertEntryKey
        get() = MonitorEventAlertEntryKey(alertTime, instanceStartTime, contentMd5)

    // not including alertTime, so multiple alerts for the same event would have identical contentMd5
    val contentMd5: md5state by lazy {
        eventContentHash(
                calendarId, isAllDay, color,
                instanceStartTime, instanceEndTime,
                eventStatus, attendanceStatus,
                title, desc, location, timeZone,
                rRule, rDate, exRRule, exRDate
        )
    }

    var isAlarm: Boolean
        get() = flags.isFlagSet(EventAlertFlags.IS_ALARM)
        set(value) { flags = flags.setFlag(EventAlertFlags.IS_ALARM, value) }

    val key: EventAlertRecordKey
        get() = EventAlertRecordKey(eventId, instanceStartTime)

    val titleAsOneLine: String by lazy { title.replace("\r\n", " ").replace("\n", " ")}

    val isRepeating: Boolean
        get() = rRule.isNotEmpty() || rDate.isNotEmpty()
}

fun EventAlertRecord.updateFrom(newEvent: EventAlertRecord): EventAlertRecord? {

    if ((title != newEvent.title)
            || (desc != newEvent.desc)
            || (alertTime != newEvent.alertTime)
            || (startTime != newEvent.startTime)
            || (endTime != newEvent.endTime)
            || (instanceStartTime != newEvent.instanceStartTime)
            || (instanceEndTime != newEvent.instanceEndTime)
            || (isAllDay != newEvent.isAllDay)
            || (location != newEvent.location)
            || (color != newEvent.color)
            || (rRule != newEvent.rRule)
            || (rDate != newEvent.rDate)
            || (exRRule != newEvent.exRRule)
            || (exRDate != newEvent.exRDate)
            || (eventStatus != newEvent.eventStatus)
            || (attendanceStatus != newEvent.attendanceStatus)
    ) {
        return this.copy(
                title = newEvent.title,
                desc= newEvent.desc,
                alertTime= newEvent.alertTime,
                startTime= newEvent.startTime,
                endTime= newEvent.endTime,
                instanceStartTime= newEvent.instanceStartTime,
                instanceEndTime= newEvent.instanceEndTime,
                isAllDay= newEvent.isAllDay,
                location= newEvent.location,
                color= newEvent.color,
                rRule= newEvent.rRule,
                rDate= newEvent.rDate,
                exRRule= newEvent.exRRule,
                exRDate= newEvent.exRDate,
                eventStatus= newEvent.eventStatus,
                attendanceStatus= newEvent.attendanceStatus
        )
    }
    return null
}

fun EventAlertRecord.updateFrom(newEvent: EventRecord): EventAlertRecord? {

    if ((title != newEvent.title)
            || (desc != newEvent.desc)
            || (location != newEvent.location)
            || (startTime != newEvent.startTime)
            || (endTime != newEvent.endTime)
            || (color != newEvent.color)
            || (isAllDay != newEvent.isAllDay)
            || (rRule != newEvent.rRule)
            || (rDate != newEvent.rDate)
            || (exRRule != newEvent.exRRule)
            || (exRDate != newEvent.exRDate)
            || (eventStatus != newEvent.eventStatus)
            || (attendanceStatus != newEvent.attendanceStatus)
    ) {
        return this.copy(
                title = newEvent.title,
                desc= newEvent.desc,
                location= newEvent.location,
                startTime= newEvent.startTime,
                endTime= newEvent.endTime,
                isAllDay= newEvent.isAllDay,
                rRule= newEvent.rRule,
                rDate= newEvent.rDate,
                exRRule= newEvent.exRRule,
                exRDate= newEvent.exRDate,
                color= newEvent.color,
                eventStatus= newEvent.eventStatus,
                attendanceStatus= newEvent.attendanceStatus
        )
    }
    return null
}

val EventAlertRecord.displayedStartTime: Long
    get() = if (instanceStartTime != 0L) instanceStartTime else startTime

val EventAlertRecord.displayedEndTime: Long
    get() = if (instanceEndTime != 0L) instanceEndTime else endTime

val EventAlertRecord.isSnoozed: Boolean
    get() = snoozedUntil != 0L

val EventAlertRecord.isNotSnoozed: Boolean
    get() = snoozedUntil == 0L

val EventAlertRecord.isActiveAlarm: Boolean
    get() = isNotSnoozed  && isAlarm

val EventAlertRecord.scanMissedTotalEvents: Long
    get() {
        if (instanceStartTime == Long.MAX_VALUE)
            return alertTime
        else
            return 0L
    }

val EventAlertRecord.isCancelledOrDeclined: Boolean
    get() = eventStatus == EventStatus.Cancelled || attendanceStatus == AttendanceStatus.Declined


enum class EventFinishType(val code: Int) {
    ManuallyViaNotification(0),
    ManuallyInTheApp(1),
    AutoDueToCalendarMove(2),
    EventMovedInTheApp(3),
    DeletedInTheApp(4);

    companion object {
        @JvmStatic
        fun fromInt(v: Int) = values()[v]
    }
}

data class FinishedEventAlertRecord(
        val event: EventAlertRecord, // actual event that was dismissed
        val finishTime: Long, // when dismissal happened
        val finishType: EventFinishType  // type of dismiss
)