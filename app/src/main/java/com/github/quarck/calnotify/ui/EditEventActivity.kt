//
//   Calendar Notifications Plus
//   Copyright (C) 2018 Sergey Parshin (s.parshin.sc@gmail.com)
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
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.CalendarContract
import android.text.format.DateUtils
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.calendar.CalendarEditor
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.utils.textutils.EventFormatter
import com.github.quarck.calnotify.utils.textutils.dateToStr
import com.github.quarck.calnotify.utils.*
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.*

fun EventReminderRecord.toLocalizedString(ctx: Context, isAllDay: Boolean): String {

    val ret = StringBuilder()

    if (!isAllDay) {
        val duration = EventFormatter(ctx).formatTimeDuration(this.millisecondsBefore, 60L)

        ret.append(
                ctx.resources.getString(R.string.add_event_fmt_before).format(duration)
        )
    }
    else {
        val fullDaysBefore = allDayDaysBefore
        val (hr, min) = allDayHourOfDayAndMinute

        val cal = DateTimeUtils.createCalendarTime(System.currentTimeMillis(), hr, min)

        val time = DateUtils.formatDateTime(ctx, cal.timeInMillis, DateUtils.FORMAT_SHOW_TIME)

        when (fullDaysBefore) {
            0 ->
                ret.append(
                        ctx.resources.getString(R.string.add_event_zero_days_before).format(time)
                )
            1 ->
                ret.append(
                        ctx.resources.getString(R.string.add_event_one_day_before).format(time)
                )
            else ->
                ret.append(
                        ctx.resources.getString(R.string.add_event_n_days_before).format(fullDaysBefore, time)
                )
        }
    }

    when (this.method) {
        CalendarContract.Reminders.METHOD_EMAIL -> {
            ret.append(" ")
            ret.append(ctx.resources.getString(R.string.add_event_as_email_suffix))
        }
        CalendarContract.Reminders.METHOD_SMS -> {
            ret.append(" ")
            ret.append(ctx.resources.getString(R.string.add_event_as_sms_suffix))
        }
        CalendarContract.Reminders.METHOD_ALARM -> {
            ret.append(" ")
            ret.append(ctx.resources.getString(R.string.add_event_as_alarm_suffix))
        }
    }

    return ret.toString()
}

class EditEventActivityState(val ctx: Context): PersistentStorageBase(ctx, "add_event_state") {
    var lastCalendar by PersistentStorageBase.LongProperty(-1, "A") // give a short name to simplify XML parsing
}

open class EditEventActivity : AppCompatActivity() {

    data class ReminderWrapper(val view: TextView, var reminder: EventReminderRecord, val isForAllDay: Boolean)

    private var receivedSharedText = ""

    private val editor: CalendarEditor by lazy { CalendarEditor(CalendarProvider) }

    private lateinit var layoutMain: LinearLayout
    private lateinit var layoutRecurrence: LinearLayout

    private lateinit var recurrenceView: RecurrenceView

    private lateinit var eventTitleText: EditText

    private lateinit var buttonSave: Button
    private lateinit var buttonCancel: ImageView

    private lateinit var accountName: TextView

    private lateinit var switchAllDay: SwitchMaterial

    private lateinit var dateFrom: Button
    private lateinit var timeFrom: Button

    private lateinit var dateTo: Button
    private lateinit var timeTo: Button

    private lateinit var buttonRecurrence: Button

    private lateinit var eventLocation: EditText

    private lateinit var notificationsLayout: LinearLayout
    private lateinit var notificationPrototype: TextView
    private lateinit var addNotification: TextView

    private lateinit var note: EditText

    private lateinit var eventTitleLayout: RelativeLayout

    private lateinit var calendars: List<CalendarRecord>
    private lateinit var calendar: CalendarRecord

    private lateinit var settings: Settings

    private lateinit var from: Calendar
    private lateinit var to: Calendar
    private var isAllDay: Boolean = false

    private lateinit var persistentState: EditEventActivityState

    var calendarProvider = CalendarProvider

    val reminders = mutableListOf<ReminderWrapper>()

    var originalEvent: EventRecord? = null
    var originalInstanceStart = 0L
    var originalInstanceEnd = 0L

    var rRule: String = ""
    var rDate: String = ""
    var exRRule: String = ""
    var exRDate: String = ""

    val anyChanges: Boolean
        get() {
            val details = originalEvent?.details

            if (details == null) {
                return eventTitleText.text.isNotEmpty() ||
                        eventLocation.text.isNotEmpty() ||
                        note.text.isNotEmpty()
            }

            if (eventTitleText.text.toString() != details.title ||
                    note.text.toString() != details.desc ||
                    eventLocation.text.toString() != details.location
                    ) {
                return true
            }

//            if (eventTimeZone.text.toString() != details.timezone)
//                return true

            if (isAllDay != details.isAllDay)
                return true

            var currentStartTime = from.timeInMillis
            var currentEndTime = to.timeInMillis

            if (isAllDay) {
                currentStartTime = DateTimeUtils.createUTCCalendarDate(from.year, from.month, from.dayOfMonth).timeInMillis
                currentEndTime = DateTimeUtils.createUTCCalendarDate(to.year, to.month, to.dayOfMonth).timeInMillis
            }

            if (currentStartTime != originalInstanceStart || currentEndTime != originalInstanceEnd)
                return true

            val currentReminders = reminders.filter { it.isForAllDay == isAllDay }.map { it.reminder }

            if (currentReminders.size != details.reminders.size)
                return true

            if (!details.reminders.containsAll(currentReminders))
                return true

            if (rRule != details.rRule || rDate != details.rDate)
                return true
            if (exRRule != details.exRRule || exRDate != details.exRDate)
                return true

            return false;
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_add_event)

