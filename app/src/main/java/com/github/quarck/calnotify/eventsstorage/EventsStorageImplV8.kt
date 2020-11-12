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

import android.content.Context
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.utils.logs.DevLog
//import com.github.quarck.calnotify.utils.logs.Logger
import java.util.*

@Suppress("VARIABLE_WITH_REDUNDANT_INITIALIZER")
class EventsStorageImplV8(val context: Context) {

    fun dropAll(db: SQLiteDatabase): Boolean {

        var ret = false

        try {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
            db.execSQL("DROP INDEX IF EXISTS " + INDEX_NAME);
            ret = true
        }
        catch (ex: SQLException) {
            DevLog.error(LOG_TAG, "dropAll: $ex")
        }

        return ret;
    }

    fun getEventsImpl(db: SQLiteDatabase): List<EventAlertRecord> {
        val ret = LinkedList<EventAlertRecord>()

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

        //DevLog.debug(LOG_TAG, "eventsImpl, returnint ${ret.size} requests")

        return ret
    }

    fun deleteEventImpl(db: SQLiteDatabase, eventId: Long, instanceStartTime: Long): Boolean {

        val rowsAffected =
                db.delete(
                        TABLE_NAME,
                        " $KEY_EVENTID = ? AND $KEY_INSTANCE_START = ?",
                        arrayOf(eventId.toString(), instanceStartTime.toString()))

        return rowsAffected == 1
    }

    private fun cursorToEventRecord(cursor: Cursor): EventAlertRecord {

        return EventAlertRecord(
                calendarId = (cursor.getLong(PROJECTION_KEY_CALENDAR_ID) as Long?) ?: -1L,
                eventId = cursor.getLong(PROJECTION_KEY_EVENTID),
                alertTime = cursor.getLong(PROJECTION_KEY_ALERT_TIME),
                notificationId = cursor.getInt(PROJECTION_KEY_NOTIFICATIONID),
                title = cursor.getString(PROJECTION_KEY_TITLE),
                desc = "",
                startTime = cursor.getLong(PROJECTION_KEY_START),
                endTime = cursor.getLong(PROJECTION_KEY_END),
                instanceStartTime = cursor.getLong(PROJECTION_KEY_INSTANCE_START),
                instanceEndTime = cursor.getLong(PROJECTION_KEY_INSTANCE_END),
                location = cursor.getString(PROJECTION_KEY_LOCATION),
                snoozedUntil = cursor.getLong(PROJECTION_KEY_SNOOZED_UNTIL),
                lastStatusChangeTime = cursor.getLong(PROJECTION_KEY_LAST_EVENT_VISIBILITY),
                displayStatus = EventDisplayStatus.fromInt(cursor.getInt(PROJECTION_KEY_DISPLAY_STATUS)),
                color = cursor.getInt(PROJECTION_KEY_COLOR),
                isRepeating = cursor.getInt(PROJECTION_KEY_IS_REPEATING) != 0,
                isAllDay = cursor.getInt(PROJECTION_KEY_ALL_DAY) != 0,
                origin = EventOrigin.fromInt(cursor.getInt(PROJECTION_KEY_EVENT_ORIGIN)),
                timeFirstSeen = cursor.getLong(PROJECTION_KEY_TIME_FIRST_SEEN),
                eventStatus = EventStatus.fromInt(cursor.getInt(PROJECTION_KEY_EVENT_STATUS)),
                attendanceStatus = AttendanceStatus.fromInt(cursor.getInt(PROJECTION_KEY_EVENT_ATTENDANCE_STATUS))
        )
    }

    companion object {
        private const val LOG_TAG = "EventsStorageImplV8"

        private const val TABLE_NAME = "eventsV8"
        private const val INDEX_NAME = "eventsIdxV8"


        // No one is going to read this SQLite manually. use column names that
        // are faster to process by computer (==shorter names)

        private const val KEY_CALENDAR_ID = "cid"
        private const val KEY_EVENTID = "id"

        private const val KEY_IS_REPEATING = "rep"
        private const val KEY_ALL_DAY = "alld"

        private const val KEY_NOTIFICATIONID = "nid"
        private const val KEY_TITLE = "ttl"
        private const val KEY_START = "estart"
        private const val KEY_END = "eend"
        private const val KEY_INSTANCE_START = "istart"
        private const val KEY_INSTANCE_END = "iend"
        private const val KEY_LOCATION = "loc"
        private const val KEY_SNOOZED_UNTIL = "snz"
        private const val KEY_DISPLAY_STATUS = "dsts"
        private const val KEY_LAST_EVENT_VISIBILITY = "ls"
        private const val KEY_COLOR = "clr"
        private const val KEY_ALERT_TIME = "altm"
        private const val KEY_EVENT_ORIGIN = "ogn"
        private const val KEY_TIME_FIRST_SEEN = "fsn"

        private const val KEY_EVENT_STATUS = "attsts"
        private const val KEY_EVENT_ATTENDANCE_STATUS = "oattsts"

        private val SELECT_COLUMNS = arrayOf<String>(
                KEY_CALENDAR_ID,
                KEY_EVENTID,
                KEY_ALERT_TIME,
                KEY_NOTIFICATIONID,
                KEY_TITLE,
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
                KEY_EVENT_ORIGIN,
                KEY_TIME_FIRST_SEEN,
                KEY_EVENT_STATUS,
                KEY_EVENT_ATTENDANCE_STATUS
        )

        const val PROJECTION_KEY_CALENDAR_ID = 0;
        const val PROJECTION_KEY_EVENTID = 1;
        const val PROJECTION_KEY_ALERT_TIME = 2;
        const val PROJECTION_KEY_NOTIFICATIONID = 3;
        const val PROJECTION_KEY_TITLE = 4;
        const val PROJECTION_KEY_START = 5;
        const val PROJECTION_KEY_END = 6;
        const val PROJECTION_KEY_INSTANCE_START = 7;
        const val PROJECTION_KEY_INSTANCE_END = 8;
        const val PROJECTION_KEY_LOCATION = 9;
        const val PROJECTION_KEY_SNOOZED_UNTIL = 10;
        const val PROJECTION_KEY_LAST_EVENT_VISIBILITY = 11;
        const val PROJECTION_KEY_DISPLAY_STATUS = 12;
        const val PROJECTION_KEY_COLOR = 13;
        const val PROJECTION_KEY_IS_REPEATING = 14;
        const val PROJECTION_KEY_ALL_DAY = 15;
        const val PROJECTION_KEY_EVENT_ORIGIN = 16;
        const val PROJECTION_KEY_TIME_FIRST_SEEN = 17;
        const val PROJECTION_KEY_EVENT_STATUS = 18
        const val PROJECTION_KEY_EVENT_ATTENDANCE_STATUS = 19
    }
}
