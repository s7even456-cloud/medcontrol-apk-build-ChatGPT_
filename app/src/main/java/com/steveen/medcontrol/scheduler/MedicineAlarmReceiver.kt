package com.steveen.medcontrol.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.steveen.medcontrol.MedControlApplication
import com.steveen.medcontrol.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MedicineAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as MedControlApplication
                val repository = app.repository
                val scheduler = app.alarmScheduler
                val medicationId = intent.getLongExtra(AlarmScheduler.EXTRA_MEDICATION_ID, -1L)
                val scheduledDate = intent.getStringExtra(AlarmScheduler.EXTRA_SCHEDULED_DATE) ?: return@launch
                val scheduledTime = intent.getStringExtra(AlarmScheduler.EXTRA_SCHEDULED_TIME) ?: return@launch
                if (medicationId <= 0) return@launch
                val settings = repository.getSettings()
                val checkDueAt = System.currentTimeMillis() + settings.checkDelayMinutos * 60_000L
                val intake = repository.onAlarmFired(medicationId, scheduledDate, scheduledTime, checkDueAt)
                scheduler.scheduleCheckAlarm(intake.id, checkDueAt)
                scheduler.scheduleNextMedicationAlarm(medicationId, scheduledTime)
                val med = repository.getMedication(medicationId)
                if (settings.notificacionesActivadas && med != null) {
                    NotificationHelper.showMedicineAlarm(
                        context = context,
                        intakeId = intake.id,
                        medicationName = med.nombre,
                        scheduledTime = scheduledTime,
                        checkDelayMinutes = settings.checkDelayMinutos
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_MEDICINE_ALARM = "com.steveen.medcontrol.ACTION_MEDICINE_ALARM"
        const val ACTION_SNOOZED_ALARM = "com.steveen.medcontrol.ACTION_SNOOZED_ALARM"
    }
}
