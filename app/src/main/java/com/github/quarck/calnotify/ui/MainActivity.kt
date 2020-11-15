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

package com.github.quarck.calnotify.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.appcompat.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.github.quarck.calnotify.*
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.app.UndoManager
import com.github.quarck.calnotify.app.UndoState
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventCompletionType
import com.github.quarck.calnotify.eventsstorage.CompleteEventsStorage
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.permissions.PermissionsManager
import com.github.quarck.calnotify.reminders.ReminderState
import com.github.quarck.calnotify.utils.background
import com.github.quarck.calnotify.utils.powerManager
import org.jetbrains.annotations.NotNull
import java.util.*

class DataUpdatedReceiver(val activity: MainActivity): BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) =
            activity.onDataUpdated(causedByUser = intent?.getBooleanExtra(Consts.INTENT_IS_USER_ACTION, false) ?: false)
}

class MainActivity : AppCompatActivity(), EventListCallback {


    private val settings: Settings by lazy { Settings(this) }

    private lateinit var staggeredLayoutManager: StaggeredGridLayoutManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var reloadLayout: RelativeLayout

    private var refreshLayout: SwipeRefreshLayout? = null

    private lateinit var floatingAddEvent: FloatingActionButton

    private lateinit var adapter: EventListAdapter

    private var lastEventDismissalScrollPosition: Int? = null

    private val undoDisappearSensitivity: Float by lazy {
        resources.getDimension(R.dimen.undo_dismiss_sensitivity)
    }

    private val dataUpdatedReceiver = DataUpdatedReceiver(this)

    private val undoManager by lazy { UndoManager }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DevLog.debug(LOG_TAG, "onCreateView")

        setContentView(R.layout.activity_main)

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        setSupportActionBar(findViewById<Toolbar?>(R.id.toolbar))
        //supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        window.navigationBarColor = ContextCompat.getColor(this, android.R.color.black)

        refreshLayout = findViewById<SwipeRefreshLayout?>(R.id.cardview_refresh_layout)

        refreshLayout?.setOnRefreshListener {
            reloadLayout.visibility = View.GONE;
            reloadData()
        }

        adapter = EventListAdapter(this, this)

