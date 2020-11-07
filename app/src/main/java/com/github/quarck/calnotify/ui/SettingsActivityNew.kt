package com.github.quarck.calnotify.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import android.view.View
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.logs.DevLog
import com.github.quarck.calnotify.prefs.CalendarsActivity
import com.github.quarck.calnotify.prefs.activities.*
import com.github.quarck.calnotify.utils.find

class SettingsActivityNew : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_settings)

        setSupportActionBar(find<Toolbar?>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        window.navigationBarColor = resources.getColor(android.R.color.black)

        DevLog.info(LOG_TAG, "onCreate")
    }

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onButtonHandledCalendars(v: View?) =
        startActivity(
                Intent(this, CalendarsActivity::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onButtonNotificaitonSettings(v: View?) =
            startActivity(
                    Intent(this, NotificationSettingsActivity::class.java)
                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onButtonBehaviourSettings(v: View?) =
            startActivity(
                    Intent(this, BehaviorSettingsActivity::class.java)
                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

    @Suppress("unused", "UNUSED_PARAMETER")
    fun onButtonMiscSettings(v: View?) =
            startActivity(
                    Intent(this, MiscSettingsActivity::class.java)
                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))

    companion object {
        private const val LOG_TAG = "SettingsActivityNew"
    }

}