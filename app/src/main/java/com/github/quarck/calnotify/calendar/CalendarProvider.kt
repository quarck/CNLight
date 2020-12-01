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

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.permissions.PermissionsManager
import com.github.quarck.calnotify.utils.detailed
import com.github.quarck.calnotify.utils.logs.DevLog
import java.util.*

object CalendarProviderHelper {

    internal enum class DurationParserState {
        Start, InDigit, InTimeUnit
    }

    fun parseRfc2445Duration(duration: String): Long {
        var sign = 1
        var accumulatedDurationSeconds = 0L
        var currentNum = 0L
        var state = DurationParserState.Start

        for (c in duration) {
            when (state) {
                DurationParserState.Start ->
                    when (c) {
                        '+' -> sign = 1
                        '-' -> sign = -1
                        'P' -> state = DurationParserState.InDigit
                        else -> throw Exception("Invalid duration $duration")
                    }
                DurationParserState.InDigit ->
                    when (c) {
                        'W' -> {
                            accumulatedDurationSeconds += currentNum * 7 * 24 * 3600
                            state = DurationParserState.InTimeUnit
                        }
                        'D' -> {
                            accumulatedDurationSeconds += currentNum * 24 * 3600
                            state = DurationParserState.InTimeUnit
                        }
                        'H' -> {
                            accumulatedDurationSeconds += currentNum * 3600
                            state = DurationParserState.InTimeUnit
                        }
                        'M' -> {
                            accumulatedDurationSeconds += currentNum * 60
                            state = DurationParserState.InTimeUnit
                        }
                        'S' -> {
                            accumulatedDurationSeconds += currentNum
                            state = DurationParserState.InTimeUnit
                        }
                        else -> {
                            if (!c.isDigit()) throw Exception("Invalid duration $duration")
                            currentNum = currentNum * 10 + c.toInt() - '0'.toInt()
                        }

                    }
                DurationParserState.InTimeUnit ->
                    when (c) {
                        'T' -> { } // just skip - useless noise
                        else -> {
                            if (!c.isDigit()) throw Exception("Invalid duration $duration")
                            currentNum = c.toLong() - '0'.toLong()
                            state = DurationParserState.InDigit
                        }
                    }
            }
        }

        return sign * accumulatedDurationSeconds * 1000L
    }

    fun encodeRfc2445Duration(durationMillis: Long) = "P${durationMillis/1000L}S"
}

@SuppressLint("MissingPermission")
object CalendarProvider  {
    private const val LOG_TAG = "CalendarProvider"

    private val alertFields =
            arrayOf(
                    CalendarContract.CalendarAlerts.EVENT_ID,
                    CalendarContract.CalendarAlerts.STATE,
                    CalendarContract.Events.CALENDAR_ID,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DESCRIPTION,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.EVENT_LOCATION,
                    CalendarContract.Events.DISPLAY_COLOR,
                    CalendarContract.CalendarAlerts.ALARM_TIME,
                    CalendarContract.CalendarAlerts.BEGIN,
                    CalendarContract.CalendarAlerts.END,
                    CalendarContract.Events.ALL_DAY,
                    CalendarContract.Events.RRULE,
                    CalendarContract.Events.RDATE,
                    CalendarContract.Events.EXRULE,
                    CalendarContract.Events.EXDATE,
                    CalendarContract.CalendarAlerts.STATUS,
                    CalendarContract.CalendarAlerts.SELF_ATTENDEE_STATUS,
                    CalendarContract.Events.EVENT_TIMEZONE
            )

    const val ALERT_PROJECTION_INDEX_EVENT_ID = 0
    const val ALERT_PROJECTION_INDEX_STATE = 1
    const val ALERT_PROJECTION_INDEX_CALENDAR_ID = 2
    const val ALERT_PROJECTION_INDEX_TITLE = 3
    const val ALERT_PROJECTION_INDEX_DESCRIPTION = 4
    const val ALERT_PROJECTION_INDEX_DTSTART = 5
    const val ALERT_PROJECTION_INDEX_DTEND = 6
    const val ALERT_PROJECTION_INDEX_LOCATION = 7
    const val ALERT_PROJECTION_INDEX_COLOR = 8
    const val ALERT_PROJECTION_INDEX_ALARM_TIME = 9
    const val ALERT_PROJECTION_INDEX_INSTANCE_BEGIN = 10
    const val ALERT_PROJECTION_INDEX_INSTANCE_END = 11
    const val ALERT_PROJECTION_INDEX_ALL_DAY = 12
    const val ALERT_PROJECTION_INDEX_RRULE = 13
    const val ALERT_PROJECTION_INDEX_RDATE = 14
    const val ALERT_PROJECTION_INDEX_EXRRULE = 15
    const val ALERT_PROJECTION_INDEX_EXRDATE = 16
    const val ALERT_PROJECTION_INDEX_STATUS = 17
    const val ALERT_PROJECTION_INDEX_ATTENDANCE_STATUS = 18
    const val ALERT_PROJECTION_INDEX_TIMEZONE= 19

    private val eventFields =
            arrayOf(
                    CalendarContract.Events.CALENDAR_ID,            // 0
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DESCRIPTION,
                    CalendarContract.Events.EVENT_TIMEZONE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.RRULE,
                    CalendarContract.Events.RDATE,
                    CalendarContract.Events.EXRULE,
                    CalendarContract.Events.EXDATE,
                    CalendarContract.Events.ALL_DAY,
                    CalendarContract.Events.EVENT_LOCATION,
                    CalendarContract.Events.DISPLAY_COLOR,
                    CalendarContract.Events.STATUS,
                    CalendarContract.Events.SELF_ATTENDEE_STATUS,
                    CalendarContract.Events.LAST_DATE,
                    CalendarContract.Events.DURATION
            )

