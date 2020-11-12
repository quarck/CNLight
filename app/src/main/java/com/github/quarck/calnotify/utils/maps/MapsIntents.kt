package com.github.quarck.calnotify.utils.maps

import android.content.Context
import android.content.Intent
import android.net.Uri

object MapsIntents {
    fun getLocationIntent(location: String)
            = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$location"));

    fun openLocation(context: Context, location: String)
            = context.startActivity(getLocationIntent(location))
}