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

package com.github.quarck.calnotify.notification

import android.app.*
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import android.text.format.DateUtils
import com.github.quarck.calnotify.*
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.utils.textutils.EventFormatter
import com.github.quarck.calnotify.ui.ViewEventActivity
import com.github.quarck.calnotify.utils.*
import com.github.quarck.calnotify.R

@Suppress("VARIABLE_WITH_REDUNDANT_INITIALIZER")
class EventNotificationManager {

    fun onEventAdded(ctx: Context, formatter: EventFormatter, event: EventAlertRecord) {
        postEventNotifications(ctx, formatter, primaryEventId = event.eventId)
    }

    fun onEventRestored(context: Context, formatter: EventFormatter, event: EventAlertRecord) {

        if (event.displayStatus != EventDisplayStatus.Hidden) {
            EventsStorage(context).use {
                it.updateEvent(event, displayStatus = EventDisplayStatus.Hidden)
            }
        }

        postEventNotifications(context, formatter)
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onEventDismissing(context: Context, eventId: Long, notificationId: Int) {
        removeNotification(context, notificationId)
    }

    fun onEventsDismissing(context: Context, events: Collection<EventAlertRecord>) {
        removeNotifications(context, events)
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onEventDismissed(context: Context, formatter: EventFormatter, eventId: Long, notificationId: Int) {
        removeNotification(context, notificationId)
        postEventNotifications(context, formatter)
    }

    fun onEventsDismissed(context: Context,
                          formatter: EventFormatter,
                          events: Collection<EventAlertRecord>,
                          postNotifications: Boolean
    ) {

        for (event in events) {
            removeNotification(context, event.notificationId)
        }

        if (postNotifications) {
            postEventNotifications(context, formatter)
        }
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onEventSnoozed(context: Context, formatter: EventFormatter, eventId: Long, notificationId: Int) {
        removeNotification(context, notificationId)
        postEventNotifications(context, formatter)
    }

    fun onAllEventsSnoozed(context: Context) {
        context.notificationManager.cancelAll()
    }

    private fun shouldCollapseAll(events: List<EventAlertRecord>): Boolean {
        if (events.size > Consts.MAX_NOTIFICATIONS)
            return true
        else if (events.any {it.displayStatus == EventDisplayStatus.DisplayedCollapsed})
            return true
        return false
    }

    // NOTES:
    // requests with snoozedUntil == 0 are currently visible ones
    // requests with experied snoozedUntil are the ones to beep about
    // everything else should be hidden and waiting for the next alarm
    private fun getCurrentEvents(db: EventsStorage, currentTime: Long)
            = db.events.filter {
                (it.snoozedUntil == 0L) || (it.snoozedUntil < currentTime + Consts.ALARM_THRESHOLD)
            }

    fun postEventNotifications(
            context: Context,
            formatter: EventFormatter? = null,
            primaryEventId: Long? = null,
            isReminder: Boolean = false
    ) {

        val formatterLocal = formatter ?: EventFormatter(context)

        EventsStorage(context).use {
            db ->

            val events = getEventsAndUnSnooze(db)
            val notificationRecords = generateNotificationRecords(
                    context = context,
                    events = events,
                    primaryEventId = primaryEventId,
                    isReminder = isReminder
            )

            if (shouldCollapseAll(events)) {
                val (alarms, nonAlarms) = notificationRecords.partition { it.event.isAlarm }
                if (alarms.isNotEmpty()) {
                    postIndividualNotifications(context, db, formatterLocal, alarms.toMutableList())
                }

                if (nonAlarms.isNotEmpty()){
                    postCollapsedNotification(context, db, nonAlarms.toMutableList())
                }
                else {
                    hideCollapsedEventsNotification(context)
                }
            }
            else {
                hideCollapsedEventsNotification(context)
                postIndividualNotifications(context, db, formatterLocal, notificationRecords)
            }
        }
    }

    fun fireEventReminder(context: Context) {
        EventsStorage(context).use {
            db ->
            if (db.events.any { it.isNotSnoozed && it.isAlarm }) {
                postEventNotifications(context, isReminder = true)
            }
        }
    }

    data class EventAlertNotificationRecord(
            val event: EventAlertRecord,
            val soundState: NotificationChannelManager.SoundState,
            val isPrimary: Boolean,
            val newNotification: Boolean, // un-snoozed or primary
            val isReminder: Boolean,
            val alertOnlyOnce: Boolean
    )

    private fun getEventsAndUnSnooze(db: EventsStorage): List<EventAlertRecord> {

        var currentTime = System.currentTimeMillis()

        val events = getCurrentEvents(db, currentTime)

        val eventsToUpdate = mutableListOf<EventAlertRecord>()

        for (event in events) {
            if (event.snoozedUntil == 0L)
                continue

            DevLog.info(LOG_TAG, "Snoozed notification id ${event.notificationId}, eventId ${event.eventId}, switching to un-snoozed state")

            // Update this time before posting notification as this is now used as a sort-key
            currentTime++ // so last change times are not all the same
            event.lastStatusChangeTime = currentTime
            event.snoozedUntil = 0
            event.displayStatus = EventDisplayStatus.Hidden // so we need to show it

            eventsToUpdate.add(event)
        }

        db.updateEvents(eventsToUpdate)

        return events
    }

    private fun generateNotificationRecords(
            context: Context,
            events: List<EventAlertRecord>,
            primaryEventId: Long?,
            isReminder: Boolean
    ): MutableList<EventAlertNotificationRecord> {

        val ret = mutableListOf<EventAlertNotificationRecord>()

        val eventsSorted = events.sortedByDescending { it.instanceStartTime }

        var firstReminder = isReminder

        for (event in eventsSorted) {
            // currently not displayed or forced -- post notifications
            val isPrimary = event.eventId == primaryEventId
            val isNew = isPrimary || (event.displayStatus == EventDisplayStatus.Hidden)
            val isAlarm = event.isAlarm

            val soundState =
                    if (event.isAlarm)
                        NotificationChannelManager.SoundState.Alarm
                    else
                        NotificationChannelManager.SoundState.Normal

            DevLog.info(LOG_TAG, "Notification id ${event.notificationId}, eventId ${event.eventId}: primary=$isPrimary, new=$isNew, " +
                    "reminder=$isReminder, soundState=$soundState")

            ret.add(EventAlertNotificationRecord(
                    event,
                    soundState,
                    isPrimary,
                    isNew,
                    isReminder = isAlarm && firstReminder,
                    alertOnlyOnce = !firstReminder))

            if (isAlarm) {
                firstReminder = false
            }
        }

        if (isReminder && events.isNotEmpty()) {
            context.persistentState.notificationLastFireTime = System.currentTimeMillis()
        }

        return ret;
    }


    ///
    /// Post events in collapsed state
    ///
    private fun postCollapsedNotification(
            context: Context,
            db: EventsStorage,
            notificationRecords: MutableList<EventAlertNotificationRecord>
    ) {
        if (notificationRecords.isEmpty()) {
            hideCollapsedEventsNotification(context)
            return
        }

        DevLog.info(LOG_TAG, "Posting ${notificationRecords.size} notifications in collapsed view")

        val events = notificationRecords.map{ it.event }

        // make sure we remove full notifications
        removeNotifications(context, events)

        val eventsToUpdate = events.filter { it.displayStatus != EventDisplayStatus.DisplayedCollapsed }
        if (eventsToUpdate.isNotEmpty()) {
            db.updateEvents(
                    eventsToUpdate,
                    snoozedUntil = 0L,
                    displayStatus = EventDisplayStatus.DisplayedCollapsed
            )
        }

        // now build actual notification and notify
        val intent = Intent(context, com.github.quarck.calnotify.ui.MainActivity::class.java)
        val pendingIntent = pendingActivityIntent(context, intent, MAIN_ACTIVITY_NUM_NOTIFICATIONS_COLLAPSED_CODE, clearTop = true)

        val numEvents = events.size

        val soundState =
                if (notificationRecords.any {it.event.isAlarm})
                    NotificationChannelManager.SoundState.Alarm
                else
                    NotificationChannelManager.SoundState.Normal

        val channel = NotificationChannelManager.createNotificationChannel(context, soundState)

        val notificationStyle = NotificationCompat.InboxStyle()

        val eventsSorted = events.sortedByDescending { it.instanceStartTime }

        val appendPlusMoreLine = eventsSorted.size > 5
        val lines = eventsSorted.take(if (appendPlusMoreLine) 4 else 5).map {
                    ev ->
                    val flags =
                            if (DateUtils.isToday(ev.displayedStartTime))
                                DateUtils.FORMAT_SHOW_TIME
                            else
                                DateUtils.FORMAT_SHOW_DATE
                    "${DateUtils.formatDateTime(context, ev.displayedStartTime, flags)}: ${ev.title}"
                }.toMutableList()

        if (appendPlusMoreLine) {
            lines.add(context.getString(R.string.plus_more).format(events.size - 4))
        }

        lines.forEach { notificationStyle.addLine(it) }
        notificationStyle.setBigContentTitle("")

        val contentTitle = context.getString(R.string.multiple_events_single_notification).format(events.size)

        val alertOnlyOnce = notificationRecords.all{it.alertOnlyOnce}
        val contentText = if (lines.size > 0) lines[0] else ""

        val builder =
                NotificationCompat.Builder(context, channel.channelId)
                        .setContentTitle(contentTitle)
                        .setContentText(contentText)
                        .setSmallIcon(R.drawable.ic_date_range_white_24dp)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(false)
                        .setOngoing(true)
                        .setStyle(notificationStyle)
                        .setNumber(numEvents)
                        .setShowWhen(false)
                        .setOnlyAlertOnce(alertOnlyOnce)

        DevLog.info(LOG_TAG, "Building collapsed notification: alertOnlyOnce=$alertOnlyOnce, contentTitle=$contentTitle, number=$numEvents, channel=$channel")

        builder.applyChannelAttributes(channel)

        val notification = builder.build()

        try {
            context.notificationManager.notify(
                    Consts.NOTIFICATION_ID_COLLAPSED, notification) // would update if already exists
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "Error posting notification: $ex, ${ex.stackTrace}")
        }
    }


    private fun postIndividualNotifications(
            context: Context,
            db: EventsStorage,
            formatter: EventFormatter,
            notificationRecords: MutableList<EventAlertNotificationRecord>
    ) {
        DevLog.debug(LOG_TAG, "Posting ${notificationRecords.size} notifications")

        val events = notificationRecords.map {it.event}

        val eventsToUpdate = events.filter { it.displayStatus != EventDisplayStatus.DisplayedNormal }
        if (eventsToUpdate.isNotEmpty()) {
            db.updateEvents(
                    eventsToUpdate,
                    snoozedUntil = 0L,
                    displayStatus = EventDisplayStatus.DisplayedNormal
            )
        }

        for (ntf in notificationRecords) {

            if (!ntf.isReminder && !ntf.newNotification) {
                // not a reminder and not a new notification
                // not need to post already visible notification, unless it is a repost where
                // we want to post everything
                continue
            }

            postNotification(
                    ctx = context,
                    formatter = formatter,
                    event = ntf.event,
                    isReminder = ntf.isReminder,
                    alertOnlyOnce = ntf.alertOnlyOnce,
                    soundState = ntf.soundState
            )

        }
   }

    private fun lastStatusChangeToSortingKey(lastStatusChangeTime: Long, eventId: Long): String {

        val sb = StringBuffer(20)

        var temp = eventId % (24 * 3)

        for (i in 0..3) {

            val chr = 24 - temp % 24
            temp /= 24

            sb.append(('A'.toInt() + chr).toChar())
        }

        temp = lastStatusChangeTime - 1500000000000L

        while (temp > 0) {

            val chr = 24 - temp % 24
            temp /= 24

            sb.append(('A'.toInt() + chr).toChar())
        }

        return sb.reverse().toString()
    }

    @Suppress("unused")
    private fun isNotificationVisible(ctx: Context, event: EventAlertRecord): Boolean {

        val intent = snoozeIntent(ctx, event.eventId, event.instanceStartTime, event.notificationId)
        val id = event.notificationId * EVENT_CODES_TOTAL + EVENT_CODE_SNOOOZE_OFFSET
        val pendingIntent: PendingIntent? = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_NO_CREATE)
        return pendingIntent != null
    }

    private fun postNotification(
            ctx: Context,
            formatter: EventFormatter,
            event: EventAlertRecord,
            isReminder: Boolean,
            alertOnlyOnce: Boolean,
            soundState: NotificationChannelManager.SoundState
    ) {
        val notificationManager = ctx.notificationManager

        val viewEventPendingIntent =
            pendingActivityIntent(ctx,
                CalendarIntents.intentForAction(Intent.ACTION_VIEW, event.eventId),
                event.notificationId * EVENT_CODES_TOTAL + EVENT_CODE_OPEN_OFFSET
            )

        val snoozeActivityIntent =
                pendingActivityIntent(ctx,
                        snoozeIntent(ctx, event.eventId, event.instanceStartTime, event.notificationId),
                        event.notificationId * EVENT_CODES_TOTAL + EVENT_CODE_SNOOOZE_OFFSET
                )

        val dismissPendingIntent =
                pendingServiceIntent(ctx,
                        dismissOrDeleteIntent(ctx, event.eventId, event.instanceStartTime, event.notificationId),
                        event.notificationId * EVENT_CODES_TOTAL + EVENT_CODE_DISMISS_OFFSET
                )


        val currentTime = System.currentTimeMillis()

        val notificationTextString = formatter.formatNotificationSecondaryText(event)
        var title = event.title

        if (isReminder) {
            title += ctx.getString(R.string.reminder_at).format(
                    DateUtils.formatDateTime(ctx, currentTime, DateUtils.FORMAT_SHOW_TIME)
            )
        }

        val sortKey = lastStatusChangeToSortingKey(event.lastStatusChangeTime, event.eventId)

        DevLog.info(LOG_TAG, "SortKey: ${event.eventId} -> ${event.lastStatusChangeTime} -> $sortKey")

        val channel = NotificationChannelManager.createNotificationChannel(ctx, soundState)

        val builder = NotificationCompat.Builder(ctx, channel.channelId)
                .setContentTitle(title)
                .setContentText(notificationTextString)
                .setSmallIcon(R.drawable.ic_event_white_24dp)
                .setContentIntent(viewEventPendingIntent)
                .setAutoCancel(false)
                .setOngoing(true)
                .setStyle(NotificationCompat.BigTextStyle().bigText(notificationTextString))
                .setWhen(event.lastStatusChangeTime)
                .setShowWhen(false)
                .setSortKey(sortKey)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setOnlyAlertOnce(alertOnlyOnce)

        builder.setGroup(NOTIFICATION_GROUP)

        val dismissAction =
            NotificationCompat.Action.Builder(
                R.drawable.ic_clear_white_24dp,
                ctx.getString(R.string.dismiss_notification),
                dismissPendingIntent
            ).build()

        builder.addAction(dismissAction)

        val snoozeAction =
            NotificationCompat.Action.Builder(
                R.drawable.ic_event_white_24dp,
                ctx.getString(R.string.snooze),
                snoozeActivityIntent
            ).build()
        
        builder.addAction(snoozeAction)

        builder.setColor(event.color.adjustCalendarColor(false))

        builder.applyChannelAttributes(channel)

        try {
            DevLog.info(LOG_TAG, "adding: notificationId=${event.notificationId}")

            notificationManager.notify(
                    event.notificationId,
                    builder.build()
            )
        }
        catch (ex: Exception) {
            DevLog.error(LOG_TAG, "Exception on notificationId=${event.notificationId}: ${ex.detailed}")
        }
    }

    private fun snoozeIntent(ctx: Context, eventId: Long, instanceStartTime: Long, notificationId: Int): Intent {

        val intent = Intent(ctx, ViewEventActivity::class.java)
        intent.putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, notificationId)
        intent.putExtra(Consts.INTENT_EVENT_ID_KEY, eventId)
        intent.putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, instanceStartTime)