    const val EVENT_PROJECTTION_INDEX_CALENDAR_ID = 0
    const val EVENT_PROJECTTION_INDEX_TITLE = 1
    const val EVENT_PROJECTTION_INDEX_DESC = 2
    const val EVENT_PROJECTTION_INDEX_TIME_ZONE = 3
    const val EVENT_PROJECTTION_INDEX_START = 4
    const val EVENT_PROJECTTION_INDEX_END = 5
    const val EVENT_PROJECTTION_INDEX_RRULE = 6
    const val EVENT_PROJECTTION_INDEX_RDATE = 7
    const val EVENT_PROJECTTION_INDEX_EXRRULE = 8
    const val EVENT_PROJECTTION_INDEX_EXRDATE = 9
    const val EVENT_PROJECTTION_INDEX_ALL_DAY = 10
    const val EVENT_PROJECTTION_INDEX_LOCATION = 11
    const val EVENT_PROJECTTION_INDEX_COLOR = 12
    const val EVENT_PROJECTTION_INDEX_STATUS = 13
    const val EVENT_PROJECTTION_INDEX_ATTENDANCE = 14
    const val EVENT_PROJECTTION_INDEX_LAST_DATE = 15
    const val EVENT_PROJECTTION_INDEX_DURATION = 16

    private val instanceFields =
            arrayOf(
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Events.CALENDAR_ID,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Events.TITLE,
                    CalendarContract.Events.DESCRIPTION,
                    CalendarContract.Events.EVENT_TIMEZONE,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.DTEND,
                    CalendarContract.Events.RRULE,
                    CalendarContract.Events.RDATE,
                    CalendarContract.Events.EXRULE,
                    CalendarContract.Events.EXDATE,
                    CalendarContract.Events.ALL_DAY,
                    CalendarContract.Events.EVENT_LOCATION,
                    CalendarContract.Events.DISPLAY_COLOR,
                    CalendarContract.Events.STATUS,
                    CalendarContract.Events.SELF_ATTENDEE_STATUS
            )

    const val INSTANCE_PROJECTION_INDEX_EVENT_ID = 0
    const val INSTANCE_PROJECTION_INDEX_CALENDAR_ID = 1
    const val INSTANCE_PROJECTION_INDEX_BEGIN = 2
    const val INSTANCE_PROJECTION_INDEX_END = 3
    const val INSTANCE_PROJECTION_INDEX_TITLE = 4
    const val INSTANCE_PROJECTION_INDEX_DESCRIPTION = 5
    const val INSTANCE_PROJECTION_INDEX_EVENT_TIMEZONE = 6
    const val INSTANCE_PROJECTION_INDEX_DTSTART = 7
    const val INSTANCE_PROJECTION_INDEX_DTEND = 8
    const val INSTANCE_PROJECTION_INDEX_RRULE = 9
    const val INSTANCE_PROJECTION_INDEX_RDATE = 10
    const val INSTANCE_PROJECTION_INDEX_EXRULE = 11
    const val INSTANCE_PROJECTION_INDEX_EXDATE = 12
    const val INSTANCE_PROJECTION_INDEX_ALL_DAY = 13
    const val INSTANCE_PROJECTION_INDEX_EVENT_LOCATION = 14
    const val INSTANCE_PROJECTION_INDEX_DISPLAY_COLOR = 15
    const val INSTANCE_PROJECTION_INDEX_STATUS = 16
    const val INSTANCE_PROJECTION_INDEX_SELF_ATTENDEE_STATUS = 17

    private fun cursorToAlertRecord(cursor: Cursor, alarmTime: Long?): Pair<Int?, EventAlertRecord?> {

        val eventId: Long? = cursor.getLong(ALERT_PROJECTION_INDEX_EVENT_ID)
        val state: Int? = cursor.getInt(ALERT_PROJECTION_INDEX_STATE)
        val title: String? = cursor.getString(ALERT_PROJECTION_INDEX_TITLE)
        val desc: String? = cursor.getString(ALERT_PROJECTION_INDEX_DESCRIPTION)
        val startTime: Long? = cursor.getLong(ALERT_PROJECTION_INDEX_DTSTART)
        val endTime: Long? = cursor.getLong(ALERT_PROJECTION_INDEX_DTEND)
        val location: String? = cursor.getString(ALERT_PROJECTION_INDEX_LOCATION)
        val color: Int? = cursor.getInt(ALERT_PROJECTION_INDEX_COLOR)
        val newAlarmTime: Long? = cursor.getLong(ALERT_PROJECTION_INDEX_ALARM_TIME)
        val calendarId: Long? = cursor.getLong(ALERT_PROJECTION_INDEX_CALENDAR_ID)

        val instanceStart: Long? = cursor.getLong(ALERT_PROJECTION_INDEX_INSTANCE_BEGIN)
        val instanceEnd: Long? = cursor.getLong(ALERT_PROJECTION_INDEX_INSTANCE_END)
        val allDay: Int? = cursor.getInt(ALERT_PROJECTION_INDEX_ALL_DAY)

        val rRule: String? = cursor.getString(ALERT_PROJECTION_INDEX_RRULE)
        val rDate: String? = cursor.getString(ALERT_PROJECTION_INDEX_RDATE)
        val exRRule: String? = cursor.getString(ALERT_PROJECTION_INDEX_EXRRULE)
        val exRDate: String? = cursor.getString(ALERT_PROJECTION_INDEX_EXRDATE)

        val status: Int? = cursor.getInt(ALERT_PROJECTION_INDEX_STATUS)
        val attendance: Int? = cursor.getInt(ALERT_PROJECTION_INDEX_ATTENDANCE_STATUS)

        val timeZone: String? = cursor.getString(ALERT_PROJECTION_INDEX_TIMEZONE)

        if (eventId == null || state == null || title == null || startTime == null)
            return Pair(null, null)

        val event =
                EventAlertRecord(
                        calendarId = calendarId ?: -1L,
                        eventId = eventId,
                        isAllDay = (allDay ?: 0) != 0,
                        rRule = rRule ?: "",
                        rDate = rDate ?: "",
                        exRRule = exRRule ?: "",
                        exRDate = exRDate ?: "",
                        notificationId = 0,
                        alertTime = alarmTime ?: newAlarmTime ?: 0,
                        title = title,
                        desc = desc ?: "",
                        startTime = startTime,
                        endTime = endTime ?: 0L,
                        instanceStartTime = instanceStart ?: 0L,
                        instanceEndTime = instanceEnd ?: 0L,
                        location = location ?: "",
                        lastStatusChangeTime = 0L,
                        displayStatus = EventDisplayStatus.Hidden,
                        color = color ?: Consts.DEFAULT_CALENDAR_EVENT_COLOR,
                        eventStatus = EventStatus.fromInt(status),
                        attendanceStatus = AttendanceStatus.fromInt(attendance),
                        timeZone = timeZone ?: ""
                )

        return Pair(state, event)
    }

