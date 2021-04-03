//
//   Calendar Notifications Plus
//   Copyright (C) 2020 Sergey Parshin (s.parshin.sc@gmail.com)
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

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.BuildConfig
import com.github.quarck.calnotify.R
import java.text.SimpleDateFormat
import java.util.*

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        DevLog.info("AboutActivity", "onCreate")

        super.onCreate(savedInstanceState)
        Theme.apply(this)

        setContentView(R.layout.activity_about)

        setSupportActionBar(findViewById<Toolbar?>(R.id.toolbar))
        supportActionBar?.let {
            it.setDisplayHomeAsUpEnabled(true)
            it.setHomeAsUpIndicator(R.drawable.ic_arrow_back)
            it.setDisplayShowHomeEnabled(true)
        }

        window.navigationBarColor = ContextCompat.getColor(this, android.R.color.black)

        val versionText = findViewById<TextView?>(R.id.text_view_app_version)

        val pInfo = packageManager.getPackageInfo(this.packageName, 0);
        versionText?.text = pInfo.versionName

        findViewById<TextView?>(R.id.text_view_app_build_time)?.text =
                String.format(resources.getString(R.string.build_time_string_format), getBuildDate())

        findViewById<TextView?>(R.id.about_button_privacy_policy)?.setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }

        findViewById<TextView?>(R.id.about_button_github)?.setOnClickListener {
            startActivity(Intent.parseUri(githubUri, 0))
        }
    }

    fun getBuildDate(): String {
        return SimpleDateFormat.getInstance().format(Date(BuildConfig.TIMESTAMP));
    }

    companion object {
        val githubUri = "https://github.com/quarck/CNLight"
    }
}