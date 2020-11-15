package com.github.quarck.calnotify.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.appcompat.widget.Toolbar
import android.view.View
import androidx.core.content.ContextCompat
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.CompleteEventAlertRecord
import com.github.quarck.calnotify.eventsstorage.CompleteEventsStorage
import com.github.quarck.calnotify.utils.adjustCalendarColor
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.utils.background
import com.github.quarck.calnotify.utils.textutils.EventFormatter


class CompleteEventsActivity : AppCompatActivity(), SimpleEventListCallback<CompleteEventAlertRecord> {

    private lateinit var staggeredLayoutManager: StaggeredGridLayoutManager
    private lateinit var recyclerView: RecyclerView

    private lateinit var adapter: SimpleEventListAdapter<CompleteEventAlertRecord>

    private val primaryColor: Int? = ContextCompat.getColor(this, R.color.primary)
    private val eventFormatter = EventFormatter(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_complete_events)

        setSupportActionBar(findViewById<Toolbar?>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        window.navigationBarColor = ContextCompat.getColor(this, android.R.color.black)

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
                    CompleteEventsStorage(this).use {
                        db ->
                        db.events.sortedByDescending { it.completionTime }.toTypedArray()
                    }.toMutableList()
            runOnUiThread {
                adapter.setEventsToDisplay(events);
            }
        }
    }

    override fun onItemClick(v: View, position: Int, entry: CompleteEventAlertRecord) {

        val popup = PopupMenu(this, v)
        val inflater = popup.menuInflater

        inflater.inflate(R.menu.complete_events, popup.menu)

        popup.setOnMenuItemClickListener {
            item ->

            when (item.itemId) {
                R.id.action_mark_not_complete -> {
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


    override fun getItemTitle(entry: CompleteEventAlertRecord): String =  entry.event.title
    override fun getItemMiddleLine(entry: CompleteEventAlertRecord): String = eventFormatter.formatDateTimeOneLine(entry.event)
    override fun getItemBottomLine(entry: CompleteEventAlertRecord): String = entry.formatReason(this)
    override fun getItemColor(entry: CompleteEventAlertRecord): Int =
            if (entry.event.color != 0)
                entry.event.color.adjustCalendarColor()
            else
                primaryColor ?: 0x7fff0000;

    companion object {
        private const val LOG_TAG = "DismissedEventsActivity"
    }
}
