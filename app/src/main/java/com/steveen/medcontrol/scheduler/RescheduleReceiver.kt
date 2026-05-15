package com.steveen.medcontrol.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.steveen.medcontrol.MedControlApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as MedControlApplication
                app.repository.ensureSchedulesForDate(com.steveen.medcontrol.util.DateTimeUtils.todayString())
                app.alarmScheduler.scheduleAllActiveMedicationAlarms()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
