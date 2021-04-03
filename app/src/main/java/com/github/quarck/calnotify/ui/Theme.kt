package com.github.quarck.calnotify.ui

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import com.github.quarck.calnotify.R

object Theme {
    fun apply(activity: Activity) {
        val currentTheme = if (isSystemInDarkTheme(activity))
            R.style.CalendarAppThemeBlackBlue
        else
            R.style.CalendarAppThemeLightBlue

        activity.setTheme(currentTheme)
    }

    private fun isSystemInDarkTheme(activity: Activity): Boolean {
        return activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }

    @ColorInt
    fun resolveColor(context: Context, @AttrRes attr: Int): Int {
        val typedValue = TypedValue()
        val theme = context.theme
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

}