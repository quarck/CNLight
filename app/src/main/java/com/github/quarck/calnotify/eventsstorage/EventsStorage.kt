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
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventDisplayStatus
import com.github.quarck.calnotify.utils.logs.DevLog
//import com.github.quarck.calnotify.utils.logs.Logger
import java.io.Closeable

data class EventWithNewInstanceTime(
        val event: EventAlertRecord,
        val newInstanceStartTime: Long,
        val newInstanceEndTime: Long
)


class EventsStorage(val context: Context)
    : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_CURRENT_VERSION), Closeable  {

    private var impl = EventsStorageImplV9(context)

    override fun onCreate(db: SQLiteDatabase)
            = impl.createDb(db)

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

        DevLog.info(LOG_TAG, "onUpgrade $oldVersion -> $newVersion")

        if (oldVersion == newVersion)
            return

        if (newVersion != DATABASE_VERSION_V9)
            throw Exception("DB storage error: upgrade from $oldVersion to $newVersion is not supported")

        val implOld =
                when (oldVersion) {
                    DATABASE_VERSION_V8 -> EventsStorageImplV8(context)
                    else -> throw Exception("DB storage error: upgrade from $oldVersion to $newVersion is not supported")
                }

        try {
            impl.createDb(db)

            val events = implOld.getEventsImpl(db)

            DevLog.info(LOG_TAG, "${events.size} requests to convert")

            for (event in events) {
                if (impl.addEventImpl(db, event)) {
                    implOld.deleteEventImpl(db, event.eventId, event.instanceStartTime)
                }

                DevLog.debug(LOG_TAG, "Done event ${event.eventId}, inst ${event.instanceStartTime}")
            }

            if (implOld.getEventsImpl(db).isEmpty()) {
                DevLog.info(LOG_TAG, "Finally - dropping old tables")
                implOld.dropAll(db)
            }
            else {
                throw Exception("DB Upgrade failed: some requests are still in the old version of DB")
            }

        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "Exception during DB upgrade $oldVersion -> $newVersion: ${ex.message}, ${ex.stackTrace}")
            throw ex
        }
    }


    fun addEvent(event: EventAlertRecord): Boolean
            = synchronized(EventsStorage::class.java) { writableDatabase.use { impl.addEventImpl(it, event) } }

    fun addEvents(events: List<EventAlertRecord>): Boolean
            = synchronized(EventsStorage::class.java) { writableDatabase.use { impl.addEventsImpl(it, events) } }

    fun updateEvent(event: EventAlertRecord,
                             alertTime: Long? = null,
                             title: String? = null,
                             snoozedUntil: Long? = null,
                             startTime: Long? = null,
                             endTime: Long? = null,
                             location: String? = null,
                             lastStatusChangeTime: Long? = null,
                             displayStatus: EventDisplayStatus? = null,
                             color: Int? = null,
                             isRepeating: Boolean? = null
        ): Pair<Boolean, EventAlertRecord> {

        val newEvent =
                event.copy(
                        alertTime = alertTime ?: event.alertTime,
                        title = title ?: event.title,
                        snoozedUntil = snoozedUntil ?: event.snoozedUntil,
                        startTime = startTime ?: event.startTime,
                        endTime = endTime ?: event.endTime,
                        location = location ?: event.location,
                        lastStatusChangeTime = lastStatusChangeTime ?: event.lastStatusChangeTime,
                        displayStatus = displayStatus ?: event.displayStatus,
                        color = color ?: event.color,
                        isRepeating = isRepeating ?: event.isRepeating,
                        flags = event.flags
                );

        val success = updateEvent(newEvent)

        return Pair(success, newEvent)
    }

    @Suppress("unused")
    fun updateEvents(events: List<EventAlertRecord>,
                              alertTime: Long? = null,
                              title: String? = null,
                              snoozedUntil: Long? = null,
                              startTime: Long? = null,
                              endTime: Long? = null,
                              location: String? = null,
                              lastStatusChangeTime: Long? = null,
                              displayStatus: EventDisplayStatus? = null,
                              color: Int? = null,
                              isRepeating: Boolean? = null): Boolean {

        val newEvents =
                events.map {
                    event ->
                    event.copy(
                            alertTime = alertTime ?: event.alertTime,
                            title = title ?: event.title,
                            snoozedUntil = snoozedUntil ?: event.snoozedUntil,
                            startTime = startTime ?: event.startTime,
                            endTime = endTime ?: event.endTime,
                            location = location ?: event.location,
                            lastStatusChangeTime = lastStatusChangeTime ?: event.lastStatusChangeTime,
                            displayStatus = displayStatus ?: event.displayStatus,
                            color = color ?: event.color,
                            isRepeating = isRepeating ?: event.isRepeating)
                }

        return updateEvents(newEvents)
    }

    fun updateEventAndInstanceTimes(event: EventAlertRecord, instanceStart: Long, instanceEnd: Long): Boolean
            = synchronized(EventsStorage::class.java) { writableDatabase.use { impl.updateEventAndInstanceTimesImpl(it, event, instanceStart, instanceEnd) } }

    fun updateEventsAndInstanceTimes(events: Collection<EventWithNewInstanceTime>): Boolean
            = synchronized(EventsStorage::class.java) { writableDatabase.use { impl.updateEventsAndInstanceTimesImpl(it, events) } }

    fun updateEvent(event: EventAlertRecord): Boolean
            = synchronized(EventsStorage::class.java) { writableDatabase.use { impl.updateEventImpl(it, event) } }

    fun updateEvents(events: List<EventAlertRecord>): Boolean
            = synchronized(EventsStorage::class.java) { writableDatabase.use { impl.updateEventsImpl(it, events) } }

    fun getEvent(eventId: Long, instanceStartTime: Long): EventAlertRecord?
            = synchronized(EventsStorage::class.java) { readableDatabase.use { impl.getEventImpl(it, eventId, instanceStartTime) } }

    fun getEventInstances(eventId: Long): List<EventAlertRecord>
            = synchronized(EventsStorage::class.java) { readableDatabase.use { impl.getEventInstancesImpl(it, eventId) } }

    fun deleteEvent(eventId: Long, instanceStartTime: Long): Boolean
            = synchronized(EventsStorage::class.java) { writableDatabase.use { impl.deleteEventImpl(it, eventId, instanceStartTime) } }

    @Suppress("unused")
    fun deleteEvent(ev: EventAlertRecord): Boolean
            = synchronized(EventsStorage::class.java) { writableDatabase.use { impl.deleteEventImpl(it, ev.eventId, ev.instanceStartTime) } }

    fun deleteEvents(events: Collection<EventAlertRecord>): Int
            = synchronized(EventsStorage::class.java) { writableDatabase.use { impl.deleteEventsImpl(it, events) } }

    fun dumpAll() = synchronized(EventsStorage::class.java) { readableDatabase.use { impl.dumpAll(it) } }

    val events: List<EventAlertRecord>
        get() = synchronized(EventsStorage::class.java) { readableDatabase.use { impl.getEventsImpl(it) } }

    override fun close() {
        super.close();
    }

    companion object {
        private const val LOG_TAG = "EventsStorage"

        private const val DATABASE_VERSION_V8 = 8
        private const val DATABASE_VERSION_V9 = 9

        private const val DATABASE_CURRENT_VERSION = DATABASE_VERSION_V9

        private const val DATABASE_NAME = "Events"
    }
}
