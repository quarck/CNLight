//
//   Calendar Notifications Plus
//   Copyright (C) 2020 Sergey Parshin (s.parshin.sc@gmail.com)
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

package com.github.quarck.calnotify.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.calendarmonitor.CalendarMonitor
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.utils.adjustCalendarColor
import com.github.quarck.calnotify.utils.background
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.utils.textutils.EventFormatter
import java.lang.StringBuilder

class MainActivityUpcomingEventsFragment : Fragment(), SimpleEventListCallback<MonitorDataPair> {

    private lateinit var recyclerView: RecyclerView

    private var adapter: SimpleEventListAdapter<MonitorDataPair>? = null

    private var primaryColor: Int? = Consts.DEFAULT_CALENDAR_EVENT_COLOR
    private var eventFormatter: EventFormatter? = null

    private var statusHandled: String? = null
    private var eventReminderTimeFmt: String? = null

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_upcoming, container, false)

        this.context?.let {
            ctx ->
            primaryColor = ContextCompat.getColor(ctx, R.color.primary)
            eventFormatter  = EventFormatter(ctx)
            adapter =
                    SimpleEventListAdapter(
                            ctx,
                            R.layout.event_card_compact,
                            this)

            statusHandled = ctx.resources.getString(R.string.event_was_marked_as_finished)
            eventReminderTimeFmt = ctx.resources.getString(R.string.reminder_at_fmt)
        }

        recyclerView = root.findViewById<RecyclerView>(R.id.list_events)
        recyclerView.adapter = adapter;
        adapter?.recyclerView = recyclerView

        return root
    }

    // TODO: coroutines!!!
    // TODO: coroutines!!!
    // TODO: coroutines!!!
    // TODO: coroutines!!!
    override fun onResume() {
        DevLog.debug(LOG_TAG, "onResume")
        super.onResume()

        this.activity?.let {
            activity ->
            background {
                val from = System.currentTimeMillis()
                val to = from + Consts.UPCOMING_EVENTS_WINDOW

                val monitorEntries =
                        CalendarMonitor(CalendarProvider)
                                .getAlertsForAlertRange(activity, scanFrom = from, scanTo = to)
                                .associateBy{ it.key }

                val events =
                        CalendarProvider
                                .getEventAlertsForInstancesInRange(activity, from, to)
                                .filter {
                                    it.alertTime >= from
                                }
                                .map {
                                    ev ->
                                    val monitorEntry = monitorEntries.getOrElse(
                                            ev.monitorEntryKey, { MonitorEventAlertEntry.fromEventAlertRecord(ev) })
                                    MonitorDataPair(monitorEntry, ev)
                                }
                                .sortedBy { it -> it.eventEntry.alertTime }
                                .toMutableList()

                activity.runOnUiThread {
                    adapter?.setEventsToDisplay(events)
                }
            }
        }

    }

    // TODO: add an option to view the event, not only to restore it
    override fun onItemClick(v: View, position: Int, entry: MonitorDataPair) {
        this.context?.let {
            ctx ->
            startActivity(
                    Intent(ctx, ViewEventActivity::class.java)
                            .putExtra(Consts.INTENT_EVENT_ID_KEY, entry.eventEntry.eventId)
                            .putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, entry.eventEntry.instanceStartTime)
                            .putExtra(Consts.INTENT_ALERT_TIME, entry.eventEntry.alertTime)
                            .putExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, true)
                            .putExtra(Consts.INTENT_VIEW_FUTURE_EVENT, true)
                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
    }

    override fun getItemTitle(entry: MonitorDataPair): String =  entry.eventEntry.title

    override fun getItemMiddleLine(entry: MonitorDataPair): String {
        return eventFormatter?.let {
            it.formatDateTimeOneLine(entry.eventEntry) + " / " +
                    (eventReminderTimeFmt ?: "%s").format(it.formatTimePoint(entry.monitorEntry.alertTime, noWeekDay = true))
        } ?: "NULL"
    }

    override fun getItemBottomLine(entry: MonitorDataPair): String {
        return if (entry.monitorEntry.wasHandled) statusHandled ?: "_handled_" else ""
    }

    override fun getItemColor(entry: MonitorDataPair): Int =
            if (entry.eventEntry.color != 0)
                entry.eventEntry.color.adjustCalendarColor()
            else
                primaryColor ?: Consts.DEFAULT_CALENDAR_EVENT_COLOR


    override fun onPause() {
        super.onPause()
        DevLog.info(LOG_TAG, "onPause")
    }

    override fun onDetach() {
        super.onDetach()
        DevLog.info(LOG_TAG, "onDetach")
    }

    companion object {
        private const val LOG_TAG = "UpcomingEventsFragment"
    }
}