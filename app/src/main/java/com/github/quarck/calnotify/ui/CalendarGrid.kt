package com.github.quarck.calnotify.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.utils.dayOfMonth
import com.github.quarck.calnotify.utils.month
import com.github.quarck.calnotify.utils.toAdjacentMonth
import java.util.*

@SuppressLint("ClickableViewAccessibility")
class CalendarGrid(val ctx: Context, inflater: LayoutInflater, val onItemClick: (CalendarGrid, Calendar)->Unit) {
    private var layout: LinearLayout

    private lateinit var gestureDetector: GestureDetectorCompat

    private var dayLabels: Array<TextView>
    private var lineLayouts: Array<LinearLayout>

    private var displayPosition: Calendar = Calendar.getInstance()
    private var today: Calendar = Calendar.getInstance()
    private var displayMonth: Int = -1
    private var displayDayOfMonth: Int = -1

    private var lastDayIdx: Int = -1

    private val dayLabelDays = IntArray(CalendarGrid.grid_size)

    private val currentMonthColor = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.cal_current_month))
    private val otherMonthColor = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.cal_other_month))
    private val currentDayColor = ColorStateList.valueOf(ContextCompat.getColor(ctx, R.color.cal_current_day))

    init {
        layout = inflater.inflate(R.layout.days_grid, null) as LinearLayout
        dayLabels = dayLabelIds.map{ id -> layout.findViewById<TextView>(id) }.toTypedArray()
        lineLayouts = lineLayoutIds.map{ id -> layout.findViewById<LinearLayout>(id) }.toTypedArray()

        gestureDetector = GestureDetectorCompat(ctx, GestureListener())

        for (lbl in dayLabels) {
            lbl.setOnClickListener(this::onDayClick)

            lbl.setOnTouchListener {
                v, event ->
                lastDayIdx = dayLabels.indexOf(v as TextView)
                gestureDetector.onTouchEvent(event)
            }

        }
    }


    val view: View
        get() = layout

    private fun onDayClick(v: View) {
        val idx = dayLabels.indexOf(v as TextView)
        if ((idx in 0 until grid_size) && dayLabelDays[idx] != -1) {
            displayPosition.dayOfMonth = dayLabelDays[idx]
            onItemClick(this, displayPosition)
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if ((lastDayIdx in 0 until grid_size) && dayLabelDays[lastDayIdx] != -1) {
                displayPosition.dayOfMonth = dayLabelDays[lastDayIdx]
                onItemClick(this@CalendarGrid, displayPosition)
            }
            return true
        }

        override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            var result = false
            try {
                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        adjustMonthPosition(if (diffX > 0) -1 else 1)
                        result = true
                    }
                }
            } catch (exception: Exception) {
                exception.printStackTrace()
            }
            return result
        }
    }

    fun adjustMonthPosition(direction: Int) {
        displayPosition = displayPosition.toAdjacentMonth(direction)

        onItemClick(this, displayPosition)

        //grid.setDisplayPosition(currentDay, today)
        //updateTitle()
        //reloadData()
    }


    fun setDisplayPosition(pos: Calendar, cur: Calendar) {

        displayPosition = pos.clone() as Calendar
        today = cur.clone() as Calendar

        displayMonth = displayPosition.month
        displayDayOfMonth =  displayPosition.dayOfMonth

        val day = displayPosition.clone() as Calendar
        day.dayOfMonth = 1

        for (idx in 0 until 7) {
            if (day.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY)
                break;
            day.timeInMillis -= 24 * 3600 * 1000L
        }

        for (idx in 0 until 7 * 6) {
            dayLabelDays[idx] = -1
        }

        for (idx in 0 until 7 * 6) {
            val layout = lineLayouts[idx / 7]

            if (idx == 5 * 7 && day.month != displayMonth) {
                layout.visibility = View.GONE
                break
            }
            layout.visibility = View.VISIBLE

            dayLabels[idx].text = "${day.dayOfMonth}"

            if (day.month == displayMonth)
                dayLabelDays[idx] = day.dayOfMonth

            dayLabels[idx].setTextColor(getDayColor(day))

            // move to the next day
            day.timeInMillis += 24 * 3600 * 1000L
        }
    }

    private fun getDayColor(day: Calendar): ColorStateList {
        return if (day.month == displayMonth) {
            if (day.dayOfMonth == displayDayOfMonth)
                currentDayColor
            else
                currentMonthColor
        }
        else otherMonthColor
    }

    companion object {
        private val lineLayoutIds = listOf(
                R.id.layout_calendar_line_0,
                R.id.layout_calendar_line_1,
                R.id.layout_calendar_line_2,
                R.id.layout_calendar_line_3,
                R.id.layout_calendar_line_4,
                R.id.layout_calendar_line_5,
        )

        private val dayLabelIds = listOf(
                R.id.cal_day_line_0_item_0, R.id.cal_day_line_0_item_1, R.id.cal_day_line_0_item_2, R.id.cal_day_line_0_item_3,
                R.id.cal_day_line_0_item_4, R.id.cal_day_line_0_item_5, R.id.cal_day_line_0_item_6,
                R.id.cal_day_line_1_item_0, R.id.cal_day_line_1_item_1, R.id.cal_day_line_1_item_2, R.id.cal_day_line_1_item_3,
                R.id.cal_day_line_1_item_4, R.id.cal_day_line_1_item_5, R.id.cal_day_line_1_item_6,
                R.id.cal_day_line_2_item_0, R.id.cal_day_line_2_item_1, R.id.cal_day_line_2_item_2, R.id.cal_day_line_2_item_3,
                R.id.cal_day_line_2_item_4, R.id.cal_day_line_2_item_5, R.id.cal_day_line_2_item_6,
                R.id.cal_day_line_3_item_0, R.id.cal_day_line_3_item_1, R.id.cal_day_line_3_item_2, R.id.cal_day_line_3_item_3,
                R.id.cal_day_line_3_item_4, R.id.cal_day_line_3_item_5, R.id.cal_day_line_3_item_6,
                R.id.cal_day_line_4_item_0, R.id.cal_day_line_4_item_1, R.id.cal_day_line_4_item_2, R.id.cal_day_line_4_item_3,
                R.id.cal_day_line_4_item_4, R.id.cal_day_line_4_item_5, R.id.cal_day_line_4_item_6,
                R.id.cal_day_line_5_item_0, R.id.cal_day_line_5_item_1, R.id.cal_day_line_5_item_2, R.id.cal_day_line_5_item_3,
                R.id.cal_day_line_5_item_4, R.id.cal_day_line_5_item_5, R.id.cal_day_line_5_item_6
        )

        val grid_size: Int
            get() = dayLabelIds.size

        private const val SWIPE_THRESHOLD = 100
        private const val SWIPE_VELOCITY_THRESHOLD = 100
    }
}
