package com.github.quarck.calnotify.ui

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.calendar.CalendarRecurrence
import com.github.quarck.calnotify.calendar.CalendarRecurrenceLimit
import com.github.quarck.calnotify.calendar.NthWeekDay
import com.github.quarck.calnotify.calendar.WeekDay
import java.text.DateFormat
import java.util.*

class DialogRecurrence(
        val context: Context,
        val view: View,
        val eventStart: Long,
        val eventEnd: Long,
        val eventTimeZone: String,
        val originalRecurrence: CalendarRecurrence,
) {
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


    private var timeUntil = eventEnd + 30 * 24 * 3600 * 1000L + 60L // must cap to end of day...
    private var numRepetitions = 10
    private var weekDays = Array<Boolean>(7, {false})
    private var interval = 1

    private var recurrenceType: RecurrenceType
    private var lastMonthlyRecurrenceType: RecurrenceType? = null
    private var limitType: RecurrenceLimit

    init {
        val builder = AlertDialog.Builder(context)
        builder.setView(view)
        builder.setNegativeButton(android.R.string.cancel) {
            _: DialogInterface?, _: Int ->
        }

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

        when (originalRecurrence) {
            is CalendarRecurrence.Daily -> {
                recurrenceType = RecurrenceType.Daily
            }
            is CalendarRecurrence.Weekly -> {
                recurrenceType = RecurrenceType.Weekly
            }
            is CalendarRecurrence.Monthly -> {
                recurrenceType = RecurrenceType.MonthlyByMonthDay
                lastMonthlyRecurrenceType = recurrenceType
            }
            is CalendarRecurrence.MonthlyByWeekDay -> {
                recurrenceType = if (originalRecurrence.weekDayNum > 0)
                    RecurrenceType.MonthlyByWeekDay
                else
                    RecurrenceType.MonthlyByLastWeekDay
                lastMonthlyRecurrenceType = recurrenceType
            }
            is CalendarRecurrence.Yearly -> {
                recurrenceType = RecurrenceType.Yearly
            }
        }

        val monthDay = CalendarRecurrence.Monthly.getDefaultValuesFor(eventStart, eventTimeZone, originalRecurrence.weekStart ?: WeekDay.MO)
        val monthByWeekDay = CalendarRecurrence.MonthlyByWeekDay.getDefaultValuesFor(eventStart, eventTimeZone, originalRecurrence.weekStart ?: WeekDay.MO, false)
        val monthByLastWeekDay = CalendarRecurrence.MonthlyByWeekDay.getDefaultValuesFor(eventStart, eventTimeZone, originalRecurrence.weekStart ?: WeekDay.MO, true)

        radioOnMonthDay.setText(context.getString(R.string.on_day_fmt).format(monthDay))
        radioOnWeekDay.setText(context.getString(R.string.on_fmt).format(NthWeekDay(monthByWeekDay.first, monthByWeekDay.second).toString()))
        radioOnLastWeekDay.setText(context.getString(R.string.on_fmt).format(NthWeekDay(monthByLastWeekDay.first, monthByLastWeekDay.second).toString()))

        val intervalAsStr = originalRecurrence.interval.toString()
        repeatsEveryText.setText(intervalAsStr)
        repeatsEveryText.setSelection(intervalAsStr.length) // focus it at the end

        radioEventNoEnd.setOnClickListener{
            limitType = RecurrenceLimit.NoLimit
            updateLimitUI()
        }
        radioEndByDate.setOnClickListener{
            limitType = RecurrenceLimit.Until
            updateLimitUI()
        }
        radioEndByCount.setOnClickListener{
            limitType = RecurrenceLimit.Count
            updateLimitUI()
        }

        repeatsEveryUnitButton.setOnClickListener{
            showRecurrenceUnitPopupMenu(it)
        }

        radioOnMonthDay.setOnClickListener {
            recurrenceType = RecurrenceType.MonthlyByMonthDay
            lastMonthlyRecurrenceType = recurrenceType
            updateRecurrenceUI()
        }
        radioOnWeekDay.setOnClickListener {
            recurrenceType = RecurrenceType.MonthlyByWeekDay
            lastMonthlyRecurrenceType = recurrenceType
            updateRecurrenceUI()
        }
        radioOnLastWeekDay.setOnClickListener {
            recurrenceType = RecurrenceType.MonthlyByLastWeekDay
            lastMonthlyRecurrenceType = recurrenceType
            updateRecurrenceUI()
        }

        updateLimitUI()
        updateRecurrenceUI()

        dialog = builder.create()

        doneButton.setOnClickListener{

            // apply count?

            //picker.clearFocus()

            dialog.dismiss()
        }
    }

    fun show() = dialog.show()

    fun updateLimitUI() {

        when (limitType) {
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

        endOnButton.text = DateUtils.formatDateTime(context, timeUntil, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL or DateUtils.FORMAT_SHOW_YEAR)
    }

    fun updateRecurrenceUI() {
        when (recurrenceType) {
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

                radioOnMonthDay.isChecked = recurrenceType == RecurrenceType.MonthlyByMonthDay
                radioOnWeekDay.isChecked = recurrenceType == RecurrenceType.MonthlyByWeekDay
                radioOnLastWeekDay.isChecked = recurrenceType == RecurrenceType.MonthlyByLastWeekDay
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
                    recurrenceType = RecurrenceType.Daily
                    true
                }
                R.id.menu_recurrence_week -> {
                    recurrenceType = RecurrenceType.Weekly
                    true
                }
                R.id.menu_recurrence_month -> {
                    recurrenceType = lastMonthlyRecurrenceType ?: RecurrenceType.MonthlyByMonthDay
                    true
                }
                R.id.menu_recurrence_year -> {
                    recurrenceType = RecurrenceType.Yearly
                    true
                }
                else -> false
            }
            updateRecurrenceUI()
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