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

package com.github.quarck.calnotify.app

import android.content.Context
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.eventsstorage.FinishedEventsStorage
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.globalState
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.notification.EventNotificationManager
import com.github.quarck.calnotify.reminders.ReminderState
import com.github.quarck.calnotify.utils.textutils.EventFormatter
import com.github.quarck.calnotify.ui.UINotifier
import com.github.quarck.calnotify.calendar.CalendarEditor
import com.github.quarck.calnotify.calendarmonitor.*
import com.github.quarck.calnotify.utils.detailed


object ApplicationController : EventMovedHandler {

    private const val LOG_TAG = "App"

    private var settings: Settings? = null
    private fun getSettings(ctx: Context): Settings {
        synchronized(this) {
            if (settings == null)
                settings = Settings(ctx)
        }
        return settings!!
    }

    private val notificationManager = EventNotificationManager()

    private val alarmScheduler = AlarmScheduler

    private val calendarReloadManager = CalendarReloadManager

    private val calendarProvider = CalendarProvider

    private val calendarEditor: CalendarEditor by lazy { CalendarEditor(calendarProvider) }

    private val calendarMonitorInternal: CalendarMonitor by lazy { CalendarMonitor(calendarProvider) }

    private val tagsManager: TagsManager by lazy { TagsManager() }

    val CalendarMonitor: CalendarMonitor
        get() = calendarMonitorInternal

    fun hasActiveEventsToRemind(context: Context) =
            EventsStorage(context).use {
                db ->
                db.events.filter { event -> event.snoozedUntil == 0L && event.isAlarm }.any()
            }


    fun onEventAlarm(context: Context) {
        DevLog.info(LOG_TAG, "onEventAlarm at ${System.currentTimeMillis()}")
        context.globalState?.lastTimerBroadcastReceived = System.currentTimeMillis()
        notificationManager.postEventNotifications(context)
        alarmScheduler.rescheduleAlarms(context)
    }

    //
    fun onPostHybernation(context: Context) {
        // mark all statuses as hidden
        EventsStorage(context).use {
            db ->
            val events = db.events
            db.updateEvents(events, displayStatus=EventDisplayStatus.Hidden)
        }

        // then post all fresh
        notificationManager.postEventNotifications(context)

        // schedule alarm and re-scan services
        alarmScheduler.rescheduleAlarms(context)
        CalendarMonitorPeriodicJobService.schedule(context)
    }

    fun onAppUpdated(context: Context) {
        DevLog.info(LOG_TAG, "Application updated")
        onPostHybernation(context)
    }

    fun onBootComplete(context: Context) {
        DevLog.info(LOG_TAG, "OS boot is complete")
        onPostHybernation(context)
    }

    fun onCalendarChanged(context: Context) {
        DevLog.info(LOG_TAG, "onCalendarChanged")
        CalendarMonitorOneTimeJobService.schedule(context, 2000)
    }

    fun onCalendarRescanForRescheduledFromService(context: Context, userActionUntil: Long) {
        DevLog.info(LOG_TAG, "onCalendarRescanForRescheduledFromService")

        val changes = EventsStorage(context).use {
            db -> calendarReloadManager.rescanForRescheduledEvents(context, db, calendarProvider, this)
        }

        if (changes) {
            notificationManager.postEventNotifications(context)

            alarmScheduler.rescheduleAlarms(context)

            val isUserAction = (System.currentTimeMillis() < userActionUntil)
            UINotifier.notify(context, isUserAction)
        }
        else {
            DevLog.debug(LOG_TAG, "No calendar changes detected")
        }
    }

    fun onCalendarReloadFromService(context: Context, userActionUntil: Long) {

        DevLog.info(LOG_TAG, "calendarReloadFromService")

        val changes = EventsStorage(context).use {
            db -> calendarReloadManager.reloadCalendar(context, db, calendarProvider, this)
        }

        DevLog.debug(LOG_TAG, "calendarReloadFromService: ${changes}")

        if (changes) {
            notificationManager.postEventNotifications(context)

            alarmScheduler.rescheduleAlarms(context)

            val isUserAction = (System.currentTimeMillis() < userActionUntil)
            UINotifier.notify(context, isUserAction)
        }
        else {
            DevLog.debug(LOG_TAG, "No calendar changes detected")
        }
    }

