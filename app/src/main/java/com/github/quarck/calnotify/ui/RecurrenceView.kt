package com.github.quarck.calnotify.ui

import android.app.DatePickerDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.utils.*
import com.github.quarck.calnotify.utils.logs.DevLog
import java.util.*
import kotlin.text.toIntOrNull

enum class RecurrenceType {
    Daily,
    Weekly,
    MonthlyByMonthDay,
    MonthlyByWeekDay,
    MonthlyByLastWeekDay,
    Yearly
}

enum class RecurrenceLimit {
    NoLimit,
    Until,
    Count
}

class RecurrenceViewModel(
        val eventStart: Long,
        val eventEnd: Long,
        val eventTimeZone: String,
        val originalRecurrence: CalendarRecurrence
) {

    val monthDay: Int
    val monthByWeekDay: NthWeekDay
    val monthByLastWeekDay: NthWeekDay

    var timeUntil = eventEnd + 30 * 24 * 3600 * 1000L + 60L // must cap to end of day...
    var numRepetitions = 10
    var weekDays = WeekDays()
    var interval = 1

    var lastMonthlyRecurrenceType: RecurrenceType? = null

    var timeZone = TimeZone.getTimeZone(eventTimeZone)

    private var _recurrenceType: RecurrenceType = RecurrenceType.Daily // just some default
    var recurrenceType: RecurrenceType
        get() = _recurrenceType
        set(value) {
            _recurrenceType = value
            if (value == RecurrenceType.MonthlyByMonthDay ||
                    value == RecurrenceType.MonthlyByWeekDay ||
                    value == RecurrenceType.MonthlyByLastWeekDay) {
                lastMonthlyRecurrenceType = value
            }
        }
    var limitType: RecurrenceLimit

    init {
        monthDay = CalendarRecurrence.Monthly.getDefaultValuesFor(eventStart, timeZone, originalRecurrence.weekStart ?: WeekDay.MO)

        val byWeekDay = CalendarRecurrence.MonthlyByWeekDay.getDefaultValuesFor(eventStart, timeZone, originalRecurrence.weekStart ?: WeekDay.MO, false)
        monthByWeekDay = NthWeekDay(byWeekDay.first, byWeekDay.second)

        val byLastWeekDay = CalendarRecurrence.MonthlyByWeekDay.getDefaultValuesFor(eventStart, timeZone, originalRecurrence.weekStart ?: WeekDay.MO, true)
        monthByLastWeekDay = NthWeekDay(byLastWeekDay.first, byLastWeekDay.second)

        val originalLimit = originalRecurrence.limit
        when (originalLimit) {
            is CalendarRecurrenceLimit.Until -> {
                timeUntil = originalLimit.until
                limitType = RecurrenceLimit.Until
            }
            is CalendarRecurrenceLimit.Count -> {
                numRepetitions = originalLimit.count
                limitType = RecurrenceLimit.Count
            }
            is CalendarRecurrenceLimit.NoLimit -> {
                limitType = RecurrenceLimit.NoLimit
            }
        }

        var currentWeekDays: WeekDays? = null

        when (originalRecurrence) {
            is CalendarRecurrence.Daily -> {
                recurrenceType = RecurrenceType.Daily
            }
            is CalendarRecurrence.Weekly -> {
                recurrenceType = RecurrenceType.Weekly
                currentWeekDays = originalRecurrence.weekDays
            }
            is CalendarRecurrence.Monthly -> {
                recurrenceType = RecurrenceType.MonthlyByMonthDay
            }
            is CalendarRecurrence.MonthlyByWeekDay -> {
                recurrenceType = if (originalRecurrence.weekDayNum > 0)
                    RecurrenceType.MonthlyByWeekDay
                else
                    RecurrenceType.MonthlyByLastWeekDay
            }
            is CalendarRecurrence.Yearly -> {
                recurrenceType = RecurrenceType.Yearly
            }
        }

        weekDays = currentWeekDays ?: CalendarRecurrence.Weekly.getDefaultValuesFor(eventStart, timeZone, originalRecurrence.weekStart ?: WeekDay.MO)
    }
}

