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

import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import android.view.View
import android.widget.*
import com.github.quarck.calnotify.app.*
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.eventsstorage.EventsStorage
//import com.github.quarck.calnotify.utils.logs.Logger
import com.github.quarck.calnotify.utils.maps.MapsIntents
import com.github.quarck.calnotify.utils.textutils.EventFormatter
import com.github.quarck.calnotify.utils.*
import com.github.quarck.calnotify.*
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.permissions.PermissionsManager
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import android.text.method.ScrollingMovementMethod
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import com.github.quarck.calnotify.calendarmonitor.CalendarReloadManager

// TODO: add repeating rule and calendar name somewhere on the snooze activity

open class ViewEventActivity : AppCompatActivity() {

    lateinit var event: EventAlertRecord

    lateinit var calendar: CalendarRecord

    lateinit var snoozePresets: LongArray

    lateinit var settings: Settings

    lateinit var formatter: EventFormatter

    private val calendarReloadManager = CalendarReloadManager
    private val calendarProvider = CalendarProvider

    var snoozeFromMainActivity = false
    var viewForFutureEvent = false

    lateinit var calendarNameTextView: TextView
    lateinit var calendarAccountTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        if (!PermissionsManager.hasAllPermissions(this)) {
            finish()
            return
        }

        setContentView(R.layout.activity_view)

        setSupportActionBar(findViewById<Toolbar?>(R.id.toolbar))
        supportActionBar?.let{
            it.setDisplayHomeAsUpEnabled(true)
            it.setHomeAsUpIndicator(R.drawable.ic_clear_white_24dp)
            it.setDisplayShowHomeEnabled(true)
        }
        window.navigationBarColor = ContextCompat.getColor(this, android.R.color.black)

        val currentTime = System.currentTimeMillis()

        settings = Settings(this)
        formatter = EventFormatter(this)

        // Populate event details
        val eventId = intent.getLongExtra(Consts.INTENT_EVENT_ID_KEY, -1)
        val instanceStartTime = intent.getLongExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, -1L)
        val alertTime = intent.getLongExtra(Consts.INTENT_ALERT_TIME, 0L)

        snoozeFromMainActivity = intent.getBooleanExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, false)
        viewForFutureEvent = intent.getBooleanExtra(Consts.INTENT_VIEW_FUTURE_EVENT_EXTRA, false)
        val noSkips = intent.getBooleanExtra(Consts.INTENT_NO_SKIPS_EXTRA, false)

        // findViewById<Toolbar?>(R.id.toolbar)?.visibility = View.GONE

        // load event if it is not a "snooze all"

        if (!viewForFutureEvent) {
            EventsStorage(this).use { db ->

                var dbEvent = db.getEvent(eventId, instanceStartTime)

                if (dbEvent != null) {
                    val eventDidChange = calendarReloadManager.reloadSingleEvent(this, db, dbEvent, calendarProvider, noAutoDismiss = true)
                    if (eventDidChange) {
                        val newDbEvent = db.getEvent(eventId, instanceStartTime)
                        if (newDbEvent != null) {
                            dbEvent = newDbEvent
                        } else {
                            DevLog.error(LOG_TAG, "ViewActivity: cannot find event after calendar reload, event $eventId, inst $instanceStartTime")
                        }
                    }
                }

                if (dbEvent == null) {
                    DevLog.error(LOG_TAG, "ViewActivity started for non-existing eveng id $eventId, st $instanceStartTime")
                    finish()
                    return
                }

                event = dbEvent
            }
        } else {
            var calEvent = CalendarProvider.getEventAlertsForInstanceAt(this, instanceStartTime, eventId)
                    .firstOrNull { alertTime == 0L || it.alertTime == alertTime }
            if (calEvent == null) {
                calEvent = CalendarProvider.getInstancesInRange(this, instanceStartTime, instanceStartTime+100L, eventId)
                        .firstOrNull()
            }
            if (calEvent == null) {
                DevLog.error(LOG_TAG, "ViewActivity started for non-existing eveng id $eventId, st $instanceStartTime")
                finish()
                return
            }
            event = calEvent
        }

        calendar = calendarProvider.getCalendarById(this, event.calendarId)
                ?: calendarProvider.createCalendarNotFoundCal(this)

        calendarNameTextView = findViewById<TextView>(R.id.view_event_calendar_name)
        calendarNameTextView.text = calendar.displayName

        calendarAccountTextView = findViewById<TextView>(R.id.view_event_calendar_account)
        calendarAccountTextView.text = calendar.accountName

        snoozePresets = Consts.DEFAULT_SNOOZE_PRESETS

        // remove "MM minutes before event" snooze presents for "Snooze All"
        // and when event time has passed already
        if (event.displayedStartTime < currentTime)
            snoozePresets = snoozePresets.filter { it > 0L }.toLongArray()

        val presetRoot = findViewById<LinearLayout>(R.id.event_view_snooze_sub_layout)

        for (p in snoozePresets) {
            val preset = p
            val childLayout = layoutInflater.inflate(R.layout.snooze_preset_layout_template, null)
            val textView = childLayout.findViewById<TextView>(R.id.event_view_snooze_template)
            textView.text = formatSnoozePreset(this, preset)
            textView.setOnClickListener { snoozeEvent(preset) }
            presetRoot.addView(childLayout)
        }

        val location = event.location;
        if (location != "") {
            findViewById<View>(R.id.event_view_location_layout).visibility = View.VISIBLE;
            val locationView = findViewById<TextView>(R.id.event_view_location)
            locationView.text = location;
            locationView.setOnClickListener { MapsIntents.openLocation(this, event.location) }
        }

        // title
        val title = findViewById<TextView>(R.id.event_view_title)
        title.text = if (event.title.isNotEmpty()) event.title else this.resources.getString(R.string.empty_title);
