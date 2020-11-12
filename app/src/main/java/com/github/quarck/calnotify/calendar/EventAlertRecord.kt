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

package com.github.quarck.calnotify.calendar

import android.provider.CalendarContract

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
        val calendarId: Long,
        val eventId: Long,
        var isAllDay: Boolean,
        var isRepeating: Boolean,
        var alertTime: Long,
        var notificationId: Int,
        var title: String,
        var desc: String,
        var startTime: Long,
        var endTime: Long,
        var instanceStartTime: Long,
        var instanceEndTime: Long,
        var location: String,
        var lastStatusChangeTime: Long,
        var snoozedUntil: Long = 0,
        var displayStatus: EventDisplayStatus = EventDisplayStatus.Hidden,
        var color: Int = 0,
        var origin: EventOrigin = EventOrigin.ProviderBroadcast,
        var timeFirstSeen: Long = 0L,
        var eventStatus: EventStatus = EventStatus.Confirmed,
        var attendanceStatus: AttendanceStatus = AttendanceStatus.None,
        var flags: Long = 0
) {
    var isAlarm: Boolean
        get() = flags.isFlagSet(EventAlertFlags.IS_ALARM)
        set(value) { flags = flags.setFlag(EventAlertFlags.IS_ALARM, value) }

    val key: EventAlertRecordKey
        get() = EventAlertRecordKey(eventId, instanceStartTime)

    val titleAsOneLine: String by lazy { title.replace("\r\n", " ").replace("\n", " ")}
}

fun EventAlertRecord.updateFrom(newEvent: EventAlertRecord): Boolean {
    var ret = false

    if (title != newEvent.title) {
        title = newEvent.title
        ret = true
    }

    if (desc != newEvent.desc) {
        desc = newEvent.desc
        ret = true
    }

    if (alertTime != newEvent.alertTime) {
        alertTime = newEvent.alertTime
        ret = true
    }

    if (startTime != newEvent.startTime) {
        startTime = newEvent.startTime
        ret = true
    }

    if (endTime != newEvent.endTime) {
        endTime = newEvent.endTime
        ret = true
    }

    if (instanceStartTime != newEvent.instanceStartTime) {
        instanceStartTime = newEvent.instanceStartTime
        ret = true
    }

    if (instanceEndTime != newEvent.instanceEndTime) {
        instanceEndTime = newEvent.instanceEndTime
        ret = true
    }


    if (isAllDay != newEvent.isAllDay) {
        isAllDay = newEvent.isAllDay
        ret = true
    }

    if (location != newEvent.location) {
        location = newEvent.location
        ret = true
    }

    if (color != newEvent.color) {
        color = newEvent.color
        ret = true
    }

    if (isRepeating != newEvent.isRepeating) { // only for upgrading from prev versions of DB
        isRepeating = newEvent.isRepeating
        ret = true
    }

    if (eventStatus != newEvent.eventStatus) {
        eventStatus = newEvent.eventStatus
        ret = true
    }

    if (attendanceStatus != newEvent.attendanceStatus) {
        attendanceStatus = newEvent.attendanceStatus
        ret = true
    }

    return ret
}

fun EventAlertRecord.updateFrom(newEvent: EventRecord): Boolean {
    var ret = false

    if (title != newEvent.title) {
        title = newEvent.title
        ret = true
    }

    if (desc != newEvent.desc) {
        desc = newEvent.desc
        ret = true
    }

    if (location != newEvent.location) {
        location = newEvent.location
        ret = true
    }

    if (startTime != newEvent.startTime) {
        startTime = newEvent.startTime
        ret = true
    }

    if (endTime != newEvent.endTime) {
        endTime = newEvent.endTime
        ret = true
    }

    if (color != newEvent.color) {
        color = newEvent.color
        ret = true
    }

    if (isAllDay != newEvent.isAllDay) {
        isAllDay = newEvent.isAllDay
        ret = true
    }

    if (eventStatus != newEvent.eventStatus) {
        eventStatus = newEvent.eventStatus
        ret = true
    }

    if (attendanceStatus != newEvent.attendanceStatus) {
        attendanceStatus = newEvent.attendanceStatus
        ret = true
    }

    return ret
}

fun EventAlertRecord.updateFromWithoutTime(newEvent: EventRecord): Boolean {
    var ret = false

    if (title != newEvent.title) {
        title = newEvent.title
        ret = true
    }

    if (color != newEvent.color) {
        color = newEvent.color
        ret = true
    }

    if (isAllDay != newEvent.isAllDay) {
        isAllDay = newEvent.isAllDay
        ret = true
    }

    if (eventStatus != newEvent.eventStatus) {
        eventStatus = newEvent.eventStatus
        ret = true
    }

    if (attendanceStatus != newEvent.attendanceStatus) {
        attendanceStatus = newEvent.attendanceStatus
        ret = true
    }

    return ret
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



enum class EventCompletionType(val code: Int) {
    ManuallyViaNotification(0),
    ManuallyInTheApp(1),
    AutoDueToCalendarMove(2),
    EventMovedInTheApp(3);

    companion object {
        @JvmStatic
        fun fromInt(v: Int) = values()[v]
    }
}

data class CompleteEventAlertRecord(
        val event: EventAlertRecord, // actual event that was dismissed
        val completionTime: Long, // when dismissal happened
        val completionType: EventCompletionType  // type of dismiss
)