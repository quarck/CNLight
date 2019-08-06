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

package com.github.quarck.calnotify.quiethours

import android.content.Context
import com.github.quarck.calnotify.Settings
import com.github.quarck.calnotify.bluetooth.BTDeviceManager
import com.github.quarck.calnotify.logs.DevLog
//import com.github.quarck.calnotify.logs.Logger
import com.github.quarck.calnotify.utils.DateTimeUtils
import com.github.quarck.calnotify.utils.addDays
import java.util.*

class QuietHoursManager (val ctx: Context) : QuietHoursManagerInterface {

    private val btDeviceManager: BTDeviceManager by lazy { BTDeviceManager(ctx) }

    private fun isEnabled(settings: Settings)
            = settings.quietHoursEnabled && (settings.quietHoursFrom != settings.quietHoursTo)

    override fun startManualQuietPeriod(settings: Settings, until: Long) {
        settings.manualQuietPeriodUntil = until
    }

    override fun stopManualQuietPeriod(settings: Settings) {
        settings.manualQuietPeriodUntil = 0L
    }

    override fun isInsideQuietPeriod(settings: Settings, time: Long) =
            getSilentUntil(settings, time) > time

    override fun isInsideQuietPeriod(settings: Settings, currentTimes: LongArray) =
            getSilentUntil(settings, currentTimes).map { it -> it > 0L }.toBooleanArray()

    override fun isCustomQuietHoursActive(settings: Settings): Boolean =
            (settings.manualQuietPeriodUntil > System.currentTimeMillis())

    // returns time in millis, when silent period ends,
    // or 0 if we are not on silent
    override fun getSilentUntil(settings: Settings, time: Long): Long {

        var ret = 0L

        val currentTime = if (time != 0L) time else System.currentTimeMillis()
        var manualEnd = Math.max(settings.manualQuietPeriodUntil, btDeviceManager.carModeSilentUntil)
        if (manualEnd < currentTime) {
            manualEnd = 0L
        }

        if (currentTime < manualEnd) {
            // we are in the manual quiet hours, but by the end of it we could hit the regular - double
            // check for that
            ret = Math.max(manualEnd, getSilentUntilImpl(settings, manualEnd))
        } else {
            ret = getSilentUntilImpl(settings, currentTime)
        }

        return ret
    }

    private fun getSilentUntilImpl(settings: Settings, currentTime: Long): Long {
        var ret: Long = 0

        if (!isEnabled(settings)) {
            return 0
        }

        val cal = Calendar.getInstance()
        cal.timeInMillis = currentTime

        val from = settings.quietHoursFrom
        val silentFrom = DateTimeUtils.createCalendarTime(currentTime, from.component1(), from.component2())

        val to = settings.quietHoursTo
        val silentTo = DateTimeUtils.createCalendarTime(currentTime, to.component1(), to.component2())

        DevLog.debug(LOG_TAG, "getSilentUntil: ct=$currentTime, $from to $to")

        // Current silent period could have started yesterday, so account for this by rolling it back to one day
        silentFrom.addDays(-1)
        silentTo.addDays(-1)

        // Check if "from" is before "to", otherwise add an extra day to "to"
        if (silentTo.before(silentFrom))
            silentTo.addDays(1)

        var cnt = 0

        while (silentFrom.before(cal)) {

            if (cal.after(silentFrom) && cal.before(silentTo)) {
                // this hits silent period -- so it should be silent until 'silentTo'
                ret = silentTo.timeInMillis
                DevLog.debug(LOG_TAG, "Time hits silent period range, would be silent for ${(ret - currentTime) / 1000L} seconds since expected wake up time")
                break
            }

            silentFrom.addDays(1)
            silentTo.addDays(1)

            if (++cnt > 1000)
                break
        }

        return ret
    }

    override fun getSilentUntil(settings: Settings, currentTimes: LongArray): LongArray {

        val manualEndValue = Math.max(settings.manualQuietPeriodUntil, btDeviceManager.carModeSilentUntil)

        val tmp = LongArray(currentTimes.size)

        for (i in 0 until currentTimes.size) {
            if (currentTimes[i] < manualEndValue) {
                tmp[i] = manualEndValue
            } else {
                tmp[i] = currentTimes[i]
            }
        }

        val ret = getSilentUntilImpl(settings, tmp)

        for (i in 0 until currentTimes.size) {
            val manualEnd = if (currentTimes[i] < manualEndValue) manualEndValue else 0L

            if (ret[i] < manualEnd)
                ret[i] = manualEnd
        }

        return ret
    }

    private fun getSilentUntilImpl(settings: Settings, currentTimes: LongArray): LongArray {

        val ret = LongArray(currentTimes.size)

        if (!isEnabled(settings))
            return ret

        if (ret.isEmpty())
            return ret

        val cals =
                Array<Calendar>(ret.size) { idx ->
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = currentTimes[idx]
                    cal
                }

        val from = settings.quietHoursFrom
        val silentFrom: Calendar = DateTimeUtils.createCalendarTime(currentTimes[0], from.component1(), from.component2())

        val to = settings.quietHoursTo
        val silentTo = DateTimeUtils.createCalendarTime(currentTimes[0], to.component1(), to.component2())

        // Current silent period could have started yesterday, so account for this by rolling it back to one day
        silentFrom.addDays(-1)
        silentTo.addDays(-1)

        // Check if "from" is before "to", otherwise add an extra day to "to"
        if (silentTo.before(silentFrom))
            silentTo.addDays(1)

        var cnt = 0

        while (true) {

            var allPassed = true

            for ((idx, cal) in cals.withIndex()) {

                if (silentFrom.before(cal)) {
                    allPassed = false
                }

                if (cal.after(silentFrom) && cal.before(silentTo))
                // this hits silent period -- so it should be silent until 'silentTo'
                    ret[idx] = silentTo.timeInMillis
            }

            if (allPassed)
                break

            silentFrom.addDays(1)
            silentTo.addDays(1)

            if (++cnt > 1000)
                break
        }

        return ret
    }

    companion object {
        private const val LOG_TAG = "QuietPeriodManager"
    }
}
