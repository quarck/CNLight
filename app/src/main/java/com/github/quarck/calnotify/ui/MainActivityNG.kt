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

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.github.quarck.calnotify.BuildConfig
import com.github.quarck.calnotify.R
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.app.ApplicationController
import com.github.quarck.calnotify.permissions.PermissionsManager
import com.github.quarck.calnotify.utils.logs.DevLog
import com.github.quarck.calnotify.utils.powerManager
import org.jetbrains.annotations.NotNull

class MainActivityNG : AppCompatActivity() {

    private val settings: Settings by lazy { Settings(this) }

    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_ng)

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        // supportActionBar?.setDisplayShowHomeEnabled(true) ?? probably never need anymore

        window.navigationBarColor = ContextCompat.getColor(this, android.R.color.black)

        val drawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        val navController = findNavController(R.id.nav_host_fragment)

        appBarConfiguration = AppBarConfiguration(
                setOf(
                    R.id.nav_home,
                    R.id.nav_oncoming,
                    R.id.nav_complete,
                    R.id.nav_about,
                    R.id.nav_settings
                ),
                drawerLayout)

        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    public override fun onStart() {
        super.onStart()
        DevLog.info(LOG_TAG, "onStart()")
        ApplicationController.onMainActivityStarted(this);
    }

    public override fun onStop() {
        DevLog.info(LOG_TAG, "onStop()")
        super.onStop()
    }

    public override fun onResume() {
        super.onResume()
        DevLog.info(LOG_TAG, "onResume()")

        checkPermissions()

        // do this only when we are in the activity launched form the notification?
//        val navController = findNavController(R.id.nav_host_fragment)
//        navController.navigate(R.id.nav_home)
    }


    @SuppressLint("BatteryLife")
    private fun checkPermissions() {
        val hasPermissions = PermissionsManager.hasAllPermissions(this)

        if (!hasPermissions) {
            if (PermissionsManager.shouldShowRationale(this)) {
                AlertDialog.Builder(this)
                        .setMessage(R.string.application_has_no_access)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok) {
                            _, _ ->
                            PermissionsManager.requestPermissions(this)
                        }
                        .setNegativeButton(R.string.exit) {
                            _, _ ->
                            this@MainActivityNG.finish()
                        }
                        .create()
                        .show()
            }
            else {
                PermissionsManager.requestPermissions(this)
            }
        }
        else {
            // if we have essential permissions - now check for power manager optimisations
            if (!settings.doNotShowBatteryOptimisationWarning) {
                if (!powerManager.isIgnoringBatteryOptimizations(BuildConfig.APPLICATION_ID)) {
                    AlertDialog.Builder(this)
                            .setTitle(getString(R.string.battery_optimisation_title))
                            .setMessage(getString(R.string.battery_optimisation_details))
                            .setPositiveButton(getString(R.string.you_can_do_it)) {
                                _, _ ->
                                val intent = Intent()
                                        .setAction(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                        .setData(Uri.parse("package:" + BuildConfig.APPLICATION_ID))
                                startActivity(intent)
                            }
                            .setNeutralButton(getString(R.string.you_can_do_it_later)) {
                                _, _ ->
                            }
                            .setNegativeButton(getString(R.string.you_cannot_do_it)) {
                                _, _ ->
                                settings.doNotShowBatteryOptimisationWarning = true
                            }
                            .create()
                            .show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, @NotNull permissions: Array<out String>, @NotNull grantResults: IntArray) {
        for (result in grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                DevLog.error(LOG_TAG, "Permission is not granted!")
            }
        }
    }

    companion object {
        private const val LOG_TAG = "MainActivityNG"
    }
}