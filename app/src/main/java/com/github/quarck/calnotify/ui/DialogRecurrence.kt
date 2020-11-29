package com.github.quarck.calnotify.ui

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.view.View
import android.widget.*
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.utils.logs.DevLog
import java.util.*

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

class DialogRecurrenceViewModel(
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
        val timeZone = TimeZone.getTimeZone(eventTimeZone)

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

class DialogRecurrence(
        val context: Context,
        val view: View,
        eventStart: Long,
        eventEnd: Long,
        eventTimeZone: String,
        originalRecurrence: CalendarRecurrence,
) {

    private val dialog: AlertDialog

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

    private val vm = DialogRecurrenceViewModel(eventStart, eventEnd, eventTimeZone, originalRecurrence)

    init {
        val builder = AlertDialog.Builder(context)
        builder.setView(view)
        builder.setNegativeButton(android.R.string.cancel) {
            _: DialogInterface?, _: Int ->
        }

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
                DevLog.info("___DIALOG___", "$s: ${vm.interval}")
            }
        });

        radioEventNoEnd.setOnClickListener{
            vm.limitType = RecurrenceLimit.NoLimit
            onVmUpdated()
        }
        radioEndByDate.setOnClickListener{
            vm.limitType = RecurrenceLimit.Until
            onVmUpdated()
        }
        radioEndByCount.setOnClickListener{
            vm.limitType = RecurrenceLimit.Count
            onVmUpdated()
        }

        repeatsEveryUnitButton.setOnClickListener{
            showRecurrenceUnitPopupMenu(it)
        }

        radioOnMonthDay.setOnClickListener {
            vm.recurrenceType = RecurrenceType.MonthlyByMonthDay
            onVmUpdated()
        }
        radioOnWeekDay.setOnClickListener {
            vm.recurrenceType = RecurrenceType.MonthlyByWeekDay
            onVmUpdated()
        }
        radioOnLastWeekDay.setOnClickListener {
            vm.recurrenceType = RecurrenceType.MonthlyByLastWeekDay
            onVmUpdated()
        }

        onVmUpdated()

        dialog = builder.create()

        doneButton.setOnClickListener{

            // apply count?

            //picker.clearFocus()

            dialog.dismiss()
        }
    }

    fun show() = dialog.show()

    fun onVmUpdated() {
        when (vm.limitType) {
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

        endOnButton.text = DateUtils.formatDateTime(
                context, vm.timeUntil,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL or DateUtils.FORMAT_SHOW_YEAR
        )

        when (vm.recurrenceType) {
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

    fun showRecurrenceUnitPopupMenu(v: View) {
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
            onVmUpdated()
            ret
        }

        popup.show()
    }

    companion object {
        private const val CODE_DAILY = 0
        private const val CODE_WEEKLY = 10
        private const val CODE_MONTHLY_BYMONTHDAY = 21
        private const val CODE_MONTHLY_BYWEEKDAY = 22
        private const val CODE_MONTHLY_BYLASTWEEKDAY = 23
        private const val CODE_YEARLY = 30

        private const val NO_LIMIT = 0
        private const val LIMIT_UNTIL = 1
        private const val LIMIT_COUNT = 2
    }
}