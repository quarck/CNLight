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
package com.github.quarck.calnotify.prefs

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.calendar.CalendarProvider
import com.github.quarck.calnotify.calendar.CalendarRecord
import com.github.quarck.calnotify.utils.logs.DevLog
import kotlinx.coroutines.*

enum class CalendarListEntryType {Header, Calendar, Divider }

class CalendarListEntry(
        val type: CalendarListEntryType,
        val headerTitle: String? = null,
        val calendar: CalendarRecord? = null,
        var isHandled: Boolean = true
)


class CalendarListAdapter(val context: Context, var entries: Array<CalendarListEntry>)
    : RecyclerView.Adapter<CalendarListAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.calendar_view, parent, false);
        return ViewHolder(view);
    }

    inner class ViewHolder(itemView: View)
        : RecyclerView.ViewHolder(itemView) {

        var entry: CalendarListEntry? = null
        var view: LinearLayout
        var calendarAccountName: TextView
        var checkboxCalendarName: CheckBox
        var colorView: View
        var calendarEntryLayout: LinearLayout
        var spacingView: View

        init {
            view = itemView.findViewById<LinearLayout>(R.id.linearLyaoutCalendarView)

            calendarAccountName = view.findViewById<TextView>(R.id.textViewCalendarOwner)
            checkboxCalendarName = view.findViewById<CheckBox>(R.id.checkBoxCalendarSelection)
            colorView = view.findViewById<View>(R.id.viewCalendarColor)
            calendarEntryLayout = view.findViewById<LinearLayout>(R.id.linearLayoutCalendarEntry)
            spacingView = view.findViewById<View>(R.id.viewCalendarsSpacing)

            checkboxCalendarName.setOnClickListener {
                view ->
                val action = onItemChanged
                val ent = entry

                if (ent != null && ent.calendar != null) {
                    ent.isHandled = checkboxCalendarName.isChecked
                    if (action != null)
                        action(view, ent.calendar.calendarId, ent.isHandled)
                }
            }
        }
    }

    var onItemChanged: ((View, Long, Boolean) -> Unit)? = null;

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        if (position >= 0 && position < entries.size) {

            val entry = entries[position]

            holder.entry = entry

            when (entry.type) {
                CalendarListEntryType.Header -> {
                    holder.calendarAccountName.text = entry.headerTitle
                    holder.calendarAccountName.visibility = View.VISIBLE
                    holder.calendarEntryLayout.visibility = View.GONE
                    holder.spacingView.visibility = View.GONE
                }

                CalendarListEntryType.Calendar -> {
                    holder.checkboxCalendarName.text = entry.calendar?.name ?: ""
                    holder.calendarAccountName.visibility = View.GONE
                    holder.calendarEntryLayout.visibility = View.VISIBLE
                    holder.colorView.background = ColorDrawable(entry.calendar?.color ?: Consts.DEFAULT_CALENDAR_EVENT_COLOR)
                    holder.checkboxCalendarName.isChecked = entry.isHandled
                    holder.spacingView.visibility = View.GONE
                }

                CalendarListEntryType.Divider -> {
                    holder.calendarEntryLayout.visibility = View.GONE
                    holder.calendarAccountName.visibility = View.GONE
                    holder.spacingView.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return entries.size;
    }
}


class CalendarsActivity : AppCompatActivity() {

    private val scope = MainScope()

    private lateinit var adapter: CalendarListAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var noCalendarsText: TextView

    private lateinit var settings: Settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DevLog.debug(LOG_TAG, "onCreate")

        setContentView(R.layout.activity_calendars)
        setSupportActionBar(findViewById<Toolbar?>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        settings = Settings(this)

        adapter = CalendarListAdapter(this, arrayOf<CalendarListEntry>())

        adapter.onItemChanged = {
            _, calendarId, isEnabled ->
            DevLog.debug(LOG_TAG, "Item has changed: $calendarId $isEnabled");

            settings.setCalendarIsHandled(calendarId, isEnabled)
        }

        recyclerView = findViewById<RecyclerView>(R.id.list_calendars)
        recyclerView.adapter = adapter;

        noCalendarsText = findViewById<TextView>(R.id.no_calendars_text)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()

        scope.launch {
            // load the data here

            val entriesFinal = withContext(Dispatchers.Default) {
                val calendars = CalendarProvider.getCalendars(this@CalendarsActivity).toTypedArray()

                val entries = mutableListOf<CalendarListEntry>()

                // Arrange entries by accountName calendar
                for ((accountName, type) in calendars.map { Pair(it.accountName, it.accountType) }.toSet()) {

                    // Add group title
                    entries.add(CalendarListEntry(type = CalendarListEntryType.Header, headerTitle = accountName))

                    // Add all the calendars for this accountName
                    entries.addAll(
                            calendars
                                    .filter { it.accountName == accountName && it.accountType == type }
                                    .sortedBy { it.calendarId }
                                    .map {
                                        CalendarListEntry(
                                                type = CalendarListEntryType.Calendar,
                                                calendar = it,
                                                isHandled = settings.getCalendarIsHandled(it.calendarId))
                                    })

                    // Add a divider
                    entries.add(CalendarListEntry(type = CalendarListEntryType.Divider))
                }

                // remove last divider
                if (entries.size >= 1 && entries[entries.size - 1].type == CalendarListEntryType.Divider)
                    entries.removeAt(entries.size - 1)

                entries.toTypedArray()
            }

            noCalendarsText.visibility = if (entriesFinal.isNotEmpty()) View.GONE else View.VISIBLE
            adapter.entries = entriesFinal
            adapter.notifyDataSetChanged()
        }
    }

    companion object {
        private const val LOG_TAG = "CalendarsActivity"
    }
}