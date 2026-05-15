package com.steveen.medcontrol.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.steveen.medcontrol.MedControlApplication
import com.steveen.medcontrol.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CheckAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as MedControlApplication
                val repository = app.repository
                val intakeId = intent.getLongExtra(AlarmScheduler.EXTRA_INTAKE_ID, -1L)
                if (intakeId <= 0) return@launch
                val intake = repository.onCheckDue(intakeId) ?: return@launch
                val med = repository.getMedication(intake.medicamentoId) ?: return@launch
                val settings = repository.getSettings()
                if (settings.notificacionesActivadas) {
                    NotificationHelper.showCheckPending(
                        context = context,
                        intakeId = intake.id,
                        medicationName = med.nombre,
                        scheduledTime = intake.horaProgramada
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_CHECK_DUE = "com.steveen.medcontrol.ACTION_CHECK_DUE"
    }
}