    fun onCalendarEventMovedWithinApp(context: Context, oldEvent: EventRecord, newEvent: EventRecord) {

        val newAlertTime = newEvent.nextAlarmTime(System.currentTimeMillis())

        val shouldAutoDismiss =
                checkShouldRemoveMovedEvent(
                        context,
                        oldEvent.eventId,
                        oldEvent.startTime,
                        newEvent.startTime,
                        newAlertTime
                )

        if (shouldAutoDismiss) {
            EventsStorage(context).use {
                db ->
                val alertRecord = db.getEvent(oldEvent.eventId, oldEvent.startTime)
                if (alertRecord != null) {
                    dismissEvent(
                            context,
                            db,
                            alertRecord,
                            EventFinishType.EventMovedInTheApp,
                            false
                    )
                }
            }
        }

        UINotifier.notify(context, true)
    }

    // some housekeeping that we have to do after firing calendar event
    fun afterCalendarEventFired(context: Context) {
        alarmScheduler.rescheduleAlarms(context)
        UINotifier.notify(context, false)
    }

    fun postEventNotifications(context: Context, events: Collection<EventAlertRecord>) {
        if (events.size == 1)
            notificationManager.onEventAdded(context, EventFormatter(context), events.first())
        else
            notificationManager.postEventNotifications(context)
    }

    fun registerNewEvent(context: Context, event: EventAlertRecord): Boolean {
        var ret = false
        val settings = getSettings(context)

        if (event.calendarId != -1L && !settings.getCalendarIsHandled(event.calendarId)) {
            DevLog.info(LOG_TAG, "Event ${event.eventId} -> calendar ${event.calendarId} is not handled");
            return ret;
        }

        tagsManager.parseEventTags(event)

        DevLog.info(LOG_TAG, "registerNewEvent: Event fired: calId ${event.calendarId}, eventId ${event.eventId}, instanceStart ${event.instanceStartTime}, alertTime ${event.alertTime}")

        // 1st step - save event into DB
        EventsStorage(context).use {
            db ->
            event.lastStatusChangeTime = System.currentTimeMillis()
            if (event.isRepeating) {
                // repeating event - always simply add
                db.addEvent(event) // ignoring result as we are using other way of validating
            }
            else {
                // non-repeating event - make sure we don't create two records with the same eventId
                val oldEvents = db.getEventInstances(event.eventId)

                DevLog.info(LOG_TAG, "Non-repeating event, already have ${oldEvents.size} old requests with same event id ${event.eventId}, removing old")

                try {
                    // delete old instances for the same event id (should be only one, but who knows)
                    notificationManager.onEventsDismissing(context, oldEvents)

                    val formatter = EventFormatter(context)
                    for (oldEvent in oldEvents) {
                        db.deleteEvent(oldEvent)
                        notificationManager.onEventDismissed(context, formatter, oldEvent.eventId, oldEvent.notificationId)
                    }
                }
                catch (ex: Exception) {
                    DevLog.error(LOG_TAG, "exception while removing old requests: ${ex.detailed}");
                }

                // add newly fired event
                db.addEvent(event)
                //notificationManager.onEventAdded(context, EventFormatter(context), event)
            }
        }

        // 2nd step - re-open new DB instance and make sure that event:
        // * is there
        // * is not set as visible
        // * is not snoozed
        EventsStorage(context).use {
            db ->

            if (event.isRepeating) {
                // return true only if we can confirm, by reading event again from DB
                // that it is there
                // Caller is using our return value as "safeToRemoveOriginalReminder" flag
                val dbEvent = db.getEvent(event.eventId, event.instanceStartTime)
                ret = dbEvent != null && dbEvent.snoozedUntil == 0L

            }
            else {
                // return true only if we can confirm, by reading event again from DB
                // that it is there
                // Caller is using our return value as "safeToRemoveOriginalReminder" flag
                val dbEvents = db.getEventInstances(event.eventId)
                ret = dbEvents.size == 1 && dbEvents[0].snoozedUntil == 0L
            }
        }

        if (!ret)
            DevLog.error(LOG_TAG, "Error adding event with id ${event.eventId}, cal id ${event.calendarId}, " +
                    "instance st ${event.instanceStartTime}, repeating: " +
                    "${event.isRepeating}, allDay: ${event.isAllDay}, alertTime=${event.alertTime}");
        else {
            DevLog.debug(LOG_TAG, "event added: ${event.eventId} (cal id: ${event.calendarId})")
        }

        ReminderState(context).onNewEventFired()

        return ret
    }