class RecurrenceView(
        val context: Context,
        val view: View,
        eventStart: Long,
        eventEnd: Long,
        eventTimeZone: String,
        originalRecurrence: CalendarRecurrence,
) {
    private val doneButton: TextView = view.findViewById(R.id.recurrence_dialog_button_done)

    private val repeatsEveryText: EditText = view.findViewById(R.id.recurrence_dialog_repeats_every_number)
    private val repeatsEveryUnitButton: Button = view.findViewById(R.id.recurrence_dialog_repeats_button_every_unit)

    private val weekDayLabels = arrayOf<TextView>(
            view.findViewById(R.id.recurrence_dialog_monday_text),
            view.findViewById(R.id.recurrence_dialog_tuesday_text),
            view.findViewById(R.id.recurrence_dialog_wednesday_text),
            view.findViewById(R.id.recurrence_dialog_thursday_text),
            view.findViewById(R.id.recurrence_dialog_friday_text),
            view.findViewById(R.id.recurrence_dialog_saturday_text),
            view.findViewById(R.id.recurrence_dialog_sunday_text)
    )
    private val vm = RecurrenceViewModel(eventStart, eventEnd, eventTimeZone, originalRecurrence)

    private val weeklyConfigLayout: LinearLayout = view.findViewById(R.id.recurrence_dialog_weekly_config)
    private val monthlyConfigLayout: LinearLayout = view.findViewById(R.id.recurrence_dialog_monthly_config)
    private val radioOnMonthDay: RadioButton = view.findViewById(R.id.recurrence_dialog_month_onmonthday)
    private val radioOnWeekDay: RadioButton = view.findViewById(R.id.recurrence_dialog_month_onweekday)
    private val radioOnLastWeekDay: RadioButton = view.findViewById(R.id.recurrence_dialog_month_onlastweekday)

    private val radioEventNoEnd: RadioButton = view.findViewById(R.id.recurrence_dialog_radio_no_end)
    private val radioEndByDate: RadioButton = view.findViewById(R.id.recurrence_dialog_radio_end_on)
    private val radioEndByCount: RadioButton = view.findViewById(R.id.recurrence_dialog_radio_end_after_n)
    private val endOnButton: Button = view.findViewById(R.id.recurrence_dialog_radio_end_on_button)
    private val endAfterCount: EditText = view.findViewById(R.id.recurrence_dialog_radio_end_after_n_edit)

    private var lastRecurrence: RecurrenceType? = null
    private var lastLimit: RecurrenceLimit? = null

    private val inactiveTextColors = view.findViewById<TextView>(R.id.recurrence_dialog_monday_text).textColors
    private val activeTextColors =  view.findViewById<TextView>(R.id.recurrence_dialog_moonday_text).textColors

    private var onCompleteFn: ((CalendarRecurrence)->Unit)? = null

    init {
        radioOnMonthDay.setText(context.getString(R.string.on_day_fmt).format(vm.monthDay))
        radioOnWeekDay.setText(context.getString(R.string.on_fmt).format(vm.monthByWeekDay.toString()))
        radioOnLastWeekDay.setText(context.getString(R.string.on_fmt).format(vm.monthByLastWeekDay.toString()))

        val intervalAsStr = originalRecurrence.interval.toString()
        repeatsEveryText.setText(intervalAsStr)
        repeatsEveryText.setSelection(intervalAsStr.length) // focus it at the end

        repeatsEveryText.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                vm.interval = repeatsEveryText.text.toString().toIntOrNull() ?: 1
            }
        })

        endAfterCount.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                vm.numRepetitions = endAfterCount.text.toString().toIntOrNull() ?: 10
            }
        })

        endOnButton.setOnClickListener(this::showUntilDateDialog)

        radioEventNoEnd.setOnClickListener{
            vm.limitType = RecurrenceLimit.NoLimit
            updateView()
        }
        radioEndByDate.setOnClickListener{
            vm.limitType = RecurrenceLimit.Until
            updateView()
        }
        radioEndByCount.setOnClickListener{
            vm.limitType = RecurrenceLimit.Count
            updateView()
        }

        repeatsEveryUnitButton.setOnClickListener{
            showRecurrenceUnitPopupMenu(it)
        }

        radioOnMonthDay.setOnClickListener {
            vm.recurrenceType = RecurrenceType.MonthlyByMonthDay
            updateView()
        }
        radioOnWeekDay.setOnClickListener {
            vm.recurrenceType = RecurrenceType.MonthlyByWeekDay
            updateView()
        }
        radioOnLastWeekDay.setOnClickListener {
            vm.recurrenceType = RecurrenceType.MonthlyByLastWeekDay
            updateView()
        }

        for (i in 0 until 7) {
            weekDayLabels[i].setOnClickListener {
                vm.weekDays[i] = !vm.weekDays[i]
                updateView()
            }
        }

        updateView()

        doneButton.setOnClickListener(this::onDoneButton)
    }

    private fun updateView() {
        if (lastLimit != vm.limitType) {
            lastLimit = vm.limitType
            when (lastLimit) {
                RecurrenceLimit.NoLimit -> {
                    radioEventNoEnd.isChecked = true
                    radioEndByDate.isChecked = false
                    radioEndByCount.isChecked = false
                    endOnButton.isEnabled = false
                    endAfterCount.isEnabled = false
                }
                RecurrenceLimit.Count -> {
                    radioEndByCount.isChecked = true
                    radioEventNoEnd.isChecked = false
                    radioEndByDate.isChecked = false
                    endOnButton.isEnabled = false
                    endAfterCount.isEnabled = true
                }
                RecurrenceLimit.Until -> {
                    radioEndByDate.isChecked = true
                    radioEventNoEnd.isChecked = false
                    radioEndByCount.isChecked = false
                    endOnButton.isEnabled = true
                    endAfterCount.isEnabled = false
                }
            }
        }

        endOnButton.text = DateUtils.formatDateTime(
                context, vm.timeUntil,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL or DateUtils.FORMAT_SHOW_YEAR
        )

        if (lastRecurrence != vm.recurrenceType) {
            lastRecurrence = vm.recurrenceType
            when (lastRecurrence) {
                RecurrenceType.Daily -> {
                    weeklyConfigLayout.visibility = View.GONE
                    monthlyConfigLayout.visibility = View.GONE
                    repeatsEveryUnitButton.text = context.resources.getString(R.string.unit_day)
                }
                RecurrenceType.Weekly -> {
                    weeklyConfigLayout.visibility = View.VISIBLE
                    monthlyConfigLayout.visibility = View.GONE
                    repeatsEveryUnitButton.text = context.resources.getString(R.string.unit_week)
                }
                RecurrenceType.MonthlyByMonthDay, RecurrenceType.MonthlyByWeekDay, RecurrenceType.MonthlyByLastWeekDay -> {
                    weeklyConfigLayout.visibility = View.GONE
                    monthlyConfigLayout.visibility = View.VISIBLE
                    repeatsEveryUnitButton.text = context.resources.getString(R.string.unit_month)

                    radioOnMonthDay.isChecked = vm.recurrenceType == RecurrenceType.MonthlyByMonthDay
                    radioOnWeekDay.isChecked = vm.recurrenceType == RecurrenceType.MonthlyByWeekDay
                    radioOnLastWeekDay.isChecked = vm.recurrenceType == RecurrenceType.MonthlyByLastWeekDay
                }
                RecurrenceType.Yearly -> {
                    weeklyConfigLayout.visibility = View.GONE
                    monthlyConfigLayout.visibility = View.GONE
                    repeatsEveryUnitButton.text = context.resources.getString(R.string.unit_year)
                }
            }
        }

        if (lastRecurrence == RecurrenceType.Weekly) {
            for (i in 0 until 7) {
                weekDayLabels[i].setTextColor(if (vm.weekDays[i]) activeTextColors else inactiveTextColors)
            }
        }
    }

    private fun showUntilDateDialog(v: View) {

        val currentUntil = Calendar.getInstance(vm.timeZone)
        currentUntil.timeInMillis = vm.timeUntil

        val dialog = DatePickerDialog(
                context,
                {
                    _, year, month, day ->

                    val ret = Calendar.getInstance(vm.timeZone)
                    ret.year = year
                    ret.month = month
                    ret.dayOfMonth = day
                    ret.hourOfDay = 23
                    ret.minute = 59
                    ret.second = 59
                    ret.millisecond = 0
                    vm.timeUntil = ret.timeInMillis

                    updateView()
                },
                currentUntil.year,
                currentUntil.month,
                currentUntil.dayOfMonth
        )

        val firstDayOfWeek = Settings(context).firstDayOfWeek
        if (firstDayOfWeek != -1) {
            dialog.datePicker.firstDayOfWeek = firstDayOfWeek
        }

        dialog.show()
    }

    private fun showRecurrenceUnitPopupMenu(v: View) {
        val popup = PopupMenu(context, v)
        val inflater = popup.menuInflater
        inflater.inflate(R.menu.recurrence_unit_menu, popup.menu)

        popup.setOnMenuItemClickListener {
            item ->
            val ret = when (item.itemId) {
                R.id.menu_recurrence_day -> {
                    vm.recurrenceType = RecurrenceType.Daily
                    true
                }
                R.id.menu_recurrence_week -> {
                    vm.recurrenceType = RecurrenceType.Weekly
                    true
                }
                R.id.menu_recurrence_month -> {
                    vm.recurrenceType = vm.lastMonthlyRecurrenceType ?: RecurrenceType.MonthlyByMonthDay
                    true
                }
                R.id.menu_recurrence_year -> {
                    vm.recurrenceType = RecurrenceType.Yearly
                    true
                }
                else -> false
            }
            updateView()
            ret
        }

        popup.show()
    }

    fun onComplete(fn: (CalendarRecurrence)->Unit) {
        onCompleteFn = fn
    }

    private fun onDoneButton(it: View) {
        val recurrence = when (vm.recurrenceType) {
            RecurrenceType.Daily ->
                CalendarRecurrence.Daily
                        .createDefaultForDate(vm.eventStart, vm.eventTimeZone, vm.originalRecurrence.weekStart ?: WeekDay.MO)

            RecurrenceType.Weekly ->
                CalendarRecurrence.Weekly
                        .createDefaultForDate(vm.eventStart, vm.eventTimeZone, vm.originalRecurrence.weekStart ?: WeekDay.MO)
                        .apply {
                            this.weekDays = if (vm.weekDays.any()) vm.weekDays else null
                        }

            RecurrenceType.MonthlyByMonthDay ->
                CalendarRecurrence.Monthly
                        .createDefaultForDate(vm.eventStart, vm.eventTimeZone, vm.originalRecurrence.weekStart ?: WeekDay.MO)
                        .apply {
                            this.monthDay = vm.monthDay
                        }

            RecurrenceType.MonthlyByWeekDay ->
                CalendarRecurrence.MonthlyByWeekDay
                        .createDefaultForDate(vm.eventStart, vm.eventTimeZone, vm.originalRecurrence.weekStart ?: WeekDay.MO, false)
                        .apply {
                            this.weekDay = vm.monthByWeekDay.weekDay
                            this.weekDayNum = vm.monthByWeekDay.n ?: 1
                        }

            RecurrenceType.MonthlyByLastWeekDay ->
                CalendarRecurrence.MonthlyByWeekDay
                        .createDefaultForDate(vm.eventStart, vm.eventTimeZone, vm.originalRecurrence.weekStart ?: WeekDay.MO, true)
                        .apply {
                            this.weekDay = vm.monthByLastWeekDay.weekDay
                            this.weekDayNum = vm.monthByLastWeekDay.n ?: 1
                        }

            RecurrenceType.Yearly ->
                CalendarRecurrence.Yearly
                        .createDefaultForDate(vm.eventStart, vm.eventTimeZone, vm.originalRecurrence.weekStart ?: WeekDay.MO)
        }

        recurrence.interval = vm.interval

        recurrence.limit = when (vm.limitType) {
            RecurrenceLimit.NoLimit ->
                CalendarRecurrenceLimit.NoLimit()

            RecurrenceLimit.Until ->
                CalendarRecurrenceLimit.Until(vm.timeUntil)

            RecurrenceLimit.Count ->
                CalendarRecurrenceLimit.Count(vm.numRepetitions)
        }

        onCompleteFn?.invoke(recurrence)
    }
}

// Corner cases:
// Recurrence weekly has no current day ->> move start day to match
