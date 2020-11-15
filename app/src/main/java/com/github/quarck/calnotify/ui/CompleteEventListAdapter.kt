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

import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.calendar.CompleteEventAlertRecord
import com.github.quarck.calnotify.calendar.EventCompletionType
import com.github.quarck.calnotify.utils.textutils.EventFormatter
import com.github.quarck.calnotify.utils.textutils.dateToStr
import com.github.quarck.calnotify.utils.adjustCalendarColor

fun CompleteEventAlertRecord.formatReason(ctx: Context): String =
        when (this.completionType) {
            EventCompletionType.ManuallyViaNotification ->
                String.format(ctx.resources.getString(R.string.complete_from_notification), dateToStr(ctx, this.completionTime))

            EventCompletionType.ManuallyInTheApp ->
                String.format(ctx.resources.getString(R.string.complete_from_the_app), dateToStr(ctx, this.completionTime))

            EventCompletionType.AutoDueToCalendarMove ->
                String.format(ctx.resources.getString(R.string.event_moved_new_time), dateToStr(ctx, this.event.startTime))

            EventCompletionType.EventMovedInTheApp ->
                String.format(ctx.resources.getString(R.string.event_moved_new_time), dateToStr(ctx, this.event.startTime))
        }

interface CompleteEventListCallback {
    fun onItemClick(v: View, position: Int, entry: CompleteEventAlertRecord): Unit
}

class CompleteEventListAdapter(
        val context: Context,
        val cardVewResourceId: Int,
        val callback: CompleteEventListCallback)

    : RecyclerView.Adapter<CompleteEventListAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View)
        : RecyclerView.ViewHolder(itemView) {
        //var eventId: Long = 0;
        var entry: CompleteEventAlertRecord? = null

        var eventHolder: RelativeLayout?
        var eventTitleText: TextView
        //var eventTitleLayout: RelativeLayout?
        var eventDateText: TextView
        var eventTimeText: TextView

        var snoozedUntilText: TextView?
        val compactViewCalendarColor: View?

        val compactViewContentLayout: RelativeLayout?
        var undoLayout: RelativeLayout?

        var calendarColor: ColorDrawable

        init {
            eventHolder = itemView.findViewById<RelativeLayout>(R.id.card_view_main_holder)
            eventTitleText = itemView.findViewById<TextView>(R.id.card_view_event_name)
            //eventTitleLayout = itemView.findViewById<RelativeLayout?>(R.id.card_view_event_title_layout)

            eventDateText = itemView.findViewById<TextView>(R.id.card_view_event_date)
            eventTimeText = itemView.findViewById<TextView>(R.id.card_view_event_time)
            snoozedUntilText = itemView.findViewById<TextView>(R.id.card_view_snoozed_until)

            undoLayout = itemView.findViewById<RelativeLayout?>(R.id.event_card_undo_layout)

            compactViewContentLayout = itemView.findViewById<RelativeLayout?>(R.id.compact_view_content_layout)
            compactViewCalendarColor = itemView.findViewById<View?>(R.id.compact_view_calendar_color)

            calendarColor = ColorDrawable(0)


            val itemClickListener = View.OnClickListener {

                if (entry != null)
                    callback.onItemClick(eventTitleText, adapterPosition, entry!!);
            }

            eventHolder?.setOnClickListener(itemClickListener)
        }
    }

    private var entries = arrayOf<CompleteEventAlertRecord>();

    private var _recyclerView: RecyclerView? = null
    var recyclerView: RecyclerView?
        get() = _recyclerView
        set(value) {
            _recyclerView = value
        }

    private val primaryColor: Int = ContextCompat.getColor(context, R.color.primary)

    private val eventFormatter = EventFormatter(context)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        //
        if (position < 0 || position >= entries.size)
            return

        val entry = entries[position]

        if (true) {
            holder.entry = entry

            holder.eventTitleText.text = entry.event.title

            holder.undoLayout?.visibility = View.GONE
            holder.compactViewContentLayout?.visibility = View.VISIBLE

            val time = eventFormatter.formatDateTimeOneLine(entry.event)
            holder.eventDateText.text = time
            holder.eventTimeText.text = ""

            holder.snoozedUntilText?.text = entry.formatReason(context)
            holder.snoozedUntilText?.visibility = View.VISIBLE;

            holder.calendarColor.color =
                    if (entry.event.color != 0)
                        entry.event.color.adjustCalendarColor()
                    else
                        primaryColor
            holder.compactViewCalendarColor?.background = holder.calendarColor
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(cardVewResourceId, parent, false);
        return ViewHolder(view);
    }

    override fun getItemCount(): Int = entries.size

    fun setEventsToDisplay(newEntries: Array<CompleteEventAlertRecord>)
            = synchronized(this) {
        entries = newEntries;
        notifyDataSetChanged();
    }

    fun removeEntry(entry: CompleteEventAlertRecord)
            = synchronized(this) {
        val idx = entries.indexOf(entry)
        entries = entries.filter { ev -> ev != entry }.toTypedArray()
        notifyItemRemoved(idx)
    }
}