    fun registerNewEvents(
            context: Context,
            pairs: List<MonitorDataPair>
    ): ArrayList<MonitorDataPair> {

        val settings = getSettings(context)

        val handledCalendars = calendarProvider.getHandledCalendarsIds(context, settings)

        val handledPairs = pairs.filter {
            (_, event) ->
            handledCalendars.contains(event.calendarId) || event.calendarId == -1L
        }

        val pairsToAdd = arrayListOf<Pair<MonitorEventAlertEntry, EventAlertRecord>>()
        val eventsToDismiss = arrayListOf<EventAlertRecord>()

        var eventsToAdd: List<EventAlertRecord>?

        // 1st step - save event into DB
        EventsStorage(context).use {
            db ->

            for ((alert, event) in handledPairs) {

                DevLog.info(LOG_TAG, "registerNewEvents: Event fired, calId ${event.calendarId}, eventId ${event.eventId}, instanceStart ${event.instanceStartTime}, alertTime=${event.alertTime}")

                tagsManager.parseEventTags(event)

                if (event.isRepeating) {
                    // repeating event - always simply add
                    pairsToAdd.add(Pair(alert, event))
                }
                else {
                    // non-repeating event - make sure we don't create two records with the same eventId
                    val oldEvents = db.getEventInstances(event.eventId)

                    DevLog.info(LOG_TAG, "Non-repeating event, already have ${oldEvents.size} old requests with same event id ${event.eventId}, removing old")

                    try {
                        // delete old instances for the same event id (should be only one, but who knows)
                        eventsToDismiss.addAll(oldEvents)
                    }
                    catch (ex: Exception) {
                        DevLog.error(LOG_TAG, "exception while removing old requests: ${ex.detailed}");
                    }

                    // add newly fired event
                    pairsToAdd.add(Pair(alert, event))
                }
            }

            if (!eventsToDismiss.isEmpty()) {
                // delete old instances for the same event id (should be only one, but who knows)

                notificationManager.onEventsDismissing(context, eventsToDismiss)

                db.deleteEvents(eventsToDismiss)

                notificationManager.onEventsDismissed(
                        context,
                        EventFormatter(context),
                        eventsToDismiss,
                        postNotifications = false
                )
            }

            if (!pairsToAdd.isEmpty()) {

                var currentTime = System.currentTimeMillis()
                for ((_, event) in pairsToAdd)
                    event.lastStatusChangeTime = currentTime++

                eventsToAdd = pairsToAdd.map {
                    it.second
                }

                eventsToAdd?.let {
                    db.addEvents(it)  // ignoring result of add - here we are using another way to validate succesfull add
                }
            }
        }

        // 2nd step - re-open new DB instance and make sure that event:
        // * is there
        // * is not set as visible
        // * is not snoozed

        val validPairs = arrayListOf<MonitorDataPair>()

        EventsStorage(context).use {
            db ->

            for ((alert, event) in pairsToAdd) {

                if (event.isRepeating) {
                    // return true only if we can confirm, by reading event again from DB
                    // that it is there
                    // Caller is using our return value as "safeToRemoveOriginalReminder" flag
                    val dbEvent = db.getEvent(event.eventId, event.instanceStartTime)

                    if (dbEvent != null && dbEvent.snoozedUntil == 0L) {
                        validPairs.add(MonitorDataPair(alert, event))
                    }
                    else {
                        DevLog.error(LOG_TAG, "Failed to add event ${event.eventId} ${event.alertTime} ${event.instanceStartTime} into DB properly")
                    }
                }
                else {
                    // return true only if we can confirm, by reading event again from DB
                    // that it is there
                    // Caller is using our return value as "safeToRemoveOriginalReminder" flag
                    val dbEvents = db.getEventInstances(event.eventId)

                    if (dbEvents.size == 1 && dbEvents[0].snoozedUntil == 0L) {
                        validPairs.add(MonitorDataPair(alert, event))
                    }
                    else {
                        DevLog.error(LOG_TAG, "Failed to add event ${event.eventId} ${event.alertTime} ${event.instanceStartTime} into DB properly")
                    }
                }
            }
        }

        if (pairs.size != validPairs.size) {
            DevLog.warn(LOG_TAG, "registerNewEvents: Added ${validPairs.size} requests out of ${pairs.size}")
        }

        ReminderState(context).onNewEventFired()

        return validPairs
    }

