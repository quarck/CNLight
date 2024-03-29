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
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.utils.textutils.EventFormatter
import com.github.quarck.calnotify.utils.adjustCalendarColor

interface EventListCallback {
    fun onItemClick(v: View, position: Int, eventId: Long): Unit
    fun onItemRemoved(event: EventAlertRecord)
    fun onItemRestored(event: EventAlertRecord) // e.g. undo
    fun onScrollPositionChange(newPos: Int)
}

class EventListAdapter(
        val context: Context,
        val callback: EventListCallback)

    : RecyclerView.Adapter<EventListAdapter.ViewHolder>() {

    val cardVewResourceId: Int = R.layout.event_card_compact

    inner class ViewHolder(itemView: View)
        : RecyclerView.ViewHolder(itemView) {
        var eventId: Long = 0;

        var eventHolder: RelativeLayout? = itemView.findViewById(R.id.card_view_main_holder)
        var eventTitleText: TextView = itemView.findViewById(R.id.card_view_event_name)
        var eventDateText: TextView = itemView.findViewById(R.id.card_view_event_date)
        var eventTimeText: TextView = itemView.findViewById(R.id.card_view_event_time)

        var snoozedUntilText: TextView? = itemView.findViewById(R.id.card_view_snoozed_until)
        var undoLayout: RelativeLayout? = itemView.findViewById(R.id.event_card_undo_layout)

        val compactViewCalendarColor: View? = itemView.findViewById(R.id.compact_view_calendar_color)
        val compactViewContentLayout: RelativeLayout? = itemView.findViewById(R.id.compact_view_content_layout)

        var undoButton: Button? = itemView.findViewById(R.id.card_view_button_undo)

        val alarmIcon: ImageView? = itemView.findViewById(R.id.imageview_is_alarm_indicator)

        var calendarColor: ColorDrawable = ColorDrawable(0)

        init {
            val itemClickListener = View.OnClickListener {
                callback.onItemClick(itemView, adapterPosition, eventId);
            }

            eventHolder?.setOnClickListener(itemClickListener)
            eventDateText.setOnClickListener(itemClickListener)
            eventTimeText.setOnClickListener(itemClickListener)
        }
    }

    private var events = arrayOf<EventAlertRecord>();

    private var _recyclerView: RecyclerView? = null
    var recyclerView: RecyclerView?
        get() = _recyclerView
        set(value) {
            _recyclerView = value
            onRecycleViewRegistered(_recyclerView)
        }

    private val primaryColor: Int

    private var currentScrollPosition: Int = 0

    private val pendingEventRemoveRunnables = mutableMapOf<EventAlertRecordKey, Runnable>()
    private var eventsPendingRemoval = mutableMapOf<EventAlertRecordKey, EventAlertRecord>()

    private val eventFormatter = EventFormatter(context)

    init {
        primaryColor = Theme.resolveColor(context, R.attr.cn_primary)
    }

    val scrollPosition: Int
        get() = currentScrollPosition

    private fun onRecycleViewRegistered(_recyclerView: RecyclerView?) {

        _recyclerView?.addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
                        currentScrollPosition += dy;
                        callback.onScrollPositionChange(currentScrollPosition)
                    }
                })

        setUpItemTouchHelper(_recyclerView, context)
    }

    private fun setUpItemTouchHelper(_recyclerView: RecyclerView?, context: Context) {

        val itemTouchCallback =
                object : ItemTouchHelper.Callback() {

                    val escapeVelocityMultiplier = 5.0f

                    val iconsColor = Theme.resolveColor(context, R.attr.cn_icons)

                    val background = ColorDrawable(Theme.resolveColor(context, R.attr.cn_complete_event_bg))
                    val vMark = (ContextCompat.getDrawable(context, R.drawable.ic_check_white_24dp) ?: throw Exception("Now v-mark"))
                            .apply{
                                colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                                        iconsColor, BlendModeCompat.SRC_ATOP)
                            }

                    var vMarkMargin = context.resources.getDimension(R.dimen.ic_clear_margin).toInt()
                    var bgMargin = context.resources.getDimension(R.dimen.swipe_bg_margin).toInt()

                    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                        val position = viewHolder.adapterPosition
                        val adapter = recyclerView.adapter as EventListAdapter?

                        if (adapter == null) {
                            DevLog.error(LOG_TAG, "getMovementFlags: no adapter! returning 0")
                            return 0
                        }

                        if (adapter.isPendingRemoval(position)) {
                            DevLog.info(LOG_TAG, "getMovementFlags: pos ${position} is pending removal, returning 0")
                            return 0
                        }

                        return makeFlag(ItemTouchHelper.ACTION_STATE_IDLE, ItemTouchHelper.RIGHT or ItemTouchHelper.LEFT) or
                                makeFlag(ItemTouchHelper.ACTION_STATE_SWIPE, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
                    }

                    @Suppress("UseExpressionBody")
                    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                        return false
                    }

                    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                        val trueViewHolder: RecyclerView.ViewHolder? = viewHolder
                        val swipedPosition = trueViewHolder?.adapterPosition
                        if (swipedPosition != null) {
                            _recyclerView?.itemAnimator?.changeDuration = 0;

                            val event = getEventAtPosition(swipedPosition)

                            if (event != null) {
                                removeWithUndo(event)
                                callback.onItemRemoved(event)
                            }
                            else {
                                DevLog.error(LOG_TAG, "Failed to get event at post $swipedPosition")
                            }
                        }
                        else {
                            DevLog.error(LOG_TAG, "onSwiped: can't get swipedPosition")
                        }
                    }

                    override fun isLongPressDragEnabled() = false

                    override fun isItemViewSwipeEnabled() = true

                    /* From documentation:
                     * Defines the minimum velocity which will be considered as a swipe action by the user.
                     * You can increase this value to make it harder to swipe or decrease it to make
                     * it easier. */
                    override fun getSwipeEscapeVelocity(defaultValue: Float) = defaultValue * escapeVelocityMultiplier

                    /* From documentation:
                     * Defines the maximum velocity ItemTouchHelper will ever calculate for pointer
                     * movements.
                     * If you increase the value, it will be easier for the user to swipe diagonally and
                     * if you decrease the value, user will need to make a rather straight finger movement
                     * to trigger a swipe.*/
                    override fun getSwipeVelocityThreshold(defaultValue: Float) = defaultValue / 3.0f

                    /* From documentation:
                     * Default value is .5f, which means, to swipe a View, user must move the View at
                     * least half of RecyclerView's width or height, depending on the swipe direction. */
