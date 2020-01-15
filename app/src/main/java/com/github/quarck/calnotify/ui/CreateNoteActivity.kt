package com.github.quarck.calnotify.ui

import android.app.AlertDialog
import android.content.Intent
import android.support.v7.app.AppCompatActivity
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
    var calendarProvider: CalendarProviderInterface = CalendarProvider
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
            DevLog.debug(LOG_TAG, "Event created: id=${eventId}")

            val nextReminder = calendarProvider.getNextEventReminderTime(this, eventId, startTime)
            if (nextReminder != 0L) {
                Toast.makeText(
                        this,
                        resources.getString(R.string.event_was_created_reminder_at).format(dateToStr(this, nextReminder)),
                        Toast.LENGTH_LONG
                ).show()
            }
            else {
                Toast.makeText(this, R.string.event_was_created, Toast.LENGTH_LONG).show()
            }
            finish()

        } else {
            DevLog.error(LOG_TAG, "Failed to create event")

            AlertDialog.Builder(this)
                    .setMessage(R.string.new_event_failed_to_create_event)
                    .setPositiveButton(android.R.string.ok) { _, _ -> }
                    .show()
        }
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