    private fun cursorToEventRecord(cursor: Cursor, eventId: Long): EventRecord? {
        val calendarId: Long? = cursor.getLong(EVENT_PROJECTTION_INDEX_CALENDAR_ID)
        val title: String? = cursor.getString(EVENT_PROJECTTION_INDEX_TITLE)
        val desc: String? = cursor.getString(EVENT_PROJECTTION_INDEX_DESC)
        val timeZone: String? = cursor.getString(EVENT_PROJECTTION_INDEX_TIME_ZONE)
        val start: Long? = cursor.getLong(EVENT_PROJECTTION_INDEX_START)
        var end: Long? = cursor.getLong(EVENT_PROJECTTION_INDEX_END)

        val rRule: String? = cursor.getString(EVENT_PROJECTTION_INDEX_RRULE)
        val rDate: String? = cursor.getString(EVENT_PROJECTTION_INDEX_RDATE)
        val exRRule: String? = cursor.getString(EVENT_PROJECTTION_INDEX_EXRRULE)
        val exRDate: String? = cursor.getString(EVENT_PROJECTTION_INDEX_EXRDATE)

        var allDay: Int? = cursor.getInt(EVENT_PROJECTTION_INDEX_ALL_DAY)
        val location: String? = cursor.getString(EVENT_PROJECTTION_INDEX_LOCATION)
        val color: Int? = cursor.getInt(EVENT_PROJECTTION_INDEX_COLOR)
        val status: Int? = cursor.getInt(EVENT_PROJECTTION_INDEX_STATUS)
        val attendance: Int? = cursor.getInt(EVENT_PROJECTTION_INDEX_ATTENDANCE)
        val lastDate: Long? = cursor.getLong(EVENT_PROJECTTION_INDEX_LAST_DATE)
        val duration: String? = cursor.getString(EVENT_PROJECTTION_INDEX_DURATION)

        if (title != null && start != null) {

            allDay = allDay ?: 0

            if (end == null) {
                if (allDay == 0)
                    end = start + Consts.HOUR_IN_MILLISECONDS
                else
                    end = start + Consts.DAY_IN_MILLISECONDS
            }

            return EventRecord(
                    calendarId = calendarId ?: -1L,
                    eventId = eventId,
                    details = CalendarEventDetails(
                            desc = desc ?: "",
                            location = location ?: "",
                            timezone = timeZone ?: "",
                            startTime = start,
                            endTime = end,
                            isAllDay = allDay != 0,
                            reminders = listOf<EventReminderRecord>(),
                            rRule = rRule ?: "",
                            rDate = rDate ?: "",
                            exRRule = exRRule ?: "",
                            exRDate = exRDate ?: "",
                            color = color ?: Consts.DEFAULT_CALENDAR_EVENT_COLOR,
                            title = title,
                            lastDate = lastDate,
                            duration = duration?.let{ CalendarProviderHelper.parseRfc2445Duration(it) }
                    ),
                    eventStatus = EventStatus.fromInt(status),
                    attendanceStatus = AttendanceStatus.fromInt(attendance)
            )
        }
        return null
    }

    private fun cursorToEventInstance(cursor: Cursor): EventAlertRecord? {

        val calendarId: Long? = cursor.getLong(INSTANCE_PROJECTION_INDEX_CALENDAR_ID)
        val eventId: Long? = cursor.getLong(INSTANCE_PROJECTION_INDEX_EVENT_ID)
        val title: String? = cursor.getString(INSTANCE_PROJECTION_INDEX_TITLE)
        val desc: String? = cursor.getString(INSTANCE_PROJECTION_INDEX_DESCRIPTION)
        val timeZone: String? = cursor.getString(INSTANCE_PROJECTION_INDEX_EVENT_TIMEZONE)
        val instanceStart: Long? = cursor.getLong(INSTANCE_PROJECTION_INDEX_BEGIN)
        var instanceEnd: Long? = cursor.getLong(INSTANCE_PROJECTION_INDEX_END)
        var start: Long? = cursor.getLong(INSTANCE_PROJECTION_INDEX_DTSTART)
        var end: Long? = cursor.getLong(INSTANCE_PROJECTION_INDEX_DTEND)

        val rRule: String? = cursor.getString(INSTANCE_PROJECTION_INDEX_RRULE)
        val rDate: String? = cursor.getString(INSTANCE_PROJECTION_INDEX_RDATE)
        val exRRule: String? = cursor.getString(INSTANCE_PROJECTION_INDEX_EXRULE)
        val exRDate: String? = cursor.getString(INSTANCE_PROJECTION_INDEX_EXDATE)

        var allDay: Int? = cursor.getInt(INSTANCE_PROJECTION_INDEX_ALL_DAY)
        val location: String? = cursor.getString(INSTANCE_PROJECTION_INDEX_EVENT_LOCATION)
        val color: Int? = cursor.getInt(INSTANCE_PROJECTION_INDEX_DISPLAY_COLOR)
        val status: Int? = cursor.getInt(INSTANCE_PROJECTION_INDEX_STATUS)
        val attendance: Int? = cursor.getInt(INSTANCE_PROJECTION_INDEX_SELF_ATTENDEE_STATUS)

        if (title != null && instanceStart != null && calendarId != null && eventId != null) {
            allDay = allDay ?: 0
            start = start ?: instanceStart
            end = end ?: start +
                    (if (allDay == 0)  Consts.HOUR_IN_MILLISECONDS  else Consts.DAY_IN_MILLISECONDS)
            instanceEnd = instanceEnd ?: instanceStart +
                    (if (allDay == 0)  Consts.HOUR_IN_MILLISECONDS  else Consts.DAY_IN_MILLISECONDS)

            return EventAlertRecord(
                    calendarId = calendarId,
                    eventId = eventId,
                    isAllDay = allDay != 0,
                    rRule = rRule ?: "",
                    rDate = rDate ?: "",
                    exRRule = exRRule ?: "",
                    exRDate = exRDate ?: "",
                    notificationId = 0,
                    alertTime = 0,
                    title = title,
                    desc = desc ?: "",
                    startTime = start,
                    endTime = end,
                    instanceStartTime = instanceStart,
                    instanceEndTime = instanceEnd,
                    location = location ?: "",
                    lastStatusChangeTime = 0L,
                    timeZone = timeZone ?: "",
                    displayStatus = EventDisplayStatus.Hidden,
                    color = color ?: Consts.DEFAULT_CALENDAR_EVENT_COLOR,
                    eventStatus = EventStatus.fromInt(status),
                    attendanceStatus = AttendanceStatus.fromInt(attendance)

            )
        }
        return null
    }

