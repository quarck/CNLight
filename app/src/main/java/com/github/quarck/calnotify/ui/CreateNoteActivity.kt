package com.github.quarck.calnotify.ui

import android.app.AlertDialog
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.calendar.*
import com.github.quarck.calnotify.calendareditor.CalendarChangeManager
import com.github.quarck.calnotify.calendareditor.CalendarChangePersistentState
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.textutils.dateToStr
import android.app.Activity


class CreateNoteActivity : AppCompatActivity() {

    private lateinit var persistentState: CalendarChangePersistentState
    private lateinit var calendars: List<CalendarRecord>
    private lateinit var calendar: CalendarRecord
    var calendarProvider = CalendarProvider
    private lateinit var settings: Settings

    private val REQUEST_SPEECH_RECOGNIZER = 3000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_note)

        persistentState = CalendarChangePersistentState(this)
        settings = Settings(this)

        // Default calendar
        calendars = calendarProvider
                .getCalendars(this)
                .filter {
                    !it.isReadOnly &&
                            it.isVisible &&
                            settings.getCalendarIsHandled(it.calendarId)
                }

        if (calendars.isEmpty()) {
            DevLog.error(LOG_TAG, "You have no enabled calendars")

            AlertDialog.Builder(this)
                    .setMessage(R.string.no_active_calendars)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        finish()
                    }
                    .show()

            return
        }



        val lastCalendar = persistentState.lastCalendar
        if (lastCalendar != -1L) {
            calendar = calendars.filter { it.calendarId == lastCalendar }.firstOrNull() ?: calendars[0]
        } else {
            calendar = calendars.filter { it.isPrimary }.firstOrNull() ?: calendars[0]
        }

        val intent = this.intent
        val useVoice = intent.getBooleanExtra("voice", false)
        val text = intent.getStringExtra("text")
        if (!useVoice) {
            if (!text.isNullOrBlank()) {
                createNote(text)
            }
        }
        else {
            createVoiceNote()
        }
    }

    private fun createNote(text: String) {

        val startTime = System.currentTimeMillis()+ 1800 * 1000L
        val endTime = startTime + 900 * 1000L

        val appendTags = "#task"

        val details = CalendarEventDetails(
                title = text,
                desc = appendTags,
                location = "",
                timezone = calendar.timeZone,
                startTime = startTime,
                endTime = endTime,
                isAllDay = false,
                repeatingRule = "",
                repeatingRDate = "",
                repeatingExRule = "",
                repeatingExRDate = "",
                color = 0,
                reminders = listOf(EventReminderRecord(60 * 1000L))
        )

        val eventId = CalendarChangeManager(CalendarProvider).createEvent(this, calendar.calendarId, calendar.owner, details)

        if (eventId != -1L) {

            onEventCreated(startTime, eventId)
        } else {
            onCreateFailed()
        }
    }

    private fun onEventCreated(startTime: Long, eventId: Long) {
        DevLog.debug(LOG_TAG, "Event created: id=${eventId}")

        startActivity(
                Intent(this, ViewEventActivity::class.java)
                        .putExtra(Consts.INTENT_EVENT_ID_KEY, eventId)
                        .putExtra(Consts.INTENT_INSTANCE_START_TIME_KEY, startTime)
                        .putExtra(Consts.INTENT_SNOOZE_FROM_MAIN_ACTIVITY, true)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    private fun onCreateFailed() {
        DevLog.error(LOG_TAG, "Failed to create event")

        AlertDialog.Builder(this)
                .setMessage(R.string.new_event_failed_to_create_event)
                .setPositiveButton(android.R.string.ok) { _, _ -> }
                .show()
    }

    private fun createVoiceNote() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
//        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, mQuestion)
        startActivityForResult(intent, REQUEST_SPEECH_RECOGNIZER)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_SPEECH_RECOGNIZER) {
            if (resultCode == Activity.RESULT_OK) {
                val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                if (results != null) {
                    createNote(results[0])
                }
            }
        }
    }

    companion object {
        val LOG_TAG = "CreateNoteActivity"
    }
}