        intent.action = Intent.ACTION_MAIN
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        return intent
    }

    private fun dismissOrDeleteIntent(ctx: Context, eventId: Long, instanceStartTime: Long, notificationId: Int): Intent {

        val intent = Intent(ctx, NotificationActionMarkDoneService::class.java)
        intent.putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, notificationId)
        intent.putExtra(Consts.INTENT_EVENT_ID_KEY, eventId)
        intent.putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, instanceStartTime)
        return intent
    }

    private fun pendingServiceIntent(ctx: Context, intent: Intent, id: Int): PendingIntent
            = PendingIntent.getService(ctx, id, intent, PendingIntent.FLAG_CANCEL_CURRENT)

    private fun pendingActivityIntent(ctx: Context, intent: Intent, id: Int, clearTop: Boolean = false): PendingIntent {

        intent.flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TASK or
                (if (clearTop) Intent.FLAG_ACTIVITY_CLEAR_TOP else 0)

        return PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_UPDATE_CURRENT)

    }

    fun removeNotification(ctx: Context, notificationId: Int) {
        val notificationManager = ctx.notificationManager
        notificationManager.cancel(notificationId)
    }

    private fun removeNotifications(context: Context, events: Collection<EventAlertRecord>) {
        val notificationManager = context.notificationManager

        DevLog.info(LOG_TAG, "Removing 'full' notifications for  ${events.size} events")

        for (event in events)
            notificationManager.cancel(event.notificationId)
    }

    private fun removeVisibleNotifications(ctx: Context, events: Collection<EventAlertRecord>) {
        val notificationManager = ctx.notificationManager

        events.filter { it.displayStatus != EventDisplayStatus.Hidden }
                .forEach { notificationManager.cancel(it.notificationId) }
    }