    fun getAlertByTime(context: Context, alertTime: Long, skipDismissed: Boolean): List<EventAlertRecord> {

        if (!PermissionsManager.hasReadCalendar(context)) {
            DevLog.error(LOG_TAG, "getAlertByTime: has no permissions")
            return listOf()
        }

        val ret = arrayListOf<EventAlertRecord>()

        val selection = CalendarContract.CalendarAlerts.ALARM_TIME + "=?"

        val cursor: Cursor? =
                context.contentResolver.query(
                        CalendarContract.CalendarAlerts.CONTENT_URI_BY_INSTANCE,
                        alertFields,
                        selection,
                        arrayOf(alertTime.toString()),
                        null
                )

        if (cursor != null && cursor.moveToFirst()) {
            do {
                val (state, event) = cursorToAlertRecord(cursor, alertTime)

                if (state != null && event != null) {
                    if (!skipDismissed || state != CalendarContract.CalendarAlerts.STATE_DISMISSED) {
                        DevLog.info(LOG_TAG, "Read event ${event.eventId}, st $state, time: [${event.startTime},${event.endTime}]")
                        ret.add(event)
                    }
                    else {
                        DevLog.info(LOG_TAG, "Read event ${event.eventId}, st $state, time: [${event.startTime},${event.endTime}] - already dismissed in provider, ignoring")
                    }
                }
                else {
                    DevLog.error(LOG_TAG, "Failed to interpret query output, alertTime=$alertTime")
                }

            } while (cursor.moveToNext())
        }
        else {
            DevLog.error(LOG_TAG, "No requests at $alertTime")
        }

        cursor?.close()

        return ret
    }

    fun getAlertByEventIdAndTime(context: Context, eventId: Long, alertTime: Long): EventAlertRecord? {

        if (!PermissionsManager.hasReadCalendar(context)) {
            DevLog.error(LOG_TAG, "getAlertByEventIdAndTime: has no permissions")
            return null
        }

        var ret: EventAlertRecord? = null

        val selection = CalendarContract.CalendarAlerts.ALARM_TIME + "=?"

        val cursor: Cursor? =
                context.contentResolver.query(
                        CalendarContract.CalendarAlerts.CONTENT_URI_BY_INSTANCE,
                        alertFields,
                        selection,
                        arrayOf(alertTime.toString()),
                        null
                )

        if (cursor != null && cursor.moveToFirst()) {
            do {
                val (_, event) = cursorToAlertRecord(cursor, alertTime)

                if (event != null && event.eventId == eventId) {
                    ret = event
                    break
                }

            } while (cursor.moveToNext())
        }
        else {
            DevLog.error(LOG_TAG, "Event $eventId not found")
        }

        cursor?.close()

        return ret
    }

    fun getEventReminders(context: Context, eventId: Long): List<EventReminderRecord> {

        val ret = mutableListOf<EventReminderRecord>()

        var cursor: Cursor? = null

        try {
            val fields = arrayOf(
                    CalendarContract.Reminders.MINUTES,
                    CalendarContract.Reminders.METHOD)

            val selection = "${CalendarContract.Reminders.EVENT_ID} = ?"

            val selectionArgs = arrayOf(eventId.toString())

            cursor = context.contentResolver.query(
                    CalendarContract.Reminders.CONTENT_URI,
                    fields,
                    selection,
                    selectionArgs,
                    null)

            while (cursor != null && cursor.moveToNext()) {
                //
                val minutes: Long? = cursor.getLong(0)
                val method: Int? = cursor.getInt(1)

                if (minutes != null && minutes != -1L && method != null) {
                    ret.add(
                            EventReminderRecord(
                                    minutes * Consts.MINUTE_IN_SECONDS * 1000L,
                                    method))
                }
            }
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "Exception while reading event $eventId reminders: ${ex.detailed}")
        }
        finally {
            cursor?.close()
        }

