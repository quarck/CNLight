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
import android.os.Build
import android.provider.CalendarContract
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.permissions.PermissionsManager
import com.github.quarck.calnotify.utils.detailed
import java.util.*

object CalendarProvider : CalendarProviderInterface {
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
                    CalendarContract.CalendarAlerts.STATUS,
                    CalendarContract.CalendarAlerts.SELF_ATTENDEE_STATUS
            )

    private val PROJECTION_INDEX_EVENT_ID = 0
    private val PROJECTION_INDEX_STATE = 1
    private val PROJECTION_INDEX_CALENDAR_ID = 2
    private val PROJECTION_INDEX_TITLE = 3
    private val PROJECTION_INDEX_DESCRIPTION = 4
    private val PROJECTION_INDEX_DTSTART = 5
    private val PROJECTION_INDEX_DTEND = 6
    private val PROJECTION_INDEX_LOCATION = 7
    private val PROJECTION_INDEX_COLOR = 8
    private val PROJECTION_INDEX_ALARM_TIME = 9
    private val PROJECTION_INDEX_INSTANCE_BEGIN = 10
    private val PROJECTION_INDEX_INSTANCE_END = 11
    private val PROJECTION_INDEX_ALL_DAY = 12
    private val PROJECTION_INDEX_STATUS = 13
    private val PROJECTION_INDEX_ATTENDANCE_STATUS = 14

    private fun cursorToAlertRecord(cursor: Cursor, alarmTime: Long?): Pair<Int?, EventAlertRecord?> {

        val eventId: Long? = cursor.getLong(PROJECTION_INDEX_EVENT_ID)
        val state: Int? = cursor.getInt(PROJECTION_INDEX_STATE)
        val title: String? = cursor.getString(PROJECTION_INDEX_TITLE)
        val desc: String? = cursor.getString(PROJECTION_INDEX_DESCRIPTION)
        val startTime: Long? = cursor.getLong(PROJECTION_INDEX_DTSTART)
        val endTime: Long? = cursor.getLong(PROJECTION_INDEX_DTEND)
        val location: String? = cursor.getString(PROJECTION_INDEX_LOCATION)
        val color: Int? = cursor.getInt(PROJECTION_INDEX_COLOR)
        val newAlarmTime: Long? = cursor.getLong(PROJECTION_INDEX_ALARM_TIME)
        val calendarId: Long? = cursor.getLong(PROJECTION_INDEX_CALENDAR_ID)

        val instanceStart: Long? = cursor.getLong(PROJECTION_INDEX_INSTANCE_BEGIN)
        val instanceEnd: Long? = cursor.getLong(PROJECTION_INDEX_INSTANCE_END)
        val allDay: Int? = cursor.getInt(PROJECTION_INDEX_ALL_DAY)

        val status: Int? = cursor.getInt(PROJECTION_INDEX_STATUS)
        val attendance: Int? = cursor.getInt(PROJECTION_INDEX_ATTENDANCE_STATUS)

        if (eventId == null || state == null || title == null || startTime == null)
            return Pair(null, null)

        val event =
                EventAlertRecord(
                        calendarId = calendarId ?: -1L,
                        eventId = eventId,
                        isAllDay = (allDay ?: 0) != 0,
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
                        isRepeating = false, // has to be updated separately
                        eventStatus = EventStatus.fromInt(status),
                        attendanceStatus = AttendanceStatus.fromInt(attendance)

                )

        return Pair(state, event)
    }

    override fun getAlertByTime(context: Context, alertTime: Long, skipDismissed: Boolean): List<EventAlertRecord> {

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

        ret.forEach {
            event ->
            event.isRepeating = isRepeatingEvent(context, event) ?: false
        }

        return ret
    }

    override fun getAlertByEventIdAndTime(context: Context, eventId: Long, alertTime: Long): EventAlertRecord? {

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

        if (ret != null)
            ret.isRepeating = isRepeatingEvent(context, ret) ?: false

        return ret
    }

