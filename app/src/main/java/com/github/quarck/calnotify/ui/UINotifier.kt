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

package com.github.quarck.calnotify.ui

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.github.quarck.calnotify.Consts

object UINotifier {
    fun notify(context: Context?, isUserAction: Boolean) {

        if (context == null)
            return

        val dataUpdatedBroadcast = Intent(Consts.DATA_UPDATED_BROADCAST)
        dataUpdatedBroadcast.putExtra(Consts.INTENT_IS_USER_ACTION, isUserAction);
        context.sendBroadcast(dataUpdatedBroadcast)
    }
}
