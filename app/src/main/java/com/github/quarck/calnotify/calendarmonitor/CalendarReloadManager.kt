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

package com.github.quarck.calnotify.calendarmonitor

import android.content.Context
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.eventsstorage.EventWithNewInstanceTime
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.utils.detailed

object CalendarReloadManager  {

    private const val LOG_TAG = "CalendarReloadManager"

    // newInstanceStartTime/newInstanceEndTime:
    // for update with move: new instance start / end
    // for auto-dismissed: new time for event (used for dismissed storage only)

    enum class ReloadCalendarResultCode {
        NoChange,
        EventMovedShouldAutoDismiss,
        EventDetailsUpdatedShouldUpdate,
        EventInstanceMovedShouldUpdate
    }

    data class ReloadCalendarResult(
            val code: ReloadCalendarResultCode,
            val event: EventAlertRecord,
            val newInstanceStartTime: Long? = null,
            val newInstanceEndTime: Long? = null,
            val setDisplayStatusHidden: Boolean = true
    )

    private fun reloadCalendarInternal(
            context: Context,
            db: EventsStorage,
            events: List<EventAlertRecord>,
            calendar: CalendarProvider,
            noAutoDismiss: Boolean = false
    ): Boolean {

        DevLog.debug(LOG_TAG, "Reloading calendar")

        val currentTime = System.currentTimeMillis()

        //val settings = Settings(context)

        val eventsMovedBy3rdParty = arrayListOf<ReloadCalendarResult>()
        val eventsToUpdate = arrayListOf<ReloadCalendarResult>()
        val eventsToUpdateWithTime = arrayListOf<ReloadCalendarResult>()

        for (event in events) {

            try {
                val reloadResult = reloadCalendarEventAlert(context, calendar, event, currentTime, noAutoDismiss)

                when (reloadResult.code) {
                // nothing required
                    ReloadCalendarResultCode.NoChange ->
                        Unit;

                // Should auto-dismiss
                    ReloadCalendarResultCode.EventMovedShouldAutoDismiss ->
                        eventsMovedBy3rdParty.add(reloadResult)

                // Simply update
                    ReloadCalendarResultCode.EventDetailsUpdatedShouldUpdate ->
                        eventsToUpdate.add(reloadResult)

                // Update with moving instance time (another type of update as it involves DB key update)
                    ReloadCalendarResultCode.EventInstanceMovedShouldUpdate ->
                        eventsToUpdateWithTime.add(reloadResult)
                }

            }
            catch (ex: Exception) {
                DevLog.error(LOG_TAG, "Got exception while trying to re-load event data for ${event.eventId}: ${ex.detailed}");
            }
        }

        var changedDetected = false

        if (!eventsMovedBy3rdParty.isEmpty()) {
            changedDetected = true

            ApplicationController.removeEventAlertsForEventsMovedIntoTheFutureBy3rdParty(
                    context,
                    db,
                    eventsMovedBy3rdParty.map { it.event }
            )
        }

        if (!eventsToUpdate.isEmpty()) {
            changedDetected = true

            val newEvents =
                    eventsToUpdate.map {
                        res ->
                        if (res.setDisplayStatusHidden)
                            res.event.displayStatus = EventDisplayStatus.Hidden // so this will en-force event to be posted
                        res.event
                    }

            db.updateEvents(newEvents) // nothing major would happen if this fails - just ignore
        }

        if (!eventsToUpdateWithTime.isEmpty()) {
            changedDetected = true

            val newEvents =
                    eventsToUpdateWithTime.map {
                        res ->

                        if (res.newInstanceStartTime == null || res.newInstanceEndTime == null) {
                            DevLog.error(LOG_TAG, "ERROR[1]: if (change.newInstanceStartTime == null || change.newInstanceEndTime == null) in calendar rescan")
                            throw Exception("Internal error in CalendarReloadManager")
                        }

                        if (res.setDisplayStatusHidden)
                            res.event.displayStatus = EventDisplayStatus.Hidden // so this will en-force event to be posted

                        EventWithNewInstanceTime(res.event, res.newInstanceStartTime, res.newInstanceEndTime)
                    }

            db.updateEventsAndInstanceTimes(newEvents)
        }

        return changedDetected
    }

