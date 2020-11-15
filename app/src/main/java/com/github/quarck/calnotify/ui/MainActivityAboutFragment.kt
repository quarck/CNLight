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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.github.quarck.calnotify.BuildConfig
import com.github.quarck.calnotify.R
import java.text.SimpleDateFormat
import java.util.*

class MainActivityAboutFragment : Fragment() {

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_about, container, false)

        val versionText = root.findViewById<TextView?>(R.id.text_view_app_version)

        context?.let {
            ctx ->
            val pInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0);
            versionText?.text = pInfo.versionName
        }

        root.findViewById<TextView?>(R.id.text_view_app_build_time)?.text =
                String.format(resources.getString(R.string.build_time_string_format), getBuildDate())

        root.findViewById<TextView?>(R.id.about_button_privacy_policy)?.setOnClickListener {
            context?.let { startActivity(Intent(it, PrivacyPolicyActivity::class.java)) }
        }

        root.findViewById<TextView?>(R.id.about_button_github)?.setOnClickListener {
            startActivity(Intent.parseUri(githubUri, 0))
        }

        return root
    }

    fun getBuildDate(): String {
        return SimpleDateFormat.getInstance().format(Date(BuildConfig.TIMESTAMP));
    }

    companion object {
        val githubUri = "https://github.com/quarck/CNLight"
    }
}