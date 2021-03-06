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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.*
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventFinishType
import com.github.quarck.calnotify.eventsstorage.FinishedEventsStorage
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.utils.logs.DevLog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*


class DataUpdatedReceiverNG(val fragment: MainActivityHomeFragment): BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) =
            fragment.onDataUpdated(causedByUser = intent?.getBooleanExtra(Consts.INTENT_IS_USER_ACTION, false) ?: false)
}

class MainActivityHomeFragment : Fragment(), EventListCallback {

    private val scope = MainScope()

    private lateinit var recyclerView: RecyclerView
    private lateinit var reloadLayout: RelativeLayout

    private var refreshLayout: SwipeRefreshLayout? = null

    private var adapter: EventListAdapter? = null

    private var lastEventDismissalScrollPosition: Int? = null

    private var emptyView: TextView? = null

    private val undoDisappearSensitivity: Float by lazy {
        resources.getDimension(R.dimen.undo_dismiss_sensitivity)
    }

    private val dataUpdatedReceiver = DataUpdatedReceiverNG(this)

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        DevLog.info(LOG_TAG, "onCreateView")

        val root = inflater.inflate(R.layout.fragment_home, container, false)

        refreshLayout = root.findViewById<SwipeRefreshLayout?>(R.id.cardview_refresh_layout)
        refreshLayout?.setOnRefreshListener {
            reloadLayout.visibility = View.GONE;
            reloadData()
        }

        this.context?.let {
            adapter = EventListAdapter(it, this)
        }

        recyclerView = root.findViewById<RecyclerView>(R.id.list_events)
        recyclerView.adapter = adapter
        adapter?.recyclerView = recyclerView

        reloadLayout = root.findViewById<RelativeLayout>(R.id.activity_main_reload_layout)

        emptyView = root.findViewById(R.id.empty_view)

        this.activity?.let {
            act ->
            root.findViewById<FloatingActionButton>(R.id.action_btn_add_event).setOnClickListener {
                act.startActivity(
                        Intent(act, EditEventActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            }
        }

        return root
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DevLog.info(LOG_TAG, "onCreate")
        setHasOptionsMenu(true)
    }

    override fun onResume() {
        super.onResume()
        DevLog.info(LOG_TAG, "onResume")

        context?.registerReceiver(dataUpdatedReceiver, IntentFilter(Consts.DATA_UPDATED_BROADCAST))

        reloadData()

        context?.let {
            ctx ->
            scope.launch(Dispatchers.Default) {
                ApplicationController.onMainActivityResumed(ctx)
            }
        }

        this.activity?.invalidateOptionsMenu()
    }

    override fun onPause() {
        DevLog.info(LOG_TAG, "onPause")
        context?.unregisterReceiver(dataUpdatedReceiver)
        super.onPause()
    }

    override fun onDetach() {
        super.onDetach()
        DevLog.info(LOG_TAG, "onDetach")
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu, menuInflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, menuInflater)
        DevLog.info(LOG_TAG, "onCreateOptionsMenu")
        menuInflater.inflate(R.menu.main_activity_n_g, menu)

        val menuItem = menu.findItem(R.id.action_snooze_all)
        if (menuItem != null) {
            menuItem.isEnabled = (adapter?.itemCount ?: 0) > 0
            menuItem.title =
                    resources.getString(
                            if (adapter?.hasActiveEvents == true) R.string.snooze_all else R.string.change_all)
        }

        if (Consts.DEV_MODE_ENABLED) {
            menu.findItem(R.id.action_test_page)?.isVisible = true
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        this.context?.let {
            ctx ->
            when (item.itemId) {
                R.id.action_snooze_all ->
                    startActivity(
                            Intent(ctx, SnoozeAllActivity::class.java)
                                    .putExtra(Consts.INTENT_SNOOZE_ALL_IS_CHANGE, !(adapter?.hasActiveEvents ?: false))
                                    .putExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, true)
                                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

                R.id.action_test_page ->
                    startActivity(
                            Intent(ctx, TestActivity::class.java)
                                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            }
        }


        return super.onOptionsItemSelected(item)
    }


    private fun reloadData() {

        val ctx = this.context ?: return

        scope.launch {

            val events =
                withContext(Dispatchers.IO) {
                    FinishedEventsStorage(ctx).use { it.purgeOld(System.currentTimeMillis(), Consts.BIN_KEEP_HISTORY_MILLISECONDS) }

                    EventsStorage(ctx).use {
                        db ->
                        db.events.sortedWith(
                                Comparator<EventAlertRecord> {
                                    lhs, rhs ->

                                    if (lhs.snoozedUntil < rhs.snoozedUntil)
                                        return@Comparator -1;
                                    else if (lhs.snoozedUntil > rhs.snoozedUntil)
                                        return@Comparator 1;

                                    if (lhs.lastStatusChangeTime > rhs.lastStatusChangeTime)
                                        return@Comparator -1;
                                    else if (lhs.lastStatusChangeTime < rhs.lastStatusChangeTime)
                                        return@Comparator 1;

                                    return@Comparator 0;

                                }).toTypedArray()
                    }
                }

            adapter?.setEventsToDisplay(events);
            onNumEventsUpdated()
            refreshLayout?.isRefreshing = false
        }
    }

    override fun onScrollPositionChange(newPos: Int) {

        val undoSense = lastEventDismissalScrollPosition
        if (undoSense != null) {
            if (Math.abs(undoSense - newPos) > undoDisappearSensitivity) {
                lastEventDismissalScrollPosition = null
                adapter?.clearUndoState()
            }
        }
    }

    private fun onNumEventsUpdated() {
        val hasEvents = (adapter?.itemCount ?: 0) > 0
        emptyView?.visibility = if (hasEvents) View.GONE else View.VISIBLE;
        this.activity?.invalidateOptionsMenu();
    }


    override fun onItemClick(v: View, position: Int, eventId: Long) {
        DevLog.info(LOG_TAG, "onItemClick, pos=$position, eventId=$eventId")

        val event = adapter?.getEventAtPosition(position, eventId)

        this.context?.let {
            ctx ->
            if (event != null) {
                startActivity(
                        Intent(ctx, ViewEventActivity::class.java)
                                .putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, event.notificationId)
                                .putExtra(Consts.INTENT_EVENT_ID_KEY, event.eventId)
                                .putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, event.instanceStartTime)
                                .putExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, true)
                                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

            }
        }
    }