//    private fun postNumNotificationsCollapsed(
//            context: Context,
//            db: EventsStorage,
//            settings: Settings,
//            events: List<EventAlertRecord>,
//            isQuietPeriodActive: Boolean
//    ) {
//        DevLog.debug(LOG_TAG, "Posting collapsed view notification for ${events.size} requests")
//
//        val intent = Intent(context, MainActivityNG::class.java)
//        val pendingIntent = pendingActivityIntent(context, intent, MAIN_ACTIVITY_NUM_NOTIFICATIONS_COLLAPSED_CODE, clearTop = true)
//
//        val numEvents = events.size
//
//        val title = java.lang.String.format(context.getString(R.string.multiple_events), numEvents)
//
//        val text = context.getString(com.github.quarck.calnotify.R.string.multiple_events_details)
//
//        val bigText =
//                events
//                        .sortedByDescending { it.instanceStartTime }
//                        .take(30)
//                        .fold(
//                                StringBuilder(), {
//                            sb, ev ->
//
//                            val flags =
//                                    if (DateUtils.isToday(ev.displayedStartTime))
//                                        DateUtils.FORMAT_SHOW_TIME
//                                    else
//                                        DateUtils.FORMAT_SHOW_DATE
//
//                            sb.append("${DateUtils.formatDateTime(context, ev.displayedStartTime, flags)}: ${ev.title}\n")
//                        })
//                        .toString()
//
//        val channel = NotificationChannelManager.createNotificationChannelForPurpose(
//                context,
//                isReminder = false,
//                soundState = NotificationChannelManager.SoundState.Silent
//        )
//
//        val builder =
//                NotificationCompat.Builder(context, channel)
//                        .setContentTitle(title)
//                        .setContentText(text)
//                        .setSmallIcon(com.github.quarck.calnotify.R.drawable.stat_notify_calendar)
//                        .setContentIntent(pendingIntent)
//                        .setAutoCancel(false)
//                        .setOngoing(true)
//                        .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
//                        .setShowWhen(false)
//                        .setOnlyAlertOnce(true)
//
//
//        builder.applyChannelAttributes(channel)
//        builder.setGroup(NOTIFICATION_GROUP)
//
//        val notification = builder.build()
//
//        context.notificationManager.notify(Consts.NOTIFICATION_ID_COLLAPSED, notification) // would update if already exists
//    }

    private fun hideCollapsedEventsNotification(context: Context) {
        DevLog.debug(LOG_TAG, "Hiding collapsed view notification")
        context.notificationManager.cancel(Consts.NOTIFICATION_ID_COLLAPSED)
    }


    companion object {
        private const val LOG_TAG = "EventNotificationManager"

        private const val NOTIFICATION_GROUP = "GROUP_1"

        const val EVENT_CODE_SNOOOZE_OFFSET = 0
        const val EVENT_CODE_DISMISS_OFFSET = 1
        @Suppress("unused")
//        const val EVENT_CODE_DELETE_OFFSET = 2
        const val EVENT_CODE_OPEN_OFFSET = 3
        const val EVENT_CODE_DEFAULT_SNOOOZE0_OFFSET = 4
        const val EVENT_CODE_MUTE_TOGGLE_OFFSET = 5
        const val EVENT_CODE_DEFAULT_SNOOOZE_MAX_ITEMS = 10
        const val EVENT_CODES_TOTAL = 16

        const val MAIN_ACTIVITY_EVERYTHING_COLLAPSED_CODE = 0
        const val MAIN_ACTIVITY_NUM_NOTIFICATIONS_COLLAPSED_CODE = 1
        const val MAIN_ACTIVITY_REMINDER_CODE = 2
        const val MAIN_ACTIVITY_GROUP_NOTIFICATION_CODE = 3
    }
}
