package com.github.quarck.calnotify.ui

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.EventFinishType
import com.github.quarck.calnotify.calendar.FinishedEventAlertRecord
import com.github.quarck.calnotify.eventsstorage.FinishedEventsStorage
import com.github.quarck.calnotify.utils.adjustCalendarColor
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.utils.textutils.EventFormatter
import com.github.quarck.calnotify.utils.textutils.dateToStr
import kotlinx.coroutines.*
import com.github.quarck.calnotify.R


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


class NotificationsLogActivity : AppCompatActivity(), SimpleEventListCallback<FinishedEventAlertRecord> {
    // TODO: Rename and change types of parameters

    private val scope = MainScope()

    private lateinit var recyclerView: RecyclerView

    private var adapter: SimpleEventListAdapter<FinishedEventAlertRecord>? = null

    //private var primaryColor: Int? = Consts.DEFAULT_CALENDAR_EVENT_COLOR
    private var eventFormatter: EventFormatter? = null

    private var bottomLineColor: Int = 0x7f3f3f3f

    override fun onCreate(savedInstanceState: Bundle?) {
        DevLog.info(LOG_TAG, "onCreate")

        super.onCreate(savedInstanceState)
        Theme.apply(this)

        setContentView(R.layout.activity_finished)

        setSupportActionBar(findViewById<Toolbar?>(R.id.toolbar))
        supportActionBar?.let{
            it.setDisplayHomeAsUpEnabled(true)
            it.setHomeAsUpIndicator(R.drawable.ic_arrow_back)
            it.setDisplayShowHomeEnabled(true)
        }

        window.navigationBarColor = ContextCompat.getColor(this, android.R.color.black)

        eventFormatter  = EventFormatter(this)
        adapter =
                SimpleEventListAdapter(
                        this,
                        R.layout.event_card_compact,
                        this)

        bottomLineColor = Theme.resolveColor(this, R.attr.cn_divider)
        recyclerView = findViewById<RecyclerView>(R.id.list_events)
        recyclerView.adapter = adapter;
        adapter?.recyclerView = recyclerView
    }

    override fun onResume() {
        DevLog.debug(LOG_TAG, "onResume")
        super.onResume()

        scope.launch {
            val events = withContext(Dispatchers.IO) {
                FinishedEventsStorage(this@NotificationsLogActivity).use { db ->
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

    override fun onItemClick(v: View, position: Int, entry: FinishedEventAlertRecord) {

        val popup = PopupMenu(this, v)
        val inflater = popup.menuInflater

        inflater.inflate(R.menu.finished_event_popup, popup.menu)

        popup.setOnMenuItemClickListener { item ->

            when (item.itemId) {
                R.id.action_mark_not_finished -> {
                    ApplicationController.restoreEvent(this, entry.event)
                    adapter?.removeEntry(entry)
                    true
                }
                else ->
                    false
            }
        }

        popup.show()
    }

    override fun getItemTitle(entry: FinishedEventAlertRecord): String =  entry.event.title

    override fun getItemMiddleLine(entry: FinishedEventAlertRecord): String = eventFormatter?.formatDateTimeOneLine(entry.event) ?: "_NO_FORMATTER_"

    override fun getItemBottomLine(entry: FinishedEventAlertRecord): Pair<String, Int> = Pair(entry.formatReason(this), bottomLineColor)

    override fun getItemColor(entry: FinishedEventAlertRecord): Int =
            if (entry.event.color != 0)
                entry.event.color.adjustCalendarColor()
            else
                Consts.DEFAULT_CALENDAR_EVENT_COLOR

    override fun getUseBoldTitle(entry: FinishedEventAlertRecord): Boolean = false

    override fun onPause() {
        super.onPause()
        DevLog.info(LOG_TAG, "onPause")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_log_activity, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {
            R.id.action_clear_log -> {
                confirmAndClearLog()
            }
        }

        return super.onOptionsItemSelected(item)
    }

    private fun confirmAndClearLog() {
        val builder = AlertDialog.Builder(this)
                .setTitle(getString(R.string.clear_log_confirmation))
                .setMessage(getString(R.string.no_undo))
                .setPositiveButton(R.string.yes) { dialog, which ->
                    doClearLog()
                }
                .setNegativeButton(getString(R.string.cancel)) { dialog: DialogInterface?, which: Int -> }

        builder.create().show()
    }

    private fun doClearLog() {
        scope.launch {
            val events = withContext(Dispatchers.IO) {
                ApplicationController.clearLog(this@NotificationsLogActivity)

                FinishedEventsStorage(this@NotificationsLogActivity).use { db ->
                    db.events.toMutableList()
                }
            }
            adapter?.setEventsToDisplay(events)
        }
    }


    companion object {
        private const val LOG_TAG = "FinishedEventsFragment"
    }

}