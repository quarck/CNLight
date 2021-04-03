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
import android.view.*
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.EventAlertRecord
import com.github.quarck.calnotify.calendar.EventFinishType
import com.github.quarck.calnotify.eventsstorage.EventsStorage
import com.github.quarck.calnotify.eventsstorage.FinishedEventsStorage
import com.github.quarck.calnotify.permissions.PermissionsManager
import com.github.quarck.calnotify.ui.*
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.utils.md5state
import com.github.quarck.calnotify.utils.powerManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*
import org.jetbrains.annotations.NotNull
import com.github.quarck.calnotify.BuildConfig
import com.github.quarck.calnotify.R

class MainActivity : AppCompatActivity(), EventListCallback {
    // TODO: Rename and change types of parameters

    private val scope = MainScope()

    private val settings: Settings by lazy { Settings(this) }

    private lateinit var recyclerView: RecyclerView
    private lateinit var reloadLayout: RelativeLayout

    private var refreshLayout: SwipeRefreshLayout? = null

    private var adapter: EventListAdapter? = null
    private var lastEventsSummary = md5state(0, 0, 0, 0)

    private var lastEventDismissalScrollPosition: Int? = null

    private var emptyView: TextView? = null

    private val undoDisappearSensitivity: Float by lazy {
        resources.getDimension(R.dimen.undo_dismiss_sensitivity)
    }

