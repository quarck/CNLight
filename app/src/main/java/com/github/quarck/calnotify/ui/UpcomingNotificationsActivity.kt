package com.github.quarck.calnotify.ui

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.calendarmonitor.CalendarMonitor
import com.github.quarck.calnotify.utils.adjustCalendarColor
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.utils.textutils.EventFormatter
import kotlinx.coroutines.*
import com.github.quarck.calnotify.R

data class UpcomingEventAlertRecordWrap(
        val isToday: Boolean,
        val event: EventAlertRecord?,
        val numItemsInGroup: Int? = null
)

class UpcomingEventListAdapter(
        val context: Context,
        val cb: UpcomingNotificationsActivity
) : RecyclerView.Adapter<UpcomingEventListAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View)
        : RecyclerView.ViewHolder(itemView) {
        //var eventId: Long = 0;
        var entry: UpcomingEventAlertRecordWrap? = null

        var eventHolder: RelativeLayout = itemView.findViewById<RelativeLayout>(R.id.card_view_main_holder)
        var eventTitleText = itemView.findViewById<TextView>(R.id.card_view_event_name)

        var eventDateText = itemView.findViewById<TextView>(R.id.card_view_event_date)
        var eventTimeText: TextView = itemView.findViewById<TextView>(R.id.card_view_event_time)

        val notificationTimeText : TextView? = itemView.findViewById<TextView>(R.id.card_view_notification_time)
        val notificationSkippedText : TextView? = itemView.findViewById<TextView>(R.id.card_view_skip_flag)

        val compactViewCalendarColor: View? = itemView.findViewById<View>(R.id.compact_view_calendar_color)

        val headingLayout: RelativeLayout = itemView.findViewById<RelativeLayout>(R.id.event_card_heading_layout)
        val headingText: TextView = itemView.findViewById<TextView>(R.id.event_view_heading_text)

        val undoLayout: RelativeLayout = itemView.findViewById(R.id.event_card_undo_layout)
        val mainLayout: RelativeLayout = itemView.findViewById(R.id.compact_view_content_layout)

        var calendarColor: ColorDrawable = ColorDrawable(0)

        init {
            eventHolder.setOnClickListener{
                if (entry != null)
                    cb.onItemClick(eventTitleText, adapterPosition, entry!!);
            }

            undoLayout.visibility = View.GONE
            mainLayout.visibility = View.VISIBLE
        }
    }

    private var entries = listOf<UpcomingEventAlertRecordWrap>();

    private var _recyclerView: RecyclerView? = null
    var recyclerView: RecyclerView?
        get() = _recyclerView
        set(value) {
            _recyclerView = value
            setUpItemTouchHelper(_recyclerView, context)
        }

    private fun getEventAtPosition(position: Int): UpcomingEventAlertRecordWrap?
            = synchronized(this) {
        if (position >= 0 && position < entries.size)
            entries[position];
        else {
            DevLog.error(LOG_TAG, "getEventAtPosition: requested pos $position, size: ${entries.size}")
            null
        }
    }

    fun toggleSkipNotification(entry: UpcomingEventAlertRecordWrap, position: Int) {
        val event = entry.event ?: return
        cb.toggleSkipNotification(event, position)
        synchronized(this) { notifyItemChanged(position) }
    }

    private fun setUpItemTouchHelper(_recyclerView: RecyclerView?, context: Context) {

        val itemTouchCallback =
                object : ItemTouchHelper.Callback() {

                    val escapeVelocityMultiplier = 5.0f

                    val background = ColorDrawable(Theme.resolveColor(context, R.attr.cn_skip_event_bg))
                    val iconsColor = Theme.resolveColor(context, R.attr.cn_icons)
                    val vMark: Drawable = (ContextCompat.getDrawable(context, R.drawable.ic_check_white_24dp) ?: throw Exception("Now v-mark"))
                            .apply{
                                colorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(iconsColor, BlendModeCompat.SRC_ATOP)
                            }

                    var vMarkMargin = context.resources.getDimension(R.dimen.ic_clear_margin).toInt()
                    var bgMargin = context.resources.getDimension(R.dimen.swipe_bg_margin).toInt()

                    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                        val adapter = recyclerView.adapter as UpcomingEventListAdapter? ?: return 0
                        val entry = getEventAtPosition(viewHolder.adapterPosition) ?: return 0
                        if (entry.event == null) {
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
                                toggleSkipNotification(event, swipedPosition)
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
        if (position < 0 || position >= entries.size)
            return

        val entry = entries[position]

        holder.entry = entry

        if (entry.event != null) {
            holder.mainLayout.visibility = View.VISIBLE
            holder.headingLayout.visibility = View.GONE

            holder.eventTitleText.text = cb.getItemTitle(entry)

            val time = cb.getItemMiddleLine(entry)
            holder.eventDateText.text = time
            holder.eventTimeText.text = ""

            holder.notificationTimeText?.text = cb.getItemNotificationTime(entry)
            holder.notificationSkippedText?.visibility = if (cb.getItemIsSkipped(entry)) View.VISIBLE else View.GONE

            holder.calendarColor.color = cb.getItemColor(entry)
            holder.compactViewCalendarColor?.background = holder.calendarColor
        }
        else {
            holder.mainLayout.visibility = View.GONE
            holder.headingLayout.visibility = View.VISIBLE
            holder.notificationSkippedText?.visibility = View.GONE
            holder.headingText.text = cb.getItemTitle(entry) // entry.event.title
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.event_card_compact_upcoming, parent, false);
        return ViewHolder(view);
    }

    override fun getItemCount(): Int = entries.size

    fun setEventsToDisplay(newEntries: List<UpcomingEventAlertRecordWrap>)
            = synchronized(this) {
        entries = newEntries
        notifyDataSetChanged()
    }

    companion object {
        private const val LOG_TAG = "UpcomingEventsFragment_Adapter"
    }
}


class UpcomingNotificationsActivity : AppCompatActivity() {
    private val scope = MainScope()

    private lateinit var recyclerView: RecyclerView

    private lateinit var adapter: UpcomingEventListAdapter

    private var primaryColor: Int = Consts.DEFAULT_CALENDAR_EVENT_COLOR
    private lateinit var eventFormatter: EventFormatter

    private lateinit var eventReminderTimeFmt: String

    private lateinit var todayHeading: String
    private lateinit var todayHeadingEmpty: String
    private lateinit var otherDayHeading: String
    private lateinit var otherDayheadingEmpty: String

    private var colorSkippedItemBotomLine: Int  = 0x7f3f3f3f
    private var colorNonSkippedItemBottomLine: Int = 0x7f7f7f7f

    private var monitorEntries = mutableMapOf<MonitorEventAlertEntryKey, MonitorEventAlertEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        DevLog.info(LOG_TAG, "onCreate")

        super.onCreate(savedInstanceState)
        Theme.apply(this)

        setContentView(R.layout.activity_upcoming)

        setSupportActionBar(findViewById<Toolbar?>(R.id.toolbar))
        supportActionBar?.let{
            it.setDisplayHomeAsUpEnabled(true)
            it.setHomeAsUpIndicator(R.drawable.ic_arrow_back)
            it.setDisplayShowHomeEnabled(true)
        }

        window.navigationBarColor = ContextCompat.getColor(this, android.R.color.black)

        primaryColor = Theme.resolveColor(this, R.attr.cn_primary)
        colorNonSkippedItemBottomLine = Theme.resolveColor(this, R.attr.cn_secondary_text)

        eventFormatter  = EventFormatter(this)
        adapter = UpcomingEventListAdapter(this, this)

        eventReminderTimeFmt = this.resources.getString(R.string.reminder_at_fmt)

        colorSkippedItemBotomLine = ContextCompat.getColor(this, R.color.material_red)

        todayHeading = this.resources.getString(R.string.today_semi)
        todayHeadingEmpty = this.resources.getString(R.string.no_more_today)
        otherDayHeading = this.resources.getString(R.string.tomorrow_and_following)
        otherDayheadingEmpty = this.resources.getString(R.string.no_more_other_days)

        recyclerView = findViewById<RecyclerView>(R.id.list_events)
        recyclerView.adapter = adapter
        adapter.recyclerView = recyclerView
        recyclerView.isNestedScrollingEnabled = false
    }

    override fun onResume() {
        DevLog.debug(LOG_TAG, "onResume")
        super.onResume()

        scope.launch {

            val from = System.currentTimeMillis()
            val to = from + Consts.UPCOMING_EVENTS_WINDOW

            val merged = withContext(Dispatchers.IO) {
                monitorEntries =
                        CalendarMonitor(CalendarProvider)
                                .getAlertsForAlertRange(this@UpcomingNotificationsActivity, scanFrom = from, scanTo = to)
                                .associateBy { it.key }
                                .toMutableMap()

                val events =
                        CalendarProvider
                                .getEventAlertsForInstancesInRange(this@UpcomingNotificationsActivity, from, to)
                                .filter { it.alertTime >= from }
                                .map { UpcomingEventAlertRecordWrap(false, it) }
                                .sortedBy { it.event?.alertTime ?: 0L }
                                .partition { isTodayAlert(it.event) }

                val ret =
                        listOf(UpcomingEventAlertRecordWrap(true, null, events.first.size)) +
                        events.first +
                        listOf(UpcomingEventAlertRecordWrap(false, null, events.second.size)) +
                        events.second

                ret
            }

            adapter.setEventsToDisplay(merged)
        }

    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun isTodayAlert(event: EventAlertRecord?): Boolean {
        val ev = event ?: return false
        return DateUtils.isToday(ev.alertTime)
    }


    // TODO: add an option to view the event, not only to restore it
    fun onItemClick(v: View, position: Int, entry: UpcomingEventAlertRecordWrap) {
        val event = entry.event ?: return

        startActivity(
                Intent(this, ViewEventActivityUpcoming::class.java)
                        .putExtra(Consts.INTENT_EVENT_ID_KEY, event.eventId)
                        .putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, event.instanceStartTime)
                        .putExtra(Consts.INTENT_ALERT_TIME, event.alertTime)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }

    fun getItemTitle(entry: UpcomingEventAlertRecordWrap): String {
        val event = entry.event
        if (event != null)
            return event.title

        if (entry.isToday) {
            return if (entry.numItemsInGroup ?: 0 > 0) todayHeading else todayHeadingEmpty
        }
        else {
            return if (entry.numItemsInGroup ?: 0 > 0) otherDayHeading else otherDayheadingEmpty
        }
    }

    fun getItemMiddleLine(entry: UpcomingEventAlertRecordWrap): String {
        val event = entry.event ?: return ""
        return eventFormatter.formatDateTimeOneLine(event)
    }

    fun getItemNotificationTime(entry: UpcomingEventAlertRecordWrap): String {
        val event = entry.event ?: return ""
        return eventReminderTimeFmt.format(eventFormatter.formatTimePoint(event.alertTime, noWeekDay = true))
    }

    fun getItemIsSkipped(entry: UpcomingEventAlertRecordWrap): Boolean {
        val event = entry.event ?: return false
        val monEntry = monitorEntries[event.monitorEntryKey]
        return monEntry?.wasHandled == true
    }

    fun getItemColor(entry: UpcomingEventAlertRecordWrap): Int {
        val event = entry.event ?: return 0
        return if (event.color != 0)
            event.color.adjustCalendarColor()
        else
            primaryColor
    }


    override fun onPause() {
        super.onPause()
        DevLog.info(LOG_TAG, "onPause")
    }

    fun toggleSkipNotification(event: EventAlertRecord, position: Int) {
        val monEntry = monitorEntries.getOrPut(event.monitorEntryKey,
                { MonitorEventAlertEntry.fromEventAlertRecord(event) })

        if (monEntry.wasHandled) {
            monEntry.wasHandled = false
            ApplicationController.restoreEvent(this, event)
        }
        else {
            monEntry.wasHandled = true
            ApplicationController.dismissFutureEvent(this, MonitorDataPair.fromEventAlertRecord(event))
        }
    }

    companion object {
        private const val LOG_TAG = "UpcomingEventsFragment"
    }
}