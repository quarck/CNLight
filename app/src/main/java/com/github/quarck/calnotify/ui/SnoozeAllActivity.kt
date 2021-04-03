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
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.app.toast
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.utils.textutils.EventFormatter


fun formatSnoozePreset(ctx: Context, preset: Long): String {
    val num: Long
    val unit: String
    val presetSeconds = preset / 1000L;

    if (presetSeconds == 0L)
        return ctx.resources.getString(R.string.until_event_time)

    if (presetSeconds % Consts.DAY_IN_SECONDS == 0L) {
        num = presetSeconds / Consts.DAY_IN_SECONDS;
        unit =
                if (num != 1L)
                    ctx.resources.getString(R.string.days)
                else
                    ctx.resources.getString(R.string.day)
    }
    else if (presetSeconds % Consts.HOUR_IN_SECONDS == 0L) {
        num = presetSeconds / Consts.HOUR_IN_SECONDS;
        unit =
                if (num != 1L)
                    ctx.resources.getString(R.string.hours)
                else
                    ctx.resources.getString(R.string.hour)
    }
    else {
        num = presetSeconds / Consts.MINUTE_IN_SECONDS;
        unit =
                if (num != 1L)
                    ctx.resources.getString(R.string.minutes)
                else
                    ctx.resources.getString(R.string.minute)
    }

    if (num <= 0) {
        val beforeEventString = ctx.resources.getString(R.string.before_event)
        return "${-num} $unit $beforeEventString"
    }
    return "$num $unit"
}


open class SnoozeAllActivity : AppCompatActivity() {

    lateinit var snoozePresets: LongArray

    lateinit var formatter: EventFormatter

    var snoozeAllIsChange = false

    val snoozePresetControlIds = intArrayOf(
            R.id.snooze_view_snooze_present1,
            R.id.snooze_view_snooze_present2,
            R.id.snooze_view_snooze_present3,
            R.id.snooze_view_snooze_present4,
            R.id.snooze_view_snooze_present5,
            R.id.snooze_view_snooze_present6
    )

    val snoozePresentQuietTimeReminderControlIds = intArrayOf(
            R.id.snooze_view_snooze_present1_quiet_time_notice,
            R.id.snooze_view_snooze_present2_quiet_time_notice,
            R.id.snooze_view_snooze_present3_quiet_time_notice,
            R.id.snooze_view_snooze_present4_quiet_time_notice,
            R.id.snooze_view_snooze_present5_quiet_time_notice,
            R.id.snooze_view_snooze_present6_quiet_time_notice
    )

    var baselineIds = intArrayOf(
            R.id.snooze_view_snooze_present1_quiet_time_notice_baseline,
            R.id.snooze_view_snooze_present2_quiet_time_notice_baseline,
            R.id.snooze_view_snooze_present3_quiet_time_notice_baseline,
            R.id.snooze_view_snooze_present4_quiet_time_notice_baseline,
            R.id.snooze_view_snooze_present5_quiet_time_notice_baseline,
            R.id.snooze_view_snooze_present6_quiet_time_notice_baseline
    )

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        Theme.apply(this)

        setContentView(R.layout.activity_snooze_all)

        window.navigationBarColor = ContextCompat.getColor(this, android.R.color.black)

        // val currentTime = System.currentTimeMillis()

        formatter = EventFormatter(this)

        snoozeAllIsChange = intent.getBooleanExtra(Consts.INTENT_SNOOZE_ALL_IS_CHANGE, false)

        val toolbar = findViewById<Toolbar?>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)


        // remove "MM minutes before event" snooze presents for "Snooze All"
        // and when event time has passed already
        snoozePresets = Consts.DEFAULT_SNOOZE_PRESETS.filter { it > 0L }.toLongArray()

        // Populate snooze controls
        for ((idx, id) in snoozePresetControlIds.withIndex()) {
            val snoozeLable = findViewById<TextView>(id);
            val quietTimeNotice = findViewById<TextView>(snoozePresentQuietTimeReminderControlIds[idx])
            val quietTimeNoticeBaseline = findViewById<TextView>(baselineIds[idx])

            if (idx < snoozePresets.size) {
                snoozeLable.text = formatSnoozePreset(this, snoozePresets[idx])
                snoozeLable.visibility = View.VISIBLE;
                quietTimeNoticeBaseline.visibility = View.VISIBLE
                quietTimeNotice.visibility = View.GONE
            }
            else {
                snoozeLable.visibility = View.GONE;
                quietTimeNotice.visibility = View.GONE
                quietTimeNoticeBaseline.visibility = View.GONE
            }
        }

        // need to hide these guys
        findViewById<TextView>(R.id.snooze_snooze_for).text =
                if (!snoozeAllIsChange)
                    this.resources.getString(R.string.snooze_all_events)
                else
                    this.resources.getString(R.string.change_all_events)

        this.title =
                if (!snoozeAllIsChange)
                    resources.getString(R.string.snooze_all_title)
                else
                    resources.getString(R.string.change_all_title)
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onButtonCancelClick(v: View?) {
        finish();
    }

    private fun snoozeEvent(snoozeDelay: Long) {
        AlertDialog.Builder(this)
                .setMessage(
                        if (snoozeAllIsChange)
                            R.string.change_all_notification
                        else
                            R.string.snooze_all_confirmation)
                .setCancelable(false)
                .setPositiveButton(R.string.yes) {
                    _, _ ->

                    DevLog.debug(LOG_TAG, "Snoozing (change=$snoozeAllIsChange) all requests, snoozeDelay=${snoozeDelay / 1000L}")

                    val result = ApplicationController.snoozeAllEvents(this, snoozeDelay, snoozeAllIsChange, false);
                    result?.toast(this)
                    finish()
                }
                .setNegativeButton(R.string.cancel) {
                    _, _ ->
                }
                .create()
                .show()
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onButtonSnoozeClick(v: View?) {
        if (v == null)
            return

        for ((idx, id) in snoozePresetControlIds.withIndex()) {
            if (id == v.id) {
                snoozeEvent(snoozePresets[idx]);
                break;
            }
        }
    }

    companion object {
        private const val LOG_TAG = "ActivitySnoozeAll"
    }

}
