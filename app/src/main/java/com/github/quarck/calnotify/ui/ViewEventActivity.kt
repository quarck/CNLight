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
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import android.text.format.DateUtils
import android.view.View
import android.widget.*
import com.github.quarck.calnotify.app.*
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.eventsstorage.EventsStorage
//import com.github.quarck.calnotify.utils.logs.Logger
import com.github.quarck.calnotify.utils.maps.MapsIntents
import com.github.quarck.calnotify.utils.textutils.EventFormatter
import com.github.quarck.calnotify.utils.*
import java.util.*
import com.github.quarck.calnotify.*
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.permissions.PermissionsManager
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import android.text.method.ScrollingMovementMethod
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

        //snoozeAllIsChange = intent.getBooleanExtra(Consts.INTENT_SNOOZE_ALL_IS_CHANGE, false)

        snoozeFromMainActivity = intent.getBooleanExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, false)

        findViewById<Toolbar?>(R.id.toolbar)?.visibility = View.GONE

        // load event if it is not a "snooze all"
        EventsStorage(this).use {
            db ->

            var dbEvent = db.getEvent(eventId, instanceStartTime)

            if (dbEvent != null) {
                val eventDidChange = calendarReloadManager.reloadSingleEvent(this, db, dbEvent, calendarProvider, null)
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
            findViewById<View>(R.id.snooze_view_location_layout).visibility = View.VISIBLE;
            val locationView = findViewById<TextView>(R.id.snooze_view_location)
            locationView.text = location;
            locationView.setOnClickListener { MapsIntents.openLocation(this, event.location) }
        }

        val title = findViewById<TextView>(R.id.snooze_view_title)
        title.text = if (event.title.isNotEmpty()) event.title else this.resources.getString(R.string.empty_title);

        val (line1, line2) = formatter.formatDateTimeTwoLines(event);

        val dateTimeFirstLine = findViewById<TextView>(R.id.snooze_view_event_date_line1)
        val dateTimeSecondLine = findViewById<TextView>(R.id.snooze_view_event_date_line2)

        dateTimeFirstLine.text = line1;

        if (line2.isEmpty())
            dateTimeSecondLine.visibility = View.GONE
        else
            dateTimeSecondLine.text = line2;

        dateTimeFirstLine.isClickable = false
        dateTimeSecondLine.isClickable = false
        title.isClickable = false

        title.setMovementMethod(ScrollingMovementMethod())
        title.post {
            val y = title.getLayout()?.getLineTop(0)
            if (y != null)
                title.scrollTo(0, y)
        }
        title.setTextIsSelectable(true)

        if (event.desc.isNotEmpty()) {
            // Show the event desc
            findViewById<RelativeLayout>(R.id.layout_event_description).visibility = View.VISIBLE
            findViewById<TextView>(R.id.snooze_view_event_description).text = event.desc
        }

        var color: Int = event.color.adjustCalendarColor()
        if (color == 0)
            color = ContextCompat.getColor(this, R.color.primary)

        val colorDrawable = ColorDrawable(color.invertColor().scaleColor(0.1f))
        findViewById<RelativeLayout>(R.id.snooze_view_event_details_layout).background = colorDrawable
        title.setTextColor(ColorStateList.valueOf(color.scaleColor(1.8f)))

        window.statusBarColor = color.scaleColor(0.7f)

        val shouldOfferMove = (DateTimeUtils.isUTCTodayOrInThePast(event.startTime))
        if (shouldOfferMove) {
            findViewById<RelativeLayout>(R.id.snooze_reschedule_layout).visibility = View.VISIBLE
            if (event.isRepeating) {
                findViewById<TextView>(R.id.snooze_reschedule_for).text = getString(R.string.change_event_time_repeating_event)
            }
        }
        else {
            findViewById<View?>(R.id.snooze_view_inter_view_divider)?.visibility = View.GONE
        }

        if (event.snoozedUntil != 0L) {
            findViewById<TextView>(R.id.snooze_snooze_for).text = resources.getString(R.string.change_snooze_to)
        }

        val nextReminderLayout: RelativeLayout? = findViewById<RelativeLayout>(R.id.layout_next_reminder)
        val nextReminderText: TextView? = findViewById<TextView>(R.id.snooze_view_next_reminder)

        if (nextReminderLayout != null && nextReminderText != null) {

            val nextReminder = calendarProvider.getNextEventReminderTime(this, event)

            if (nextReminder != 0L) {
                nextReminderLayout.visibility = View.VISIBLE
                nextReminderText.visibility = View.VISIBLE

                val format = this.resources.getString(R.string.next_reminder_fmt)

                nextReminderText.text = format.format(formatter.formatTimePoint(nextReminder))
            }
        }


        val fab = findViewById<FloatingActionButton>(R.id.floating_edit_button)

        if (!calendar.isReadOnly) {
            if (!event.isRepeating) {

                fab.setOnClickListener { _ ->
                    val intent = Intent(this, EditEventActivity::class.java)
                    intent.putExtra(EditEventActivity.EVENT_ID, event.eventId)
                    startActivity(intent)
                    finish()
                }

            } else {
                fab.setOnClickListener { _ ->
                    CalendarIntents.viewCalendarEvent(this, event)
                    finish()
                }
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


        val menuButton = findViewById<ImageView?>(R.id.snooze_view_menu)
        menuButton?.setOnClickListener { showDismissEditPopup(menuButton) }
    }

    fun showDismissEditPopup(v: View) {
        val popup = PopupMenu(this, v)
        val inflater = popup.menuInflater
        inflater.inflate(R.menu.snooze, popup.menu)

        popup.setOnMenuItemClickListener {
            item ->

            when (item.itemId) {
                R.id.action_dismiss_event -> {
                    ApplicationController.dismissEvent(this, EventFinishType.ManuallyInTheApp, event)
                    finish()
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
        val layout2 = findViewById<LinearLayout>(R.id.snooze_view_snooze_sub_layout_4)
        btnEnable.visibility = View.GONE
        layout2.visibility = View.VISIBLE
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onButtonSnoozeClick(v: View?) {
        if (v == null)
            return

        when (v.id) {
            R.id.snooze_view_snooze_15m -> snoozeEvent(snoozePresets[0])
            R.id.snooze_view_snooze_60m -> snoozeEvent(snoozePresets[1])
            R.id.snooze_view_snooze_240m -> snoozeEvent(snoozePresets[2])
            R.id.snooze_view_snooze_8h -> snoozeEvent(snoozePresets[3])
            R.id.snooze_view_snooze_1d -> snoozeEvent(snoozePresets[4])
            R.id.snooze_view_snooze_minus_5m -> snoozeEvent(snoozePresets[5])
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
            R.id.snooze_view_move_1d ->
                confirmAndReschedule(addDays = 1)
            R.id.snooze_view_move_7d ->
                confirmAndReschedule(addDays = 7)
            R.id.snooze_view_move_30d ->
                confirmAndReschedule(addDays = 30)
        }
    }

    companion object {
        private const val LOG_TAG = "ActivitySnooze"
    }

}