        staggeredLayoutManager = StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL)
        recyclerView = findViewById<RecyclerView>(R.id.list_events)
        recyclerView.layoutManager = staggeredLayoutManager;
        recyclerView.adapter = adapter;
        adapter.recyclerView = recyclerView

        reloadLayout = findViewById<RelativeLayout>(R.id.activity_main_reload_layout)

        floatingAddEvent = findViewById<FloatingActionButton>(R.id.action_btn_add_event)

        //floatingAddEvent.visibility = if (settings.enableAddEvent) View.VISIBLE else View.GONE

        floatingAddEvent.setOnClickListener {
            startActivity(
                    Intent(this, EditEventActivity::class.java)
                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
    }

    public override fun onStart() {
        DevLog.info(LOG_TAG, "onStart()")
        super.onStart()

        ApplicationController.onMainActivityStarted(this);
    }

    private fun refreshReminderLastFired() {
        // avoid firing reminders when UI is active and user is interacting with it
        ReminderState(applicationContext).reminderLastFireTime = System.currentTimeMillis()
    }

    public override fun onResume() {
        DevLog.info(LOG_TAG, "onResume")
        super.onResume()

        checkPermissions()

        registerReceiver(dataUpdatedReceiver, IntentFilter(Consts.DATA_UPDATED_BROADCAST));

        reloadData()
        refreshReminderLastFired()

        background {
            ApplicationController.onMainActivityResumed(this)
        }

        if (undoManager.canUndo) {
            val coordinatorLayout = findViewById<CoordinatorLayout>(R.id.main_activity_coordinator)

            Snackbar.make(coordinatorLayout, resources.getString(R.string.event_dismissed), Snackbar.LENGTH_LONG)
                    .setAction(resources.getString(R.string.undo)) { onUndoButtonClick(null) }
                    .show()
        }

        invalidateOptionsMenu();
    }

    @SuppressLint("BatteryLife")
    private fun checkPermissions() {
        val hasPermissions = PermissionsManager.hasAllPermissions(this)

        //find<TextView>(R.id.no_permissions_view).visibility = if (hasPermissions) View.GONE else View.VISIBLE;

        if (!hasPermissions) {
            if (PermissionsManager.shouldShowRationale(this)) {

                AlertDialog.Builder(this)
                        .setMessage(R.string.application_has_no_access)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok) {
                            _, _ ->
                            PermissionsManager.requestPermissions(this)
                        }
                        .setNegativeButton(R.string.exit) {
                            _, _ ->
                            this@MainActivity.finish()
                        }
                        .create()
                        .show()
            }
            else {
                PermissionsManager.requestPermissions(this)
            }
        }
        else {
            // if we have essential permissions - now check for power manager optimisations
            if (!settings.doNotShowBatteryOptimisationWarning) {
                if (!powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID)) {

                    AlertDialog.Builder(this)
                            .setTitle(getString(R.string.battery_optimisation_title))
                            .setMessage(getString(R.string.battery_optimisation_details))
                            .setPositiveButton(getString(R.string.you_can_do_it)) {
                                _, _ ->
                                val intent = Intent()
                                        .setAction(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                        .setData(Uri.parse("package:" + BuildConfig.APPLICATION_ID))
                                startActivity(intent)
                            }
                            .setNeutralButton(getString(R.string.you_can_do_it_later)) {
                                _, _ ->
                            }
                            .setNegativeButton(getString(R.string.you_cannot_do_it)) {
                                _, _ ->
                                settings.doNotShowBatteryOptimisationWarning = true
                            }
                            .create()
                            .show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, @NotNull permissions: Array<out String>, @NotNull grantResults: IntArray) {

//        var granted = true
//
        for (result in grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                DevLog.error(LOG_TAG, "Permission is not granted!")
            }
        }

        //find<TextView>(R.id.no_permissions_view).visibility = if (granted) View.GONE else View.VISIBLE;
    }

    public override fun onPause() {
        DevLog.info(LOG_TAG, "onPause")

        refreshReminderLastFired()

        undoManager.clearUndoState()

        unregisterReceiver(dataUpdatedReceiver)

        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)

        val menuItem = menu.findItem(R.id.action_snooze_all)
        if (menuItem != null) {
            menuItem.isEnabled = adapter.itemCount > 0
            menuItem.title =
                    resources.getString(
                            if (adapter.hasActiveEvents) R.string.snooze_all else R.string.change_all)
        }

        val completeEventsMenuItem = menu.findItem(R.id.action_complete_events)
        if (completeEventsMenuItem != null) {
            completeEventsMenuItem.isEnabled = true
            completeEventsMenuItem.isVisible = true
        }

        if (Consts.DEV_MODE_ENABLED) {
            menu.findItem(R.id.action_test_page)?.isVisible = true
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        refreshReminderLastFired()

        when (item.itemId) {
            R.id.action_snooze_all ->
                startActivity(
                        Intent(this, SnoozeAllActivity::class.java)
                                .putExtra(Consts.INTENT_SNOOZE_ALL_IS_CHANGE, !adapter.hasActiveEvents)
                                .putExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, true)
                                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

            R.id.action_complete_events ->
                startActivity(
                        Intent(this, CompleteEventsActivity::class.java)
                                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

            R.id.action_settings -> {
                startActivity(
                        Intent(this, SettingsActivityNew::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            }

            R.id.action_about ->
                startActivity(
                        Intent(this, AboutActivity::class.java)
                                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

            R.id.action_test_page ->
                startActivity(
                        Intent(this, TestActivity::class.java)
                                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }

        return super.onOptionsItemSelected(item)
    }

    private fun reloadData() {

        background {
            CompleteEventsStorage(this).use { it.purgeOld(System.currentTimeMillis(), Consts.BIN_KEEP_HISTORY_MILLISECONDS) }

            val events =
                    EventsStorage(this).use {

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

            runOnUiThread {
                adapter.setEventsToDisplay(events);
                onNumEventsUpdated()
                refreshLayout?.isRefreshing = false
            }
        }
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onUndoButtonClick(v: View?) {
        undoManager.undo()
        reloadData()
    }

    override fun onScrollPositionChange(newPos: Int) {

        val undoSense = lastEventDismissalScrollPosition
        if (undoSense != null) {
            if (Math.abs(undoSense - newPos) > undoDisappearSensitivity) {
                lastEventDismissalScrollPosition = null
                adapter.clearUndoState()
            }
        }
    }

    private fun onNumEventsUpdated() {
        val hasEvents = adapter.itemCount > 0
        findViewById<TextView>(R.id.empty_view).visibility = if (hasEvents) View.GONE else View.VISIBLE;
        this.invalidateOptionsMenu();
    }


    override fun onItemClick(v: View, position: Int, eventId: Long) {
        DevLog.info(LOG_TAG, "onItemClick, pos=$position, eventId=$eventId")

        val event = adapter.getEventAtPosition(position, eventId)

        if (event != null) {
           startActivity(
                    Intent(this, ViewEventActivity::class.java)
                            .putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, event.notificationId)
                            .putExtra(Consts.INTENT_EVENT_ID_KEY, event.eventId)
                            .putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, event.instanceStartTime)
                            .putExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, true)
                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

        }
    }

    // TODO: KILL UNDO MANAGER
    // TODO: KILL THIS METHOD ALSO
    // TODO: NOT USED IN THE MODERN VERSION OF THE APP
    // user clicks on 'dismiss' button, item still in the list
    override fun onItemDismiss(v: View, position: Int, eventId: Long) {
        DevLog.info(LOG_TAG, "onItemDismiss, pos=$position, eventId=$eventId");

        val event = adapter.getEventAtPosition(position, eventId)

        if (event != null) {
            DevLog.info(LOG_TAG, "Removing event id ${event.eventId} from DB and dismissing notification id ${event.notificationId}")
            ApplicationController.dismissEvent(this, EventCompletionType.ManuallyInTheApp, event)

            undoManager.addUndoState(
                    UndoState(
                            undo = Runnable { ApplicationController.restoreEvent(this, event) }))

            adapter.removeEvent(event)
            lastEventDismissalScrollPosition = adapter.scrollPosition

            onNumEventsUpdated()

            val coordinatorLayout = findViewById<CoordinatorLayout>(R.id.main_activity_coordinator)

            Snackbar.make(coordinatorLayout, resources.getString(R.string.event_dismissed), Snackbar.LENGTH_LONG)
                    .setAction(resources.getString(R.string.undo)) { onUndoButtonClick(null) }
                    .show()
        }
    }

    // Item was already removed from UI, we just have to dismiss it now
    override fun onItemRemoved(event: EventAlertRecord) {

        DevLog.info(LOG_TAG, "onItemRemoved: Removing event id ${event.eventId} from DB and dismissing notification id ${event.notificationId}")
        ApplicationController.dismissEvent(this, EventCompletionType.ManuallyInTheApp, event)
        lastEventDismissalScrollPosition = adapter.scrollPosition
        onNumEventsUpdated()
    }

    override fun onItemRestored(event: EventAlertRecord) {
        DevLog.info(LOG_TAG, "onItemRestored, eventId=${event.eventId}")
        ApplicationController.restoreEvent(this, event)

        onNumEventsUpdated()
    }

    override fun onItemSnooze(v: View, position: Int, eventId: Long) {
        DevLog.info(LOG_TAG, "onItemSnooze, pos=$position, eventId=$eventId");

        val event = adapter.getEventAtPosition(position, eventId)

        if (event != null) {
            startActivity(
                    Intent(this, ViewEventActivity::class.java)
                            .putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, event.notificationId)
                            .putExtra(Consts.INTENT_EVENT_ID_KEY, event.eventId)
                            .putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, event.instanceStartTime)
                            .putExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, true)
                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
    }

    fun onDataUpdated(causedByUser: Boolean) {
        if (causedByUser)
            reloadData()
        else
            runOnUiThread { reloadLayout.visibility = View.VISIBLE }
    }

    companion object {
        private const val LOG_TAG = "MainActivity"
    }
}
