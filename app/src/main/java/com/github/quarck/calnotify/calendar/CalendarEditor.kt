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

import android.content.Context
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.permissions.PermissionsManager

class CalendarEditor(val provider: CalendarProvider) {

    fun createEvent(context: Context, calendarId: Long, calendarOwnerAccount: String, details: CalendarEventDetails): Long {

        DevLog.info(LOG_TAG, "Request to create an event")

        if (!PermissionsManager.hasAllPermissions(context)) {
            DevLog.error(LOG_TAG, "createEvent: no permissions");
            return -1L;
        }

        val eventId = provider.createEvent(context, calendarId, calendarOwnerAccount, details)

        if (eventId != -1L) {
            DevLog.info(LOG_TAG, "Created new event, id $eventId")
            ApplicationController.CalendarMonitor.onEventEditedByUs(context, eventId);
        }
        else {
            DevLog.info(LOG_TAG, "Failed to create a new event")
        }

        return eventId
    }

    fun moveEvent(context: Context, event: EventAlertRecord, addTimeMillis: Long): Boolean {

        if (!PermissionsManager.hasAllPermissions(context)) {
            DevLog.error(LOG_TAG, "moveEvent: no permissions");
            return false;
        }

        // Get full event details from the provider, if failed - construct a failback version
        val oldDetails =
                provider.getEvent(context, event.eventId)?.details
                        ?: CalendarEventDetails(
                        title = event.title,
                        desc = "",
                        location = event.location,
                        timezone = "",
                        startTime = event.startTime,
                        endTime = event.endTime,
                        isAllDay = event.isAllDay,
                        reminders = listOf<EventReminderRecord>(EventReminderRecord.minutes(15)),
                        color = event.color
                )

        val newStartTime: Long
        val newEndTime: Long

        val currentTime = System.currentTimeMillis()

        val numSecondsInThePast = currentTime + Consts.ALARM_THRESHOLD - event.startTime

        if (numSecondsInThePast > 0) {
            val addUnits = numSecondsInThePast / addTimeMillis + 1

            newStartTime = event.startTime + addTimeMillis * addUnits
            newEndTime = event.endTime + addTimeMillis * addUnits

            DevLog.warn(LOG_TAG, "Requested time is already in the past, total added time: ${addTimeMillis * addUnits}")
        }
        else {
            newStartTime = event.startTime + addTimeMillis
            newEndTime = event.endTime + addTimeMillis
        }

        DevLog.info(LOG_TAG, "Moving event ${event.eventId} from ${event.startTime} / ${event.endTime} to $newStartTime / $newEndTime")

        val ret = provider.moveEvent(context, event.eventId, newStartTime, newEndTime)
        event.startTime = newStartTime
        event.endTime = newEndTime

        DevLog.info(LOG_TAG, "Provider move event for ${event.eventId} result: $ret")

        val newDetails = oldDetails.copy(startTime = newStartTime, endTime = newEndTime)

        DevLog.info(LOG_TAG, "Adding move request into DB: move: ${event.eventId} ${oldDetails.startTime} / ${oldDetails.endTime} -> ${newDetails.startTime} / ${newDetails.endTime}")

        if (event.eventId != -1L) {
            ApplicationController.CalendarMonitor.onEventEditedByUs(context, event.eventId);
        }

        return ret
    }

    fun moveRepeatingAsCopy(context: Context, calendar: CalendarRecord, event: EventAlertRecord, addTimeMillis: Long): Long {

        if (!PermissionsManager.hasAllPermissions(context)) {
            DevLog.error(LOG_TAG, "moveRepeatingAsCopy: no permissions");
            return -1L
        }

        // Get full event details from the provider, if failed - construct a failback version
        val oldEvent = provider.getEvent(context, event.eventId) ?: return -1L

        val newStartTime: Long
        val newEndTime: Long

        val currentTime = System.currentTimeMillis()

        val numSecondsInThePast = currentTime + Consts.ALARM_THRESHOLD - event.instanceStartTime

        if (numSecondsInThePast > 0) {
            val addUnits = numSecondsInThePast / addTimeMillis + 1

            newStartTime = event.instanceStartTime + addTimeMillis * addUnits
            newEndTime = event.instanceEndTime + addTimeMillis * addUnits

            DevLog.warn(LOG_TAG, "Requested time is already in the past, total added time: ${addTimeMillis * addUnits}")
        }
        else {
            newStartTime = event.instanceStartTime + addTimeMillis
            newEndTime = event.instanceEndTime + addTimeMillis
        }

        DevLog.info(LOG_TAG, "Moving event ${event.eventId} from ${event.startTime} / ${event.endTime} to $newStartTime / $newEndTime")

        val details = oldEvent.details.copy(
                startTime = newStartTime,
                endTime = newEndTime,
                repeatingRule = "",
                repeatingRDate = "",
                repeatingExRule = "",
                repeatingExRDate = ""
        )

        val ret = createEvent(context, calendar.calendarId, calendar.owner, details)
        if (ret != -1L) {
            event.startTime = newStartTime
            event.endTime = newEndTime
        }

        return ret
    }

    fun updateEvent(context: Context, eventToEdit: EventRecord, details: CalendarEventDetails): Boolean {

        if (!PermissionsManager.hasAllPermissions(context)) {
            DevLog.error(LOG_TAG, "updateEvent: no permissions");
            return false;
        }

        val ret = provider.updateEvent(context, eventToEdit, details)
        if (ret) {
            DevLog.info(LOG_TAG, "Successfully updated provider, event ${eventToEdit.eventId}")
        }
        else {
            DevLog.error(LOG_TAG, "Failed to updated provider, event ${eventToEdit.eventId}")
        }

        DevLog.info(LOG_TAG, "Adding edit request into DB: ${eventToEdit.eventId} ")

        if (ret && (eventToEdit.startTime != details.startTime)) {

            DevLog.info(LOG_TAG, "Event ${eventToEdit.eventId} was moved, ${eventToEdit.startTime} != ${details.startTime}, checking for notification auto-dismissal")

            val newEvent = provider.getEvent(context, eventToEdit.eventId)

            if (newEvent != null) {
                ApplicationController.onCalendarEventMovedWithinApp(
                        context,
                        eventToEdit,
                        newEvent
                )
            }
        }

        if (eventToEdit.eventId != -1L) {
            ApplicationController.CalendarMonitor.onEventEditedByUs(context, eventToEdit.eventId);
        }

        return ret
    }

    companion object {
        const val LOG_TAG = "CalendarChangeMonitor"
    }
}