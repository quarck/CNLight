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

package com.github.quarck.calnotify.completeeventsstorage

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.utils.detailed
//import com.github.quarck.calnotify.logs.Logger
import java.io.Closeable

class CompleteEventsStorage(val context: Context)
    : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_CURRENT_VERSION), Closeable {

    private var impl = CompleteEventsStorageImplV2()

    override fun onCreate(db: SQLiteDatabase)
            = impl.createDb(db)

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

        DevLog.info(LOG_TAG, "onUpgrade $oldVersion -> $newVersion")

        if (oldVersion == newVersion)
            return

        if (newVersion != DATABASE_VERSION_V2)
            throw Exception("DB storage error: upgrade from $oldVersion to $newVersion is not supported")

        val implOld =
                when (oldVersion) {
                    DATABASE_VERSION_V1 -> CompleteEventsStorageImplV1()
                    else -> throw Exception("DB storage error: upgrade from $oldVersion to $newVersion is not supported")
                }

        try {
            impl.createDb(db)

            val events = implOld.getEventsImpl(db)

            DevLog.info(LOG_TAG, "${events.size} requests to convert")

            for ((event, time, type) in events) {
                impl.addEventImpl(db, type, time, event)
                implOld.deleteEventImpl(db, event)

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
            DevLog.error(LOG_TAG, "Exception during DB upgrade $oldVersion -> $newVersion: ${ex.detailed}")
            throw ex
        }
    }

    fun addEvent(type: EventCompletionType, event: EventAlertRecord)
            = addEvent(type, System.currentTimeMillis(), event)

    fun addEvent(type: EventCompletionType, changeTime: Long, event: EventAlertRecord)
            = synchronized(CompleteEventsStorage::class.java) { writableDatabase.use { impl.addEventImpl(it, type, changeTime, event) } }

    fun addEvents(type: EventCompletionType, events: Collection<EventAlertRecord>)
            = synchronized(CompleteEventsStorage::class.java) { writableDatabase.use { impl.addEventsImpl(it, type, System.currentTimeMillis(), events) } }

    fun deleteEvent(entry: CompleteEventAlertRecord)
            = synchronized(CompleteEventsStorage::class.java) { writableDatabase.use { impl.deleteEventImpl(it, entry) } }

    fun deleteEvent(event: EventAlertRecord)
            = synchronized(CompleteEventsStorage::class.java) { writableDatabase.use { impl.deleteEventImpl(it, event) } }

    fun clearHistory()
            = synchronized(CompleteEventsStorage::class.java) { writableDatabase.use { impl.clearHistoryImpl(it) } }

    val events: List<CompleteEventAlertRecord>
        get() = synchronized(CompleteEventsStorage::class.java) { readableDatabase.use { impl.getEventsImpl(it) } }

    fun purgeOld(currentTime: Long, maxLiveTime: Long)
            = events.filter { (currentTime - it.completionTime) > maxLiveTime }.forEach { deleteEvent(it) }

    override fun close() = super.close()

    companion object {
        private val LOG_TAG = "DismissedEventsStorage"

        private const val DATABASE_VERSION_V1 = 1
        private const val DATABASE_VERSION_V2 = 2
        private const val DATABASE_CURRENT_VERSION = DATABASE_VERSION_V2

        private const val DATABASE_NAME = "DismissedEvents"
    }
}
