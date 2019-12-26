package com.github.quarck.calnotify.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.github.quarck.calnotify.logs.DevLog

class KillGoogleCalendarNotificationsService : NotificationListenerService(){

    private val googleCalPackage = "com.google.android.calendar"

    override fun onNotificationPosted(sbnotification: StatusBarNotification?) {
        if (sbnotification == null)
            return

        val isGoogleCalendar = sbnotification.packageName == googleCalPackage
        if (!isGoogleCalendar)
            return

        val notification = sbnotification.notification

        val hasActions = notification.actions != null

        val extras = notification.extras
        val extraBigTitle = (extras.get(Notification.EXTRA_TITLE_BIG) as CharSequence?)?.toString()
        val extraTitle = (extras.get(Notification.EXTRA_TITLE) as CharSequence?)?.toString()

        val title = extraBigTitle ?: extraTitle ?: ""

        DevLog.debug(LOG_TAG,"title: $title, hasActions: $hasActions")

        if (title != "" && !hasActions) {
            cancelNotification(sbnotification.key)
        }
    }

    override fun onNotificationRemoved(notification: StatusBarNotification?) {
    }

    companion object {
        private const val LOG_TAG = "KillGoogleCalendarNotificationsService"
    }
}