//    override fun getEventAlerts(context: Context, eventId: Long, startingAlertTime: Long, maxEntries: Int): List<EventAlertRecord> {
//
//        if (!PermissionsManager.hasReadCalendar(context)) {
//            DevLog.error(context, LOG_TAG, "getEventAlerts: has no permissions");
//            return listOf();
//        }
//
//        val ret = arrayListOf<EventAlertRecord>()
//
//        val selection =
//                "${CalendarContract.CalendarAlerts.ALARM_TIME} > ? AND ${CalendarContract.CalendarAlerts.EVENT_ID} = ?"
//
//        val cursor: Cursor? =
//                context.contentResolver.query(
//                        CalendarContract.CalendarAlerts.CONTENT_URI_BY_INSTANCE,
//                        alertFields,
//                        selection,
//                        arrayOf(startingAlertTime.toString(), eventId.toString()),
//                        null
//                );
//
//        var totalEntries = 0
//
//        if (cursor != null && cursor.moveToFirst()) {
//            do {
//                val eventPair = cursorToAlertRecord(cursor, null)
//                val event = eventPair.component2()
//
//                if (event != null && event.eventId == eventId) {
//                    ret.add(event)
//                    ++totalEntries
//                    if (totalEntries >= maxEntries)
//                        break;
//                }
//
//            } while (cursor.moveToNext())
//
//        }
//        else {
//            DevLog.error(context, LOG_TAG, "Event $eventId not found")
//        }
//
//        cursor?.close()
//
//        ret.forEach {
//            event ->
//            event.isRepeating = isRepeatingEvent(context, event) ?: false
//        }
//
//        return ret
//    }

    @SuppressLint("MissingPermission")
    override fun getEventReminders(context: Context, eventId: Long): List<EventReminderRecord> {

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

    override fun getNextEventReminderTime(context: Context, eventId: Long, instanceStartTime: Long): Long {

        val instanceStart = instanceStartTime

        val currentTime = System.currentTimeMillis()

        val nextReminder =
                getEventLocalReminders(context, eventId)
                        .map { reminder -> instanceStart - reminder }
                        .filter { reminder -> reminder > currentTime + Consts.ALARM_THRESHOLD }
                        .min()

        return nextReminder ?: 0L
    }

    override fun getNextEventReminderTime(context: Context, event: EventAlertRecord): Long {

        val instanceStart = event.instanceStartTime

        val currentTime = System.currentTimeMillis()

        val nextReminder =
                getEventLocalReminders(context, event.eventId)
                        .map { reminder -> instanceStart - reminder }
                        .filter { reminder -> reminder > currentTime + Consts.ALARM_THRESHOLD }
                        .min()

        return nextReminder ?: 0L
    }

    override fun getEvent(context: Context, eventId: Long): EventRecord? {

        if (!PermissionsManager.hasReadCalendar(context)) {
            DevLog.error(LOG_TAG, "getEvent: has no permissions")
            return null
        }

        var ret: EventRecord? = null

        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)

        val fields =
                arrayOf(
                        CalendarContract.Events.CALENDAR_ID,
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
                        CalendarContract.Events.LAST_SYNCED
                )

        val cursor: Cursor? =
                context.contentResolver.query(
                        uri, // CalendarContract.CalendarAlerts.CONTENT_URI,
                        fields,
                        null, //selection,
                        null, //arrayOf(eventId.toString()),
                        null
                )

        if (cursor != null && cursor.moveToFirst()) {

            val calendarId: Long? = cursor.getLong(0)
            val title: String? = cursor.getString(1)
            val desc: String? = cursor.getString(2)
            val timeZone: String? = cursor.getString(3)
            val start: Long? = cursor.getLong(4)
            var end: Long? = cursor.getLong(5)

            val rRule: String? = cursor.getString(6)
            val rDate: String? = cursor.getString(7)
            val exRRule: String? = cursor.getString(8)
            val exRDate: String? = cursor.getString(9)

            var allDay: Int? = cursor.getInt(10)
            val location: String? = cursor.getString(11)
            val color: Int? = cursor.getInt(12)
            val status: Int? = cursor.getInt(13)
            val attendance: Int? = cursor.getInt(14)

            if (title != null && start != null) {

                allDay = allDay ?: 0

                if (end == null) {
                    if (allDay == 0)
                        end = start + Consts.HOUR_IN_MILLISECONDS
                    else
                        end = start + Consts.DAY_IN_MILLISECONDS
                }

                ret =
                        EventRecord(
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
                                        repeatingRule = rRule ?: "",
                                        repeatingRDate = rDate ?: "",
                                        repeatingExRule = exRRule ?: "",
                                        repeatingExRDate = exRDate ?: "",
                                        color = color ?: Consts.DEFAULT_CALENDAR_EVENT_COLOR, title = title // stub for now
                                ),
                                eventStatus = EventStatus.fromInt(status),
                                attendanceStatus = AttendanceStatus.fromInt(attendance)
                        )
            }
        }
        else {
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

    override fun getEventIsDirty(context: Context, eventId: Long): Boolean? {

        val SYNC_IS_DIRTY = "dirty"

        if (!PermissionsManager.hasReadCalendar(context)) {
            DevLog.error(LOG_TAG, "getEvent: has no permissions")
            return null
        }

        var ret: Boolean? = null

        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)

        val fields = arrayOf( SYNC_IS_DIRTY ) //  CalendarContracts.SyncColumns is a private class

        try {
            val cursor: Cursor? =
                    context.contentResolver.query(
                            uri, // CalendarContract.CalendarAlerts.CONTENT_URI,
                            fields,
                            null, //selection,
                            null, //arrayOf(eventId.toString()),
                            null
                    )

            if (cursor != null && cursor.moveToFirst()) {
                val isDirty: Int? = cursor.getInt(0)
                if (isDirty != null)
                    ret = isDirty != 0
            } else {
                DevLog.error(LOG_TAG, "Event $eventId not found")
            }

            cursor?.close()
        }
        catch (ex: Exception) {
            ret = null
        }

        return ret
    }


    override fun dismissNativeEventAlert(context: Context, eventId: Long) {

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

    //
    // Reschedule works by creating a new event with exactly the same contents but for the new date / time
    // Original notification is dismissed after that
    //
    // Returns event ID of the new event, or -1 on error
    //
    override fun cloneAndMoveEvent(context: Context, event: EventAlertRecord, addTime: Long): Long {

        var ret = -1L

        DevLog.debug(LOG_TAG, "Request to reschedule event ${event.eventId}, addTime=$addTime")

        if (!PermissionsManager.hasAllCalendarPermissions(context)) {
            DevLog.error(LOG_TAG, "cloneAndMoveEvent: no permissions")
            return -1
        }

        if (event.alertTime == 0L) {
            DevLog.error(LOG_TAG, "cloneAndMoveEvent: alert time is zero")
            return -1
        }

        val fields = arrayOf(
                CalendarContract.CalendarAlerts.EVENT_ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.CALENDAR_ID,
                CalendarContract.Events.EVENT_TIMEZONE,
                CalendarContract.Events.DESCRIPTION,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.EVENT_LOCATION,
                CalendarContract.Events.DISPLAY_COLOR,
                CalendarContract.Events.ACCESS_LEVEL,
                CalendarContract.Events.AVAILABILITY,
                CalendarContract.Events.HAS_ALARM,
                CalendarContract.Events.ALL_DAY,
                CalendarContract.Events.DURATION,
                CalendarContract.Events.EVENT_END_TIMEZONE,
                CalendarContract.Events.HAS_EXTENDED_PROPERTIES,
                CalendarContract.Events.ORGANIZER,
                CalendarContract.Events.CUSTOM_APP_PACKAGE,
                CalendarContract.Events.CUSTOM_APP_URI
        )

        //
        // First - retrieve full set of requests we are looking for
        //
        var values: ContentValues? = null // values for the new event

        val selection = CalendarContract.CalendarAlerts.ALARM_TIME + "=?"

        val cursor: Cursor? =
                context.contentResolver.query(
                        CalendarContract.CalendarAlerts.CONTENT_URI_BY_INSTANCE,
                        fields,
                        selection,
                        arrayOf(event.alertTime.toString()),
                        null
                )

        try {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    val eventId = cursor.getLong(0)
                    if (eventId != event.eventId)
                        continue

                    values = ContentValues()

                    val title: String = (cursor.getString(1)) ?: throw Exception("Title must not be null")
                    val calendarId: Long = (cursor.getLong(2) as Long?) ?: throw Exception("Calendar ID must not be null")
                    val timeZone: String? = cursor.getString(3)
                    val description: String? = cursor.getString(4)
                    val dtStart = (cursor.getLong(5) as Long?) ?: throw Exception("dtStart must not be null")
                    val dtEnd = (cursor.getLong(6) as Long?) ?: throw Exception("dtEnd must not be null")
                    val location: String? = cursor.getString(7)
                    val color: Int? = cursor.getInt(8)
                    val accessLevel: Int? = cursor.getInt(9)
                    val availability: Int? = cursor.getInt(10)
                    val hasAlarm: Int? = cursor.getInt(11)
                    val allDay: Int? = cursor.getInt(12)

                    val duration: String? = cursor.getString(13) // CalendarContract.Events.DURATION
                    val eventEndTimeZone: String? = cursor.getString(14) // CalendarContract.Events.EVENT_END_TIMEZONE
                    val hasExtProp: Long? = cursor.getLong(15) // CalendarContract.Events.HAS_EXTENDED_PROPERTIES
                    val organizer: String? = cursor.getString(16) // CalendarContract.Events.ORGANIZER
                    val customAppPackage: String? = cursor.getString(17) // CalendarContract.Events.CUSTOM_APP_PACKAGE
                    val appUri: String? = cursor.getString(18) // CalendarContract.Events.CUSTOM_APP_URI

                    values.put(CalendarContract.Events.TITLE, title)
                    values.put(CalendarContract.Events.CALENDAR_ID, calendarId)
                    values.put(CalendarContract.Events.EVENT_TIMEZONE, timeZone)
                    values.put(CalendarContract.Events.DESCRIPTION, description ?: "")

                    values.put(CalendarContract.Events.DTSTART, dtStart + addTime)
                    values.put(CalendarContract.Events.DTEND, dtEnd + addTime)

                    if (location != null)
                        values.put(CalendarContract.Events.EVENT_LOCATION, location)
                    if (color != null)
                        values.put(CalendarContract.Events.EVENT_COLOR, color)
                    if (accessLevel != null)
                        values.put(CalendarContract.Events.ACCESS_LEVEL, accessLevel)
                    if (availability != null)
                        values.put(CalendarContract.Events.AVAILABILITY, availability)
                    if (hasAlarm != null)
                        values.put(CalendarContract.Events.HAS_ALARM, hasAlarm)
                    if (allDay != null)
                        values.put(CalendarContract.Events.ALL_DAY, allDay)
                    if (duration != null)
                        values.put(CalendarContract.Events.DURATION, duration)
                    if (eventEndTimeZone != null)
                        values.put(CalendarContract.Events.EVENT_END_TIMEZONE, eventEndTimeZone)
                    if (hasExtProp != null)
                        values.put(CalendarContract.Events.HAS_EXTENDED_PROPERTIES, hasExtProp)
                    if (organizer != null)
                        values.put(CalendarContract.Events.ORGANIZER, organizer)
                    if (customAppPackage != null)
                        values.put(CalendarContract.Events.CUSTOM_APP_PACKAGE, customAppPackage)
                    if (appUri != null)
                        values.put(CalendarContract.Events.CUSTOM_APP_URI, appUri)

                    values.put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
                    values.put(CalendarContract.Events.SELF_ATTENDEE_STATUS, CalendarContract.Events.STATUS_CONFIRMED)

                    DevLog.info(LOG_TAG, "Event details for calendarId: $calendarId / eventId: $eventId captured")
                    break

                } while (cursor.moveToNext())
            }


        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "Exception while reading calendar event: ${ex.detailed}")
        }
        finally {
            cursor?.close()
        }

        if (values != null) {
            try {
                val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)

                // get the event ID that is the last element in the Uri
                ret = uri.lastPathSegment.toLong()
            }
            catch (ex: Exception) {
                DevLog.error(LOG_TAG, "Exception while adding new event: ${ex.detailed}")
            }
        }
        else {
            DevLog.error(LOG_TAG, "Calendar event wasn't found")
        }

        if (ret != -1L) {
            // Now copy reminders
            val reminders = getEventReminders(context, event.eventId)
            for (reminder in reminders) {
                val reminderValues = ContentValues()
                reminderValues.put(CalendarContract.Reminders.MINUTES, reminder.millisecondsBefore / Consts.MINUTE_IN_SECONDS / 1000L)
                reminderValues.put(CalendarContract.Reminders.EVENT_ID, event.eventId)
                reminderValues.put(CalendarContract.Reminders.METHOD, reminder.method)
                context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
            }
        }

        return ret
    }

    override fun createEvent(context: Context, calendarId: Long, calendarOwnerAccount: String, details: CalendarEventDetails): Long {

        var eventId = -1L

        DevLog.debug(LOG_TAG, "Request to create Event, startTime: ${details.startTime}, endTime: ${details.endTime}, reminder: ${details.reminders}")

        if (!PermissionsManager.hasAllCalendarPermissions(context)) {
            DevLog.error(LOG_TAG, "createEvent: no permissions")
            return -1
        }

        val values = ContentValues()

        values.put(CalendarContract.Events.TITLE, details.title)
        values.put(CalendarContract.Events.CALENDAR_ID, calendarId)
        values.put(CalendarContract.Events.EVENT_TIMEZONE, details.timezone) // Irish summer time
        values.put(CalendarContract.Events.DESCRIPTION, details.desc)

        values.put(CalendarContract.Events.DTSTART, details.startTime)
        values.put(CalendarContract.Events.DTEND, details.endTime)

        values.put(CalendarContract.Events.EVENT_LOCATION, details.location)

        //

        if (details.color != 0)
            values.put(CalendarContract.Events.EVENT_COLOR, details.color) // just something

        values.put(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_DEFAULT)
        values.put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)

        values.put(CalendarContract.Events.HAS_ALARM, 1)
        values.put(CalendarContract.Events.ALL_DAY, if (details.isAllDay) 1 else 0)

        if (details.repeatingRule != "")
            values.put(CalendarContract.Events.RRULE, details.repeatingRule)
        if (details.repeatingRDate != "")
            values.put(CalendarContract.Events.RDATE, details.repeatingRDate)

        if (details.repeatingExRule != "")
            values.put(CalendarContract.Events.EXRULE, details.repeatingExRule)
        if (details.repeatingExRDate != "")
            values.put(CalendarContract.Events.EXDATE, details.repeatingExRDate)


        values.put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
        values.put(CalendarContract.Events.SELF_ATTENDEE_STATUS, CalendarContract.Events.STATUS_CONFIRMED)

        // https://gist.github.com/mlc/5188579
        values.put(CalendarContract.Events.ORGANIZER, calendarOwnerAccount)
        values.put(CalendarContract.Events.HAS_ATTENDEE_DATA, 1);

        try {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)

            // get the event ID that is the last element in the Uri
            eventId = uri.lastPathSegment.toLong()
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
//                if (reminder.isEmail)
//                    reminderValues.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_EMAIL)
//                else
//                    reminderValues.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_DEFAULT)

                context.contentResolver.insert(
                        CalendarContract.Reminders.CONTENT_URI,
                        reminderValues
                )
            }
        }

        return eventId
    }


    private fun isRepeatingEvent(context: Context, event: EventAlertRecord)
            = isRepeatingEvent(context, event.eventId)

    override fun isRepeatingEvent(context: Context, eventId: Long): Boolean? {

        var ret: Boolean? = null

        val fields = arrayOf(
                CalendarContract.Events.ORIGINAL_ID,
                CalendarContract.Events.RRULE,
                CalendarContract.Events.RDATE
        )

        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)

        val cursor: Cursor? =
                context.contentResolver.query(
                        uri,
                        fields,
                        null,
                        null,
                        null
                )
        try {
            if (cursor != null && cursor.moveToFirst()) {

                val rRule = cursor.getString(1) ?: ""
                val rDate = cursor.getString(2) ?: ""

                ret = rRule.isNotEmpty() || rDate.isNotEmpty()
            }
        }
        catch (ex: Exception) {
            ret = null
        }

        cursor?.close()

        return ret
    }

    override fun moveEvent(context: Context, eventId: Long, newStartTime: Long, newEndTime: Long): Boolean {
        var ret = false

        DevLog.debug(LOG_TAG, "Request to reschedule event ${eventId}, newStartTime: $newStartTime, newEndTime: $newEndTime")

        if (!PermissionsManager.hasAllCalendarPermissions(context)) {
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

    override fun updateEvent(
            context: Context,
            eventId: Long,
            calendarId: Long,
            oldDetails: CalendarEventDetails,
            newDetails: CalendarEventDetails
    ): Boolean {

        var ret = false

        DevLog.debug(LOG_TAG, "Request to update event $eventId")

        if (!PermissionsManager.hasAllCalendarPermissions(context)) {
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

            if (oldDetails.color != newDetails.color)
                values.put(CalendarContract.Events.EVENT_COLOR, newDetails.color)

            if (oldDetails.repeatingRule != newDetails.repeatingRule)
                values.put(CalendarContract.Events.RRULE, newDetails.repeatingRule)

            if (oldDetails.repeatingRDate != newDetails.repeatingRDate)
                values.put(CalendarContract.Events.RDATE, newDetails.repeatingRDate)

            if (oldDetails.repeatingExRule != newDetails.repeatingExRule)
                values.put(CalendarContract.Events.EXRULE, newDetails.repeatingExRule)

            if (oldDetails.repeatingExRDate != newDetails.repeatingExRDate)
                values.put(CalendarContract.Events.EXDATE, newDetails.repeatingExRDate)

            val updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val updated = context.contentResolver.update(updateUri, values, null, null)

            ret = updated > 0

            if (ret && oldDetails.reminders != newDetails.reminders)
            {
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


    override fun updateEvent(context: Context, event: EventRecord, newDetails: CalendarEventDetails): Boolean {

        return updateEvent(
                context,
                event.eventId,
                event.calendarId,
                event.details,
                newDetails)
    }


    override fun deleteEvent(context: Context, eventId: Long): Boolean {
        var ret = false

        DevLog.debug(LOG_TAG, "Request to delete event $eventId")

        if (!PermissionsManager.hasAllCalendarPermissions(context)) {
            DevLog.error(LOG_TAG, "deleteEvent: no permissions")
            return false
        }

        try {
//             First - remove all the reminders

            val remindersToRemove = mutableListOf<Long>()

            val cursor = CalendarContract.Reminders.query(
                    context.contentResolver,
                    eventId,
                    arrayOf(CalendarContract.Reminders._ID)
            )

            while (cursor.moveToNext()) {
                val reminderId = cursor.getLong(0)
                remindersToRemove.add(reminderId)
            }
            cursor.close()

            for (reminderId in remindersToRemove) {
                val reminderUri = ContentUris.withAppendedId(CalendarContract.Reminders.CONTENT_URI, reminderId)
                context.contentResolver.delete(reminderUri, null, null)
                DevLog.info(LOG_TAG, "Removed reminder id $reminderId (of event $eventId)")
            }

            // Now - remove actual event
            val removedEvents = context.contentResolver.delete(
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                    null,
                    null
            )

            DevLog.info(LOG_TAG, "Removed $removedEvents events by event id $eventId")

            ret = removedEvents > 0

            if (removedEvents > 1) {
                throw Exception("Removed more than expected: Event: $eventId, removed: $removedEvents")
            }
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "Exception while removing calendar event: ${ex.detailed}")
        }

        return ret
    }

//    fun moveEvent(context: Context, event: EventAlertRecord, addTime: Long): Boolean {
//
//        var ret = false;
//
//        DevLog.debug(LOG_TAG, "Request to reschedule event ${event.eventId}, addTime=$addTime");
//
//        if (!PermissionsManager.hasAllCalendarPermissions(context)) {
//            DevLog.error(context, LOG_TAG, "moveEvent: no permissions");
//            return false;
//        }
//
//        try {
//            val values = ContentValues();
//
//            val currentTime = System.currentTimeMillis()
//
//            val newStartTime: Long
//            val newEndTime: Long
//
//            val numSecondsInThePast = currentTime + Consts.ALARM_THRESHOLD - event.startTime
//
//            if (numSecondsInThePast > 0) {
//                val addUnits = numSecondsInThePast / addTime + 1
//
//                newStartTime = event.startTime + addTime * addUnits
//                newEndTime = event.endTime + addTime * addUnits
//
//                if (addUnits != 1L)
//                    DevLog.error(context, LOG_TAG, "Requested time is already in the past, total added time: ${addTime * addUnits}")
//
//                values.put(CalendarContract.Events.DTSTART, newStartTime);
//                values.put(CalendarContract.Events.DTEND, newEndTime);
//            }
//            else {
//                newStartTime = event.startTime + addTime
//                newEndTime = event.endTime + addTime
//
//                values.put(CalendarContract.Events.DTSTART, newStartTime);
//                values.put(CalendarContract.Events.DTEND, newEndTime);
//            }
//
//            val updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId);
//            val updated = context.contentResolver.update(updateUri, values, null, null);
//
//            ret = updated > 0
//
//            if (ret) {
//                event.startTime = newStartTime
//                event.endTime = newEndTime
//            }
//
//        }
//        catch (ex: Exception) {
//            DevLog.error(context, LOG_TAG, "Exception while reading calendar event: ${ex.detailed}");
//        }
//
//        return ret;
//    }

    override fun getCalendars(context: Context): List<CalendarRecord> {

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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                fields.add(CalendarContract.Calendars.IS_PRIMARY)
            }

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

                val isPrimary: Int? =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                            cursor.getInt(11)
                        } else {
                            0
                        }

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

    override fun findNextAlarmTime(cr: ContentResolver, millis: Long): Long? {

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

    override fun getCalendarById(context: Context, calendarId: Long): CalendarRecord? {
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

    override fun createCalendarNotFoundCal(context: Context): CalendarRecord {
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

    override fun getHandledCalendarsIds(context: Context, settings: Settings): Set<Long> {
        val handledCalendars =
                getCalendars(context)
                        .filter { settings.getCalendarIsHandled(it.calendarId) }
                        .map { it.calendarId }
                        .toSet()

        return handledCalendars
    }

    data class EventEntry(
            val eventId: Long,
            val instanceStart: Long,
            val instanceEnd: Long,
            val isAllDay: Long
    )


    @Suppress("VARIABLE_WITH_REDUNDANT_INITIALIZER")
    override fun getEventAlertsForEvent(
            context: Context,
            event: EventRecord
    ): List<MonitorEventAlertEntry> {
        val ret = arrayListOf<MonitorEventAlertEntry>()

        if (!PermissionsManager.hasReadCalendar(context)) {
            DevLog.error(LOG_TAG, "getEventAlertsForEventId: no permissions")
            return ret
        }

        val settings = Settings(context)

        val shouldRemindForEventsWithNoReminders = settings.shouldRemindForEventsWithNoReminders

        val notifyOnEmailOnlyEvents = settings.notifyOnEmailOnlyEvents

        val defaultReminderTimeForEventWithNoReminder =
                settings.defaultReminderTimeForEventWithNoReminderMillis

        val defaultReminderTimeForAllDayEventWithNoreminder =
                settings.defaultReminderTimeForAllDayEventWithNoreminderMillis

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

                val entry = MonitorEventAlertEntry(
                        event.eventId,
                        event.isAllDay,
                        alertTime,
                        event.startTime,
                        event.endTime,
                        false,
                        false
                )

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

                var alertTime = 0L

                if (event.isAllDay) {
                    alertTime = event.startTime - defaultReminderTimeForEventWithNoReminder
                }
                else {
                    val utcOffset = timezone.getOffset(event.startTime)
                    alertTime = event.startTime + defaultReminderTimeForAllDayEventWithNoreminder - utcOffset
                    DevLog.debug(LOG_TAG, "Event id ${event.eventId}, UTC offset $utcOffset applied to ${event.startTime} - $defaultReminderTimeForAllDayEventWithNoreminder")
                }

                val entry = MonitorEventAlertEntry(
                        event.eventId,
                        event.isAllDay,
                        alertTime,
                        event.startTime,
                        event.endTime,
                        true,
                        false
                )

                ret.add(entry)
            }
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "getEventAlertsForInstancesInRange: exception ${ex.detailed}")
        }

        return ret
    }

    @Suppress("VARIABLE_WITH_REDUNDANT_INITIALIZER")
    override fun getEventAlertsForInstancesInRange(
            context: Context,
            instanceFrom: Long,
            instanceTo: Long
    ): List<MonitorEventAlertEntry> {
        val ret = arrayListOf<MonitorEventAlertEntry>()

        if (!PermissionsManager.hasReadCalendar(context)) {
            DevLog.error(LOG_TAG, "getEventAlertsForInstancesInRange: no permissions")
            return ret
        }

        val settings = Settings(context)

        val handledCalendars = getHandledCalendarsIds(context, settings)

        val shouldRemindForEventsWithNoReminders = settings.shouldRemindForEventsWithNoReminders

        val notifyOnEmailOnlyEvents = settings.notifyOnEmailOnlyEvents

        val defaultReminderTimeForEventWithNoReminder =
                settings.defaultReminderTimeForEventWithNoReminderMillis

        val defaultReminderTimeForAllDayEventWithNoreminder =
                settings.defaultReminderTimeForAllDayEventWithNoreminderMillis

        try {
            val timezone = TimeZone.getDefault()

            val projection =
                    arrayOf(
                            CalendarContract.Instances.EVENT_ID,
                            CalendarContract.Events.CALENDAR_ID,
                            CalendarContract.Instances.BEGIN,
                            CalendarContract.Instances.END,
                            CalendarContract.Events.ALL_DAY
                    )
            val PROJECTION_INDEX_INST_EVENT_ID = 0
            val PROJECTION_INDEX_INST_CALENDAR_ID = 1
            val PROJECTION_INDEX_INST_BEGIN = 2
            val PROJECTION_INDEX_INST_END = 3
            val PROJECTION_INDEX_INST_ALL_DAY = 4

            DevLog.info(LOG_TAG, "getEventAlertsForInstancesInRange: Manual alerts scan started, range: from $instanceFrom to $instanceTo")


            val intermitEvents = arrayListOf<EventEntry>()

            val scanStart = System.currentTimeMillis()

            val instanceCursor: Cursor? =
                    CalendarContract.Instances.query(
                            context.contentResolver,
                            projection,
                            instanceFrom,
                            instanceTo
                    )

            if (instanceCursor != null && instanceCursor.moveToFirst()) {

                do {
                    val eventId: Long? = instanceCursor.getLong(PROJECTION_INDEX_INST_EVENT_ID)
                    val calendarId: Long? = instanceCursor.getLong(PROJECTION_INDEX_INST_CALENDAR_ID)

                    val instanceStart: Long? = instanceCursor.getLong(PROJECTION_INDEX_INST_BEGIN)

                    var instanceEnd: Long? = instanceCursor.getLong(PROJECTION_INDEX_INST_END)

                    var isAllDay: Long? = instanceCursor.getLong(PROJECTION_INDEX_INST_ALL_DAY)

                    if (instanceStart == null || eventId == null || calendarId == null) {
                        DevLog.info(LOG_TAG, "Got entry with one of: instanceStart, eventId or calendarId not present - skipping")
                        continue
                    }

                    if (!handledCalendars.contains(calendarId) || calendarId == -1L) {
                        DevLog.info(LOG_TAG, "Event id $eventId / calId $calendarId - not handling")
                        continue
                    }

                    if (instanceStart < instanceFrom) {
                        DevLog.debug(LOG_TAG, "Event id $eventId: instanceStart $instanceStart is actully before instanceFrom $instanceFrom, skipping")
                        continue
                    }

                    isAllDay = isAllDay ?: 0L

                    if (instanceEnd == null) {
                        if (isAllDay == 0L)
                            instanceEnd = instanceStart + Consts.HOUR_IN_MILLISECONDS
                        else
                            instanceEnd = instanceStart + Consts.DAY_IN_MILLISECONDS
                    }

                    intermitEvents.add(
                            EventEntry(
                                    eventId = eventId,
                                    instanceStart = instanceStart,
                                    instanceEnd = instanceEnd,
                                    isAllDay = isAllDay
                            ))

                } while (instanceCursor.moveToNext())

                val knownReminders =
                        intermitEvents
                                .map { it.eventId }
                                .toSet()
                                .map {
                                    eventId ->
                                    eventId to
                                        getEventReminders(context, eventId)
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
                                }
                                .toMap()

                for (evt in intermitEvents) {
                    val reminders = knownReminders[evt.eventId] // getEventLocalReminders(context, eventId);

                    var hasAnyReminders = false
                    var hasNonLocalReminders = false


                    if (reminders != null)
                        for ((isLocal, reminderTime) in reminders) {

                            if (!isLocal) {
                                hasNonLocalReminders = true
                                continue
                            }

                            //DevLog.debug(context, LOG_TAG, "Event ${evt.eventId}, reminder time: $reminderTime")

                            var utcOffset = 0

                            if (evt.isAllDay != 0L) {
                                utcOffset = timezone.getOffset(evt.instanceStart)
                                DevLog.debug(LOG_TAG, "Event id ${evt.eventId}, UTC offset $utcOffset applied to ${evt.instanceStart} - $reminderTime")
                            }

                            val alertTime = evt.instanceStart - reminderTime - utcOffset

                            val entry = MonitorEventAlertEntry(
                                    evt.eventId,
                                    evt.isAllDay != 0L,
                                    alertTime,
                                    evt.instanceStart,
                                    evt.instanceEnd,
                                    false,
                                    false
                            )

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

                        var alertTime = 0L

                        if (evt.isAllDay == 0L) {
                            alertTime = evt.instanceStart - defaultReminderTimeForEventWithNoReminder
                        }
                        else {
                            val utcOffset = timezone.getOffset(evt.instanceStart)
                            alertTime = evt.instanceStart + defaultReminderTimeForAllDayEventWithNoreminder - utcOffset
                            DevLog.debug(LOG_TAG, "Event id ${evt.eventId}, UTC offset $utcOffset applied to ${evt.instanceStart} - $defaultReminderTimeForAllDayEventWithNoreminder")
                        }

                        val entry = MonitorEventAlertEntry(
                                evt.eventId,
                                evt.isAllDay != 0L,
                                alertTime,
                                evt.instanceStart,
                                evt.instanceEnd,
                                true,
                                false
                        )

                        ret.add(entry)
                    }
                }

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
}
