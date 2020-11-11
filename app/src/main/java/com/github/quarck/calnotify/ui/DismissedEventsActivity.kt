package com.github.quarck.calnotify.ui

import android.app.AlertDialog
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.appcompat.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.core.content.ContextCompat
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.completeeventsstorage.CompleteEventAlertRecord
import com.github.quarck.calnotify.completeeventsstorage.CompleteEventsStorage
import com.github.quarck.calnotify.logs.DevLog
//import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.utils.background
import com.github.quarck.calnotify.utils.find
import com.github.quarck.calnotify.utils.findOrThrow

class DismissedEventsActivity : AppCompatActivity(), DismissedEventListCallback {

    private val settings: Settings by lazy { Settings(this) }

    private lateinit var staggeredLayoutManager: StaggeredGridLayoutManager
    private lateinit var recyclerView: RecyclerView

    private lateinit var adapter: DismissedEventListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dismissed_events)

        setSupportActionBar(find<Toolbar?>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        window.navigationBarColor = ContextCompat.getColor(this, android.R.color.black)

        adapter =
                DismissedEventListAdapter(
                        this,
                        R.layout.event_card_compact,
                        this)

        staggeredLayoutManager = StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL)
        recyclerView = findOrThrow<RecyclerView>(R.id.list_events)
        recyclerView.layoutManager = staggeredLayoutManager;
        recyclerView.adapter = adapter;
        adapter.recyclerView = recyclerView

    }

    public override fun onResume() {
        DevLog.debug(LOG_TAG, "onResume")
        super.onResume()
        reloadData()
    }

    private fun reloadData() {
        background {
            val events =
                    CompleteEventsStorage(this).use {
                        db ->
                        db.events.sortedByDescending { it.completionTime }.toTypedArray()
                    }
            runOnUiThread {
                adapter.setEventsToDisplay(events);
            }
        }
    }


    override fun onItemRemoved(entry: CompleteEventAlertRecord) {
        CompleteEventsStorage(this).use { db -> db.deleteEvent(entry) }
    }

    override fun onItemClick(v: View, position: Int, entry: CompleteEventAlertRecord) {

        val popup = PopupMenu(this, v)
        val inflater = popup.menuInflater

        inflater.inflate(R.menu.dismissed_events, popup.menu)

        popup.setOnMenuItemClickListener {
            item ->

            when (item.itemId) {
                R.id.action_restore -> {
                    ApplicationController.restoreEvent(this, entry.event)
                    adapter.removeEntry(entry)
                    true
                }

//                R.id.action_remove_dismissed -> {
//                    DismissedEventsStorage(this).use { db -> db.deleteEvent(entry) }
//                    adapter.removeEntry(entry)
//                    true
//                }
                else ->
                    false
            }
        }

        popup.show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.dismissed_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {
            R.id.action_remove_all -> {

                AlertDialog.Builder(this)
                        .setMessage(resources.getString(R.string.remove_all_confirmation))
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok) {
                            _, _ ->
                            CompleteEventsStorage(this).use { db -> db.clearHistory() }
                            adapter.removeAll()
                        }
                        .setNegativeButton(R.string.cancel) {
                            _, _ ->
                        }
                        .create()
                        .show()
            }
        }

        return super.onOptionsItemSelected(item)
    }


    companion object {
        private const val LOG_TAG = "DismissedEventsActivity"
    }
}
