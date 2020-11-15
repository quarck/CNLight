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
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.CompleteEventAlertRecord
import com.github.quarck.calnotify.eventsstorage.CompleteEventsStorage
import com.github.quarck.calnotify.utils.background
import com.github.quarck.calnotify.utils.logs.DevLog

class MainActivityCompleteEventsFragment : Fragment(), CompleteEventListCallback {

    private lateinit var staggeredLayoutManager: StaggeredGridLayoutManager
    private lateinit var recyclerView: RecyclerView

    private var adapter: CompleteEventListAdapter? = null

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
                    CompleteEventListAdapter(
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
                        }
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