        return ret
    }

    fun getEventLocalReminders(context: Context, eventId: Long): List<Long> {

        val ret = mutableListOf<Long>()

        val fields = arrayOf(CalendarContract.Reminders.MINUTES)

        val selection = "${CalendarContract.Reminders.EVENT_ID} = ?" +
                " AND ${CalendarContract.Reminders.METHOD} != ${CalendarContract.Reminders.METHOD_EMAIL}" +
                " AND ${CalendarContract.Reminders.METHOD} != ${CalendarContract.Reminders.METHOD_SMS}"

        val selectionArgs = arrayOf(eventId.toString())

        val cursor = context.contentResolver.query(
                CalendarContract.Reminders.CONTENT_URI,
                fields,
                selection,
                selectionArgs,
                null)

        while (cursor != null && cursor.moveToNext()) {
            //
            val minutes: Long? = cursor.getLong(0)

            if (minutes != null && minutes != -1L) {
                ret.add(minutes * Consts.MINUTE_IN_SECONDS * 1000L)
            }
        }

        cursor?.close()

        return ret
    }

    fun getNextEventReminderTime(context: Context, eventId: Long, instanceStartTime: Long): Long {

        val instanceStart = instanceStartTime

        val currentTime = System.currentTimeMillis()

        val nextReminder =
                getEventLocalReminders(context, eventId)
                        .map { reminder -> instanceStart - reminder }
                        .filter { reminder -> reminder > currentTime + Consts.ALARM_THRESHOLD }
                        .minOrNull()

        return nextReminder ?: 0L
    }

    fun getNextEventReminderTime(context: Context, event: EventAlertRecord): Long {

        val instanceStart = event.instanceStartTime

        val currentTime = System.currentTimeMillis()

        val nextReminder =
                getEventLocalReminders(context, event.eventId)
                        .map { reminder -> instanceStart - reminder }
                        .filter { reminder -> reminder > currentTime + Consts.ALARM_THRESHOLD }
                        .minOrNull()

        return nextReminder ?: 0L
    }

    fun getEvent(context: Context, eventId: Long): EventRecord? {

        if (!PermissionsManager.hasReadCalendar(context)) {
            DevLog.error(LOG_TAG, "getEvent: has no permissions")
            return null
        }

        var ret: EventRecord? = null

        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)

        val cursor: Cursor? =
                context.contentResolver.query(
                        uri, // CalendarContract.CalendarAlerts.CONTENT_URI,
                        eventFields,
                        null, //selection,
                        null, //arrayOf(eventId.toString()),
                        null
                )

        if (cursor != null && cursor.moveToFirst()) {
            ret = cursorToEventRecord(cursor, eventId)
        }
        if (ret == null) {
            DevLog.error(LOG_TAG, "Event $eventId not found")
        }

        cursor?.close()

        try {
            if (ret != null)
                ret.reminders = getEventReminders(context, eventId)
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "Exception while trying to read reminders for $eventId: ${ex.detailed}")
        }

        return ret
    }

    fun dismissNativeEventAlert(context: Context, eventId: Long) {

        if (!PermissionsManager.hasWriteCalendar(context)) {
            DevLog.error(LOG_TAG, "dismissNativeEventAlert: has no permissions")
            return
        }

        try {
            val uri = CalendarContract.CalendarAlerts.CONTENT_URI

            val selection =
                    "(" +
                            "${CalendarContract.CalendarAlerts.STATE}=${CalendarContract.CalendarAlerts.STATE_FIRED}" +
                            " OR " +
                            "${CalendarContract.CalendarAlerts.STATE}=${CalendarContract.CalendarAlerts.STATE_SCHEDULED}" +
                            ")" +
                            " AND ${CalendarContract.CalendarAlerts.EVENT_ID}=$eventId"

            val dismissValues = ContentValues()
            dismissValues.put(
                    CalendarContract.CalendarAlerts.STATE,
                    CalendarContract.CalendarAlerts.STATE_DISMISSED
            )

            context.contentResolver.update(uri, dismissValues, selection, null)

            DevLog.debug(LOG_TAG, "dismissNativeEventReminder: eventId $eventId")
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "dismissNativeReminder failed: ${ex.detailed}")
        }
    }

    fun createEvent(context: Context, calendarId: Long, calendarOwnerAccount: String, details: CalendarEventDetails): Long {

        var eventId = -1L

        DevLog.debug(LOG_TAG, "Request to create Event, startTime: ${details.startTime}, endTime: ${details.endTime}, reminder: ${details.reminders}")

        if (!PermissionsManager.hasAllPermissions(context)) {
            DevLog.error(LOG_TAG, "createEvent: no permissions")
            return -1
        }

        val values = ContentValues()

        values.put(CalendarContract.Events.TITLE, details.title)
        values.put(CalendarContract.Events.CALENDAR_ID, calendarId)
        values.put(CalendarContract.Events.EVENT_TIMEZONE, details.timezone) // Irish summer time
        values.put(CalendarContract.Events.DESCRIPTION, details.desc)

        values.put(CalendarContract.Events.DTSTART, details.startTime)
        if (details.endTime != 0L)
            values.put(CalendarContract.Events.DTEND, details.endTime)
        if (details.duration != null)
            values.put(CalendarContract.Events.DURATION, CalendarProviderHelper.encodeRfc2445Duration(details.duration))

        if (details.lastDate != null)
            values.put(CalendarContract.Events.LAST_DATE, details.lastDate)

        values.put(CalendarContract.Events.EVENT_LOCATION, details.location)

        if (details.color != 0)
            values.put(CalendarContract.Events.EVENT_COLOR, details.color) // just something

        values.put(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_DEFAULT)
        values.put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)

        values.put(CalendarContract.Events.HAS_ALARM, 1)
        values.put(CalendarContract.Events.ALL_DAY, if (details.isAllDay) 1 else 0)

        if (details.rRule != "")
            values.put(CalendarContract.Events.RRULE, details.rRule)
        if (details.rDate != "")
            values.put(CalendarContract.Events.RDATE, details.rDate)

        if (details.exRRule != "")
            values.put(CalendarContract.Events.EXRULE, details.exRRule)
        if (details.exRDate != "")
            values.put(CalendarContract.Events.EXDATE, details.exRDate)



        values.put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
        //values.put(CalendarContract.Events.SELF_ATTENDEE_STATUS, CalendarContract.Events.STATUS_CONFIRMED)

        // https://gist.github.com/mlc/5188579
        //values.put(CalendarContract.Events.ORGANIZER, calendarOwnerAccount)
        //values.put(CalendarContract.Events.HAS_ATTENDEE_DATA, 1);

        try {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)

            // get the event ID that is the last element in the Uri
            eventId = uri?.lastPathSegment?.toLong() ?: 0L
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "Exception while adding new event: ${ex.detailed}")
        }

        if (eventId != -1L) {
            // Now add reminders
            for (reminder in details.reminders) {
                val reminderValues = ContentValues()
                reminderValues.put(CalendarContract.Reminders.MINUTES, (reminder.millisecondsBefore / Consts.MINUTE_IN_MILLISECONDS).toInt())

                reminderValues.put(CalendarContract.Reminders.EVENT_ID, eventId)
                reminderValues.put(CalendarContract.Reminders.METHOD, reminder.method)

                context.contentResolver.insert(
                        CalendarContract.Reminders.CONTENT_URI,
                        reminderValues
                )
            }
        }

        return eventId
    }

    fun moveEvent(context: Context, eventId: Long, newStartTime: Long, newEndTime: Long): Boolean {
        var ret = false

        DevLog.debug(LOG_TAG, "Request to reschedule event ${eventId}, newStartTime: $newStartTime, newEndTime: $newEndTime")

        if (!PermissionsManager.hasAllPermissions(context)) {
            DevLog.error(LOG_TAG, "moveEvent: no permissions")
            return false
        }

        try {
            val values = ContentValues()

            values.put(CalendarContract.Events.DTSTART, newStartTime)
            values.put(CalendarContract.Events.DTEND, newEndTime)

            val updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val updated = context.contentResolver.update(updateUri, values, null, null)

            ret = updated > 0
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "Exception while reading calendar event: ${ex.detailed}")
        }

        return ret
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun updateEvent(
            context: Context,
            eventId: Long,
            calendarId: Long,
            oldDetails: CalendarEventDetails,
            newDetails: CalendarEventDetails
    ): Boolean {

        var ret = false

        DevLog.debug(LOG_TAG, "Request to update event $eventId")

        if (!PermissionsManager.hasAllPermissions(context)) {
            DevLog.error(LOG_TAG, "updateEvent: no permissions")
            return false
        }

        if (oldDetails == newDetails) {
            DevLog.error(LOG_TAG, "No changes requested")
            return false
        }

        if (oldDetails.isAllDay != newDetails.isAllDay) {
            DevLog.error(LOG_TAG, "Cannot change 'is all day'")
            return false
        }

        try {
            val values = ContentValues()

            if (oldDetails.title != newDetails.title)
                values.put(CalendarContract.Events.TITLE, newDetails.title)

            if (oldDetails.desc != newDetails.desc)
                values.put(CalendarContract.Events.DESCRIPTION, newDetails.desc)

            if (oldDetails.location != newDetails.location)
                values.put(CalendarContract.Events.EVENT_LOCATION, newDetails.location)

            if (oldDetails.timezone != newDetails.timezone)
                values.put(CalendarContract.Events.EVENT_TIMEZONE, newDetails.timezone)

            if (oldDetails.startTime != newDetails.startTime)
                values.put(CalendarContract.Events.DTSTART, newDetails.startTime)

            if (oldDetails.endTime != newDetails.endTime)
                values.put(CalendarContract.Events.DTEND, newDetails.endTime)

            if (oldDetails.duration != newDetails.duration && newDetails.duration != null)
                values.put(CalendarContract.Events.DURATION, CalendarProviderHelper.encodeRfc2445Duration(newDetails.duration))

            if (oldDetails.color != newDetails.color)
                values.put(CalendarContract.Events.EVENT_COLOR, newDetails.color)

            if (oldDetails.rRule != newDetails.rRule)
                values.put(CalendarContract.Events.RRULE, newDetails.rRule)

            if (oldDetails.rDate != newDetails.rDate)
                values.put(CalendarContract.Events.RDATE, newDetails.rDate)

            if (oldDetails.exRRule != newDetails.exRRule)
                values.put(CalendarContract.Events.EXRULE, newDetails.exRRule)

            if (oldDetails.exRDate != newDetails.exRDate)
                values.put(CalendarContract.Events.EXDATE, newDetails.exRDate)

            if (oldDetails.lastDate != newDetails.lastDate && newDetails.lastDate != null)
                values.put(CalendarContract.Events.LAST_DATE, newDetails.lastDate)

            val updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val updated = context.contentResolver.update(updateUri, values, null, null)

            ret = updated > 0

            if (ret && oldDetails.reminders != newDetails.reminders) {

                // Now - update reminders also
                val remindersProjection = arrayOf(
                        CalendarContract.Reminders._ID,
                        CalendarContract.Reminders.METHOD,
                        CalendarContract.Reminders.MINUTES
                )

                val newReminders = newDetails.reminders.toMutableSet()
                val remindersToRemove = mutableListOf<Long>()

                val cursor = CalendarContract.Reminders.query(
                        context.contentResolver,
                        eventId,
                        remindersProjection
                )

                while (cursor.moveToNext()) {
                    val reminderId = cursor.getLong(0)
                    val method = cursor.getInt(1)
                    val minutes = cursor.getInt(2)

                    val reminder = EventReminderRecord(minutes * Consts.MINUTE_IN_MILLISECONDS, method)

                    if (reminder in newReminders) {
                        // we still have this one - don't need to add
                        newReminders.remove(reminder)
                    }
                    else {
                        // This one one got disappeared
                        remindersToRemove.add(reminderId)
                    }
                }
                cursor.close()

                for (reminderId in remindersToRemove) {
                    val reminderUri = ContentUris.withAppendedId(
                            CalendarContract.Reminders.CONTENT_URI, reminderId)
                    /*val rows = */context.contentResolver.delete(reminderUri, null, null)
                }

                for (reminder in newReminders) {
                    val reminderValues = ContentValues()
                    reminderValues.put(CalendarContract.Reminders.EVENT_ID, eventId)
                    reminderValues.put(CalendarContract.Reminders.MINUTES, (reminder.millisecondsBefore / Consts.MINUTE_IN_MILLISECONDS).toInt())
                    reminderValues.put(CalendarContract.Reminders.METHOD, reminder.method)

                    context.contentResolver.insert(
                            CalendarContract.Reminders.CONTENT_URI,
                            reminderValues
                    )
                }

            }
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "Exception while reading calendar event: ${ex.detailed}")
        }

        return ret
    }


    fun updateEvent(context: Context, event: EventRecord, newDetails: CalendarEventDetails): Boolean {

        return updateEvent(
                context,
                event.eventId,
                event.calendarId,
                event.details,
                newDetails)
    }

    fun getCalendars(context: Context): List<CalendarRecord> {

        val ret = mutableListOf<CalendarRecord>()

        if (!PermissionsManager.hasReadCalendar(context)) {
            DevLog.error(LOG_TAG, "getCalendars: no permissions")
            return ret
        }

        try {

            val fields = mutableListOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.NAME,
                    CalendarContract.Calendars.OWNER_ACCOUNT,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.ACCOUNT_TYPE,
                    CalendarContract.Calendars.CALENDAR_COLOR,

                    CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                    CalendarContract.Calendars.CALENDAR_TIME_ZONE,
                    CalendarContract.Calendars.SYNC_EVENTS,
                    CalendarContract.Calendars.VISIBLE
            )

            fields.add(CalendarContract.Calendars.IS_PRIMARY)

            val uri = CalendarContract.Calendars.CONTENT_URI

            val cursor = context.contentResolver.query(
                    uri, fields.toTypedArray(), null, null, null)

            while (cursor != null && cursor.moveToNext()) {

                // Get the field values
                val calID: Long? = cursor.getLong(0)
                val displayName: String? = cursor.getString(1)
                val name: String? = cursor.getString(2)
                val ownerAccount: String? = cursor.getString(3)
                val accountName: String? = cursor.getString(4)
                val accountType: String? = cursor.getString(5)
                val color: Int? = cursor.getInt(6)
                val accessLevel: Int? = cursor.getInt(7)
                val timeZone: String? = cursor.getString(8)
                val syncEvents: Int? = cursor.getInt(9)
                val visible: Int? = cursor.getInt(10)

                val isPrimary: Int? = cursor.getInt(11)

                val isEditable =
                        when(accessLevel ?: 0) {
                            CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR -> true
                            CalendarContract.Calendars.CAL_ACCESS_OWNER -> true
                            CalendarContract.Calendars.CAL_ACCESS_ROOT -> true
                            else -> false
                        }

                ret.add(CalendarRecord(
                        calendarId = calID ?: -1L,
                        owner = ownerAccount ?: "",
                        accountName = accountName ?: "",
                        accountType = accountType ?: "",
                        displayName = displayName ?: "",
                        name = name ?: "",
                        color = color ?: Consts.DEFAULT_CALENDAR_EVENT_COLOR,
                        isPrimary = (isPrimary ?: 0) != 0,
                        isReadOnly = !isEditable,
                        isVisible = (visible ?: 0) != 0,
                        isSynced = (syncEvents ?: 0) != 0,
                        timeZone = timeZone ?: TimeZone.getDefault().getID()
                ))
            }

            cursor?.close()

        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "Exception while reading list of calendars: ${ex.detailed}")
        }

        return ret
    }

    fun findNextAlarmTime(cr: ContentResolver, millis: Long): Long? {

        var alarmTime: Long? = null

        val alarmTimeColumn = CalendarContract.CalendarAlerts.ALARM_TIME

        val projection = arrayOf(alarmTimeColumn)
        val selection = alarmTimeColumn + ">=?"
        val sortOrder = alarmTimeColumn + " ASC"

        val cursor = cr.query(
                CalendarContract.CalendarAlerts.CONTENT_URI,
                projection,
                selection,
                arrayOf(millis.toString()),
                sortOrder)

        try {
            if (cursor != null && cursor.moveToFirst()) {
                alarmTime = cursor.getLong(0)
            }
        }
        finally {
            cursor?.close()
        }

        return alarmTime
    }

    fun getCalendarById(context: Context, calendarId: Long): CalendarRecord? {
        var ret: CalendarRecord? = null

        if (!PermissionsManager.hasReadCalendar(context)) {
            DevLog.error(LOG_TAG, "getCalendarById: no permissions")
            return null
        }

        try {

            val fields = mutableListOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.NAME,
                    CalendarContract.Calendars.OWNER_ACCOUNT,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.ACCOUNT_TYPE,
                    CalendarContract.Calendars.CALENDAR_COLOR,

                    CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                    CalendarContract.Calendars.CALENDAR_TIME_ZONE,
                    CalendarContract.Calendars.SYNC_EVENTS,
                    CalendarContract.Calendars.VISIBLE
            )

            fields.add(CalendarContract.Calendars.IS_PRIMARY)

            val uri = CalendarContract.Calendars.CONTENT_URI

            val selection = CalendarContract.Calendars._ID + "=?"

            val cursor = context.contentResolver.query(
                    uri,
                    fields.toTypedArray(),
                    selection,
                    arrayOf(calendarId.toString()),
                    null)

            while (cursor != null && cursor.moveToNext()) {

                // Get the field values
                val calID: Long? = cursor.getLong(0)
                val displayName: String? = cursor.getString(1)
                val name: String? = cursor.getString(2)
                val ownerAccount: String? = cursor.getString(3)
                val accountName: String? = cursor.getString(4)
                val accountType: String? = cursor.getString(5)
                val color: Int? = cursor.getInt(6)
                val accessLevel: Int? = cursor.getInt(7)
                val timeZone: String? = cursor.getString(8)
                val syncEvents: Int? = cursor.getInt(9)
                val visible: Int? = cursor.getInt(10)

                val isPrimary: Int? =cursor.getInt(11)

                val isEditable =
                        when(accessLevel ?: 0) {
                            CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR -> true
                            CalendarContract.Calendars.CAL_ACCESS_OWNER -> true
                            CalendarContract.Calendars.CAL_ACCESS_ROOT -> true
                            else -> false
                        }

                ret = CalendarRecord(
                        calendarId = calID ?: -1L,
                        owner = ownerAccount ?: "",
                        accountName = accountName ?: "",
                        accountType = accountType ?: "",
                        displayName = displayName ?: "",
                        name = name ?: "",
                        color = color ?: Consts.DEFAULT_CALENDAR_EVENT_COLOR,
                        isPrimary = (isPrimary ?: 0) != 0,
                        isReadOnly = !isEditable,
                        isVisible = (visible ?: 0) != 0,
                        isSynced = (syncEvents ?: 0) != 0,
                        timeZone = timeZone ?: TimeZone.getDefault().getID()
                )
                break
            }

            cursor?.close()

        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "Exception while reading list of calendars: ${ex.detailed}")
        }

        return ret
    }

    fun createCalendarNotFoundCal(context: Context): CalendarRecord {
        return CalendarRecord(
                calendarId = -1,
                owner = context.getString(R.string.owner_dummy_local),
                displayName = context.getString(R.string.calendar_not_found),
                name = context.getString(R.string.calendar_not_found),
                accountName = context.getString(R.string.no_account),
                accountType = context.getString(R.string.dummy),
                timeZone = context.getString(R.string.unknown),
                color = Consts.DEFAULT_CALENDAR_EVENT_COLOR,
                isVisible = true,
                isPrimary = false,
                isReadOnly = true,
                isSynced = false)
    }

    fun getHandledCalendarsIds(context: Context, settings: Settings): Set<Long> {
        val handledCalendars =
                getCalendars(context)
                        .filter { settings.getCalendarIsHandled(it.calendarId) }
                        .map { it.calendarId }
                        .toSet()

        return handledCalendars
    }