    override fun checkShouldRemoveMovedEvent(
            context: Context,
            oldEvent: EventAlertRecord,
            newEvent: EventRecord,
            newAlertTime: Long
    ): Boolean
            = checkShouldRemoveMovedEvent(
                    context,
                    oldEvent.eventId,
                    oldEvent.displayedStartTime,
                    newEvent.startTime,
                    newAlertTime
            )

    override fun checkShouldRemoveMovedEvent(
            context: Context,
            eventId: Long,
            oldStartTime: Long,
            newStartTime: Long,
            newAlertTime: Long
    ): Boolean {
        var ret = false

        if (newStartTime - oldStartTime > Consts.EVENT_MOVE_THRESHOLD) {
            if (newAlertTime > System.currentTimeMillis() + Consts.ALARM_THRESHOLD) {
                DevLog.info(LOG_TAG, "Event ${eventId} - alarm in the future confirmed, at $newAlertTime, marking for auto-dismissal")
                ret = true
            }
            else {
                DevLog.info(LOG_TAG, "Event ${eventId} moved by ${newStartTime - oldStartTime} ms - not enought to auto-dismiss")
            }
        }

        return ret
    }

    fun snoozeEvent(context: Context, eventId: Long, instanceStartTime: Long, snoozeDelay: Long): SnoozeResult? {

        var ret: SnoozeResult? = null

        val currentTime = System.currentTimeMillis()

        val snoozedEvent: EventAlertRecord? =
                EventsStorage(context).use {
                    db ->
                    var event = db.getEvent(eventId, instanceStartTime)

                    if (event != null) {
                        var snoozedUntil =
                                if (snoozeDelay > 0L)
                                    currentTime + snoozeDelay
                                else
                                    event.displayedStartTime - Math.abs(snoozeDelay) // same as "event.instanceStart + snoozeDelay" but a little bit more readable

                        if (snoozedUntil < currentTime + Consts.ALARM_THRESHOLD) {
                            DevLog.error(LOG_TAG, "snooze: $eventId / $instanceStartTime by $snoozeDelay: new time is in the past, snoozing by 1m instead")
                            snoozedUntil = currentTime + Consts.FAILBACK_SHORT_SNOOZE
                        }

                        val (success, newEvent) = db.updateEvent(event,
                                snoozedUntil = snoozedUntil,
                                lastStatusChangeTime = currentTime,
                                displayStatus = EventDisplayStatus.Hidden)

                        event = if (success) newEvent else null
                    }

                    event;
                }

        if (snoozedEvent != null) {
            notificationManager.onEventSnoozed(context, EventFormatter(context), snoozedEvent.eventId, snoozedEvent.notificationId);
            ReminderState(context).onUserInteraction(System.currentTimeMillis())
            alarmScheduler.rescheduleAlarms(context)
            ret = SnoozeResult(SnoozeType.Snoozed, snoozedEvent.snoozedUntil)
            DevLog.info(LOG_TAG, "Event ${eventId} / ${instanceStartTime} snoozed: by $snoozeDelay: $ret")
        }
        else {
            DevLog.info(LOG_TAG, "Event ${eventId} / ${instanceStartTime} - failed to snooze evend by $snoozeDelay")
        }

        return ret
    }

