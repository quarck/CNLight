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
import com.github.quarck.calnotify.calendar.WeekDay
import java.text.DateFormat
import java.util.*

class DialogRecurrence(
        val context: Context,
        val inflater: LayoutInflater,
        root: ViewGroup?,
        val eventStart: Long,
        val eventEnd: Long,
        val eventTimeZone: String,
        val originalRecurrence: CalendarRecurrence
) {
    val view: View = inflater.inflate(R.layout.dialog_recurrence, root)
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

    private var recurrenceType: Int
    private var limitType: Int

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
                limitType = LIMIT_UNTIL
            }
            is CalendarRecurrenceLimit.Count -> {
                numRepetitions = originalLimit.count
                limitType = LIMIT_COUNT
            }
            is CalendarRecurrenceLimit.NoLimit -> {
                limitType = NO_LIMIT
            }
        }

        when (originalRecurrence) {
            is CalendarRecurrence.Daily -> {
                recurrenceType = CODE_DAILY
            }
            is CalendarRecurrence.Weekly -> {
                recurrenceType = CODE_WEEKLY
            }
            is CalendarRecurrence.Monthly -> {
                recurrenceType = CODE_MONTHLY_BYMONTHDAY
            }
            is CalendarRecurrence.MonthlyByWeekDay -> {
                recurrenceType = if (originalRecurrence.weekDayNum > 0)
                    CODE_MONTHLY_BYWEEKDAY
                else
                    CODE_MONTHLY_BYLASTWEEKDAY
            }
            is CalendarRecurrence.Yearly -> {
                recurrenceType = CODE_YEARLY
            }
        }

        radioEventNoEnd.setOnClickListener{
            limitType = NO_LIMIT
            updateLimitUI()
        }

        radioEndByDate.setOnClickListener{
            limitType = LIMIT_UNTIL
            updateLimitUI()
        }

        radioEndByCount.setOnClickListener{
            limitType = LIMIT_COUNT
            updateLimitUI()
        }

        repeatsEveryUnitButton.setOnClickListener{
            showRecurrenceUnitPopupMenu(it)
        }


        updateLimitUI(true)
        updateRecurrenceUI(true)

        dialog = builder.create()

        doneButton.setOnClickListener{

            // apply count?

            dialog.dismiss()
        }
    }

    fun show() = dialog.show()

    fun updateLimitUI(initialUpdate: Boolean = false) {

        val untilTime: Long

        when (limitType) {
            NO_LIMIT -> {
                if (initialUpdate)
                    radioEventNoEnd.isChecked = true
                radioEndByDate.isChecked = false
                radioEndByCount.isChecked = false
                endOnButton.isEnabled = false
                endAfterCount.isEnabled = false
            }
            LIMIT_COUNT -> {
                if (initialUpdate)
                    radioEndByCount.isChecked = true
                radioEventNoEnd.isChecked = false
                radioEndByDate.isChecked = false
                endOnButton.isEnabled = false
                endAfterCount.isEnabled = true
            }
            LIMIT_UNTIL -> {
                if (initialUpdate)
                    radioEndByDate.isChecked = true
                radioEventNoEnd.isChecked = false
                radioEndByCount.isChecked = false
                endOnButton.isEnabled = true
                endAfterCount.isEnabled = false
            }
        }

        endOnButton.text = DateUtils.formatDateTime(context, timeUntil, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL or DateUtils.FORMAT_SHOW_YEAR)
    }

    fun updateRecurrenceUI(initialUpdate: Boolean = false) {

    }

    fun showRecurrenceUnitPopupMenu(v: View) {
        val popup = PopupMenu(context, v)
        val inflater = popup.menuInflater
        inflater.inflate(R.menu.recurrence_unit_menu, popup.menu)

        popup.setOnMenuItemClickListener {
            item ->
            when (item.itemId) {
                R.id.menu_recurrence_day -> onDayUnitSelected()
                R.id.menu_recurrence_week -> onWeekUnitSelected()
                R.id.menu_recurrence_month -> onMonthUnitSelected()
                R.id.menu_recurrence_year -> onYearUnitSelected()
                else -> false
            }
        }

        popup.show()
    }

    private fun onDayUnitSelected(): Boolean {
        recurrenceType = CODE_DAILY
        return true
    }
    private fun onWeekUnitSelected(): Boolean {
        recurrenceType = CODE_WEEKLY
        return true
    }
    private fun onMonthUnitSelected(): Boolean {
        recurrenceType = CODE_MONTHLY_BYMONTHDAY
        return true
    }
    private fun onYearUnitSelected(): Boolean {
        recurrenceType = CODE_YEARLY
        return true
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