//    data class EventEntry(
//            val eventId: Long,
//            val instanceStart: Long,
//            val instanceEnd: Long,
//            val isAllDay: Long
//    )


    fun getEventAlertsForEvent(
            context: Context,
            event: EventRecord
    ): List<MonitorEventAlertEntry> {
        val ret = arrayListOf<MonitorEventAlertEntry>()

        if (!PermissionsManager.hasReadCalendar(context)) {
            DevLog.error(LOG_TAG, "getEventAlertsForEventId: no permissions")
            return ret
        }

        val settings = Settings(context)

        val shouldRemindForEventsWithNoReminders = settings.handleEventsWithNoReminders

        val notifyOnEmailOnlyEvents = settings.notifyOnEmailOnlyEvents

        val defaultReminderTimeForEventWithNoReminder =
                settings.defaultReminderTime

        val defaultReminderTimeForAllDayEventWithNoreminder =
                settings.defaultAllDayReminderTime

        try {
            val timezone = TimeZone.getDefault()

            DevLog.info(LOG_TAG, "getEventAlertsForEventId(${event.eventId})")

            val reminders =
                    getEventReminders(context, event.eventId)
                            .filter {
                                it.method != CalendarContract.Reminders.METHOD_SMS
                            }
                            .map {
                                Pair(
                                        it.method != CalendarContract.Reminders.METHOD_EMAIL,
                                        it.millisecondsBefore
                                )
                            }
                            .toTypedArray()

            var hasAnyReminders = false
            var hasNonLocalReminders = false

            for ((isLocal, reminderTime) in reminders) {

                if (!isLocal) {
                    hasNonLocalReminders = true
                    continue
                }

                //DevLog.debug(context, LOG_TAG, "Event ${evt.eventId}, reminder time: $reminderTime")

                var utcOffset = 0

                if (event.isAllDay) {
                    utcOffset = timezone.getOffset(event.startTime)
                    DevLog.debug(LOG_TAG, "Event id ${event.eventId}, UTC offset $utcOffset applied to ${event.startTime} - $reminderTime")
                }

                val alertTime = event.startTime - reminderTime - utcOffset

                val entry = MonitorEventAlertEntry.fromEventRecord(event, alertTime, false, false)

                ret.add(entry)
                hasAnyReminders = true
            }

            var shouldAddManualReminder = false

            // has no reminders and we should notify about such requests
            if (!hasAnyReminders && shouldRemindForEventsWithNoReminders) {

                // it also has no remote (email) reminders or we were configured to notify on such requests
                if (!hasNonLocalReminders || notifyOnEmailOnlyEvents) {
                    shouldAddManualReminder = true
                }
            }

            if (shouldAddManualReminder) {

                val alertTime =
                        if (event.isAllDay) {
                            event.startTime - defaultReminderTimeForEventWithNoReminder
                        } else {
                            val utcOffset = timezone.getOffset(event.startTime)
                            DevLog.debug(LOG_TAG, "Event id ${event.eventId}, UTC offset $utcOffset applied to ${event.startTime} - $defaultReminderTimeForAllDayEventWithNoreminder")
                            event.startTime + defaultReminderTimeForAllDayEventWithNoreminder - utcOffset
                        }

                val entry = MonitorEventAlertEntry.fromEventRecord(event, alertTime, true, false)
                ret.add(entry)
            }
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "getEventAlertsForInstancesInRange: exception ${ex.detailed}")
        }

        return ret
    }

    fun getEventAlertsForInstancesInRange(
            context: Context,
            instanceFrom: Long,
            instanceTo: Long,
            eventId: Long? = null
    ): List<EventAlertRecord> {
        val ret = arrayListOf<EventAlertRecord>()

        if (!PermissionsManager.hasReadCalendar(context)) {
            DevLog.error(LOG_TAG, "getEventAlertsForInstancesInRange: no permissions")
            return ret
        }

        val settings = Settings(context)

        val handledCalendars = getHandledCalendarsIds(context, settings)

        val handleEventsWithNoReminders = settings.handleEventsWithNoReminders
        val notifyOnEmailOnlyEvents = settings.notifyOnEmailOnlyEvents
        val defaultReminderTime = settings.defaultReminderTime
        val defaultAllDayReminderTime = settings.defaultAllDayReminderTime

        try {
            val timezone = TimeZone.getDefault()

            DevLog.info(LOG_TAG, "getEventAlertsForInstancesInRange: Manual alerts scan started, range: from $instanceFrom to $instanceTo")

            val scanStart = System.currentTimeMillis()

            val instanceCursor: Cursor? =
                    CalendarContract.Instances.query(
                            context.contentResolver,
                            instanceFields,
                            instanceFrom,
                            instanceTo
                    )

            if (instanceCursor != null && instanceCursor.moveToFirst()) {
                do {
                    val event = cursorToEventInstance(instanceCursor)
                    if (event == null) {
                        DevLog.info(LOG_TAG, "Got entry with one of: instanceStart, eventId or calendarId not present - skipping")
                        continue
                    }

                    if (!handledCalendars.contains(event.calendarId) || event.calendarId == -1L) {
                        DevLog.info(LOG_TAG, "Event id ${event.eventId} / calId ${event.calendarId} - not handling")
                        continue
                    }

                    if (event.instanceStartTime < instanceFrom) {
                        DevLog.debug(LOG_TAG, "Event id ${event.eventId}: instanceStart ${event.instanceStartTime} is actully before instanceFrom $instanceFrom, skipping")
                        continue
                    }

                    if (eventId != null && eventId != event.eventId) {
                        // looking for alerts for a particular eventid - skip everything else
                        continue
                    }

                    val reminders = getEventReminders(context, event.eventId)
                            .filter { it.method != CalendarContract.Reminders.METHOD_SMS }
                            .map { Pair(it.method != CalendarContract.Reminders.METHOD_EMAIL, it.millisecondsBefore) }
                            .toTypedArray()

                    val hasLocalReminders = reminders.any{ it -> it.first }
                    var addedAnyReminders = false
                    for ((isLocal, reminderTime) in reminders) {
                        if (!isLocal && (hasLocalReminders || !notifyOnEmailOnlyEvents)) {
                            continue
                        }
                        val utcOffset = if (event.isAllDay) timezone.getOffset(event.instanceStartTime) else 0
                        ret.add(event.copy(alertTime = event.instanceStartTime - reminderTime - utcOffset))
                        addedAnyReminders = true
                    }

                    if (!addedAnyReminders && handleEventsWithNoReminders) {
                        val reminderOffset =
                                if (!event.isAllDay)
                                    defaultReminderTime
                                else
                                    timezone.getOffset(event.instanceStartTime) - defaultAllDayReminderTime
                        val alertTime = event.instanceStartTime - reminderOffset
                        DevLog.debug(LOG_TAG, "Manual reminder for event ${event.eventId}, offset: ${reminderOffset / 1000L / 60L}m")
                        ret.add(event.copy(alertTime = alertTime))
                    }

                } while (instanceCursor.moveToNext())
            }

            instanceCursor?.close()

            val scanEnd = System.currentTimeMillis()

            DevLog.info(LOG_TAG, "getEventAlertsForInstancesInRange: found ${ret.size} entries, scan time: ${scanEnd - scanStart}ms")
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "getEventAlertsForInstancesInRange: exception ${ex.detailed}")
        }

        return ret
    }


    fun getEventAlertsForInstanceAt(
            context: Context,
            instanceStartTime: Long,
            eventId: Long? = null
    ) = getEventAlertsForInstancesInRange(context, instanceStartTime, instanceStartTime + 1, eventId)
}
