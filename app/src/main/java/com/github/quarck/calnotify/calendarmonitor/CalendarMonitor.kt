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

import android.content.ContentProviderResult
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.util.Log
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.broadcastreceivers.ManualEventAlarmBroadcastReceiver
import com.github.quarck.calnotify.broadcastreceivers.ManualEventExactAlarmBroadcastReceiver
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.permissions.PermissionsManager
import com.github.quarck.calnotify.utils.alarmManager
import com.github.quarck.calnotify.utils.cancelExactAndAlarm
import com.github.quarck.calnotify.utils.detailed
import com.github.quarck.calnotify.utils.setExactAndAlarm
import java.util.*


class CalendarMonitor(val calendarProvider: CalendarProvider) {

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onSystemTimeChange(context: Context) {
        DevLog.info(LOG_TAG, "onSystemTimeChange");
    }

    // should return true if we have fired at new requests, so UI should reload if it is open
    fun startRescanService(context: Context) {

        DevLog.info(LOG_TAG, "onAppResumed")
        CalendarMonitorIntentService.startRescanService(
                context,
                0,
                true,
                System.currentTimeMillis() + Consts.MAX_USER_ACTION_DELAY
        )
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onAlarmBroadcast(context: Context, intent: Intent) {
        DevLog.info(LOG_TAG, "onAlarmBroadcast")

        if (!PermissionsManager.hasAllPermissionsNoCache(context)) {
            DevLog.error(LOG_TAG, "onAlarmBroadcast - no calendar permission to proceed")
            setOrCancelAlarm(context, Long.MAX_VALUE)
            return
        }

        try {
            val state = CalendarMonitorState(context)
            val currentTime = System.currentTimeMillis()

            if (state.nextEventFireFromScan < currentTime + Consts.ALARM_THRESHOLD) {
                val scanTo = System.currentTimeMillis() + Consts.ALARM_THRESHOLD
                val scanFrom = scanTo - Consts.MANUAL_SCAN_WINDOW - 2 * Consts.ALARM_THRESHOLD
                DevLog.info(LOG_TAG, "onAlarmBroadcast: time for the next manual fire, re-scanning range $scanFrom-$scanTo")
                if (manualFireEventsInRangeWithoutHousekeeping(context, from = scanFrom, to = scanTo)) {
                    ApplicationController.afterCalendarEventFired(context)
                }
            }
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "Exception in onAlarmBroadcast: $ex, ${ex.detailed}")
        }
    }

    // proper broadcast from the Calendar Provider. Normally this is a proper
    // way of receiving information about ongoing requests. Apparently not always
    // working, that's why the rest of the class is here
    fun onProviderReminderBroadcast(context: Context, intent: Intent) {
        DevLog.info(LOG_TAG, "onProviderReminderBroadcast");

        if (!PermissionsManager.hasAllPermissionsNoCache(context)) {
            DevLog.error(LOG_TAG, "onProviderReminderBroadcast - no calendar permission to proceed")
            setOrCancelAlarm(context, Long.MAX_VALUE)
            return
        }

        val uri = intent.data;

        val alertTime = uri?.lastPathSegment?.toLongOrNull()
        if (alertTime == null) {
            DevLog.error(LOG_TAG, "ERROR alertTime is null!")
            //launchRescanService(context)
            return
        }

        val eventsToPost = mutableListOf<EventAlertRecord>()
        val eventsToSilentlyDrop = mutableListOf<EventAlertRecord>()

        try {
            val events = CalendarProvider.getAlertByTime(context, alertTime, skipDismissed = false)
            for (event in events) {
                if (getAlertWasHandled(context, event)) {
                    DevLog.info(LOG_TAG, "Broadcast: Event ${event.eventId} / ${event.instanceStartTime} was handled already")
                    continue
                }

                DevLog.info(LOG_TAG, "Broadcast: Seen event ${event.eventId} / ${event.instanceStartTime}")

                event.origin = EventOrigin.ProviderBroadcast
                event.timeFirstSeen = System.currentTimeMillis()

                if (event.isCancelledOrDeclined) {
                    eventsToSilentlyDrop.add(event)
                } else if (ApplicationController.registerNewEvent(context, event)) {
                    eventsToPost.add(event)
                }
            }

        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "Exception while trying to load fired event details, ${ex.detailed}")
        }

