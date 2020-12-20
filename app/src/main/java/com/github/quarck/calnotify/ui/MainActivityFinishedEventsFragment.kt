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

import android.content.Context
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
import com.github.quarck.calnotify.calendar.FinishedEventAlertRecord
import com.github.quarck.calnotify.calendar.EventFinishType
import com.github.quarck.calnotify.eventsstorage.FinishedEventsStorage
import com.github.quarck.calnotify.utils.adjustCalendarColor
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.utils.textutils.EventFormatter
import com.github.quarck.calnotify.utils.textutils.dateToStr
import kotlinx.coroutines.*


fun FinishedEventAlertRecord.formatReason(ctx: Context): String =
        when (this.finishType) {
            EventFinishType.ManuallyViaNotification ->
                String.format(ctx.resources.getString(R.string.complete_from_notification), dateToStr(ctx, this.finishTime))

            EventFinishType.ManuallyInTheApp ->
                String.format(ctx.resources.getString(R.string.complete_from_the_app), dateToStr(ctx, this.finishTime))

            EventFinishType.AutoDueToCalendarMove ->
                String.format(ctx.resources.getString(R.string.event_moved_new_time), dateToStr(ctx, this.event.startTime))

            EventFinishType.EventMovedInTheApp ->
                String.format(ctx.resources.getString(R.string.event_moved_new_time), dateToStr(ctx, this.event.startTime))

            EventFinishType.DeletedInTheApp ->
                String.format(ctx.resources.getString(R.string.event_deleted_in_the_app), dateToStr(ctx, this.finishTime))
        }

class MainActivityFinishedEventsFragment : Fragment(), SimpleEventListCallback<FinishedEventAlertRecord> {

    private val scope = MainScope()

    private lateinit var recyclerView: RecyclerView

    private var adapter: SimpleEventListAdapter<FinishedEventAlertRecord>? = null

    private var primaryColor: Int? = Consts.DEFAULT_CALENDAR_EVENT_COLOR
    private var eventFormatter: EventFormatter? = null

    private var bottomLineColor: Int = 0x7f3f3f3f

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        DevLog.info(LOG_TAG, "onCreateView")

        val root = inflater.inflate(R.layout.fragment_finished, container, false)

        this.context?.let {
            ctx ->
            primaryColor = ContextCompat.getColor(ctx, R.color.primary)
            eventFormatter  = EventFormatter(ctx)
            adapter =
                    SimpleEventListAdapter(
                            ctx,
                            R.layout.event_card_compact,
                            this)

            bottomLineColor = ContextCompat.getColor(ctx, R.color.divider)
        }

        recyclerView = root.findViewById<RecyclerView>(R.id.list_events)
        recyclerView.adapter = adapter;
        adapter?.recyclerView = recyclerView

        return root
    }

    override fun onResume() {
        DevLog.debug(LOG_TAG, "onResume")
        super.onResume()

        val ctx = this.activity ?: return

        scope.launch {
            val events = withContext(Dispatchers.IO) {
                FinishedEventsStorage(ctx).use { db ->
                    db.events.sortedByDescending { it.finishTime }.toMutableList()
                }
            }
            adapter?.setEventsToDisplay(events)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // TODO: add an option to view the event, not only to restore it
    override fun onItemClick(v: View, position: Int, entry: FinishedEventAlertRecord) {

        this.context?.let {
            ctx ->

            val popup = PopupMenu(ctx, v)
            val inflater = popup.menuInflater

            inflater.inflate(R.menu.finished_event_popup, popup.menu)

            popup.setOnMenuItemClickListener {
                item ->

                when (item.itemId) {
                    R.id.action_mark_not_finished -> {
                        ApplicationController.restoreEvent(ctx, entry.event)
                        adapter?.removeEntry(entry)
                        true
                    }
                    else ->
                        false
                }
            }

            popup.show()
        }
    }

    override fun getItemTitle(entry: FinishedEventAlertRecord): String =  entry.event.title

    override fun getItemMiddleLine(entry: FinishedEventAlertRecord): String = eventFormatter?.formatDateTimeOneLine(entry.event) ?: "_NO_FORMATTER_"

    override fun getItemBottomLine(entry: FinishedEventAlertRecord): Pair<String, Int> = Pair(context?.let{ entry.formatReason(it) } ?: "_NO_CONTEXT_", bottomLineColor)

    override fun getItemColor(entry: FinishedEventAlertRecord): Int =
            if (entry.event.color != 0)
                entry.event.color.adjustCalendarColor()
            else
                primaryColor ?: Consts.DEFAULT_CALENDAR_EVENT_COLOR

    override fun getUseBoldTitle(entry: FinishedEventAlertRecord): Boolean = false

    override fun onPause() {
        super.onPause()
        DevLog.info(LOG_TAG, "onPause")
    }

    override fun onDetach() {
        super.onDetach()
        DevLog.info(LOG_TAG, "onDetach")
    }

    companion object {
        private const val LOG_TAG = "FinishedEventsFragment"
    }
}