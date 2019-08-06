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

package com.github.quarck.calnotify.utils

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.os.Vibrator
import android.widget.TimePicker
import com.github.quarck.calnotify.Consts
import com.github.quarck.calnotify.logs.DevLog


@Suppress("UNCHECKED_CAST")
fun <T> Context.service(svc: String) = getSystemService(svc) as T

val Context.alarmManager: AlarmManager
    get() = service(Context.ALARM_SERVICE)

val Context.audioManager: AudioManager
    get() = service(Context.AUDIO_SERVICE)

val Context.powerManager: PowerManager
    get() = service(Context.POWER_SERVICE)

val Context.vibratorService: Vibrator
    get() = service(Context.VIBRATOR_SERVICE)

val Context.notificationManager: NotificationManager
    get() = service(Context.NOTIFICATION_SERVICE)

fun wakeLocked(pm: PowerManager, timeout: Long, levelAndFlags: Int, tag: String, fn: () -> Unit) {

    val wakeLock = pm.newWakeLock(levelAndFlags, tag) ?: throw Exception("Failed to acquire wakelock")

    try {
        wakeLock.acquire(timeout)
        fn()
    }
    finally {
        try {
            wakeLock.release()
        }
        catch (ex: Exception) {
            // ignore
        }
    }
}

fun partialWakeLocked(ctx: Context, timeout: Long, tag: String, fn: () -> Unit) =
        wakeLocked(ctx.powerManager, timeout, PowerManager.PARTIAL_WAKE_LOCK, tag, fn)

val isMarshmallowOrAbove: Boolean
    get() = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M

val isLollipopOrAbove: Boolean
    get() = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP

val isKitkatOrAbove: Boolean
    get() = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT

@SuppressLint("NewApi")
fun AlarmManager.setExactAndAlarm(
        context: Context,
        useSetAlarmClock: Boolean, // settings: Settings,
        triggerAtMillis: Long,
        roughIntentClass: Class<*>, // ignored on KitKat and below
        exactIntentClass: Class<*>,
        alarmInfoIntent: Class<*>
) {
    val LOG_TAG = "AlarmManager.setExactAndAlarm"

    if (isMarshmallowOrAbove) {
        // setExactAndAllowWhileIdle supposed to work during idle / doze standby, but it is very non-precise
        // so set it as a "first thing", followed by more precise alarm
        val intent = Intent(context, roughIntentClass);
        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis + Consts.ALARM_THRESHOLD / 3, pendingIntent);


        // add more precise alarm, depending on the setting it is a setAlarmClock or "setExact"
        // setAlarmClock is very precise, but it shows UI indicating that alarm is pending
        // on the other hand setExact is more precise than setExactAndAllowWhileIdle, but it can't
        // fire during doze / standby

        val intentExact = Intent(context, exactIntentClass);
        val pendingIntentExact = PendingIntent.getBroadcast(context, 0, intentExact, PendingIntent.FLAG_UPDATE_CURRENT)

        //if (settings.useSetAlarmClock) {
        if (useSetAlarmClock) {

            val intentInfo = Intent(context, alarmInfoIntent);
            val pendingIntentInfo = PendingIntent.getActivity(context, 0, intentInfo, PendingIntent.FLAG_UPDATE_CURRENT)

            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, pendingIntentInfo)

            setAlarmClock(
                    alarmClockInfo,
                    pendingIntentExact)

            DevLog.info(LOG_TAG, "alarm scheduled for $triggerAtMillis using setExactAndAllowWhileIdle(T+8s) + setAlarmClock(T+0)")
        }
        else {
            setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntentExact);

            DevLog.info(LOG_TAG, "alarm scheduled for $triggerAtMillis using setExactAndAllowWhileIdle(T+8s) + setExact(T+0)")
        }
    }
    else {
        val intent = Intent(context, exactIntentClass);
        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        if (isKitkatOrAbove) {
            // KitKat way
            setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            DevLog.info(LOG_TAG, "alarm scheduled for $triggerAtMillis using setExact(T+0)")
        }
        else {
            // Ancient way
            set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            DevLog.info(LOG_TAG, "alarm scheduled for $triggerAtMillis using set(T+0)")
        }
    }
}

fun AlarmManager.cancelExactAndAlarm(
        context: Context,
        roughIntentClass: Class<*>,
        exactIntentClass: Class<*>
) {
    val LOG_TAG = "AlarmManager.cancelExactAndAlarm"
    // reverse of the prev guy

    val intent = Intent(context, roughIntentClass);
    val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    cancel(pendingIntent);

    if (isMarshmallowOrAbove) {
        val intentExact = Intent(context, exactIntentClass);
        val pendingIntentExact = PendingIntent.getBroadcast(context, 0, intentExact, PendingIntent.FLAG_UPDATE_CURRENT)
        cancel(pendingIntentExact)
    }

    DevLog.info(LOG_TAG, "Cancelled alarm")
}

@Suppress("DEPRECATION")
var TimePicker.hourCompat: Int
    get() {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            this.hour
        else
            this.currentHour
    }
    set(value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            this.hour = value
        else
            this.currentHour = value
    }

@Suppress("DEPRECATION")
var TimePicker.minuteCompat: Int
    get() {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            this.minute
        else
            this.currentMinute
    }
    set(value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            this.minute = value
        else
            this.currentMinute = value
    }


val Exception.detailed: String
    get() {
        return "${this}: ${this.message}, stack: ${this.stackTrace.joinToString("\n")}"
    }