    private val dataUpdatedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            onDataUpdated(causedByUser = intent?.getBooleanExtra(Consts.INTENT_IS_USER_ACTION, false)
                    ?: false)
        }
    } // DataUpdatedReceiverNG(this)


    override fun onCreate(savedInstanceState: Bundle?) {
        DevLog.info(LOG_TAG, "onCreate")

        super.onCreate(savedInstanceState)
        Theme.apply(this)

        setContentView(R.layout.activity_notifications)

        setSupportActionBar(findViewById<Toolbar?>(R.id.toolbar))
        supportActionBar?.let{
            it.setDisplayHomeAsUpEnabled(false)
            it.setHomeAsUpIndicator(R.drawable.ic_arrow_back)
            it.setDisplayShowHomeEnabled(false)
        }

        window.navigationBarColor = ContextCompat.getColor(this, android.R.color.black)

        refreshLayout = findViewById<SwipeRefreshLayout?>(R.id.cardview_refresh_layout)
        refreshLayout?.setOnRefreshListener {
            reloadLayout.visibility = View.GONE;
            reloadData()
        }

        adapter = EventListAdapter(this, this)

        recyclerView = findViewById<RecyclerView>(R.id.list_events)
        recyclerView.adapter = adapter
        adapter?.recyclerView = recyclerView

        reloadLayout = findViewById<RelativeLayout>(R.id.activity_main_reload_layout)

        emptyView = findViewById(R.id.empty_view)

        findViewById<FloatingActionButton>(R.id.action_btn_add_event).setOnClickListener {
            startActivity(
                    Intent(this, EditEventActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
    }

    override fun onResume() {
        super.onResume()
        DevLog.info(LOG_TAG, "onResume")

        checkPermissions()

        registerReceiver(dataUpdatedReceiver, IntentFilter(Consts.DATA_UPDATED_BROADCAST))

        reloadData()

        scope.launch(Dispatchers.Default) {
            ApplicationController.onMainActivityResumed(this@MainActivity)
        }

        invalidateOptionsMenu()
    }



    override fun onPause() {
        DevLog.info(LOG_TAG, "onPause")
        unregisterReceiver(dataUpdatedReceiver)
        super.onPause()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {

        DevLog.info(LOG_TAG, "onCreateOptionsMenu")
        menuInflater.inflate(R.menu.notifications_menu, menu)

        val menuItem = menu.findItem(R.id.action_snooze_all)
        if (menuItem != null) {
            menuItem.isEnabled = (adapter?.itemCount ?: 0) > 0
            menuItem.title =
                    resources.getString(
                            if (adapter?.hasActiveEvents == true) R.string.snooze_all else R.string.change_all)
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_snooze_all ->
                startActivity(
                        Intent(this, SnoozeAllActivity::class.java)
                                .putExtra(Consts.INTENT_SNOOZE_ALL_IS_CHANGE, !(adapter?.hasActiveEvents ?: false))
                )

            R.id.action_upcoming -> {
                startActivity(
                        Intent(this, UpcomingNotificationsActivity::class.java))
            }

            R.id.action_log -> {
                startActivity(
                        Intent(this, NotificationsLogActivity::class.java))
            }

            R.id.action_about -> {
                startActivity(
                        Intent(this, AboutActivity::class.java))
            }
        }
        return super.onOptionsItemSelected(item)
    }

    @SuppressLint("BatteryLife")
    private fun checkPermissions() {
        val hasPermissions = PermissionsManager.hasAllPermissions(this)

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
        for (result in grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                DevLog.error(LOG_TAG, "Permission is not granted!")
            }
        }
    }

    private suspend fun loadCurrentEvents(ctx: Context, skipPurge: Boolean = false) = withContext(Dispatchers.IO) {

        if (!skipPurge) {
            FinishedEventsStorage(ctx).use {
                it.purgeOld(System.currentTimeMillis(), Consts.BIN_KEEP_HISTORY_MILLISECONDS)
            }
        }

        val events = EventsStorage(ctx).use { db ->
            db.events.sortedWith(
                    Comparator { lhs, rhs ->
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

        val summary = md5state(0, 0, 0, 0)

        for (e in events) {
            summary.xor(e.contentMd5)
        }

        Pair(events, summary)
    }


    private fun reloadData() {
        scope.launch {
            val (events, eventsSummary) = loadCurrentEvents(this@MainActivity)
            adapter?.setEventsToDisplay(events)
            lastEventsSummary = eventsSummary
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
        invalidateOptionsMenu();
    }


    override fun onItemClick(v: View, position: Int, eventId: Long) {
        DevLog.info(LOG_TAG, "onItemClick, pos=$position, eventId=$eventId")

        val event = adapter?.getEventAtPosition(position, eventId)

        if (event != null) {
            startActivity(
                    Intent(this, ViewEventActivity::class.java)
                            .putExtra(Consts.INTENT_NOTIFICATION_ID_KEY, event.notificationId)
                            .putExtra(Consts.INTENT_EVENT_ID_KEY, event.eventId)
                            .putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, event.instanceStartTime)
                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

        }
    }

    // Item was already removed from UI, we just have to dismiss it now
    override fun onItemRemoved(event: EventAlertRecord) {
        DevLog.info(LOG_TAG, "onItemRemoved: Removing event id ${event.eventId} from DB and dismissing notification id ${event.notificationId}")
        ApplicationController.dismissEvent(this, EventFinishType.ManuallyInTheApp, event)
        lastEventDismissalScrollPosition = adapter?.scrollPosition
        lastEventsSummary.xor(event.contentMd5)
        onNumEventsUpdated()
    }

    override fun onItemRestored(event: EventAlertRecord) {
        DevLog.info(LOG_TAG, "onItemRestored, eventId=${event.eventId}")
        ApplicationController.restoreEvent(this, event)
        lastEventsSummary.xor(event.contentMd5)
        onNumEventsUpdated()
    }

    fun onDataUpdated(causedByUser: Boolean) {
        if (causedByUser) {
            reloadData()
            return
        }

        scope.launch {
            val (_, eventsSummary) = loadCurrentEvents(this@MainActivity, true)
            DevLog.debug(LOG_TAG, "onDataUpdated: last summary: $lastEventsSummary, new summary: $eventsSummary")
            if (lastEventsSummary != eventsSummary) {
                withContext(Dispatchers.Main) {
                    reloadLayout.visibility = View.VISIBLE
                }
            }
        }
    }

    companion object {
        const val LOG_TAG = "NotificationsActivity"
    }
}