package com.steveen.medcontrol.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.steveen.medcontrol.MedControlApplication
import com.steveen.medcontrol.scheduler.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MedicineActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as MedControlApplication
                val repo = app.repository
                val scheduler = app.alarmScheduler
                val intakeId = intent.getLongExtra(AlarmScheduler.EXTRA_INTAKE_ID, -1L)
                if (intakeId <= 0) return@launch
                when (intent.action) {
                    ACTION_CONFIRM -> {
                        repo.confirmIntake(intakeId)
                        NotificationHelper.dismiss(context, intakeId)
                    }
                    ACTION_MARK_MISSED -> {
                        repo.markMissed(intakeId)
                        NotificationHelper.dismiss(context, intakeId)
                    }
                    ACTION_SNOOZE_10 -> {
                        val updated = repo.snoozeIntake(intakeId, 10)
                        if (updated != null) scheduler.scheduleSnoozedAlarm(updated)
                        NotificationHelper.dismiss(context, intakeId)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_CONFIRM = "com.steveen.medcontrol.ACTION_CONFIRM"
        const val ACTION_MARK_MISSED = "com.steveen.medcontrol.ACTION_MARK_MISSED"
        const val ACTION_SNOOZE_10 = "com.steveen.medcontrol.ACTION_SNOOZE_10"
    }
}
