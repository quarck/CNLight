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
import android.view.*
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.utils.*
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.utils.textutils.EventFormatter
import java.util.*


class MainActivityCalendarFragment : Fragment(), SimpleEventListCallback<EventAlertRecord> {

    private lateinit var staggeredLayoutManager: StaggeredGridLayoutManager
    private lateinit var recyclerView: RecyclerView

    private var adapter: SimpleEventListAdapter<EventAlertRecord>? = null

    private var primaryColor: Int? = Consts.DEFAULT_CALENDAR_EVENT_COLOR
    private var eventFormatter: EventFormatter? = null

    private lateinit var calendarViewRoot: LinearLayout
    private lateinit var grid: CalendarGrid

    private lateinit var monthNames: Array<String>

    private val deviceTimeZone = TimeZone.getDefault()

    private var today: Calendar = Calendar.getInstance(deviceTimeZone)
    private var currentDay: Calendar = Calendar.getInstance(deviceTimeZone)

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_calendar, container, false)

        this.context?.let { ctx ->
            primaryColor = ContextCompat.getColor(ctx, R.color.primary)
            eventFormatter = EventFormatter(ctx)
            adapter = SimpleEventListAdapter(ctx, R.layout.event_card_compact, this)

            monthNames = ctx.resources.getStringArray(R.array.month_names)

            grid = CalendarGrid(ctx, inflater, this::onDayClick)

            calendarViewRoot = root.findViewById(R.id.layout_calendar_root)
            val lp = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            calendarViewRoot.addView(grid.view, lp)
        }

        staggeredLayoutManager = StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL)
        recyclerView = root.findViewById<RecyclerView>(R.id.list_events)
        recyclerView.layoutManager = staggeredLayoutManager;
        recyclerView.adapter = adapter;
        adapter?.recyclerView = recyclerView

        currentDay.firstDayOfWeek = Calendar.MONDAY
        currentDay.hourOfDay = 0
        currentDay.minute = 0
        currentDay.second = 0
        currentDay.millisecond = 0

        return root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DevLog.info(LOG_TAG, "onCreate")
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, menuInflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, menuInflater)
        DevLog.info(LOG_TAG, "onCreateOptionsMenu")
        menuInflater.inflate(R.menu.calendar_activity_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        this.context?.let {
            ctx ->
            when (item.itemId) {
                R.id.action_go_today ->
                    onDayClick(grid, today)
            }
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onResume() {
        DevLog.debug(LOG_TAG, "onResume")
        super.onResume()
        grid.setDisplayPosition(currentDay, today)
        updateTitle()
        reloadData()
    }

    private fun reloadData() {

        this.activity?.let { activity ->
            background {
                val from = currentDay.timeInMillis
                val to = from + Consts.DAY_IN_MILLISECONDS

                val events =
                        CalendarProvider
                                .getInstancesInRange(activity, from, to)
                                .sortedBy { it -> it.instanceStartTime }
                                .toMutableList()

                activity.runOnUiThread {
                    adapter?.setEventsToDisplay(events)
                }
            }
        }
    }

    private fun updateTitle() {
        this.activity?.let { activity ->
            (activity as MainActivityNG).supportActionBar?.title = "${monthNames[currentDay.month]} ${currentDay.year}"
        }
    }


    private fun onDayClick(grid: CalendarGrid, c: Calendar) {
        currentDay = c.clone() as Calendar
        grid.setDisplayPosition(currentDay, today)
        reloadData()
        updateTitle()
    }

    // Click on the event in the list
    override fun onItemClick(v: View, position: Int, entry: EventAlertRecord) {
        this.context?.let { ctx ->
            startActivity(
                    Intent(ctx, ViewEventActivity::class.java)
                            .putExtra(Consts.INTENT_EVENT_ID_KEY, entry.eventId)
                            .putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, entry.instanceStartTime)
                            .putExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, true)
                            .putExtra(Consts.INTENT_VIEW_FUTURE_EVENT, true)
                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
    }

    override fun getItemTitle(entry: EventAlertRecord): String =  entry.title

    override fun getItemMiddleLine(entry: EventAlertRecord): String {
        return eventFormatter?.formatDateTimeOneLine(entry) ?: "NULL"
    }

    override fun getItemBottomLine(entry: EventAlertRecord): String {
        return "__ADD_UTC_OFFSET_WHEN_LOADING__"
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

        private const val SWIPE_THRESHOLD = 100
        private const val SWIPE_VELOCITY_THRESHOLD = 100
    }
}