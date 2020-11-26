package com.github.quarck.calnotify.ui

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.calendar.CalendarRecurrence
import com.github.quarck.calnotify.calendar.CalendarRecurrenceLimit

class DialogRecurrence(
        val context: Context,
        val inflater: LayoutInflater,
        root: ViewGroup?,
        val eventStart: Long,
        val eventTimeZone: String,
        var recurrence: CalendarRecurrence
) {

    val view: View = inflater.inflate(R.layout.dialog_recurrence, root)
    private val dialog: AlertDialog

    private val doneButton: Button = view.findViewById(R.id.recurrence_dialog_button_done)

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
    private val endAfterCount: TextView = view.findViewById(R.id.recurrence_dialog_radio_end_after_n_edit)

    init {
        val builder = AlertDialog.Builder(context)
        builder.setView(view)
        builder.setNegativeButton(android.R.string.cancel) {
            _: DialogInterface?, _: Int ->
        }

        radioEventNoEnd.setOnClickListener{ onEndingType(it) }
        radioEndByDate.setOnClickListener{ onEndingType(it) }
        radioEndByCount.setOnClickListener{ onEndingType(it) }

        applyLimit(recurrence.limit)

        dialog = builder.create()
    }

    fun show() = dialog.show()

    fun applyLimit(l: CalendarRecurrenceLimit) {
        when (l) {
            is CalendarRecurrenceLimit.NoLimit -> {
                radioEventNoEnd.isChecked = true
                endOnButton.isEnabled = false
                endAfterCount.isEnabled = false
            }
            is CalendarRecurrenceLimit.Count -> {
                radioEndByCount.isChecked = true
                endOnButton.isEnabled = false
                endAfterCount.isEnabled = true
            }
            is CalendarRecurrenceLimit.Until -> {
                radioEndByDate.isChecked = true
                endOnButton.isEnabled = true
                endAfterCount.isEnabled = false
            }
        }
    }

    fun onEndingType(v: View) {
        when (v) {
            radioEventNoEnd -> {
                radioEndByDate.isChecked = false
                radioEndByCount.isChecked = false
                endOnButton.isEnabled = false
                endAfterCount.isEnabled = false
            }
            radioEndByDate -> {
                radioEventNoEnd.isChecked = false
                radioEndByCount.isChecked = false
                endOnButton.isEnabled = true
                endAfterCount.isEnabled = false
            }
            radioEndByCount -> {
                radioEventNoEnd.isChecked = false
                radioEndByDate.isChecked = false
                endOnButton.isEnabled = false
                endAfterCount.isEnabled = true
            }
        }
    }
}