        val toolbar = findViewById<Toolbar?>(R.id.toolbar)
        toolbar?.visibility = View.GONE

        window.navigationBarColor = ContextCompat.getColor(this, android.R.color.black)

        persistentState = EditEventActivityState(this)

        val intent = intent
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                receivedSharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            }
        }
        else if (Intent.ACTION_PROCESS_TEXT.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                receivedSharedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()  ?: ""
            }
        }

        val eventId = intent.getLongExtra(EVENT_ID, -1)
        originalInstanceStart = intent.getLongExtra(INSTANCE_START, 0)
        originalInstanceEnd = intent.getLongExtra(INSTANCE_END, 0)

        if (eventId != -1L) {
            originalEvent = CalendarProvider.getEvent(this, eventId)

            if (originalEvent == null) {
                Toast.makeText(this, R.string.event_not_found, Toast.LENGTH_LONG).show()
                finish()
                return
            }

            originalEvent?.let {
                if (it.rRule == "") {
                    originalInstanceStart = it.startTime
                    originalInstanceEnd = it.endTime
                }
            }
        }
        else {
            if (receivedSharedText.isEmpty())
                findViewById<LinearLayout>(R.id.layout_focus_catcher).visibility = View.GONE
        }

        layoutMain = findViewById(R.id.layout_main)
        layoutRecurrence = findViewById(R.id.layout_recurrence)

        recurrenceView = RecurrenceView(this, layoutRecurrence)
        recurrenceView.onComplete(this::onCustomRecurrenceComplete)

        eventTitleLayout = findViewById<RelativeLayout?>(R.id.event_view_event_details_layout) ?: throw Exception("Cant find snooze_view_event_details_layout")

        // get all the objects first
        eventTitleText = findViewById<EditText?>(R.id.add_event_title) ?: throw Exception("Can't find add_event_title")

        buttonSave = findViewById<Button?>(R.id.add_event_save) ?: throw Exception("Can't find add_event_save")
        buttonCancel = findViewById<ImageView?>(R.id.add_event_view_cancel) ?: throw Exception("Can't find add_event_view_cancel")

        accountName = findViewById<TextView?>(R.id.account_name) ?: throw Exception("Can't find account_name")

        switchAllDay = findViewById<SwitchMaterial?>(R.id.switch_all_day) ?: throw Exception("Can't find switch_all_day")

        dateFrom = findViewById<Button?>(R.id.add_event_date_from) ?: throw Exception("Can't find add_event_date_from")
        timeFrom = findViewById<Button?>(R.id.add_event_time_from) ?: throw Exception("Can't find add_event_time_from")

        dateTo = findViewById<Button?>(R.id.add_event_date_to) ?: throw Exception("Can't find add_event_date_to")
        timeTo = findViewById<Button?>(R.id.add_event_time_to) ?: throw Exception("Can't find add_event_time_to")

        buttonRecurrence = findViewById(R.id.add_event_recurrence) ?: throw Exception("Can't find add_event_recurrence")

        eventLocation = findViewById<EditText?>(R.id.event_location) ?: throw Exception("Can't find event_location")

        notificationsLayout = findViewById<LinearLayout?>(R.id.notifications) ?: throw Exception("Can't find notifications")
        notificationPrototype = findViewById<TextView?>(R.id.notificationPrototype) ?: throw Exception("Can't find notificationPrototype")
        addNotification = findViewById<TextView?>(R.id.add_notification) ?: throw Exception("Can't find add_notification")

        note = findViewById<EditText?>(R.id.event_note) ?: throw Exception("Can't find event_note")

        notificationPrototype.visibility = View.GONE

        // settings
        settings = Settings(this)

        // Default calendar
        calendars = calendarProvider
                .getCalendars(this)
                .filter {
                    !it.isReadOnly &&
                            it.isVisible &&
                            settings.getCalendarIsHandled(it.calendarId)
                }

        if (calendars.isEmpty()) {
            DevLog.error(LOG_TAG, "You have no enabled calendars")

            accountName.text = "" // remove debug mess

            AlertDialog.Builder(this)
                    .setMessage(R.string.no_active_calendars)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        finish()
                    }
                    .show()

            return
        }

        val lastCalendar = persistentState.lastCalendar
        if (lastCalendar != -1L) {
            calendar = calendars.filter { it.calendarId == lastCalendar }.firstOrNull() ?: calendars[0]
        } else {
            calendar = calendars.filter { it.isPrimary }.firstOrNull() ?: calendars[0]
        }

        // Set onClickListener-s
        buttonSave.setOnClickListener(this::onButtonSaveClick)
        buttonCancel.setOnClickListener(this::onButtonCancelClick)

        accountName.setOnClickListener(this::onAccountClick)

        switchAllDay.setOnClickListener(this::onSwitchAllDayClick)

        dateFrom.setOnClickListener(this::onDateFromClick)
        timeFrom.setOnClickListener(this::onTimeFromClick)

        dateTo.setOnClickListener(this::onDateToClick)
        timeTo.setOnClickListener(this::onTimeToClick)

        addNotification.setOnClickListener(this::onAddNotificationClick)

        buttonRecurrence.setOnClickListener(this::onButtonRecurrence)

        // Set-up fields

        val eventToEdit = originalEvent

        if (eventToEdit != null) {
            val cal = calendars.find { it.calendarId == eventToEdit.calendarId }
            if (cal == null) {
                Toast.makeText(this, R.string.calendar_not_found, Toast.LENGTH_LONG).show()
                finish()
                return
            }
            calendar = cal

            isAllDay = eventToEdit.isAllDay
            switchAllDay.isChecked = isAllDay
            switchAllDay.isEnabled = false

            rRule = eventToEdit.rRule
            rDate = eventToEdit.rDate
            exRRule = eventToEdit.exRRule
            exRDate = eventToEdit.exRDate

            accountName.text = calendar.name
            eventTitleLayout.background = ColorDrawable(eventToEdit.color.invertColor().scaleColor(0.1f))
            eventTitleText.background = ColorDrawable(eventToEdit.color.invertColor().scaleColor(0.1f))
            eventTitleText.setTextColor(ColorStateList.valueOf(eventToEdit.color.scaleColor(1.8f)))

            window.statusBarColor = 0

            eventTitleText.setText(eventToEdit.title)
            note.setText(eventToEdit.desc)
            eventLocation.setText(eventToEdit.location)

//            eventTimeSonze.setText(eventToEdit.timezone)

            from = DateTimeUtils.createCalendarTime(originalInstanceStart)
            to = DateTimeUtils.createCalendarTime(originalInstanceEnd)

            if (eventToEdit.isAllDay) {
                val fromUtc = DateTimeUtils.createUTCCalendarTime(originalInstanceStart)
                val toUtc = DateTimeUtils.createUTCCalendarTime(originalInstanceEnd)

                from.year = fromUtc.year
                from.month = fromUtc.month
                from.dayOfMonth = fromUtc.dayOfMonth

                to.year = toUtc.year
                to.month = toUtc.month
                to.dayOfMonth = toUtc.dayOfMonth
            }

            eventTitleText.clearFocus()

            updateDateTimeUI()

            for (reminder in eventToEdit.reminders) {
                addReminder(reminder, isAllDay)
            }

            updateReminders()

        }
        else {
            // Initialize default values
            accountName.text = calendar.name
            eventTitleText.setTextColor(ColorStateList.valueOf(calendar.color.scaleColor(1.8f)))

            eventTitleLayout.background = ColorDrawable(calendar.color.invertColor().scaleColor(0.1f))
            eventTitleText.background = ColorDrawable(calendar.color.invertColor().scaleColor(0.1f))

            if (receivedSharedText.isNotEmpty()) {
                eventTitleText.setText(receivedSharedText)
            }

            window.statusBarColor = calendar.color.scaleColor(0.7f)

            // Set default date and time
            var currentTime = System.currentTimeMillis()
            currentTime -= (currentTime % 1000)  // Drop millis

            from = DateTimeUtils.createCalendarTime(currentTime)
            from.addHours(Consts.NEW_EVENT_DEFAULT_ADD_HOURS)
            from.minute = 0
            from.second = 0

            to = DateTimeUtils.createCalendarTime(from.timeInMillis)
            to.addMinutes(Consts.DEFAULT_NEW_EVENT_DURATION_MINUTES)

            DevLog.debug(LOG_TAG, "${from.timeInMillis}, ${to.timeInMillis}, $from, $to")

            updateDateTimeUI();

            addReminder(EventReminderRecord(Consts.NEW_EVENT_DEFAULT_NEW_EVENT_REMINDER), false)
            addReminder(EventReminderRecord(Consts.NEW_EVENT_DEFAULT_ALL_DAY_REMINDER), true)

//            val emailMethod = CalendarContract.Reminders.METHOD_EMAIL
//            addReminder(EventReminderRecord(Consts.NEW_EVENT_DEFAULT_NEW_EVENT_REMINDER, method=emailMethod), false)
//            addReminder(EventReminderRecord(Consts.NEW_EVENT_DEFAULT_ALL_DAY_REMINDER, method=emailMethod), true)

            updateReminders()
        }

        updateRecurrenceLabel()
    }

    fun updateRecurrenceLabel() {

        var currentStartTime = from.timeInMillis
        if (isAllDay) {
            currentStartTime = DateTimeUtils.createUTCCalendarDate(from.year, from.month, from.dayOfMonth).timeInMillis
        }


        if (rRule != "") {
            val parsed = CalendarRecurrence.tryInterpretRecurrence(
                    currentStartTime,
                    originalEvent?.timeZone ?: TimeZone.getDefault().id,
                    rRule,
                    rDate,
                    exRRule,
                    exRDate
            )
            if (parsed != null) {
                buttonRecurrence.setText(parsed.toString())
            }
            else {
                buttonRecurrence.setText(rRule)
            }
        } else {
            buttonRecurrence.setText(resources.getString(R.string.recurrence_does_not_repeat))
        }
    }

    fun updateDateTimeUI() {

        if (isAllDay) {
            timeTo.visibility = View.GONE
            timeFrom.visibility = View.GONE
        }
        else {
            timeTo.visibility = View.VISIBLE
            timeFrom.visibility = View.VISIBLE
        }

        val dateFormat =
                DateUtils.FORMAT_SHOW_DATE or
                        DateUtils.FORMAT_SHOW_YEAR or
                        DateUtils.FORMAT_SHOW_WEEKDAY or
                        DateUtils.FORMAT_ABBREV_MONTH or
                        DateUtils.FORMAT_ABBREV_WEEKDAY

        val timeFormat =
                DateUtils.FORMAT_SHOW_TIME

        if (!isAllDay) {
            dateFrom.text = DateUtils.formatDateTime(this, from.timeInMillis, dateFormat)
            timeFrom.text = DateUtils.formatDateTime(this, from.timeInMillis, timeFormat)

            dateTo.text = DateUtils.formatDateTime(this, to.timeInMillis, dateFormat)
            timeTo.text = DateUtils.formatDateTime(this, to.timeInMillis, timeFormat)
        }
        else {
            val fromClean = DateTimeUtils.createCalendarTime(from.timeInMillis)
            fromClean.hourOfDay = 0
            fromClean.minute = 0

            val toClean = DateTimeUtils.createCalendarTime(to.timeInMillis)
            toClean.hourOfDay = 0
            toClean.minute = 0

            dateFrom.text = DateUtils.formatDateTime(this, fromClean.timeInMillis, dateFormat)
            dateTo.text = DateUtils.formatDateTime(this,
                    Math.max(toClean.timeInMillis-1000L, fromClean.timeInMillis),
                    dateFormat)
        }
    }

    fun updateReminders() {

        for (reminder in reminders) {
            if (reminder.isForAllDay == isAllDay) {
                reminder.view.visibility = View.VISIBLE
            }
            else {
                reminder.view.visibility = View.GONE
            }
        }
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.add_event, menu)
        return true
    }

    override fun onBackPressed() {
        if (anyChanges) {

            AlertDialog.Builder(this)
                    .setMessage(R.string.discard_new_event)
                    .setCancelable(false)
                    .setPositiveButton(R.string.yes) {
                        _, _ ->
                        this@EditEventActivity.finish()
                    }
                    .setNegativeButton(R.string.cancel) {
                        _, _ ->
                    }
                    .create()
                    .show()
        }
        else {
            finish()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onButtonCancelClick(v: View) {
        onBackPressed()
    }

    @Suppress("UNUSED_PARAMETER")
    fun onAccountClick(v: View) {

        if (originalEvent != null)
            return // not editable anymore

        val builder = AlertDialog.Builder(this)

        val adapter = ArrayAdapter<String>(this, R.layout.simple_list_item_medium)

        val listCalendars = calendars.filter { !it.isReadOnly }.map { "${it.displayName} <${it.accountName}>" }.toList()

        adapter.addAll(listCalendars)


        builder.setCancelable(true)

        builder.setAdapter(adapter) {
            _, which ->
            if (which in 0..calendars.size-1) {

                calendar = calendars.get(which)

                persistentState.lastCalendar = calendar.calendarId

                accountName.text = calendar.name
//                eventTitleText.background = ColorDrawable(
//                        calendar.color.adjustCalendarColor(settings.darkerCalendarColors))

                window.statusBarColor = 0

                eventTitleLayout.background = ColorDrawable(calendar.color.invertColor().scaleColor(0.1f))
                eventTitleText.background = ColorDrawable(calendar.color.invertColor().scaleColor(0.1f))
                eventTitleText.setTextColor(ColorStateList.valueOf(calendar.color.scaleColor(1.8f)))

            }
        }
        builder.show()
    }

    private fun onEventUpdated(newEvent: Boolean, eventId: Long, instanceStart: Long){
        val nextReminder = calendarProvider.getNextEventReminderTime(this, eventId, instanceStart)

        if (newEvent) {
            if (nextReminder != 0L)
                Toast.makeText(
                        this,
                        resources.getString(R.string.event_was_created_reminder_at).format(dateToStr(this, nextReminder)),
                        Toast.LENGTH_LONG
                ).show()
            else
                Toast.makeText(this, R.string.event_was_created, Toast.LENGTH_LONG).show()
        }
        else {
            if (nextReminder != 0L)
                    Toast.makeText(
                            this,
                            resources.getString(R.string.event_was_updated_next_reminder).format(dateToStr(this, nextReminder)),
                            Toast.LENGTH_LONG
                    ).show()
            else
                Toast.makeText(this, getString(R.string.event_was_updated), Toast.LENGTH_LONG).show()
        }

        finish()
    }


    @Suppress("UNUSED_PARAMETER")
    fun onButtonSaveClick(v: View) {

        var startTime = from.timeInMillis
        var endTime = to.timeInMillis

        if (isAllDay) {
            startTime = DateTimeUtils.createUTCCalendarDate(from.year, from.month, from.dayOfMonth).timeInMillis
            endTime = DateTimeUtils.createUTCCalendarDate(to.year, to.month, to.dayOfMonth).timeInMillis
        }

        val remindersToAdd = reminders.filter { it.isForAllDay == isAllDay }.map { it.reminder }.toList()

        val oldRRule = RRule.tryParse(originalEvent?.rRule ?: "")
        val newRRule = RRule.tryParse(rRule)

        val isRecurring = newRRule != null && !newRRule.isEmpty()

        val details = CalendarEventDetails(
                title = eventTitleText.text.toString(),
                desc = note.text.toString(),
                location = eventLocation.text.toString(),
                timezone = originalEvent?.timezone ?: calendar.timeZone,
                startTime = startTime,
                endTime = if (isRecurring) 0L else endTime,
                duration = if (isRecurring) (endTime - startTime) else null,
                isAllDay = isAllDay,
                rRule = rRule,
                rDate = rDate,
                exRRule = exRRule,
                exRDate = exRDate,
                color = originalEvent?.color ?: 0,
                reminders = remindersToAdd,
                lastDate = newRRule?.until?.value
        )

        val eventToEdit = originalEvent

        if (eventToEdit == null) {
            val eventId = editor.createEvent(this, calendar.calendarId, calendar.owner, details)
            if (eventId != -1L)
                onEventUpdated(true, eventId, startTime)
            else
                Toast.makeText(this, R.string.new_event_failed_to_create_event, Toast.LENGTH_LONG).show()
        }
        else if (oldRRule == null || oldRRule.isEmpty() || // non-recurrent (or non-recurrent becoming recurrent) or ..
                originalInstanceStart == eventToEdit.startTime // recurrent, but we are looking at the very first instance
        ) {
            val success = editor.updateEvent(this, eventToEdit, details)
            if (success)
                onEventUpdated(false, eventToEdit.eventId, details.startTime)
            else
                Toast.makeText(this, R.string.failed_to_update_event_details, Toast.LENGTH_LONG).show()
        }
        else {
            // Updating the repeating event, we are doing that by creating the new event from today, and
            // then cutting the recurrence of the old event by adding UNTIL= to the RRULE
            if (newRRule == null) {
                Toast.makeText(this, R.string.failed_to_parse_new_rrule, Toast.LENGTH_LONG).show()
                return
            }

            // create the new recurrence first - from today onward
            val eventId = editor.createEvent(this, calendar.calendarId, calendar.owner, details)
            if (eventId != -1L) {
                // update the old recurrence now, making it stop just before the original instance start
                val lastDate = originalInstanceStart - 1 * 1000L
                oldRRule.until = RRuleVal.UNTIL(lastDate)
                if (editor.updateEvent(this, eventToEdit,
                                eventToEdit.details.copy(rRule = oldRRule.serialize(), lastDate = lastDate)))
                    onEventUpdated(false, eventId, details.startTime)
                else
                    Toast.makeText(this, R.string.failed_to_update_old_recurrence, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, R.string.failed_to_update_recurrence, Toast.LENGTH_LONG).show()
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun onSwitchAllDayClick(v: View) {
        isAllDay = switchAllDay.isChecked

        if (isAllDay) {
            to = DateTimeUtils.createCalendarTime(from.timeInMillis)
            to.addDays(1)
        }
        else {
            to = DateTimeUtils.createCalendarTime(from.timeInMillis)
            to.addMinutes(Consts.DEFAULT_NEW_EVENT_DURATION_MINUTES)
        }

        updateDateTimeUI()
        updateReminders()
    }

    @Suppress("UNUSED_PARAMETER")
    fun onDateFromClick(v: View) {

        val durationMinutes = (to.timeInMillis - from.timeInMillis) / Consts.MINUTE_IN_MILLISECONDS

        val dialog = DatePickerDialog(
                this,
                {
                    _, year, month, day ->

                    from.year = year
                    from.month = month
                    from.dayOfMonth = day

                    to = DateTimeUtils.createCalendarTime(from.timeInMillis)
                    to.addMinutes(durationMinutes.toInt())

                    updateDateTimeUI()

                },
                from.year,
                from.month,
                from.dayOfMonth
        )

        val firstDayOfWeek = Settings(this).firstDayOfWeek
        if (firstDayOfWeek != -1) {
            dialog.datePicker.firstDayOfWeek = firstDayOfWeek
        }

        dialog.show()
        //builder.setIcon(R.mipmap.ic_launcher_new)
    }

    @Suppress("UNUSED_PARAMETER")
    fun onTimeFromClick(v: View) {

        val durationMinutes = (to.timeInMillis - from.timeInMillis) / Consts.MINUTE_IN_MILLISECONDS

        val dialog = TimePickerDialog(
                this,
                {
                    _, hour, min ->

                    from.hourOfDay = hour
                    from.minute = min

                    to = DateTimeUtils.createCalendarTime(from.timeInMillis)
                    to.addMinutes(durationMinutes.toInt())

                    updateDateTimeUI()
                },
                from.hourOfDay,
                from.minute,
                android.text.format.DateFormat.is24HourFormat(this)
        )

        dialog.show()
    }

    @Suppress("UNUSED_PARAMETER")
    fun onDateToClick(v: View) {

        val durationMinutes = (to.timeInMillis - from.timeInMillis) / Consts.MINUTE_IN_MILLISECONDS

        val dialog = DatePickerDialog(
                this,
                {
                    _, year, month, day ->

                    to.year = year
                    to.month = month
                    to.dayOfMonth = day

                    if (to.before(from)) {
                        Toast.makeText(this, getString(R.string.end_time_before_start_time), Toast.LENGTH_LONG).show()
                        to = DateTimeUtils.createCalendarTime(from.timeInMillis)
                        to.addMinutes(durationMinutes.toInt())
                    }

                    updateDateTimeUI()

                },
                to.year,
                to.month,
                to.dayOfMonth
        )
        val firstDayOfWeek = Settings(this).firstDayOfWeek
        if (firstDayOfWeek != -1) {
            dialog.datePicker.firstDayOfWeek = firstDayOfWeek
        }

        dialog.show()
    }

    @Suppress("UNUSED_PARAMETER")
    fun onTimeToClick(v: View) {

        val durationMinutes = (to.timeInMillis - from.timeInMillis) / Consts.MINUTE_IN_MILLISECONDS

        val dialog = TimePickerDialog(
                this,
                {
                    _, hour, min ->

                    to.hourOfDay = hour
                    to.minute = min

                    if (to.before(from)) {
                        Toast.makeText(this, getString(R.string.end_time_before_start_time), Toast.LENGTH_LONG).show()
                        to = DateTimeUtils.createCalendarTime(from.timeInMillis)
                        to.addMinutes(durationMinutes.toInt())
                    }

                    updateDateTimeUI()
                },
                to.hourOfDay,
                to.minute,
                android.text.format.DateFormat.is24HourFormat(this)
        )

        dialog.show()
    }

    fun onButtonRecurrence(v: View) {
        val popup = PopupMenu(this, v)
        val inflater = popup.menuInflater
        inflater.inflate(R.menu.menu_recurrence_popup, popup.menu)

        val timeZone = originalEvent?.timeZone ?: TimeZone.getDefault().id
        val weekStart = WeekDay.fromJavaCalendarDayOfWeek(Settings(this).firstDayOfWeek)

        if (originalEvent != null) {
            val item = popup.menu.findItem(R.id.repeats_does_not)
            item.isEnabled = false
            item.isVisible = false
        }

        var currentStartTime = from.timeInMillis

        if (isAllDay) {
            currentStartTime = DateTimeUtils.createUTCCalendarDate(from.year, from.month, from.dayOfMonth).timeInMillis
        }

        popup.setOnMenuItemClickListener {
            item ->
            val ret = when (item.itemId) {
                R.id.repeats_does_not -> {
                    if (originalEvent == null) {
                        rRule = ""
                        rDate = ""
                    }
                    true
                }
                R.id.repeats_daily -> {
                    rRule = CalendarRecurrence.Daily
                            .createDefaultForDate(currentStartTime, timeZone, weekStart)
                            .serialize().serialize()
                    rDate = ""
                    true
                }
                R.id.repeats_weekly -> {
                    rRule = CalendarRecurrence.Weekly
                            .createDefaultForDate(currentStartTime, timeZone, weekStart)
                            .serialize().serialize()
                    rDate = ""
                    true
                }
                R.id.repeats_monthly -> {
                    rRule = CalendarRecurrence.Monthly
                            .createDefaultForDate(currentStartTime, timeZone, weekStart)
                            .serialize().serialize()
                    rDate = ""
                    true
                }
                R.id.repeats_yearly -> {
                    rRule = CalendarRecurrence.Yearly
                            .createDefaultForDate(currentStartTime, timeZone, weekStart)
                            .serialize().serialize()
                    rDate = ""
                    true
                }
                R.id.repeats_custom -> {
                    showCustomRecurrenceDialog()
                    true
                }
                else -> false
            }

            updateRecurrenceLabel()

            ret
        }

        popup.show()
    }

    fun showCustomRecurrenceDialog() {

        var currentStartTime = from.timeInMillis
        var currentEndTime = to.timeInMillis

        if (isAllDay) {
            currentStartTime = DateTimeUtils.createUTCCalendarDate(from.year, from.month, from.dayOfMonth).timeInMillis
            currentEndTime = DateTimeUtils.createUTCCalendarDate(to.year, to.month, to.dayOfMonth).timeInMillis
        }

        val tz = originalEvent?.timeZone ?: TimeZone.getDefault().id
        var currentRecurrence = CalendarRecurrence.tryInterpretRecurrence(
                currentStartTime,
                tz,
                rRule,
                rDate,
                exRRule,
                exRDate
        )

        if (currentRecurrence == null)
            currentRecurrence = CalendarRecurrence.Weekly.createDefaultForDate(
                    currentStartTime, tz, WeekDay.fromJavaCalendarDayOfWeek(settings.firstDayOfWeek))

        recurrenceView.setViewModel(RecurrenceViewModel(
                currentStartTime,
                currentEndTime,
                tz,
                currentRecurrence
        ))

        layoutMain.visibility = View.GONE
        layoutRecurrence.visibility = View.VISIBLE
    }

    private fun onCustomRecurrenceComplete(newRecurrence: CalendarRecurrence?, startTimeAdjust: Long) {
        layoutMain.visibility = View.VISIBLE
        layoutRecurrence.visibility = View.GONE
        if (newRecurrence != null) {
            from.timeInMillis += startTimeAdjust
            to.timeInMillis += startTimeAdjust

            rRule = newRecurrence.serialize().serialize()
            rDate = ""
        }

        updateRecurrenceLabel()
    }

    @Suppress("UNUSED_PARAMETER")
    fun onNotificationClick(v: View) {

        val wrapper = reminders.find { it.view == v }

        if (wrapper != null) {
            if (wrapper.isForAllDay)
                showAddReminderListAllDayDialog(wrapper.reminder, wrapper.view)
            else
                showAddReminderListDialog(wrapper.reminder, wrapper.view)
        }
    }

    fun showAddReminderCustomDialog(currentReminder: EventReminderRecord, existingReminderView: View?) {

        val dialogView = this.layoutInflater.inflate(R.layout.dialog_add_event_notification, null);

        val timeIntervalPicker = TimeIntervalPickerController(dialogView, null,
                Consts.NEW_EVENT_MAX_REMINDER_MILLISECONDS_BEFORE, false)
        timeIntervalPicker.intervalMilliseconds = currentReminder.millisecondsBefore

        val isEmailCb = dialogView.findViewById<CheckBox?>(R.id.checkbox_as_email)

        val builder = AlertDialog.Builder(this)

        builder.setView(dialogView)

        builder.setPositiveButton(android.R.string.ok) {
            _: DialogInterface?, _: Int ->

            var intervalMilliseconds = timeIntervalPicker.intervalMilliseconds
            val isEmail = isEmailCb?.isChecked ?: false

            if (intervalMilliseconds > Consts.NEW_EVENT_MAX_REMINDER_MILLISECONDS_BEFORE) {
                intervalMilliseconds = Consts.NEW_EVENT_MAX_REMINDER_MILLISECONDS_BEFORE
                Toast.makeText(this, R.string.new_event_max_reminder_is_28_days, Toast.LENGTH_LONG).show()
            }

            val reminder = EventReminderRecord(
                    intervalMilliseconds,
                    if (isEmail) CalendarContract.Reminders.METHOD_EMAIL
                    else CalendarContract.Reminders.METHOD_DEFAULT
            )

            if (existingReminderView != null)
                modifyReminder(existingReminderView, reminder)
            else
                addReminder(reminder, isForAllDay = false)
        }

        if (existingReminderView != null) {
            builder.setNegativeButton(R.string.remove_reminder) {
                _: DialogInterface?, _: Int ->
                removeReminder(existingReminderView)
            }
        }
        else {
            builder.setNegativeButton(android.R.string.cancel) {
                _: DialogInterface?, _: Int ->
            }
        }

        builder.create().show()
    }

    fun showAddReminderListDialog(currentReminder: EventReminderRecord, existingReminderView: View?) {

        if (currentReminder.method != CalendarContract.Reminders.METHOD_DEFAULT)
            return showAddReminderCustomDialog(currentReminder, existingReminderView)

        val intervalNames: Array<String> = this.resources.getStringArray(R.array.default_reminder_intervals)
        val intervalValues = this.resources.getIntArray(R.array.default_reminder_intervals_milliseconds_values)

        if (intervalValues.find { it.toLong() == currentReminder.millisecondsBefore } == null) {
            // reminder is not one of standard ones - we have to show custom idalog
            return showAddReminderCustomDialog(currentReminder, existingReminderView)
        }

        val builder = AlertDialog.Builder(this)

        val adapter = ArrayAdapter<String>(this, R.layout.simple_list_item_medium)

        adapter.addAll(intervalNames.toMutableList())

        builder.setCancelable(true)

        builder.setAdapter(adapter) {
            _, which ->
            if (which in 0..intervalValues.size-1) {

                val intervalMillis = intervalValues[which].toLong()
                if (intervalMillis != -1L) {
                    if (existingReminderView != null)
                        modifyReminder(existingReminderView, EventReminderRecord(intervalMillis))
                    else
                        addReminder(EventReminderRecord(intervalMillis), isForAllDay = false)
                } else {
                    showAddReminderCustomDialog(currentReminder, existingReminderView)
                }
            }
        }

        if (existingReminderView != null) {
            builder.setNegativeButton(R.string.remove_reminder) {
                _: DialogInterface?, _: Int ->
                removeReminder(existingReminderView)
            }
        }
        else {
            builder.setNegativeButton(android.R.string.cancel) {
                _: DialogInterface?, _: Int ->
            }
        }

        builder.show()
    }

    fun showAddReminderCustomAllDayDialog(currentReminder: EventReminderRecord, existingReminderView: View?) {

        val dialogView = this.layoutInflater.inflate(R.layout.dialog_add_event_allday_notification, null);

        val numberPicker = dialogView.findViewById<NumberPicker>(R.id.number_picker_days_before)
        val timePicker = dialogView.findViewById<TimePicker>(R.id.time_picker_notification_time_of_day)
        val isEmailCb = dialogView.findViewById<CheckBox>(R.id.checkbox_as_email)

        numberPicker.minValue = 0
        numberPicker.maxValue = Consts.NEW_EVENT_MAX_ALL_DAY_REMINDER_DAYS_BEFORE
        numberPicker.value = currentReminder.allDayDaysBefore

        timePicker.setIs24HourView(android.text.format.DateFormat.is24HourFormat(this))

        val (hr, min) = currentReminder.allDayHourOfDayAndMinute

        timePicker.hour = hr
        timePicker.minute = min


        val builder = AlertDialog.Builder(this)

        builder.setView(dialogView)

        builder.setPositiveButton(android.R.string.ok) {
            _: DialogInterface?, _: Int ->

            numberPicker.clearFocus()
            timePicker.clearFocus()

            val daysBefore = numberPicker.value
            val pickerHr = timePicker.hour
            val pickerMin = timePicker.minute

            val daysInMilliseconds = daysBefore * Consts.DAY_IN_MILLISECONDS
            val hrMinInMilliseconds = pickerHr * Consts.HOUR_IN_MILLISECONDS + pickerMin * Consts.MINUTE_IN_MILLISECONDS
            val reminderTimeMilliseconds = daysInMilliseconds - hrMinInMilliseconds

            val isEmail = isEmailCb.isChecked

            val reminder = EventReminderRecord(
                    reminderTimeMilliseconds,
                    if (isEmail) CalendarContract.Reminders.METHOD_EMAIL
                    else CalendarContract.Reminders.METHOD_DEFAULT
            )

            if (existingReminderView != null)
                modifyReminder(existingReminderView, reminder)
            else
                addReminder(reminder, isForAllDay = true)
        }

        if (existingReminderView != null) {
            builder.setNegativeButton(R.string.remove_reminder) {
                _: DialogInterface?, _: Int ->
                removeReminder(existingReminderView)
            }
        }
        else {
            builder.setNegativeButton(android.R.string.cancel) {
                _: DialogInterface?, _: Int ->
            }
        }

        builder.create().show()
    }

    fun showAddReminderListAllDayDialog(currentReminder: EventReminderRecord, existingReminderView: View?) {

        if (currentReminder.method != CalendarContract.Reminders.METHOD_DEFAULT)
            return showAddReminderCustomAllDayDialog(currentReminder, existingReminderView)

        val reminderNames: Array<String> = this.resources.getStringArray(R.array.default_reminder_intervals_all_day)
        val reminderValues = this.resources.getIntArray(R.array.default_reminder_intervals_all_day_seconds_values)

        val enterManuallyValue = -2147483648

        if (reminderValues.find { it.toLong() == currentReminder.millisecondsBefore / 1000L } == null) {
            // reminder is not one of standard ones - we have to show custom idalog
            return showAddReminderCustomAllDayDialog(currentReminder, existingReminderView)
        }

        val builder = AlertDialog.Builder(this)

        val adapter = ArrayAdapter<String>(this, R.layout.simple_list_item_medium)

        adapter.addAll(reminderNames.toMutableList())

        builder.setCancelable(true)

        builder.setAdapter(adapter) {
            _, which ->
            if (which in 0..reminderValues.size-1) {

                val reminderSeconds = reminderValues[which]
                if (reminderSeconds != enterManuallyValue) {

                    val reminderTimeMillis = reminderSeconds.toLong() * 1000L

                    if (existingReminderView != null)
                        modifyReminder(existingReminderView, EventReminderRecord(reminderTimeMillis))
                    else
                        addReminder(EventReminderRecord(reminderTimeMillis), isForAllDay = true)
                } else {
                    showAddReminderCustomAllDayDialog(currentReminder, existingReminderView)
                }
            }
        }

        if (existingReminderView != null) {
            builder.setNegativeButton(R.string.remove_reminder) {
                _: DialogInterface?, _: Int ->
                removeReminder(existingReminderView)
            }
        }
        else {
            builder.setNegativeButton(android.R.string.cancel) {
                _: DialogInterface?, _: Int ->
            }
        }

        builder.show()
    }


    @Suppress("UNUSED_PARAMETER")
    fun onAddNotificationClick(v: View) {
        if (!isAllDay) {
            showAddReminderListDialog(EventReminderRecord(Consts.NEW_EVENT_DEFAULT_NEW_EVENT_REMINDER), null)
        }
        else {
            showAddReminderListAllDayDialog(EventReminderRecord(Consts.NEW_EVENT_DEFAULT_ALL_DAY_REMINDER), null)
        }
    }

    private fun removeReminder(existingReminderView: View) {

        val wrapper = reminders.find { it.view == existingReminderView }
        if (wrapper != null) {
            reminders.remove(wrapper)
            notificationsLayout.removeView(wrapper.view)
        }
    }

    private fun modifyReminder(existingReminderView: View, newReminder: EventReminderRecord) {

        if (reminders.find { it.reminder == newReminder && it.view != existingReminderView} != null) {
            // we have another reminder with the same params in the list -- remove this one (cruel!!)
            removeReminder(existingReminderView)
            return
        }

        val wrapper = reminders.find { it.view == existingReminderView }
        if (wrapper != null) {
            wrapper.reminder = newReminder
            wrapper.view.text = newReminder.toLocalizedString(this, isAllDay)
        }
    }

    private fun addReminder(reminder: EventReminderRecord, isForAllDay: Boolean) {

        if (reminders.find { it.reminder == reminder} != null) {
            DevLog.warn(LOG_TAG, "Not adding reminder: already in the list")
            return
        }

        val textView = TextView(this)
        textView.text = reminder.toLocalizedString(this, isForAllDay)

        textView.setOnClickListener (this::onNotificationClick)

        textView.setTextAppearance(android.R.style.TextAppearance_Medium)

        textView.setTextColor(notificationPrototype.textColors)

        textView.setPaddingRelative(
                notificationPrototype.paddingStart,
                notificationPrototype.paddingTop,
                notificationPrototype.paddingEnd,
                notificationPrototype.paddingBottom)

        textView.isClickable = true
        textView.background = notificationPrototype.background

        val lp = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        notificationsLayout.addView(textView, lp)

        reminders.add(ReminderWrapper(textView, reminder, isForAllDay))
    }

    companion object {
        private const val LOG_TAG = "EditEventActivity"
        const val EVENT_ID = "event_id"
        const val INSTANCE_START = "instance_start"
        const val INSTANCE_END = "instance_end"
    }
}