    fun snoozeEvents(context: Context, filter: (EventAlertRecord)->Boolean, snoozeDelay: Long, isChange: Boolean, onlySnoozeVisible: Boolean): SnoozeResult? {
        var ret: SnoozeResult? = null
        val currentTime = System.currentTimeMillis()
        var snoozedUntil = 0L
        var allSuccess = true

        EventsStorage(context).use {
            db ->
            val events = db.events.filter { filter(it) }

            // Don't allow requests to have exactly the same "snoozedUntil", so to have
            // predicted sorting order, so add a tiny (0.001s per event) adjust to each
            // snoozed time
            var snoozeAdjust = 0

            for (event in events) {
                val newSnoozeUntil = currentTime + snoozeDelay + snoozeAdjust
                // onlySnoozeVisible
                var snoozeThisEvent: Boolean

                if (!onlySnoozeVisible) {
                    snoozeThisEvent = isChange || event.snoozedUntil == 0L || event.snoozedUntil < newSnoozeUntil
                }
                else {
                    snoozeThisEvent = event.snoozedUntil == 0L
                }

                if (snoozeThisEvent) {
                    val (success, _) =
                            db.updateEvent(
                                    event,
                                    snoozedUntil = newSnoozeUntil,
                                    lastStatusChangeTime = currentTime
                            )

                    allSuccess = allSuccess && success;

                    ++snoozeAdjust

                    snoozedUntil = newSnoozeUntil
                }
            }
        }

        if (allSuccess && snoozedUntil != 0L) {
            notificationManager.onAllEventsSnoozed(context)
            alarmScheduler.rescheduleAlarms(context)
            ret = SnoozeResult(SnoozeType.Snoozed, snoozedUntil)
            DevLog.info(LOG_TAG, "Snooze all by $snoozeDelay: success, $ret")
        }
        else {
            DevLog.info(LOG_TAG, "Snooze all by $snoozeDelay: failed")
        }

        return ret
    }

    fun snoozeAllCollapsedEvents(context: Context, snoozeDelay: Long, isChange: Boolean, onlySnoozeVisible: Boolean): SnoozeResult? {
        return snoozeEvents(context, { it.displayStatus == EventDisplayStatus.DisplayedCollapsed }, snoozeDelay, isChange, onlySnoozeVisible)
    }

    fun snoozeAllEvents(context: Context, snoozeDelay: Long, isChange: Boolean, onlySnoozeVisible: Boolean): SnoozeResult? {
        return snoozeEvents(context, { true }, snoozeDelay, isChange, onlySnoozeVisible)
    }

    fun fireEventReminder(context: Context) = notificationManager.fireEventReminder(context)

    fun onMainActivityStarted(context: Context?) {
        if (context != null)
            CalendarMonitorPeriodicJobService.schedule(context)
    }

    fun onMainActivityResumed(context: Context?) {
        if (context == null)
            return
        alarmScheduler.rescheduleAlarms(context)
        // this might fire new notifications
        // This would automatically launch the rescan of calendar and monitor
        calendarMonitorInternal.startRescanService(context)
    }

    fun onTimeChanged(context: Context) {
        alarmScheduler.rescheduleAlarms(context)
        calendarMonitorInternal.onSystemTimeChange(context)
    }

    fun dismissEventsNoMonitorUpdate(
            context: Context,
            db: EventsStorage,
            events: Collection<EventAlertRecord>,
            finishType: EventFinishType,
            notifyActivity: Boolean
    ) {

        DevLog.info(LOG_TAG, "Dismissing ${events.size}  requests")

        FinishedEventsStorage(context).use {
            it.addEvents(finishType, events)
        }

        notificationManager.onEventsDismissing(context, events)

        if (db.deleteEvents(events) == events.size) {
            notificationManager.onEventsDismissed(context, EventFormatter(context), events, true);
            ReminderState(context).onUserInteraction(System.currentTimeMillis())
            alarmScheduler.rescheduleAlarms(context)
            if (notifyActivity)
                UINotifier.notify(context, true)
        }
    }

    fun dismissEvent(
            context: Context,
            db: EventsStorage,
            event: EventAlertRecord,
            finishType: EventFinishType,
            notifyActivity: Boolean
    ) {

        DevLog.info(LOG_TAG, "Dismissing event id ${event.eventId} / instance ${event.instanceStartTime}")

        FinishedEventsStorage(context).use {
            it.addEvent(finishType, event)
        }

        notificationManager.onEventDismissing(context, event.eventId, event.notificationId);

        if (db.deleteEvent(event.eventId, event.instanceStartTime)) {
            notificationManager.onEventDismissed(context, EventFormatter(context), event.eventId, event.notificationId);
            ReminderState(context).onUserInteraction(System.currentTimeMillis())
            alarmScheduler.rescheduleAlarms(context)
            if (notifyActivity)
                UINotifier.notify(context, true)
        }
        else {
            DevLog.error(LOG_TAG, "Failed to delete event id ${event.eventId} instance start ${event.instanceStartTime} from DB")
            DevLog.error(LOG_TAG, " -- known events / instances: ")
            for (ev in db.events) {
                DevLog.error(LOG_TAG, " -- : ${ev.eventId}, ${ev.instanceStartTime}, ${ev.alertTime}, ${ev.snoozedUntil}")
            }
        }
    }

