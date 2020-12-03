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
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toolbar
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.navigation.ui.navigateUp
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.calendarmonitor.CalendarMonitor
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.utils.*
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.utils.textutils.EventFormatter
import java.lang.StringBuilder
import java.util.*

class MainActivityCalendarFragment : Fragment(), SimpleEventListCallback<EventAlertRecord> {

    private lateinit var staggeredLayoutManager: StaggeredGridLayoutManager
    private lateinit var recyclerView: RecyclerView

    private var adapter: SimpleEventListAdapter<EventAlertRecord>? = null

    private var primaryColor: Int? = Consts.DEFAULT_CALENDAR_EVENT_COLOR
    private var eventFormatter: EventFormatter? = null

    private var currentDay: Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

    private lateinit var lineLayouts: Array<LinearLayout>
    private lateinit var dayLabels: Array<TextView>

    private lateinit var monthNames: Array<String>

    private lateinit var currentMonthColor: ColorStateList
    private lateinit var currentDayColor: ColorStateList
    private lateinit var otherMonthColor: ColorStateList

    private lateinit var settings: Settings

    private val lineLayoutIds = listOf(
            R.id.layout_calendar_line_0,
            R.id.layout_calendar_line_1,
            R.id.layout_calendar_line_2,
            R.id.layout_calendar_line_3,
            R.id.layout_calendar_line_4,
            R.id.layout_calendar_line_5,
    )

    private val dayLabelIds = listOf(
            R.id.cal_day_line_0_item_0, R.id.cal_day_line_0_item_1, R.id.cal_day_line_0_item_2, R.id.cal_day_line_0_item_3,
            R.id.cal_day_line_0_item_4, R.id.cal_day_line_0_item_5, R.id.cal_day_line_0_item_6,
            R.id.cal_day_line_1_item_0, R.id.cal_day_line_1_item_1, R.id.cal_day_line_1_item_2, R.id.cal_day_line_1_item_3,
            R.id.cal_day_line_1_item_4, R.id.cal_day_line_1_item_5, R.id.cal_day_line_1_item_6,
            R.id.cal_day_line_2_item_0, R.id.cal_day_line_2_item_1, R.id.cal_day_line_2_item_2, R.id.cal_day_line_2_item_3,
            R.id.cal_day_line_2_item_4, R.id.cal_day_line_2_item_5, R.id.cal_day_line_2_item_6,
            R.id.cal_day_line_3_item_0, R.id.cal_day_line_3_item_1, R.id.cal_day_line_3_item_2, R.id.cal_day_line_3_item_3,
            R.id.cal_day_line_3_item_4, R.id.cal_day_line_3_item_5, R.id.cal_day_line_3_item_6,
            R.id.cal_day_line_4_item_0, R.id.cal_day_line_4_item_1, R.id.cal_day_line_4_item_2, R.id.cal_day_line_4_item_3,
            R.id.cal_day_line_4_item_4, R.id.cal_day_line_4_item_5, R.id.cal_day_line_4_item_6,
            R.id.cal_day_line_5_item_0, R.id.cal_day_line_5_item_1, R.id.cal_day_line_5_item_2, R.id.cal_day_line_5_item_3,
            R.id.cal_day_line_5_item_4, R.id.cal_day_line_5_item_5, R.id.cal_day_line_5_item_6
    )

    private val dayLabelDays = IntArray(dayLabelIds.size)


    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_calendar, container, false)

        this.context?.let {
            ctx ->
            primaryColor = ContextCompat.getColor(ctx, R.color.primary)
            eventFormatter  = EventFormatter(ctx)
            adapter =
                    SimpleEventListAdapter(
                            ctx,
                            R.layout.event_card_compact,
                            this)

            monthNames = ctx.resources.getStringArray(R.array.month_names_short)

            currentMonthColor = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.cal_current_month))
            otherMonthColor = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.cal_other_month))
            currentDayColor = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.cal_current_day))
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


        dayLabels = dayLabelIds.map{ id -> root.findViewById<TextView>(id) }.toTypedArray()
        lineLayouts = lineLayoutIds.map{ id -> root.findViewById<LinearLayout>(id) }.toTypedArray()

        for (lbl in dayLabels) {
            lbl.setOnClickListener(this::onDayClick)
        }


        return root
    }

    // TODO: coroutines!!!
    // TODO: coroutines!!!
    // TODO: coroutines!!!
    // TODO: coroutines!!!
    override fun onResume() {
        DevLog.debug(LOG_TAG, "onResume")
        super.onResume()
        displayCalendar()
        updateTitle()
        reloadData()
    }

    fun reloadData() {
        this.activity?.let {
            activity ->

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

    fun updateTitle() {
        this.activity?.let { activity ->
            (activity as MainActivityNG).supportActionBar?.title = "${monthNames[currentDay.month]} ${currentDay.year}"
        }
    }

    fun displayCalendar() {
        val currentMonth = currentDay.month
        val currentDayOfMonth =  currentDay.dayOfMonth

        val day = currentDay.clone() as Calendar
        day.dayOfMonth = 1

        for (idx in 0 until 7) {
            if (day.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY)
                break;
            day.timeInMillis -= 24 * 3600 * 1000L
        }

        for (idx in 0 until 7 * 6) {
            dayLabelDays[idx] = -1
        }

        for (idx in 0 until 7 * 6) {
            val layout = lineLayouts[idx / 7]

            if (idx == 5 * 7 && day.month != currentMonth) {
                layout.visibility = View.GONE
                break
            }
            layout.visibility = View.VISIBLE

            dayLabels[idx].setText("${day.dayOfMonth}")

            if (day.month == currentMonth) {
                dayLabelDays[idx] = day.dayOfMonth
                if (day.dayOfMonth == currentDayOfMonth)
                    dayLabels[idx].setTextColor(currentDayColor)
                else
                    dayLabels[idx].setTextColor(currentMonthColor)
            }
            else {
                dayLabels[idx].setTextColor(otherMonthColor)
            }


            // move to the next day
            day.timeInMillis += 24 * 3600 * 1000L
        }
    }

    fun onDayClick(v: View) {
        val idx = dayLabels.indexOf(v as TextView)
        if (idx < 0 || idx >= dayLabelDays.size || dayLabelDays[idx] == -1) {
            return
        }
        currentDay.dayOfMonth = dayLabelDays[idx]
        displayCalendar()
        reloadData()
    }

    // TODO: add an option to view the event, not only to restore it
    override fun onItemClick(v: View, position: Int, entry: EventAlertRecord) {
        this.context?.let {
            ctx ->
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
        return ""
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