//                override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) = 0.5f

                    override fun onChildDraw(
                            c: Canvas, recyclerView: RecyclerView,
                            viewHolder: RecyclerView.ViewHolder,
                            dX: Float, dY: Float,
                            actionState: Int, isCurrentlyActive: Boolean) {

                        val itemView = viewHolder.itemView

                        if (viewHolder.adapterPosition == -1)
                            return

                        if (dX < 0)
                            background.setBounds(
                                    itemView.right + dX.toInt() + bgMargin,
                                    itemView.top + bgMargin,
                                    itemView.right - bgMargin,
                                    itemView.bottom - bgMargin
                            )
                        else
                            background.setBounds(
                                    itemView.left + bgMargin,
                                    itemView.top + bgMargin,
                                    itemView.left + (dX.toInt() - bgMargin).coerceAtLeast(0),
                                    itemView.bottom - bgMargin
                            )

                        background.draw(c)

                        val itemHeight = itemView.bottom - itemView.top
                        val intrinsicWidth = vMark.intrinsicWidth
                        val intrinsicHeight = vMark.intrinsicWidth


                        if (dX < 0) {
                            val vMarkLeft = itemView.right - vMarkMargin - intrinsicWidth
                            val vMarkRight = itemView.right - vMarkMargin
                            val vMarkTop = itemView.top + (itemHeight - intrinsicHeight) / 2
                            val vMarkBottom = vMarkTop + intrinsicHeight
                            vMark.setBounds(vMarkLeft, vMarkTop, vMarkRight, vMarkBottom)
                        }
                        else {
                            val vMarkLeft = itemView.left + vMarkMargin
                            val vMarkRight = itemView.left + vMarkMargin + intrinsicWidth
                            val vMarkTop = itemView.top + (itemHeight - intrinsicHeight) / 2
                            val vMarkBottom = vMarkTop + intrinsicHeight
                            vMark.setBounds(vMarkLeft, vMarkTop, vMarkRight, vMarkBottom)
                        }

                        vMark.draw(c)

                        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                    }
                }

        if (_recyclerView != null) {
            val touchHelper = ItemTouchHelper(itemTouchCallback)
            touchHelper.attachToRecyclerView(_recyclerView)
        }
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        //
        if (position < 0 || position >= events.size)
            return

        val event = events[position]
        val eventKey = event.key

        if (eventsPendingRemoval.contains(eventKey)) {
            // we need to show the "undo" state of the row
            holder.undoLayout?.visibility = View.VISIBLE
            holder.compactViewContentLayout?.visibility = View.GONE

            holder.undoButton?.setOnClickListener {
                _ ->
                callback.onItemRestored(event)
                pendingEventRemoveRunnables.remove(eventKey)
                eventsPendingRemoval.remove(eventKey)
                notifyItemChanged(events.indexOf(event))
            }
        }
        else {
            holder.eventId = event.eventId;

            holder.eventTitleText.text = event.titleAsOneLine

            holder.undoLayout?.visibility = View.GONE
            holder.compactViewContentLayout?.visibility = View.VISIBLE

            val time = eventFormatter.formatDateTimeOneLine(event)
            holder.eventDateText.text = time
            holder.eventTimeText.text = ""

            if (event.snoozedUntil != 0L) {
                holder.snoozedUntilText?.text =
                        context.resources.getString(R.string.snoozed_until_string) + " " + eventFormatter.formatSnoozedUntil(event);

                holder.snoozedUntilText?.visibility = View.VISIBLE;
            }
            else {
                holder.snoozedUntilText?.text = "";
                holder.snoozedUntilText?.visibility = View.GONE;
            }

            holder.calendarColor.color =
                    if (event.color != 0)
                        event.color.adjustCalendarColor()
                    else
                        primaryColor

            holder.alarmIcon?.visibility = if (event.isAlarm) View.VISIBLE else View.GONE

            holder.compactViewCalendarColor?.background = holder.calendarColor
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(cardVewResourceId, parent, false);
        return ViewHolder(view);
    }

    override fun getItemCount(): Int = events.size

    val hasActiveEvents: Boolean
        get() = events.any { it.snoozedUntil == 0L }

    fun setEventsToDisplay(newEvents: Array<EventAlertRecord>)
            = synchronized(this) {
        events = newEvents;
        eventsPendingRemoval.clear()
        pendingEventRemoveRunnables.clear()
        notifyDataSetChanged();
    }

    fun getEventAtPosition(position: Int, expectedEventId: Long): EventAlertRecord?
            = synchronized(this) {

                if (position < 0) {
                    DevLog.error(LOG_TAG, "getEventAtPosition: negative position $position")
                    return null
                }

                if (position >= events.size) {
                    DevLog.error(LOG_TAG, "getEventAtPosition: position $position exceeds events size ${events.size}")
                    return null
                }

                if (events[position].eventId != expectedEventId) {
                    DevLog.error(LOG_TAG, "Event at position $position is not matching expected event id, " +
                            "expected: $expectedEventId, found: ${events[position].eventId} " +
                            "(instance start ${events[position].instanceStartTime}, alertTime ${events[position].alertTime}, " +
                            "calendar id ${events[position].calendarId})")
                    return null
                }

                return events[position];
            }

    private fun getEventAtPosition(position: Int): EventAlertRecord?
            = synchronized(this) {
        if (position >= 0 && position < events.size)
            events[position];
        else {
            DevLog.error(LOG_TAG, "getEventAtPosition: requested pos $position, size: ${events.size}")
            null
        }
    }


    fun removeEvent(event: EventAlertRecord)
            = synchronized(this) {
        val idx = events.indexOf(event)
        events = events.filter { ev -> ev != event }.toTypedArray()
        notifyItemRemoved(idx)
    }

    fun removeAll() {
        synchronized(this) {
            events = arrayOf<EventAlertRecord>();
            notifyDataSetChanged()
        }
    }

    fun removeWithUndo(event: EventAlertRecord) {

        val eventKey = event.key

        if (!eventsPendingRemoval.contains(eventKey)) {

            eventsPendingRemoval[eventKey] = event;

            pendingEventRemoveRunnables.put(
                    eventKey,
                    Runnable() {
                        synchronized(this) {
                            val idx = events.indexOf(event)
                            if (idx != -1) {
                                events = events.filter { ev -> ev != event }.toTypedArray()
                                notifyItemRemoved(idx)
                            }
                            else {
                                DevLog.error(LOG_TAG, "removeWithUndo pending action: cannot find event with id ${event.eventId}, instance start ${event.instanceStartTime}")
                                DevLog.error(LOG_TAG, "Known events: ")
                                for (ev in events) {
                                    DevLog.error(LOG_TAG, "${ev.eventId}, ${ev.instanceStartTime}, eq: ${ev == event}")
                                }

                                val foundByManual =
                                        events.withIndex().find {
                                            (_, ev) ->
                                            ev.eventId == event.eventId && ev.instanceStartTime == event.instanceStartTime
                                        }

                                DevLog.error(LOG_TAG, "Found by manual: ${foundByManual != null}")

                                if (foundByManual != null) {
                                    notifyItemRemoved(foundByManual.index)
                                }
                            }
                        }
                    });

            synchronized(this) {
                notifyItemChanged(events.indexOf(event));
            }
        }
        else {
            DevLog.error(LOG_TAG, "Event is already scheduled for removal!")
        }
    }

    fun isPendingRemoval(position: Int): Boolean {
        val event = getEventAtPosition(position)
        return event != null && eventsPendingRemoval.contains(event.key)
    }

    fun clearUndoState() {

        for ((_, runnable) in pendingEventRemoveRunnables)
            runnable.run()
        pendingEventRemoveRunnables.clear()
    }

    companion object {
        private const val LOG_TAG = "EventListAdapter"
    }
}
