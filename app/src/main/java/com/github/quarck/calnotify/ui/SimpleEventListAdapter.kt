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
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import com.github.quarck.calnotify.R

interface SimpleEventListCallback<T> {
    fun getItemTitle(entry: T): String
    fun getItemMiddleLine(entry: T): String
    fun getItemBottomLine(entry: T): Pair<String, Int>
    fun getItemColor(entry: T): Int
    fun onItemClick(v: View, position: Int, entry: T)
    fun getUseBoldTitle(entry: T): Boolean
}

// T == CompleteEventAlertRecord originally

class SimpleEventListAdapter<T>(
        val context: Context,
        val cardVewResourceId: Int,
        val cb: SimpleEventListCallback<T>
) : RecyclerView.Adapter<SimpleEventListAdapter<T>.ViewHolder>() {

    inner class ViewHolder(itemView: View)
        : RecyclerView.ViewHolder(itemView) {
        //var eventId: Long = 0;
        var entry: T? = null

        var eventHolder: RelativeLayout?
        var eventTitleText: TextView

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

            eventDateText = itemView.findViewById<TextView>(R.id.card_view_event_date)
            eventTimeText = itemView.findViewById<TextView>(R.id.card_view_event_time)
            snoozedUntilText = itemView.findViewById<TextView>(R.id.card_view_snoozed_until)

            undoLayout = itemView.findViewById<RelativeLayout?>(R.id.event_card_undo_layout)

            compactViewContentLayout = itemView.findViewById<RelativeLayout?>(R.id.compact_view_content_layout)
            compactViewCalendarColor = itemView.findViewById<View?>(R.id.compact_view_calendar_color)

            calendarColor = ColorDrawable(0)


            val itemClickListener = View.OnClickListener {

                if (entry != null)
                    cb.onItemClick(eventTitleText, adapterPosition, entry!!);
            }

            eventHolder?.setOnClickListener(itemClickListener)
        }
    }

    private var entries = mutableListOf<T>();

    private var _recyclerView: RecyclerView? = null
    var recyclerView: RecyclerView?
        get() = _recyclerView
        set(value) {
            _recyclerView = value
        }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        //
        if (position < 0 || position >= entries.size)
            return

        val entry = entries[position]

        if (true) {
            holder.entry = entry

            holder.eventTitleText.text = cb.getItemTitle(entry) // entry.event.title

            if (cb.getUseBoldTitle(entry))
               holder.eventTitleText.setTypeface(null, Typeface.BOLD_ITALIC)
            else
                holder.eventTitleText.setTypeface(null, Typeface.NORMAL)

            holder.undoLayout?.visibility = View.GONE
            holder.compactViewContentLayout?.visibility = View.VISIBLE

            val time = cb.getItemMiddleLine(entry) // eventFormatter.formatDateTimeOneLine(entry.event)
            holder.eventDateText.text = time
            holder.eventTimeText.text = ""

            val (bottomText, bottomColor) = cb.getItemBottomLine(entry)
            holder.snoozedUntilText?.text =  bottomText
            holder.snoozedUntilText?.setTextColor(bottomColor)
            holder.snoozedUntilText?.visibility = View.VISIBLE;

            holder.calendarColor.color = cb.getItemColor(entry)
            holder.compactViewCalendarColor?.background = holder.calendarColor
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(cardVewResourceId, parent, false);
        return ViewHolder(view);
    }

    override fun getItemCount(): Int = entries.size

    fun setEventsToDisplay(newEntries: MutableList<T>)
            = synchronized(this) {
        entries = newEntries;
        notifyDataSetChanged();
    }

    fun removeEntry(entry: T) = synchronized(this) {
        val idx = entries.indexOf(entry)
        entries.removeAt(idx)
        notifyItemRemoved(idx)
    }
}
