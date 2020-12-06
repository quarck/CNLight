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
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.calendarmonitor.CalendarMonitor
import com.github.quarck.calnotify.utils.DateTimeUtils
import com.github.quarck.calnotify.utils.adjustCalendarColor
import com.github.quarck.calnotify.utils.background
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.utils.textutils.EventFormatter

class MainActivityUpcomingEventsFragment : Fragment(), SimpleEventListCallback<EventAlertRecord> {

    private lateinit var recyclerViewToday: RecyclerView
    private lateinit var recyclerViewOther: RecyclerView

    private var adapterToday: SimpleEventListAdapter<EventAlertRecord>? = null
    private var adapterOther: SimpleEventListAdapter<EventAlertRecord>? = null

    private var primaryColor: Int? = Consts.DEFAULT_CALENDAR_EVENT_COLOR
    private var eventFormatter: EventFormatter? = null

    private var statusHandled: String? = null
    private var eventReminderTimeFmt: String? = null
    private var colorSkippedItemBotomLine: Int  = 0x7f3f3f3f
    private var colorNonSkippedItemBottomLine: Int = 0x7f7f7f7f

    private var monitorEntries = mapOf<MonitorEventAlertEntryKey, MonitorEventAlertEntry>()

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
            adapterToday = SimpleEventListAdapter(ctx, R.layout.event_card_compact, this)
            adapterOther = SimpleEventListAdapter(ctx, R.layout.event_card_compact, this)

            statusHandled = ctx.resources.getString(R.string.event_was_marked_as_finished)
            eventReminderTimeFmt = ctx.resources.getString(R.string.reminder_at_fmt)

            colorSkippedItemBotomLine = ContextCompat.getColor(ctx, R.color.divider)
            colorNonSkippedItemBottomLine = ContextCompat.getColor(ctx, R.color.secondary_text)
        }

        recyclerViewToday = root.findViewById<RecyclerView>(R.id.list_events_today)
        recyclerViewToday.adapter = adapterToday
        adapterToday?.recyclerView = recyclerViewToday
        recyclerViewToday.isNestedScrollingEnabled = false

        recyclerViewOther = root.findViewById<RecyclerView>(R.id.list_events_other)
        recyclerViewOther.adapter = adapterOther
        adapterOther?.recyclerView = recyclerViewOther
        recyclerViewOther.isNestedScrollingEnabled = false

        return root
    }

    override fun onResume() {
        DevLog.debug(LOG_TAG, "onResume")
        super.onResume()

        this.activity?.let {
            activity ->
            background {

                val from = System.currentTimeMillis()
                val mid = from + Consts.UPCOMING_EVENTS_WINDOW / 2
                val to = from + Consts.UPCOMING_EVENTS_WINDOW

                monitorEntries =
                        CalendarMonitor(CalendarProvider)
                                .getAlertsForAlertRange(activity, scanFrom = from, scanTo = to)
                                .associateBy{ it.key }

                val events1 =
                        CalendarProvider
                                .getEventAlertsForInstancesInRange(activity, from, mid)
                                .filter { it.alertTime >= from }
                                .partition { isToday(it) }

                val today = events1.first.sortedBy { it.alertTime }.toMutableList()

                activity.runOnUiThread { adapterToday?.setEventsToDisplay(today) }

                val events2 =
                        CalendarProvider
                                .getEventAlertsForInstancesInRange(activity, mid, to)
                                .filter { it.alertTime >= from }


                val otherDays = (events1.second + events2).sortedBy { it.alertTime }.toMutableList()

                activity.runOnUiThread {
                    adapterOther?.setEventsToDisplay(otherDays)
                }
            }
        }

    }

    // TODO: add an option to view the event, not only to restore it
    override fun onItemClick(v: View, position: Int, entry: EventAlertRecord) {
        this.context?.let {
            ctx ->
            startActivity(
                    Intent(ctx, ViewEventActivity::class.java)
                            .putExtra(Consts.INTENT_EVENT_ID_KEY, entry.eventId)
                            .putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, entry.instanceStartTime)
                            .putExtra(Consts.INTENT_ALERT_TIME, entry.alertTime)
                            .putExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, true)
                            .putExtra(Consts.INTENT_VIEW_FUTURE_EVENT_EXTRA, true)
                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
    }

    override fun getItemTitle(entry: EventAlertRecord): String =  entry.title

    override fun getUseBoldTitle(entry: EventAlertRecord): Boolean = false

    private fun isToday(event: EventAlertRecord): Boolean =
            if (event.isAllDay) DateUtils.isToday(event.displayedStartTime)
            else DateTimeUtils.isUTCToday(event.displayedStartTime)

    override fun getItemMiddleLine(entry: EventAlertRecord): String {
        return eventFormatter?.formatDateTimeOneLine(entry) ?: "NULL"
    }

    override fun getItemBottomLine(entry: EventAlertRecord): Pair<String, Int> {

        val monEntry = monitorEntries.get(entry.monitorEntryKey)
        val wasHandled = monEntry?.wasHandled == true
        val reminderLine = eventFormatter?.let { (eventReminderTimeFmt ?: "%s").format(it.formatTimePoint(entry.alertTime, noWeekDay = true)) } ?: ""

        return if (wasHandled)
            Pair((statusHandled ?: "/SKIP/") + " " + reminderLine, colorSkippedItemBotomLine)
        else
            Pair(reminderLine, colorNonSkippedItemBottomLine)
    }

    override fun getItemColor(entry: EventAlertRecord): Int =
            if (entry.color != 0)
                entry.color.adjustCalendarColor()
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