    fun checkShouldRemoveMovedEvent(oldEvent: EventAlertRecord, newEvent: EventRecord, newAlertTime: Long): Boolean
            = checkShouldRemoveMovedEvent(oldEvent.eventId, oldEvent.displayedStartTime, newEvent.startTime, newAlertTime)

    fun checkShouldRemoveMovedEvent(
            eventId: Long,
            oldStartTime: Long,
            newStartTime: Long,
            newAlertTime: Long
    ): Boolean {
        val ret = (newStartTime - oldStartTime > Consts.EVENT_MOVE_THRESHOLD) && // moved enough
                (newAlertTime > System.currentTimeMillis() + Consts.ALARM_THRESHOLD) // into the future enough
        if (ret) {
            DevLog.info(LOG_TAG, "Event ${eventId} - alarm in the future confirmed, at $newAlertTime, marking for auto-dismissal")
        }
        return ret
    }

    fun reloadCalendar(
            context: Context,
            db: EventsStorage,
            calendar: CalendarProvider
    ): Boolean {
        // don't rescan manually created requests - we won't find most of them
        val events = db.events.filter { event -> event.origin != EventOrigin.FullManual }
        return reloadCalendarInternal(context, db, events, calendar)
    }

    fun rescanForRescheduledEvents(
            context: Context,
            db: EventsStorage,
            calendar: CalendarProvider
    ): Boolean {

        //val settings = Settings(context)

        val events = db.events.filter {
            event ->
            event.origin != EventOrigin.FullManual
                    && event.snoozedUntil != 0L
                    && !event.isRepeating
        }

        val currentTime = System.currentTimeMillis()

        val movedEventsToRemove = mutableListOf<EventAlertRecord>()

        for (event in events) {

            val newEvent = calendar.getEvent(context, event.eventId) ?: continue
            val newAlertTime = newEvent.nextAlarmTime(currentTime)

            if (event.startTime != newEvent.startTime) {
                DevLog.info(LOG_TAG, "Event ${event.eventId} - move detected, ${event.startTime} != ${newEvent.startTime}")
                if (checkShouldRemoveMovedEvent(event, newEvent, newAlertTime))
                    movedEventsToRemove.add(event)
            }
        }

        var changedDetected = false

        if (!movedEventsToRemove.isEmpty()) {
            changedDetected = true

            ApplicationController.removeEventAlertsForEventsMovedIntoTheFutureBy3rdParty(
                    context,
                    db,
                    movedEventsToRemove
            )

        }

        return changedDetected
    }

    // returns true if event has changed. Event is updated in place
    fun reloadSingleEvent(
            context: Context,
            db: EventsStorage,
            event: EventAlertRecord,
            calendar: CalendarProvider,
            noAutoDismiss: Boolean = false
    ): Boolean {
        return reloadCalendarInternal(context, db, listOf(event), calendar, noAutoDismiss)
    }

    fun reloadCalendarEventAlert(
            context: Context,
            calendarProvider: CalendarProvider,
            event: EventAlertRecord,
            currentTime: Long,
            noAutoDismiss: Boolean
    ): ReloadCalendarResult {

        // Quick short-cut for non-repeating requests: quickly check if instance time is different now
        // - can't use the same for repeating requests
        if (!noAutoDismiss && !event.isRepeating) {

            val newEvent = calendarProvider.getEvent(context, event.eventId)

            if (newEvent != null) {
                val newAlertTime = newEvent.nextAlarmTime(currentTime)

                if (event.startTime != newEvent.startTime) {

                    DevLog.info(LOG_TAG, "Event ${event.eventId} - move detected, ${event.startTime} != ${newEvent.startTime}")

                    // a bit ugly here with all these multiple-returns
                    if (checkShouldRemoveMovedEvent(event, newEvent, newAlertTime)) {
                        return ReloadCalendarResult(
                                ReloadCalendarResultCode.EventMovedShouldAutoDismiss,
                                event.copy(startTime = newEvent.startTime, endTime = newEvent.endTime)
                        )
                    }
                }
            }
        }

        val newEventInstance = calendarProvider.getAlertByEventIdAndTime(context, event.eventId, event.alertTime)
        if (newEventInstance != null)
            return checkCalendarAlertHasChanged(event, newEventInstance)

        return reloadCalendarEventAlertFromEvent(context, calendarProvider, event, currentTime)
    }

