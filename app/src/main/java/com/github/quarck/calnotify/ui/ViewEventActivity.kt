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
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import android.text.method.ScrollingMovementMethod
import com.github.quarck.calnotify.calendarmonitor.CalendarReloadManager
import java.lang.StringBuilder

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

        window.navigationBarColor = ContextCompat.getColor(this, android.R.color.black)

        val currentTime = System.currentTimeMillis()

        settings = Settings(this)
        formatter = EventFormatter(this)

        // Populate event details
        val eventId = intent.getLongExtra(Consts.INTENT_EVENT_ID_KEY, -1)
        val instanceStartTime = intent.getLongExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, -1L)
        val alertTime = intent.getLongExtra(Consts.INTENT_ALERT_TIME, -1L)

        snoozeFromMainActivity = intent.getBooleanExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, false)
        viewForFutureEvent = intent.getBooleanExtra(Consts.INTENT_VIEW_FUTURE_EVENT, false)

        findViewById<Toolbar?>(R.id.toolbar)?.visibility = View.GONE

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
            val calEvent = CalendarProvider.getEventAlertsForInstanceAt(this, instanceStartTime, eventId)
                    .firstOrNull { it.alertTime == alertTime }
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
        title.setMovementMethod(ScrollingMovementMethod())
        title.post {
            val y = title.getLayout()?.getLineTop(0)
            if (y != null)
                title.scrollTo(0, y)
        }
        title.setTextIsSelectable(true)

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

        var color: Int = event.color.adjustCalendarColor()
        if (color == 0)
            color = ContextCompat.getColor(this, R.color.primary)

        val colorDrawable = ColorDrawable(color.invertColor().scaleColor(0.1f))
        findViewById<RelativeLayout>(R.id.event_view_event_details_layout).background = colorDrawable
        title.setTextColor(ColorStateList.valueOf(color.scaleColor(1.8f)))

        window.statusBarColor = color.scaleColor(0.7f)

        if (!viewForFutureEvent) {
            val shouldOfferMove = (DateTimeUtils.isUTCTodayOrInThePast(event.startTime))
            if (shouldOfferMove) {
                findViewById<RelativeLayout>(R.id.snooze_reschedule_layout).visibility = View.VISIBLE
                if (event.isRepeating) {
                    findViewById<TextView>(R.id.snooze_reschedule_for).text = getString(R.string.change_event_time_repeating_event)
                }
            }
            findViewById<RelativeLayout>(R.id.snooze_layout).visibility = View.VISIBLE
        } else {
            findViewById<RelativeLayout>(R.id.snooze_reschedule_layout).visibility = View.GONE
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

//        findViewById<TextView>(R.id.debug_details_line).apply {
//            text = "" //  "RRULE: ${event.rRule}\nRDATE: ${event.rDate}\nEXRRULE: ${event.exRRule}\nEXRDATE: ${event.exRDate}"
//        }

        val fab = findViewById<FloatingActionButton>(R.id.floating_edit_button)

        if (!calendar.isReadOnly) {
            fab.setOnClickListener { _ ->
                val intent = Intent(this, EditEventActivity::class.java)
                intent.putExtra(EditEventActivity.EVENT_ID, event.eventId)
                intent.putExtra(EditEventActivity.INSTANCE_START, event.instanceStartTime)
                intent.putExtra(EditEventActivity.IS_RECURRING, event.isRepeating)
                startActivity(intent)
                finish()
            }

            val states = arrayOf(intArrayOf(android.R.attr.state_enabled), // enabled
                    intArrayOf(android.R.attr.state_pressed)  // pressed
            )

            val colors = intArrayOf(
                    event.color.adjustCalendarColor(false),
                    event.color.adjustCalendarColor(true)
            )

            fab.backgroundTintList = ColorStateList(states, colors)
        }


        val menuButton = findViewById<ImageView?>(R.id.event_view_menu)
        menuButton?.setOnClickListener { showDismissEditPopup(menuButton) }
        menuButton?.visibility = View.VISIBLE
    }

    fun showDismissEditPopup(v: View) {
        val popup = PopupMenu(this, v)
        val inflater = popup.menuInflater
        inflater.inflate(R.menu.snooze, popup.menu)

        val dismissItem = popup.menu.findItem(R.id.action_dismiss_event)
        dismissItem?.isVisible = !viewForFutureEvent

        popup.setOnMenuItemClickListener {
            item ->

            when (item.itemId) {
                R.id.action_dismiss_event -> {
                    if (!viewForFutureEvent) {
                        ApplicationController.dismissEvent(this, EventFinishType.ManuallyInTheApp, event)
                        finish()
                    }
                    true
                }

                R.id.action_open_in_calendar -> {
                    CalendarIntents.viewCalendarEvent(this, event)
                    finish()
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onButtonCancelClick(v: View?) {
        finish();
    }

    private fun snoozeEvent(snoozeDelay: Long) {
        DevLog.debug(LOG_TAG, "Snoozing event id ${event.eventId}, snoozeDelay=${snoozeDelay / 1000L}")

        val result = ApplicationController.snoozeEvent(this, event.eventId, event.instanceStartTime, snoozeDelay);
        result?.toast(this)
        finish()
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onEnableMoveEvent(v: View?) {
        val btnEnable = findViewById<TextView>(R.id.enable_snooze_reschedule)
        val layout2 = findViewById<LinearLayout>(R.id.event_view_snooze_sub_layout_4)
        btnEnable.visibility = View.GONE
        layout2.visibility = View.VISIBLE
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onButtonSnoozeClick(v: View?) {
        if (v == null)
            return

        when (v.id) {
            R.id.event_view_snooze_15m -> snoozeEvent(snoozePresets[0])
            R.id.event_view_snooze_60m -> snoozeEvent(snoozePresets[1])
            R.id.event_view_snooze_240m -> snoozeEvent(snoozePresets[2])
            R.id.event_view_snooze_8h -> snoozeEvent(snoozePresets[3])
            R.id.event_view_snooze_1d -> snoozeEvent(snoozePresets[4])
            R.id.event_view_snooze_minus_5m -> snoozeEvent(snoozePresets[5])
        }
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

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onButtonRescheduleClick(v: View?) {
        if (v == null)
            return

        when (v.id) {
            R.id.event_view_move_1d ->
                confirmAndReschedule(addDays = 1)
            R.id.event_view_move_7d ->
                confirmAndReschedule(addDays = 7)
            R.id.event_view_move_30d ->
                confirmAndReschedule(addDays = 30)
        }
    }

    companion object {
        private const val LOG_TAG = "ActivitySnooze"
    }

}
