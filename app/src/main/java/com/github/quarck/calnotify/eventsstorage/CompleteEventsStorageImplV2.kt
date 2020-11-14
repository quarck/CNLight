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

package com.github.quarck.calnotify.eventsstorage

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import com.github.quarck.calnotify.calendar.CompleteEventAlertRecord
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventCompletionType
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.utils.logs.DevLog
//import com.github.quarck.calnotify.utils.logs.Logger
import java.util.*

class CompleteEventsStorageImplV2 {

    fun dropAll(db: SQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME)
        db.execSQL("DROP INDEX IF EXISTS " + INDEX_NAME)
    }


    fun deleteEventImpl(db: SQLiteDatabase, event: EventAlertRecord) {
        db.delete(
                TABLE_NAME,
                " $KEY_EVENTID = ? AND $KEY_INSTANCE_START = ?",
                arrayOf(event.eventId.toString(), event.instanceStartTime.toString()))

//        DevLog.debug(LOG_TAG, "deleteEventImpl ${event.eventId}, instance=${event.instanceStartTime} ")
    }

    fun getEventsImpl(db: SQLiteDatabase): List<CompleteEventAlertRecord> {

        val ret = LinkedList<CompleteEventAlertRecord>()

        val cursor = db.query(TABLE_NAME, // a. table
                SELECT_COLUMNS, // b. column names
                null, // c. selections
                null,
                null, // e. group by
                null, // f. h aving
                null, // g. order by
                null) // h. limit

        if (cursor.moveToFirst()) {
            do {
                ret.add(cursorToEventRecord(cursor))

            } while (cursor.moveToNext())
        }
        cursor.close()

        DevLog.debug(LOG_TAG, "eventsImpl, returning ${ret.size} requests")

        return ret
    }

    private fun cursorToEventRecord(cursor: Cursor): CompleteEventAlertRecord {

        val event = EventAlertRecord(
                calendarId = (cursor.getLong(PROJECTION_KEY_CALENDAR_ID) as Long?) ?: -1L,
                eventId = cursor.getLong(PROJECTION_KEY_EVENTID),
                alertTime = cursor.getLong(PROJECTION_KEY_ALERT_TIME),
                notificationId = 0,
                title = cursor.getString(PROJECTION_KEY_TITLE),
                desc = cursor.getString(PROJECTION_KEY_DESCRIPTION),
                startTime = cursor.getLong(PROJECTION_KEY_START),
                endTime = cursor.getLong(PROJECTION_KEY_END),
                instanceStartTime = cursor.getLong(PROJECTION_KEY_INSTANCE_START),
                instanceEndTime = cursor.getLong(PROJECTION_KEY_INSTANCE_END),
                location = cursor.getString(PROJECTION_KEY_LOCATION),
                snoozedUntil = cursor.getLong(PROJECTION_KEY_SNOOZED_UNTIL),
                lastStatusChangeTime = cursor.getLong(PROJECTION_KEY_LAST_EVENT_VISIBILITY),
                displayStatus = EventDisplayStatus.fromInt(cursor.getInt(PROJECTION_KEY_DISPLAY_STATUS)),
                color = cursor.getInt(PROJECTION_KEY_COLOR),
                rRule = if (cursor.getInt(PROJECTION_KEY_IS_REPEATING) != 0) "--non-empty--" else "",
                rDate = if (cursor.getInt(PROJECTION_KEY_IS_REPEATING) != 0) "--non-empty--" else "",
                exRRule = "",
                exRDate = "",
                isAllDay = cursor.getInt(PROJECTION_KEY_ALL_DAY) != 0,
                flags = cursor.getLong(PROJECTION_KEY_FLAGS),
                timeZone = "UTC"
        )

        return CompleteEventAlertRecord(
                event,
                cursor.getLong(PROJECTION_KEY_DISMISS_TIME),
                EventCompletionType.fromInt(cursor.getInt(PROJECTION_KEY_DISMISS_TYPE))
        )
    }

    companion object {
        private const val LOG_TAG = "DismissedEventsStorageImplV2"

        private const val TABLE_NAME = "dismissedEventsV2"
        private const val INDEX_NAME = "dismissedEventsIdxV2"

        private const val KEY_CALENDAR_ID = "calendarId"
        private const val KEY_EVENTID = "eventId"

        private const val KEY_DISMISS_TIME = "dismissTime"
        private const val KEY_DISMISS_TYPE = "dismissType"

        private const val KEY_IS_REPEATING = "isRepeating"
        private const val KEY_ALL_DAY = "allDay"

        private const val KEY_TITLE = "title"
        private const val KEY_DESCRIPTION = "s1"

        private const val KEY_START = "eventStart"
        private const val KEY_END = "eventEnd"
        private const val KEY_INSTANCE_START = "instanceStart"
        private const val KEY_INSTANCE_END = "instanceEnd"
        private const val KEY_LOCATION = "location"
        private const val KEY_SNOOZED_UNTIL = "snoozeUntil"
        private const val KEY_DISPLAY_STATUS = "displayStatus"
        private const val KEY_LAST_EVENT_VISIBILITY = "lastSeen"
        private const val KEY_COLOR = "color"
        private const val KEY_ALERT_TIME = "alertTime"
        private const val KEY_FLAGS = "i1"

        private const val KEY_RESERVED_STR2 = "s2"
        private const val KEY_RESERVED_STR3 = "s3"

        private const val KEY_RESERVED_INT2 = "i2"
        private const val KEY_RESERVED_INT3 = "i3"
        private const val KEY_RESERVED_INT4 = "i4"
        private const val KEY_RESERVED_INT5 = "i5"
        private const val KEY_RESERVED_INT6 = "i6"
        private const val KEY_RESERVED_INT7 = "i7"
        private const val KEY_RESERVED_INT8 = "i8"
        private const val KEY_RESERVED_INT9 = "i9"

        private val SELECT_COLUMNS = arrayOf<String>(
                KEY_CALENDAR_ID,
                KEY_EVENTID,
                KEY_DISMISS_TIME,
                KEY_DISMISS_TYPE,
                KEY_ALERT_TIME,
                KEY_TITLE,
                KEY_DESCRIPTION,
                KEY_START,
                KEY_END,
                KEY_INSTANCE_START,
                KEY_INSTANCE_END,
                KEY_LOCATION,
                KEY_SNOOZED_UNTIL,
                KEY_LAST_EVENT_VISIBILITY,
                KEY_DISPLAY_STATUS,
                KEY_COLOR,
                KEY_IS_REPEATING,
                KEY_ALL_DAY,
                KEY_FLAGS
        )

        const val PROJECTION_KEY_CALENDAR_ID = 0
        const val PROJECTION_KEY_EVENTID = 1
        const val PROJECTION_KEY_DISMISS_TIME = 2
        const val PROJECTION_KEY_DISMISS_TYPE = 3
        const val PROJECTION_KEY_ALERT_TIME = 4
        const val PROJECTION_KEY_TITLE = 5
        const val PROJECTION_KEY_DESCRIPTION = 6
        const val PROJECTION_KEY_START = 7
        const val PROJECTION_KEY_END = 8
        const val PROJECTION_KEY_INSTANCE_START = 9
        const val PROJECTION_KEY_INSTANCE_END = 10
        const val PROJECTION_KEY_LOCATION = 11
        const val PROJECTION_KEY_SNOOZED_UNTIL = 12
        const val PROJECTION_KEY_LAST_EVENT_VISIBILITY = 13
        const val PROJECTION_KEY_DISPLAY_STATUS = 14
        const val PROJECTION_KEY_COLOR = 15
        const val PROJECTION_KEY_IS_REPEATING = 16
        const val PROJECTION_KEY_ALL_DAY = 17
        const val PROJECTION_KEY_FLAGS = 18
    }
}
