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

package com.github.quarck.calnotify.calendar

import com.github.quarck.calnotify.utils.md5state

data class MonitorEventAlertEntryKey(
        val alertTime: Long,
        val instanceStartTime: Long,
        val md5: md5state
) {
    override fun toString(): String {
        val sb = StringBuilder(128)
        sb.append(instanceStartTime/1000L);sb.append(";")
        sb.append((instanceStartTime-alertTime)/1000L);sb.append(";")
        for (i in 0 until 4)
            sb.append(String.format("%02x", (md5.a ushr (i * 8)) and 0xFF))
        sb.append(':')
        for (i in 0 until 4)
            sb.append(String.format("%02x", (md5.b ushr (i * 8)) and 0xFF))
        sb.append(':')
        for (i in 0 until 4)
            sb.append(String.format("%02x", (md5.c ushr (i * 8)) and 0xFF))
        sb.append(':')
        for (i in 0 until 4)
            sb.append(String.format("%02x", (md5.d ushr (i * 8)) and 0xFF))
        return sb.toString()
    }
}

data class MonitorEventAlertEntry(
        val alertTime: Long,
        val instanceStartTime: Long,
        val md5: md5state,
        var alertCreatedByUs: Boolean,
        var wasHandled: Boolean // we should keep event alerts for a little bit longer to avoid double
        // alerting when reacting to different notification sources
        // (e.g. calendar provider vs our internal manual handler)
) {
    val key: MonitorEventAlertEntryKey
        get() = MonitorEventAlertEntryKey(alertTime, instanceStartTime, md5)

    override fun toString(): String {
        val sb = StringBuilder(128)
        if (wasHandled)
            sb.append("H:") // handled
        else
            sb.append("U:") // unhandled
        if (alertCreatedByUs)
            sb.append("M:") // manual
        else
            sb.append("A:") // automatic
        sb.append(instanceStartTime/1000L);sb.append(";")
        sb.append((instanceStartTime-alertTime)/1000L);sb.append(";")
        for (i in 0 until 4)
            sb.append(String.format("%02x", (md5.a ushr (i * 8)) and 0xFF))
        sb.append(':')
        for (i in 0 until 4)
            sb.append(String.format("%02x", (md5.b ushr (i * 8)) and 0xFF))
        sb.append(':')
        for (i in 0 until 4)
            sb.append(String.format("%02x", (md5.c ushr (i * 8)) and 0xFF))
        sb.append(':')
        for (i in 0 until 4)
            sb.append(String.format("%02x", (md5.d ushr (i * 8)) and 0xFF))
        return sb.toString()
    }

    fun keyEquals(alertKey: MonitorEventAlertEntryKey) =
            (alertTime == alertKey.alertTime) &&
                    (instanceStartTime == alertKey.instanceStartTime) &&
                    (md5 == alertKey.md5)

    fun keyEquals(other: MonitorEventAlertEntry) =
            (alertTime == other.alertTime) &&
                    (instanceStartTime == other.instanceStartTime) &&
                    (md5 == other.md5)

    fun keyEquals(event: EventAlertRecord): Boolean {
        return (alertTime == event.alertTime) &&
                (instanceStartTime == event.instanceStartTime) &&
                (md5 == event.contentMd5)
    }

    fun detailsChanged(other: MonitorEventAlertEntry) = !keyEquals(other)


    companion object {
        fun fromEventRecord(event: EventRecord, alertTime: Long, alertCreatedByUs: Boolean=false, wasHandled: Boolean=false): MonitorEventAlertEntry {
            return MonitorEventAlertEntry(
                    alertTime,
                    event.startTime,
                    event.contentMd5,
                    alertCreatedByUs,
                    wasHandled
            )
        }

        fun fromEventAlertRecord(event: EventAlertRecord, alertCreatedByUs: Boolean=false, wasHandled: Boolean=false): MonitorEventAlertEntry {
            return MonitorEventAlertEntry(
                    event.alertTime,
                    event.instanceStartTime,
                    event.contentMd5,
                    alertCreatedByUs,
                    wasHandled
            )
        }
    }
}

data class MonitorDataPair(val monitorEntry: MonitorEventAlertEntry, val eventEntry: EventAlertRecord) {
    companion object {
        fun fromEventAlertRecord(event: EventAlertRecord, alertCreatedByUs: Boolean = true, wasHandled: Boolean = false): MonitorDataPair {
            return MonitorDataPair(
                    MonitorEventAlertEntry.fromEventAlertRecord(event, alertCreatedByUs, wasHandled),
                    event
            )
        }
    }
}
