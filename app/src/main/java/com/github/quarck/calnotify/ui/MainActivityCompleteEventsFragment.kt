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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.CompleteEventAlertRecord
import com.github.quarck.calnotify.eventsstorage.CompleteEventsStorage
import com.github.quarck.calnotify.utils.adjustCalendarColor
import com.github.quarck.calnotify.utils.background
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.utils.textutils.EventFormatter

class MainActivityCompleteEventsFragment : Fragment(), SimpleEventListCallback<CompleteEventAlertRecord> {

    private lateinit var staggeredLayoutManager: StaggeredGridLayoutManager
    private lateinit var recyclerView: RecyclerView

    private var adapter: SimpleEventListAdapter<CompleteEventAlertRecord>? = null

    private val primaryColor: Int? = context?.let{ ContextCompat.getColor(it, R.color.primary) }
    private val eventFormatter = context?.let{ EventFormatter(it) }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        DevLog.info(LOG_TAG, "onCreateView")

        val root = inflater.inflate(R.layout.fragment_complete, container, false)

        this.context?.let {
            ctx ->
            adapter =
                    SimpleEventListAdapter(
                            ctx,
                            R.layout.event_card_compact,
                            this)
        }

        staggeredLayoutManager = StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL)
        recyclerView = root.findViewById<RecyclerView>(R.id.list_events)
        recyclerView.layoutManager = staggeredLayoutManager;
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
                val events =
                        CompleteEventsStorage(activity).use {
                            db ->
                            db.events.sortedByDescending { it.completionTime }.toTypedArray()
                        }.toMutableList()
                activity.runOnUiThread {
                    adapter?.setEventsToDisplay(events)
                }
            }
        }

    }

    // TODO: add an option to view the event, not only to restore it
    override fun onItemClick(v: View, position: Int, entry: CompleteEventAlertRecord) {

        this.context?.let {
            ctx ->

            val popup = PopupMenu(ctx, v)
            val inflater = popup.menuInflater

            inflater.inflate(R.menu.complete_events, popup.menu)

            popup.setOnMenuItemClickListener {
                item ->

                when (item.itemId) {
                    R.id.action_mark_not_complete -> {
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

    override fun getItemTitle(entry: CompleteEventAlertRecord): String =  entry.event.title
    override fun getItemMiddleLine(entry: CompleteEventAlertRecord): String = eventFormatter?.formatDateTimeOneLine(entry.event) ?: "_NO_FORMATTER_"
    override fun getItemBottomLine(entry: CompleteEventAlertRecord): String = context?.let{ entry.formatReason(it) } ?: "_NO_CONTEXT_"
    override fun getItemColor(entry: CompleteEventAlertRecord): Int =
            if (entry.event.color != 0)
                entry.event.color.adjustCalendarColor()
            else
                primaryColor ?: 0x7fff0000;


    override fun onPause() {
        super.onPause()
        DevLog.info(LOG_TAG, "onPause")
    }

    override fun onDetach() {
        super.onDetach()
        DevLog.info(LOG_TAG, "onDetach")
    }

    companion object {
        private const val LOG_TAG = "SlideshowFragment"
    }
}