//        title.setMovementMethod(ScrollingMovementMethod())
//        title.post {
//            val y = title.getLayout()?.getLineTop(0)
//            if (y != null)
//                title.scrollTo(0, y)
//        }
        title.setTextIsSelectable(true)

        findViewById<View>(R.id.event_view_event_color_view).setBackgroundColor(event.color.adjustCalendarColor(darker = false))

        // date
        val (line1, line2) = formatter.formatDateTimeTwoLines(event)

        findViewById<TextView>(R.id.event_view_date_line1).apply {
            text = line1
        }

        findViewById<TextView>(R.id.event_view_date_line2).apply {
            text = line2
            visibility = if (line2.isNotEmpty()) View.VISIBLE else View.GONE
        }

        var eventTimeZoneOffset = 0
//        var deviceTimeZoneOffset = 0
        if (event.timeZone.isNotBlank()) {
            try {
                val eventTimeZone = java.util.TimeZone.getTimeZone(event.timeZone)
                eventTimeZoneOffset = eventTimeZone.getOffset(event.instanceStartTime)
               // val deviceTimeZone = java.util.TimeZone.getDefault()
                ///deviceTimeZoneOffset = deviceTimeZone.getOffset(event.instanceStartTime)
            }
            catch (ex: Exception) {
            }
        }

        findViewById<TextView>(R.id.event_view_timezone).apply {
//            if (eventTimeZoneOffset == deviceTimeZoneOffset) {
//                text = event.timeZone
//                visibility = View.VISIBLE
//            }
//            else {
            visibility = View.GONE
            //}
        }

        // recurrence
        findViewById<TextView>(R.id.event_view_recurrence).apply {
            if (event.rRule.isNotBlank() || event.rDate.isNotBlank()) {

                val recurrence = CalendarRecurrence.tryInterpretRecurrence(
                        event.instanceStartTime,
                        event.timeZone,
                        event.rRule,
                        event.rDate,
                        event.exRRule,
                        event.exRDate
                )

                if (recurrence != null) {
                    text = recurrence.toString()
                }
                else {
                    text = "Failed to parse: ${event.rRule} / ${event.rDate}"
                }
                visibility = View.VISIBLE
            }
            else {
                visibility = View.GONE
            }
        }

        if (event.desc.isNotEmpty()) {
            // Show the event desc
            findViewById<RelativeLayout>(R.id.layout_event_description).visibility = View.VISIBLE
            findViewById<TextView>(R.id.event_view_description).text = event.desc
        }

        if (!viewForFutureEvent) {
            findViewById<RelativeLayout>(R.id.snooze_layout).visibility = View.VISIBLE
        } else {
            findViewById<RelativeLayout>(R.id.snooze_layout).visibility = View.GONE
        }

        if (event.snoozedUntil != 0L) {
            findViewById<TextView>(R.id.snooze_snooze_for)?.text = resources.getString(R.string.change_snooze_to)
        }


        val reminders: List<EventReminderRecord> = calendarProvider.getEventReminders(this@ViewEventActivity, event.eventId)
        if (reminders.isNotEmpty()) {
            findViewById<RelativeLayout>(R.id.event_view_reminders_layout).visibility = View.VISIBLE

            findViewById<TextView>(R.id.event_view_reminders).text =
                    reminders.joinToString(separator = "\n") { it.toLocalizedString(this, event.isAllDay) }

            val nextReminder = calendarProvider.getNextEventReminderTime(this, event)
            if (nextReminder != 0L) {
                findViewById<TextView>(R.id.label_next).visibility = View.VISIBLE
                findViewById<TextView>(R.id.event_view_next_reminder).apply {
                    visibility = View.VISIBLE
                    text = formatter.formatTimePoint(nextReminder)
                }
            }
            else {
                findViewById<TextView>(R.id.label_next).visibility = View.GONE
                findViewById<TextView>(R.id.event_view_next_reminder).visibility = View.GONE
            }
        }
        else {
            findViewById<RelativeLayout>(R.id.event_view_reminders_layout).visibility = View.GONE
        }

        val fabMoveButton = findViewById<FloatingActionButton>(R.id.floating_move_button)

        val fabColorStateList =  ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf(android.R.attr.state_pressed)),
                intArrayOf(event.color.adjustCalendarColor(false),  event.color.adjustCalendarColor(true)))

        fabMoveButton.backgroundTintList = fabColorStateList

        val allowEdit = !calendar.isReadOnly

        val eventStartTimeHasPassed = (DateTimeUtils.isUTCTodayOrInThePast(event.startTime))
        if (allowEdit && !viewForFutureEvent && eventStartTimeHasPassed) {
            fabMoveButton.setOnClickListener(this::showMoveMenu)
        } else {
            fabMoveButton.visibility = View.GONE
        }
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.view_event_menu, menu)

        val allowEdit = !calendar.isReadOnly
        if (!allowEdit) {
            menu.findItem(R.id.action_edit)?.isVisible = false
        }

        if (!allowEdit || event.isRepeating) {
            menu.findItem(R.id.action_delete_event)?.isVisible = false
        }

        if (viewForFutureEvent) {
            menu.findItem(R.id.action_dismiss)?.isVisible = false

            val now = System.currentTimeMillis()
            if (event.instanceStartTime !in now until now + 2 * Consts.DAY_IN_MILLISECONDS) {
                menu.findItem(R.id.action_mark_done)?.isVisible = false
                menu.findItem(R.id.action_mark_not_done)?.isVisible = false
            }
        }
        else {
            menu.findItem(R.id.action_mark_done)?.isVisible = false
            menu.findItem(R.id.action_mark_not_done)?.isVisible = false
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {
            R.id.action_edit -> {
                if (!event.isRepeating) {
                    val intent = Intent(this, EditEventActivity::class.java)
                            .putExtra(EditEventActivity.EVENT_ID, event.eventId)
                            .putExtra(EditEventActivity.INSTANCE_START, event.instanceStartTime)
                            .putExtra(EditEventActivity.INSTANCE_END, event.instanceEndTime)
                    startActivity(intent)
                    finish()
                } else {
                    CalendarIntents.viewCalendarEvent(this, event)
                    finish()
                }
            }

            R.id.action_delete_event -> {
                if (!event.isRepeating) {
                    confirmAndDelete()
                }
            }

            R.id.action_open_in_calendar -> {
                CalendarIntents.viewCalendarEvent(this, event)
                finish()
            }

            R.id.action_dismiss -> {
                ApplicationController.dismissEvent(this, EventFinishType.ManuallyInTheApp, event)
                finish()
            }

            R.id.action_mark_done -> {
                ApplicationController.dismissFutureEvent(this, MonitorDataPair.fromEventAlertRecord(event))
                finish()
            }

            R.id.action_mark_not_done -> {
                ApplicationController.restoreEvent(this, event)
                finish()
            }
        }

        return super.onOptionsItemSelected(item)
    }

    fun showMoveMenu(v: View) {
        val popup = PopupMenu(this, v)
        val inflater = popup.menuInflater
        inflater.inflate(R.menu.move_options, popup.menu)
        popup.setOnMenuItemClickListener {
            item ->

            when (item.itemId) {
                R.id.action_move_next_day -> {
                    reschedule(addTime = 1 * Consts.DAY_IN_SECONDS * 1000L)
                    true
                }
                R.id.action_move_next_week -> {
                    reschedule(addTime = 7 * Consts.DAY_IN_SECONDS * 1000L)
                    true
                }
                R.id.action_move_next_month_30d -> {
                    reschedule(addTime = 30 * Consts.DAY_IN_SECONDS * 1000L)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun snoozeEvent(snoozeDelay: Long) {
        DevLog.debug(LOG_TAG, "Snoozing event id ${event.eventId}, snoozeDelay=${snoozeDelay / 1000L}")

        val result = ApplicationController.snoozeEvent(this, event.eventId, event.instanceStartTime, snoozeDelay);
        result?.toast(this)
        finish()
    }

    private fun confirmAndReschedule(addDays: Long) {

        AlertDialog.Builder(this)
                .setMessage(getString(R.string.move_event_confirm).format(addDays))
                .setCancelable(true)
                .setPositiveButton(R.string.yes) { _, _ ->
                    reschedule(addTime = addDays * Consts.DAY_IN_SECONDS * 1000L)
                }
                .setNegativeButton(R.string.cancel) { _, _ ->
                }
                .create()
                .show()
    }

    private fun confirmAndDelete() {
        AlertDialog.Builder(this)
                .setMessage(getString(R.string.delete_event_question))
                .setCancelable(true)
                .setPositiveButton(R.string.yes) { _, _ ->
                    CalendarProvider.deleteEvent(this, event.eventId)
                }
                .setNegativeButton(R.string.cancel) { _, _ ->
                }
                .create()
                .show()

    }

    private fun reschedule(addTime: Long) {

        DevLog.info(LOG_TAG, "Moving event ${event.eventId} by ${addTime / 1000L} seconds, isRepeating = ${event.isRepeating}");

        if (!event.isRepeating) {
            val moved = ApplicationController.moveEvent(this, event, addTime)

            if (moved != null) {
                SnoozeResult(SnoozeType.Moved, event.startTime).toast(this) // Show
                finish()  // terminate ourselves
            } else {
                DevLog.info(LOG_TAG, "snooze: Failed to move event ${event.eventId} by ${addTime / 1000L} seconds")
            }
        }
        else {
            val moved = ApplicationController.moveAsCopy(this, calendar, event, addTime)
            if (moved != null) {
                SnoozeResult(SnoozeType.Moved, event.startTime).toast(this) // Show
                finish() // terminate ourselves
            } else {
                DevLog.info(LOG_TAG, "snooze: Failed to move event ${event.eventId} by ${addTime / 1000L} seconds")
            }
        }
    }

    companion object {
        private const val LOG_TAG = "ActivitySnooze"
    }

}