    // Item was already removed from UI, we just have to dismiss it now
    override fun onItemRemoved(event: EventAlertRecord) {

        this.context?.let {
            ctx ->
            DevLog.info(LOG_TAG, "onItemRemoved: Removing event id ${event.eventId} from DB and dismissing notification id ${event.notificationId}")
            ApplicationController.dismissEvent(ctx, EventFinishType.ManuallyInTheApp, event)
            lastEventDismissalScrollPosition = adapter?.scrollPosition
            onNumEventsUpdated()
        }
    }

    // TODO: validate this is used!!
    // TODO: validate this is used!!
    // TODO: validate this is used!!

    override fun onItemRestored(event: EventAlertRecord) {
        this.context?.let {
            ctx ->
            DevLog.info(LOG_TAG, "onItemRestored, eventId=${event.eventId}")
            ApplicationController.restoreEvent(ctx, event)
            onNumEventsUpdated()
        }
    }

    override fun onItemSnooze(v: View, position: Int, eventId: Long) {
        DevLog.info(LOG_TAG, "onItemSnooze, pos=$position, eventId=$eventId");

        val event = adapter?.getEventAtPosition(position, eventId)
        if (event != null) {

            this.context?.let {
                ctx ->
                startActivity(
                        Intent(ctx, ViewEventActivity::class.java)
                                .putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, event.notificationId)
                                .putExtra(Consts.INTENT_EVENT_ID_KEY, event.eventId)
                                .putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, event.instanceStartTime)
                                .putExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, true)
                                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            }
        }
    }

    fun onDataUpdated(causedByUser: Boolean) {
        if (causedByUser)
            reloadData()
        else
            this.activity?.runOnUiThread { reloadLayout.visibility = View.VISIBLE }
    }

    companion object {
        private const val LOG_TAG = "HomeFragment"
    }
}