    fun dismissEvent(context: Context, finishType: EventFinishType, event: EventAlertRecord) {
        EventsStorage(context).use {
            db ->
            dismissEvent(context, db, event, finishType, false)
        }
    }

    fun dismissFutureEvent(context: Context, event: MonitorDataPair) {
        FinishedEventsStorage(context).use {
            it.addEvent(EventFinishType.ManuallyInTheApp, event.eventEntry)
        }

        CalendarMonitorStorage(context).use {
            db ->
            event.monitorEntry.wasHandled = true
            db.updateAlert(event.monitorEntry)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun dismissEvent(
            context: Context,
            finishType: EventFinishType,
            eventId: Long,
            instanceStartTime: Long,
            notificationId: Int,
            notifyActivity: Boolean = true
    ) {

        EventsStorage(context).use {
            db ->
            val event = db.getEvent(eventId, instanceStartTime)
            if (event != null) {
                DevLog.info(LOG_TAG, "Dismissing event ${event.eventId} / ${event.instanceStartTime}")
                dismissEvent(context, db, event, finishType, notifyActivity)
            }
            else {
                DevLog.error(LOG_TAG, "dismissEvent: can't find event $eventId, $instanceStartTime")
                DevLog.error(LOG_TAG, " -- known events / instances: ")
                for (ev in db.events) {
                    DevLog.error(LOG_TAG, " -- : ${ev.eventId}, ${ev.instanceStartTime}, ${ev.alertTime}, ${ev.snoozedUntil}")
                }
            }
        }
    }

    fun restoreEvent(context: Context, event: EventAlertRecord) {

        if (event.instanceStartTime < System.currentTimeMillis() + Consts.ALARM_THRESHOLD) {
            val toRestore =
                    event.copy(
                            notificationId = 0, // re-assign new notification ID since old one might already in use
                            displayStatus = EventDisplayStatus.Hidden) // ensure correct visibility is set

            val successOnAdd =
                    EventsStorage(context).use {
                        db ->
                        val ret = db.addEvent(toRestore)
                        calendarReloadManager.reloadSingleEvent(context, db, toRestore, calendarProvider, null)
                        ret
                    }

            if (successOnAdd) {
                notificationManager.onEventRestored(context, EventFormatter(context), toRestore)
                FinishedEventsStorage(context).use {
                    db ->
                    db.deleteEvent(event)
                }
            }
        }
        else {
            CalendarMonitor(CalendarProvider)
                    .setAlertWasHandled(context, event, createdByUs = true, handled = false)

            FinishedEventsStorage(context).use {
                db ->
                db.deleteEvent(event)
            }
        }
    }

    fun moveEvent(context: Context, event: EventAlertRecord, addTime: Long): EventAlertRecord? {

        val moved = calendarEditor.moveEvent(context, event, addTime)
        if (moved != null) {
            DevLog.info(LOG_TAG, "moveEvent: Moved event ${event.eventId} by ${addTime / 1000L} seconds")
            EventsStorage(context).use {
                db ->
                dismissEvent(
                        context,
                        db,
                        event,
                        EventFinishType.EventMovedInTheApp,
                        true
                )
            }
        }
        return moved
    }

    fun moveAsCopy(context: Context, calendar: CalendarRecord, event: EventAlertRecord, addTime: Long): EventAlertRecord? {

        val moved = calendarEditor.moveRepeatingAsCopy(context, calendar, event, addTime)
        if (moved != null) {
            DevLog.debug(LOG_TAG, "Event created: id=${moved.eventId}")

            EventsStorage(context).use {
                db ->
                dismissEvent(
                        context,
                        db,
                        event,
                        EventFinishType.EventMovedInTheApp,
                        true
                )
            }

        } else {
            DevLog.error(LOG_TAG, "Failed to create event")
        }

        return moved
    }
}
