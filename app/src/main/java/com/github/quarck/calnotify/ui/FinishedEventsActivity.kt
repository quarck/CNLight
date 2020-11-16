package com.github.quarck.calnotify.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.appcompat.widget.Toolbar
import android.view.View
import androidx.core.content.ContextCompat
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.FinishedEventAlertRecord
import com.github.quarck.calnotify.eventsstorage.FinishedEventsStorage
import com.github.quarck.calnotify.utils.adjustCalendarColor
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.utils.background
import com.github.quarck.calnotify.utils.textutils.EventFormatter


class FinishedEventsActivity : AppCompatActivity(), SimpleEventListCallback<FinishedEventAlertRecord> {

    private lateinit var staggeredLayoutManager: StaggeredGridLayoutManager
    private lateinit var recyclerView: RecyclerView

    private lateinit var adapter: SimpleEventListAdapter<FinishedEventAlertRecord>

    private var primaryColor: Int = Consts.DEFAULT_CALENDAR_EVENT_COLOR
    private lateinit var eventFormatter: EventFormatter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_finished_events)

        setSupportActionBar(findViewById<Toolbar?>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        window.navigationBarColor = ContextCompat.getColor(this, android.R.color.black)

        primaryColor = ContextCompat.getColor(this, R.color.primary)
        eventFormatter = EventFormatter(this)

        adapter =
                SimpleEventListAdapter(
                        this,
                        R.layout.event_card_compact,
                        this)

        staggeredLayoutManager = StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL)
        recyclerView = findViewById<RecyclerView>(R.id.list_events)
        recyclerView.layoutManager = staggeredLayoutManager;
        recyclerView.adapter = adapter;
        adapter.recyclerView = recyclerView

    }

    public override fun onResume() {
        DevLog.debug(LOG_TAG, "onResume")
        super.onResume()

        background {
            val events =
                    FinishedEventsStorage(this).use {
                        db ->
                        db.events.sortedByDescending { it.finishTime }.toMutableList()
                    }
            runOnUiThread {
                adapter.setEventsToDisplay(events);
            }
        }
    }

    override fun onItemClick(v: View, position: Int, entry: FinishedEventAlertRecord) {

        val popup = PopupMenu(this, v)
        val inflater = popup.menuInflater

        inflater.inflate(R.menu.finished_event_popup, popup.menu)

        popup.setOnMenuItemClickListener {
            item ->

            when (item.itemId) {
                R.id.action_mark_not_finished -> {
                    ApplicationController.restoreEvent(this, entry.event)
                    adapter.removeEntry(entry)
                    true
                }
                else ->
                    false
            }
        }

        popup.show()
    }

    override fun getItemTitle(entry: FinishedEventAlertRecord): String =  entry.event.title

    override fun getItemMiddleLine(entry: FinishedEventAlertRecord): String = eventFormatter.formatDateTimeOneLine(entry.event)

    override fun getItemBottomLine(entry: FinishedEventAlertRecord): String = entry.formatReason(this)

    override fun getItemColor(entry: FinishedEventAlertRecord): Int =
            if (entry.event.color != 0)
                entry.event.color.adjustCalendarColor()
            else
                primaryColor

    companion object {
        private const val LOG_TAG = "DismissedEventsActivity"
    }
}
