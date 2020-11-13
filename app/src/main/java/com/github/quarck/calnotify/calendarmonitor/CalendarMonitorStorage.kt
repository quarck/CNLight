//
//   Calendar Notifications Plus
//   Copyright (C) 2017 Sergey Parshin (s.parshin.sc@gmail.com)
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


package com.github.quarck.calnotify.calendarmonitor

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.github.quarck.calnotify.calendar.MonitorEventAlertEntry
import com.github.quarck.calnotify.utils.logs.DevLog
//import com.github.quarck.calnotify.utils.logs.Logger
import java.io.Closeable


class CalendarMonitorStorage(val context: Context)
    : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_CURRENT_VERSION), Closeable {

    private var impl = CalendarMonitorStorageImplV2(context)

    override fun onCreate(db: SQLiteDatabase)
            = impl.createDb(db)

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        DevLog.info(LOG_TAG, "onUpgrade $oldVersion -> $newVersion")

//        if (oldVersion != newVersion) {
//            throw Exception("DB storage error: upgrade from $oldVersion to $newVersion is not supported")
//        }
    }

    fun addAlert(entry: MonitorEventAlertEntry)
            = synchronized(CalendarMonitorStorage::class.java) { writableDatabase.use { impl.addAlert(it, entry) } }

    fun addAlerts(entries: Collection<MonitorEventAlertEntry>)
            = synchronized(CalendarMonitorStorage::class.java) { writableDatabase.use { impl.addAlerts(it, entries) } }

    fun deleteAlert(entry: MonitorEventAlertEntry)
            = deleteAlert(entry.eventId, entry.alertTime, entry.instanceStartTime)

    fun deleteAlerts(entries: Collection<MonitorEventAlertEntry>)
            = synchronized(CalendarMonitorStorage::class.java) { writableDatabase.use { impl.deleteAlerts(it, entries) } }

    fun deleteAlert(eventId: Long, alertTime: Long, instanceStart: Long)
            = synchronized(CalendarMonitorStorage::class.java) { writableDatabase.use { impl.deleteAlert(it, eventId, alertTime, instanceStart) } }

    fun deleteAlertsMatching(filter: (MonitorEventAlertEntry) -> Boolean)
            = synchronized(CalendarMonitorStorage::class.java) { writableDatabase.use { impl.deleteAlertsMatching(it, filter) } }

    fun updateAlert(entry: MonitorEventAlertEntry)
            = synchronized(CalendarMonitorStorage::class.java) { writableDatabase.use { impl.updateAlert(it, entry) } }

    fun updateAlerts(entries: Collection<MonitorEventAlertEntry>)
            = synchronized(CalendarMonitorStorage::class.java) { writableDatabase.use { impl.updateAlerts(it, entries) } }

    fun getAlert(eventId: Long, alertTime: Long, instanceStart: Long): MonitorEventAlertEntry?
            = synchronized(CalendarMonitorStorage::class.java) { readableDatabase.use { impl.getAlert(it, eventId, alertTime, instanceStart) } }

    fun getInstanceAlerts(eventId: Long, instanceStart: Long): List<MonitorEventAlertEntry>
            = synchronized(CalendarMonitorStorage::class.java) { readableDatabase.use { impl.getInstanceAlerts(it, eventId, instanceStart) } }

    fun getNextAlert(since: Long): Long?
            = synchronized(CalendarMonitorStorage::class.java) { readableDatabase.use { impl.getNextAlert(it, since) } }

    fun getAlertsAt(time: Long): List<MonitorEventAlertEntry>
            = synchronized(CalendarMonitorStorage::class.java) { readableDatabase.use { impl.getAlertsAt(it, time) } }

    val alerts: List<MonitorEventAlertEntry>
        get() = synchronized(CalendarMonitorStorage::class.java) { readableDatabase.use { impl.getAlerts(it) } }

    fun getAlertsForInstanceStartRange(scanFrom: Long, scanTo: Long): List<MonitorEventAlertEntry>
            = synchronized(CalendarMonitorStorage::class.java) { readableDatabase.use { impl.getAlertsForInstanceStartRange(it, scanFrom, scanTo) } }

    fun getAlertsForAlertRange(scanFrom: Long, scanTo: Long): List<MonitorEventAlertEntry>
            = synchronized(CalendarMonitorStorage::class.java) { readableDatabase.use { impl.getAlertsForAlertRange(it, scanFrom, scanTo) } }

    fun dumpAll() = synchronized(CalendarMonitorStorage::class.java) { readableDatabase.use { impl.dumpAll(it) } }

    companion object {
        private const val LOG_TAG = "MonitorStorage"

        private const val DATABASE_VERSION_V2 = 2
        private const val DATABASE_CURRENT_VERSION = DATABASE_VERSION_V2

        private const val DATABASE_NAME = "CalendarMonitor"
    }
}