        try {
            ApplicationController.postEventNotifications(context, eventsToPost)

            for (event in eventsToSilentlyDrop) {
                setAlertWasHandled(context, event, createdByUs = false)
                CalendarProvider.dismissNativeEventAlert(context, event.eventId);
                DevLog.info(LOG_TAG, "IGNORED Event ${event.eventId} / ${event.instanceStartTime} is marked as handled in the DB and in the provider")
            }

            for (event in eventsToPost) {
                setAlertWasHandled(context, event, createdByUs = false)
                CalendarProvider.dismissNativeEventAlert(context, event.eventId);
                DevLog.info(LOG_TAG, "Event ${event.eventId} / ${event.instanceStartTime}: marked as handled in the DB and in the provider")
            }
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "Exception while posting notifications: ${ex.detailed}")
        }

        ApplicationController.afterCalendarEventFired(context)
    }

    fun onEventEditedByUs(context: Context, eventId: Long) {

        DevLog.info(LOG_TAG, "onEventEditedByUs")

        if (!PermissionsManager.hasAllPermissionsNoCache(context)) {
            DevLog.error(LOG_TAG, "onEventEditedByUs - no calendar permission to proceed")
            setOrCancelAlarm(context, Long.MAX_VALUE)
            return
        }

        val event: EventRecord? = calendarProvider.getEvent(context, eventId)
        if (event == null) {
            DevLog.error(LOG_TAG, "onEventEditedByUs - cannot find event $eventId")
            return
        }

        var firedAnything = false

        try {
            firedAnything = scanForSingleEvent(context, event)
            DevLog.info(LOG_TAG, "scanForSingleEvent - done")
        }
        catch (ex: java.lang.SecurityException) {
            DevLog.error(LOG_TAG, "onEventEditedByUs: SecurityException, ${ex.detailed}")
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "onEventEditedByUs: exception, ${ex.detailed}")
        }

        if (firedAnything)
            ApplicationController.afterCalendarEventFired(context)
    }

    fun onRescanFromService(context: Context) {
        DevLog.info(LOG_TAG, "onRescanFromService")

        if (!PermissionsManager.hasAllPermissionsNoCache(context)) {
            DevLog.error(LOG_TAG, "onRescanFromService - no calendar permission to proceed")
            setOrCancelAlarm(context, Long.MAX_VALUE)
            return
        }

        var firedAnything = false
        try {
            val state = CalendarMonitorState(context)
            val (nextAlarmFromManual, firedEventsManual) = scanNextEvent(context, state)
            setOrCancelAlarm(context, nextAlarmFromManual)
            firedAnything = firedEventsManual
            DevLog.info(LOG_TAG, "Manual scan done, next alarm: $nextAlarmFromManual")
        }
        catch (ex: java.lang.SecurityException) {
            DevLog.error(LOG_TAG, "onRescanFromService: SecurityException, ${ex.detailed}")
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "onRescanFromService: exception, ${ex.detailed}")
        }

        if (firedAnything)
            ApplicationController.afterCalendarEventFired(context)
    }

    private fun setOrCancelAlarm(context: Context, time: Long) {

        DevLog.debug(LOG_TAG, "setOrCancelAlarm");

        if (time != Long.MAX_VALUE && time != 0L) {

            val now = System.currentTimeMillis()

            DevLog.info(LOG_TAG, "Setting alarm at $time (T+${(time - now) / 1000L / 60L}min)")

            val exactTime = time + Consts.ALARM_THRESHOLD / 2 // give calendar provider a little chance - schedule alarm to a bit after

            context.alarmManager.setExactAndAlarm(
                    context,
                    false,
                    exactTime,
                    ManualEventAlarmBroadcastReceiver::class.java, // ignored on KitKat and below
                    ManualEventExactAlarmBroadcastReceiver::class.java,
                    com.github.quarck.calnotify.ui.MainActivity::class.java // alarm info intent
                    )
        }
        else {
            DevLog.info(LOG_TAG, "No next alerts, cancelling")
            context.alarmManager.cancelExactAndAlarm(
                    context,
                    ManualEventAlarmBroadcastReceiver::class.java,
                    ManualEventExactAlarmBroadcastReceiver::class.java
                    )
        }
    }

    fun getAlertsAt(context: android.content.Context, time: Long): List<MonitorEventAlertEntry> =
            CalendarMonitorStorage(context).use { db ->  db.getAlertsAt(time) }

    fun getAlertsForAlertRange(context: Context, scanFrom: Long, scanTo: Long): List<MonitorEventAlertEntry> =
            CalendarMonitorStorage(context).use { db ->  db.getAlertsForAlertRange(scanFrom, scanTo)  }

    fun setAlertWasHandled(context: Context, ev: EventAlertRecord, createdByUs: Boolean, handled: Boolean = true) {

        CalendarMonitorStorage(context).use {
            db ->
            var alert: MonitorEventAlertEntry? = db.getAlert(ev.contentMd5, ev.alertTime, ev.instanceStartTime)

            if (alert != null) {

                DevLog.debug(LOG_TAG, "setAlertWasHandled, ${ev.eventId}, ${ev.instanceStartTime}, ${ev.alertTime}: seen this alert already, updating status to wasHandled");
                alert.wasHandled = handled
                db.updateAlert(alert)

            }
            else {

                DevLog.debug(LOG_TAG, "setAlertWasHandled, ${ev.eventId}, ${ev.instanceStartTime}, ${ev.alertTime}: new alert, simply adding");
                alert = MonitorEventAlertEntry.fromEventAlertRecord(
                        ev,
                        wasHandled = handled,
                        alertCreatedByUs = createdByUs
                )
                db.addAlert(alert)
            }
        }
    }

    fun getAlertWasHandled(db: CalendarMonitorStorage, ev: EventAlertRecord): Boolean {
        DevLog.debug(LOG_TAG, "getAlertWasHandled, ${ev.eventId}, ${ev.instanceStartTime}, ${ev.alertTime}");
        return db.getAlert(ev.contentMd5, ev.alertTime, ev.instanceStartTime)?.wasHandled ?: false
    }

    fun getAlertWasHandled(context: Context, ev: EventAlertRecord): Boolean {
        return CalendarMonitorStorage(context).use {
            db ->
            getAlertWasHandled(db, ev)
        }
    }



    private fun manualFireAlertList(context: Context, alerts: List<MonitorDataPair>): Boolean {

        var fired = false

        val firedAlerts = registerFiredEventsInDB(context, alerts)

        if (firedAlerts.isNotEmpty()) {

            fired = true

            try {
                ApplicationController.postEventNotifications(context, firedAlerts.map { it.eventEntry })
            }
            catch (ex: Exception) {
                DevLog.error(LOG_TAG, "Got exception while posting notifications: ${ex.detailed}")
            }
            markAlertsAsHandledInDB(context, firedAlerts.map { it.monitorEntry })
            DevLog.info(LOG_TAG, "${firedAlerts.size} requests were marked in DB as handled")
        }

        return fired
    }

    private val alertToMonitorEntrySearchRanges = listOf(2 * 1000L, 300 * 1000L, 24 * 3600 * 1000L)

    fun alertToMonitorEntry(context: Context, alert: MonitorEventAlertEntry): MonitorDataPair? {

        for (range in alertToMonitorEntrySearchRanges) {
            val ev = calendarProvider.getEventAlertsForInstancesInRange(
                    context = context,
                    instanceFrom = alert.instanceStartTime - range,
                    instanceTo = alert.instanceStartTime + range
            ).firstOrNull{ e -> alert.keyEquals(e) }

            if (ev != null)
                return MonitorDataPair.fromEventAlertRecord(ev)

            DevLog.error(LOG_TAG, "Error: failed to find event for alert $alert while using range $range")
        }

        DevLog.error(LOG_TAG, "!!!! ERROR ERROR ERROR!!!! : alertToMonitorEntry: failed to find event for alert $alert AFTER WIDE RANGE ATTEMPT!!! ")
        DevLog.error(LOG_TAG, "!!!! ERROR ERROR ERROR!!!! : alertToMonitorEntry: failed to find event for alert $alert AFTER WIDE RANGE ATTEMPT!!! ")
        DevLog.error(LOG_TAG, "!!!! ERROR ERROR ERROR!!!! : alertToMonitorEntry: failed to find event for alert $alert AFTER WIDE RANGE ATTEMPT!!! ")

        return null
    }

    fun alertListIntoMonitorPairsList(context: Context, alertList: List<MonitorEventAlertEntry>): List<MonitorDataPair> =
            alertList.map{ alertToMonitorEntry(context, it) }.filterNotNull()

    // should return true if we have fired at new requests, so UI should reload if it is open
    fun manualFireEventsInRangeWithoutHousekeeping(context: Context, to: Long, from: Long? = null): Boolean {

        if (!PermissionsManager.hasAllPermissions(context)) {
            DevLog.error(LOG_TAG, "manualFireEventsAt_NoHousekeeping: no permissions");
            return false
        }

        val alertsInRange =
                CalendarMonitorStorage(context)
                        .use {
                                db ->
                                if (from == null)
                                    db.getAlertsAt(to)
                                else
                                    db.getAlertsForAlertRange(from, to)
                            }
                        .filter { !it.wasHandled }

        val alerts = alertListIntoMonitorPairsList(context, alertsInRange).sortedBy { it.monitorEntry.alertTime }

        DevLog.info(LOG_TAG, "manualFireEventsAt: got ${alerts.size} alerts to fire at");

        return manualFireAlertList(context, alerts)
    }

    private fun registerFiredEventsInDB(
            context: Context,
            alerts: Collection<MonitorDataPair>
    ): List<MonitorDataPair> {

        // val pairs = arrayListOf<MonitorDataPair>()

//        var numAlertsFound = 0
//        var numAlertsNotFound = 0
//        var numErrors = 0

        val pairs = alerts
                .filter {
                    p -> !p.monitorEntry.wasHandled && !p.eventEntry.isCancelledOrDeclined
                }
                .map{
                    p ->
                    MonitorDataPair(
                            p.monitorEntry,
                            p.eventEntry.copy(origin = EventOrigin.FullManual, timeFirstSeen = System.currentTimeMillis())
                            )
                }


//        for (p in alerts) {
//            if (p.monitorEntry.wasHandled)
//                continue
//            if (p.eventEntry.isCancelledOrDeclined)
//                continue
//            DevLog.info(LOG_TAG, "registerFiredEventsInDB: $p")
//
//            var event: EventAlertRecord? = null
//
//            if (!p.monitorEntry.alertCreatedByUs) {
//                // not manually created -- can read directly from the provider!
//                DevLog.info(LOG_TAG, "Alert was not created by the app, so trying to read alert off the provider")
//                event = calendarProvider.getAlertByEventIdAndTime(context, p.eventEntry.eventId, p.eventEntry.alertTime)
//
//                if (event != null)
//                    numAlertsFound++
//                else
//                    numAlertsNotFound++
//            }
//
//            if (event == null) {
//                DevLog.warn(LOG_TAG, "Alert not found - reading event by ID for details")
//
//                val calEvent = calendarProvider.getEvent(context, p.eventEntry.eventId)
//                if (calEvent != null) {
//                    event = EventAlertRecord(
//                            calendarId = calEvent.calendarId,
//                            eventId = calEvent.eventId,
//                            isAllDay = calEvent.isAllDay,
//                            rRule = calEvent.rRule,
//                            rDate = calEvent.rDate,
//                            exRRule = calEvent.exRRule,
//                            exRDate = calEvent.exRDate,
//                            alertTime = p.monitorEntry.alertTime,
//                            notificationId = 0,
//                            title = calEvent.title,
//                            desc = calEvent.desc,
//                            startTime = calEvent.startTime,
//                            endTime = calEvent.endTime,
//                            instanceStartTime = p.monitorEntry.instanceStartTime,
//                            instanceEndTime = p.monitorEntry.instanceStartTime + calEvent.endTime - calEvent.startTime,
//                            location = calEvent.location,
//                            color = calEvent.color,
//                            timeZone = calEvent.timeZone,
//                            lastStatusChangeTime = 0,
//                            snoozedUntil = 0
//                    )
//                }
//            }
//
//            pairs.add(MonitorDataPair(p.monitorEntry, p.eventEntry.copy(
//                    origin = EventOrigin.FullManual,
//                    timeFirstSeen = System.currentTimeMillis()
//            )))
//
//
//            if (event != null) {
//                event.origin = EventOrigin.FullManual
//                event.timeFirstSeen = System.currentTimeMillis()
//                pairs.add(Pair(alert, event))
//            } else {
//                DevLog.error(LOG_TAG, "Alert: $alert, cant find neither alert nor event. Marking as handled and ignoring.")
//                // all attempts failed - still, markt it as handled, so avoid repeated attempts all over again
//                markAlertsAsHandledInDB(context, listOf(alert))
//                numErrors++
//            }
//        }

//        if (numAlertsNotFound != 0 || numErrors != 0)
//            DevLog.info(LOG_TAG, "Got ${pairs.size} pairs, num found alerts: $numAlertsFound, not found: $numAlertsNotFound, errors: $numErrors")

//        val pairsToAdd = pairs.filter { (_, event) ->  !event.isCancelledOrDeclined  }

        return ApplicationController.registerNewEvents(context, pairs)
    }


    private fun markAlertsAsHandledInDB(context: Context, alerts: Collection<MonitorEventAlertEntry>) {
        CalendarMonitorStorage(context).use {
            db ->
            DevLog.info(LOG_TAG, "marking ${alerts.size} alerts as handled in the manual alerts DB");

            for (alert in alerts)
                alert.wasHandled = true

            db.updateAlerts(alerts)
        }
    }

    private fun markAlertPairsAsHandledInDB(context: Context, alerts: Collection<Pair<MonitorEventAlertEntry, EventAlertRecord?>>) {
        CalendarMonitorStorage(context).use {
            db ->
            DevLog.info(LOG_TAG, "marking ${alerts.size} alerts as handled in the manual alerts DB");

            for (alert in alerts)
                alert.first.wasHandled = true

            db.updateAlerts(alerts.map{ it.first })
        }
    }

    fun scanForSingleEvent(context: Context, event: EventRecord): Boolean {

        if (!PermissionsManager.hasAllPermissions(context)) {
            DevLog.error(LOG_TAG, "scanForSingleEvent: no permissions");
            return false
        }

        var hasFiredAnything = false

        val currentTime = System.currentTimeMillis()

        val alerts = calendarProvider.getEventAlertsForEvent(context, event).associateBy { it.key }

        val alertsToVerify = mutableListOf<MonitorEventAlertEntry>()

        var numUpdatedAlerts = 0
        var numAddedAlerts = 0

        CalendarMonitorStorage(context).use {
            db ->
            val knownAlerts = db.getInstanceAlerts(event.contentMd5, event.startTime).associateBy { it.key }

            // Add new alerts into DB
            for ((key, alert) in alerts) {

                val knownAlert = knownAlerts.get(key)

                if (knownAlert == null) {
                    alertsToVerify.add(alert)
                    db.addAlert(alert)
                    ++numAddedAlerts
                }
                else if (knownAlert.detailsChanged(alert)) {
                    alertsToVerify.add(alert)
                    db.updateAlert(alert)
                    ++numUpdatedAlerts
                }
                else if (!knownAlert.wasHandled) {
                    alertsToVerify.add(knownAlert)
                }
            }
        }

        DevLog.info(LOG_TAG, "scanForSingleEvent: ${event.eventId}, num alerts: ${alerts.size}," +
                " new alerts: $numAddedAlerts, updated alerts: $numUpdatedAlerts")

        // Check what we should have already fired at

        val fireAlertsUpTo = currentTime + Consts.ALARM_THRESHOLD

        val dueAlerts = alertsToVerify.filter { !it.wasHandled && it.alertTime <= fireAlertsUpTo }

        if (dueAlerts.isNotEmpty()) {
            DevLog.warn(LOG_TAG, "scanForSingleEvent: ${dueAlerts.size} due alerts - nearly missed these")
            hasFiredAnything = manualFireAlertList(context, alertListIntoMonitorPairsList(context, dueAlerts))
        }

        return hasFiredAnything
    }

    fun scanNextEvent(context: Context, state: CalendarMonitorState): Pair<Long, Boolean> {

        if (!PermissionsManager.hasAllPermissions(context)) {
            DevLog.error(LOG_TAG, "scanNextEvent: no permissions");
            return Pair(Long.MAX_VALUE, false)
        }

        var hasFiredAnything = false

        val currentTime = System.currentTimeMillis()

        var scanFrom = currentTime - Consts.MANUAL_SCAN_WINDOW
        val scanTo = currentTime + Consts.MANUAL_SCAN_WINDOW

        // cap scan from range to 1 month back only
        val monthAgo = currentTime - Consts.MAX_SCAN_BACKWARD_DAYS * Consts.DAY_IN_MILLISECONDS
        if (scanFrom < monthAgo) {
            DevLog.info(LOG_TAG, "scan from capped from $scanFrom to $monthAgo")
            scanFrom = monthAgo
        }

        val firstScanEver = state.firstScanEver

        val alerts = calendarProvider.getEventAlertsForInstancesInRange(context, scanFrom, scanTo)
                .map{ MonitorDataPair.fromEventAlertRecord(it) }

        val alertsMerged =
                filterAndMergeAlerts(context, alerts, scanFrom, scanTo).sortedBy { it.first.alertTime }

        DevLog.info(LOG_TAG, "scanNextEvent: scan range: $scanFrom, $scanTo (${Date(scanFrom)} - ${Date(scanTo)})," +
                " got ${alerts.size} requests off the provider, merged count: ${alertsMerged.size}")

        // now we only need to simply fire at all missed requests,
        // and pick the nearest future event,
        // don't forget to update state

        val fireAlertsUpTo = currentTime + Consts.ALARM_THRESHOLD

        var dueAlerts =
                alertsMerged.filter { !it.first.wasHandled && it.first.alertTime <= fireAlertsUpTo }

        if (firstScanEver) {
            state.firstScanEver = false
            DevLog.info(LOG_TAG, "This is a first deep scan ever, not posting 'due' requests")
            markAlertPairsAsHandledInDB(context, dueAlerts)

        }
        else if (dueAlerts.isNotEmpty()) {
            DevLog.warn(LOG_TAG, "scanNextEvent: ${dueAlerts.size} due alerts - nearly missed these")

            if (dueAlerts.size > Consts.MAX_DUE_ALERTS_FOR_MANUAL_SCAN) {
                dueAlerts = dueAlerts
                        .sortedBy { it.first.instanceStartTime }
                        .takeLast(Consts.MAX_DUE_ALERTS_FOR_MANUAL_SCAN)
            }

            val toManualFire = dueAlerts.mapNotNull {
                (m, e) ->
                if (e != null)
                    MonitorDataPair(m, e)
                else
                    alertToMonitorEntry(context, m)
            }

            if (manualFireAlertList(context, toManualFire))
                hasFiredAnything = true
        }

        // Finally - find the next nearest alert
        val nextAlert = alertsMerged.filter { !it.first.wasHandled && it.first.alertTime > fireAlertsUpTo }
                .minByOrNull { it.first.alertTime }

        val nextAlertTime = nextAlert?.first?.alertTime ?: Long.MAX_VALUE
        state.nextEventFireFromScan = nextAlertTime

        DevLog.info(LOG_TAG, "scanNextEvent: next alert $nextAlertTime");

        // Very finally - delete requests that we are no longer interested in:
        // * requests that were handled already
        // * and old enough (before this iteration's 'scanFrom'
        CalendarMonitorStorage(context).use {
            it.deleteAlertsMatching {
                alert ->
                alert.instanceStartTime < currentTime - Consts.ALERTS_DB_REMOVE_AFTER && alert.wasHandled
            }
        }

        return Pair(nextAlertTime, hasFiredAnything)
    }

    private fun filterAndMergeAlerts(context: Context, alerts: List<MonitorDataPair>, scanFrom: Long, scanTo: Long)
            : List<Pair<MonitorEventAlertEntry, EventAlertRecord?>> {

        val ret = arrayListOf<Pair<MonitorEventAlertEntry, EventAlertRecord?>>()

        val providedAlerts =
                alerts.map{ Pair<MonitorEventAlertEntry, EventAlertRecord?>(it.monitorEntry, it.eventEntry) }
                        .associateBy { it.first.key }

        CalendarMonitorStorage(context).use {
            db ->
            val knownAlerts =
                    db.getAlertsForInstanceStartRange(scanFrom, scanTo)
                            .map{ Pair<MonitorEventAlertEntry, EventAlertRecord?>(it, null)}
                            .associateBy { it.first.key }

            val newAlerts = providedAlerts - knownAlerts.keys
            val disappearedAlerts = knownAlerts - providedAlerts.keys

            // only delete events that are both disappeared and outdated
            val eventsToDelete =
                disappearedAlerts.values
                    .filter { it.first.instanceStartTime < scanFrom && it.first.alertTime < scanFrom }
                    .map { it.first }

            DevLog.info(LOG_TAG, "filterAndMergeAlerts: ${newAlerts.size} new alerts, ${disappearedAlerts.size} disappeared alerts (${eventsToDelete.size} to remove)")

            db.deleteAlerts(eventsToDelete)

            db.addAlerts(newAlerts.values.map{ it.first })

            // Presumably this would be faster than re-reading SQLite again
            ret.addAll((knownAlerts - disappearedAlerts.keys + newAlerts).values)

            DevLog.debug(LOG_TAG, "filterAndMergeAlerts - done")
        }

        return ret
    }

    companion object {
        private const val LOG_TAG = "CalendarMonitor"
    }
}