    fun checkCalendarAlertHasChanged(
            oldEvent: EventAlertRecord,
            newEvent: EventAlertRecord
    ): ReloadCalendarResult {

        val merged = oldEvent.updateFrom(newEvent)
                ?: return ReloadCalendarResult(ReloadCalendarResultCode.NoChange, oldEvent)

        if (!newEvent.isRepeating) {
            if (oldEvent.instanceStartTime == newEvent.instanceStartTime) {
                DevLog.info(LOG_TAG, "Non-repeating event ${oldEvent.eventId} / ${oldEvent.instanceStartTime} was updated");
                return ReloadCalendarResult(
                        ReloadCalendarResultCode.EventDetailsUpdatedShouldUpdate,
                        merged
                )
            }
            else {
                DevLog.info(LOG_TAG, "Non-repeating event ${oldEvent.eventId} / ${oldEvent.instanceStartTime} was updated, new instance start ${merged.instanceStartTime} -- event was moved");

                return ReloadCalendarResult(
                        ReloadCalendarResultCode.EventInstanceMovedShouldUpdate,
                        merged,
                        merged.instanceStartTime,
                        merged.instanceEndTime
                )
            }
        }
        else {
            // ignore updated instance times for repeating requests - they are unpredictable
            DevLog.info(LOG_TAG, "Repeating event ${oldEvent.eventId} / ${oldEvent.instanceStartTime} was updated");

            return ReloadCalendarResult(
                    ReloadCalendarResultCode.EventDetailsUpdatedShouldUpdate,
                    merged
            )
        }
    }

    fun reloadCalendarEventAlertFromEvent(
            context: Context,
            calendar: CalendarProvider,
            event: EventAlertRecord,
            currentTime: Long
    ): ReloadCalendarResult {

        DevLog.debug(LOG_TAG, "event ${event.eventId} / ${event.instanceStartTime} - instance NOT found")

        if (event.isRepeating) {
            // This is repeating event -- can't do anything, we can't match new instances to the current one
            DevLog.info(LOG_TAG, "Repeating event ${event.eventId} instance ${event.instanceStartTime} disappeared");
            // can't do anything about it - just ignore, assuming no change
            return ReloadCalendarResult(ReloadCalendarResultCode.NoChange, event)
        }

        // Try loading at least basic params from "event"
        val newEvent = calendar.getEvent(context, event.eventId)
        if (newEvent == null) {
            // Here we can't confirm that event was moved into the future.
            // Perhaps it was removed, but this is not what users usually do.
            // Leave it for user to remove the notification
            DevLog.info(LOG_TAG, "Event ${event.eventId} disappeared completely (Known instance ${event.instanceStartTime})");
            // can't do anything about it - just ignore, assuming no change
            return ReloadCalendarResult(ReloadCalendarResultCode.NoChange, event)
        }

        val newAlertTime = newEvent.nextAlarmTime(currentTime)

        if (event.startTime != newEvent.startTime) {
            DevLog.info(LOG_TAG, "Event ${event.eventId} - move detected, ${event.startTime} != ${newEvent.startTime}")
        }

        var merged = event.updateFrom(newEvent)
        if (event.alertTime != newAlertTime)
            merged = (merged ?: event).copy(alertTime = newAlertTime)

        if (merged != null) {
            DevLog.info(LOG_TAG, "Event ${event.eventId} for lost instance ${merged.instanceStartTime} was updated, new start time ${newEvent.startTime}, alert time ${event.alertTime} -> $newAlertTime");

            merged.displayStatus = EventDisplayStatus.Hidden

            return ReloadCalendarResult(
                    ReloadCalendarResultCode.EventInstanceMovedShouldUpdate,
                    merged,
                    newInstanceStartTime = newEvent.startTime,
                    newInstanceEndTime = newEvent.endTime,
                    setDisplayStatusHidden = false
            )
        }
        else {
            DevLog.info(LOG_TAG, "Event instance ${event.eventId} / ${event.instanceStartTime} disappeared, actual event is still exactly the same");
        }

        return ReloadCalendarResult(ReloadCalendarResultCode.NoChange, event)
    }
}