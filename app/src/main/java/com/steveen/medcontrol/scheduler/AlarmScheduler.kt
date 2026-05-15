package com.steveen.medcontrol.scheduler

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import com.steveen.medcontrol.data.IntakeScheduleEntity
import com.steveen.medcontrol.data.MedControlRepository
import com.steveen.medcontrol.util.DateTimeUtils
import java.util.Locale
import kotlin.math.abs

class AlarmScheduler(
    val appContext: Context,
    private val repository: MedControlRepository
) {
    private val alarmManager: AlarmManager = appContext.getSystemService(AlarmManager::class.java)

    suspend fun scheduleAllActiveMedicationAlarms() {
        repository.ensureSchedulesForDate(DateTimeUtils.todayString())
        repository.getAllMedications().filter { it.activo }.forEach { med ->
            med.horasToma.forEach { time ->
                scheduleNextMedicationAlarm(med.id, time)
            }
        }
        repository.getAllIntakes().filter { it.pospuestaHasta != null && it.pospuestaHasta > System.currentTimeMillis() }.forEach { intake ->
            scheduleSnoozedAlarm(intake)
        }
    }

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else true
    }

    fun notificationsPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun scheduleNextMedicationAlarm(medicationId: Long, time: String) {
        val (date, triggerAt) = DateTimeUtils.nextOccurrenceMillis(time)
        val intent = Intent(appContext, MedicineAlarmReceiver::class.java).apply {
            action = MedicineAlarmReceiver.ACTION_MEDICINE_ALARM
            putExtra(EXTRA_MEDICATION_ID, medicationId)
            putExtra(EXTRA_SCHEDULED_DATE, date)
            putExtra(EXTRA_SCHEDULED_TIME, time)
        }
        val pi = PendingIntent.getBroadcast(
            appContext,
            requestCode("alarm-$medicationId-$date-$time"),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        schedule(triggerAt, pi)
    }

    fun scheduleCheckAlarm(intakeId: Long, triggerAt: Long) {
        val intent = Intent(appContext, CheckAlarmReceiver::class.java).apply {
            action = CheckAlarmReceiver.ACTION_CHECK_DUE
            putExtra(EXTRA_INTAKE_ID, intakeId)
        }
        val pi = PendingIntent.getBroadcast(
            appContext,
            requestCode("check-$intakeId"),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        schedule(triggerAt, pi)
    }

    fun scheduleSnoozedAlarm(intake: IntakeScheduleEntity) {
        val triggerAt = intake.pospuestaHasta ?: return
        val intent = Intent(appContext, MedicineAlarmReceiver::class.java).apply {
            action = MedicineAlarmReceiver.ACTION_SNOOZED_ALARM
            putExtra(EXTRA_MEDICATION_ID, intake.medicamentoId)
            putExtra(EXTRA_SCHEDULED_DATE, intake.fechaProgramada)
            putExtra(EXTRA_SCHEDULED_TIME, intake.horaProgramada)
            putExtra(EXTRA_INTAKE_ID, intake.id)
        }
        val pi = PendingIntent.getBroadcast(
            appContext,
            requestCode("snooze-${intake.id}"),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        schedule(triggerAt, pi)
    }

    private fun schedule(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        val safeTriggerAt = triggerAtMillis.coerceAtLeast(System.currentTimeMillis() + 1_000L)
        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                safeTriggerAt,
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                safeTriggerAt,
                pendingIntent
            )
        }
    }

    companion object {
        const val EXTRA_MEDICATION_ID = "extra_medication_id"
        const val EXTRA_SCHEDULED_DATE = "extra_scheduled_date"
        const val EXTRA_SCHEDULED_TIME = "extra_scheduled_time"
        const val EXTRA_INTAKE_ID = "extra_intake_id"

        fun requestCode(seed: String): Int = abs(seed.lowercase(Locale.ROOT).hashCode())
    }
}
