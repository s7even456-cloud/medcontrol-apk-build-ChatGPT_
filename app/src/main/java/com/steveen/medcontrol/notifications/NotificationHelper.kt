package com.steveen.medcontrol.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.steveen.medcontrol.MainActivity
import com.steveen.medcontrol.R
import com.steveen.medcontrol.scheduler.AlarmScheduler

object NotificationHelper {
    private const val CHANNEL_MEDICINE = "medicine_reminders"
    private const val CHANNEL_STOCK = "stock_alerts"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val medicine = NotificationChannel(
                CHANNEL_MEDICINE,
                "Recordatorios de medicación",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarmas y checks de confirmación de tomas"
                enableVibration(true)
            }
            val stock = NotificationChannel(
                CHANNEL_STOCK,
                "Avisos de stock",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Avisos para comprar o reponer medicación"
            }
            manager.createNotificationChannel(medicine)
            manager.createNotificationChannel(stock)
        }
    }

    fun showMedicineAlarm(context: Context, intakeId: Long, medicationName: String, scheduledTime: String, checkDelayMinutes: Int) {
        if (!canPostNotifications(context)) return
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmScheduler.EXTRA_INTAKE_ID, intakeId)
        }
        val contentPi = PendingIntent.getActivity(
            context,
            AlarmScheduler.requestCode("open-$intakeId"),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val confirmPi = actionPendingIntent(context, MedicineActionReceiver.ACTION_CONFIRM, intakeId)
        val snoozePi = actionPendingIntent(context, MedicineActionReceiver.ACTION_SNOOZE_10, intakeId)
        val notification = NotificationCompat.Builder(context, CHANNEL_MEDICINE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Toca tomar: $medicationName")
            .setContentText("Hora programada: $scheduledTime. El check aparecerá en $checkDelayMinutes min.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(false)
            .setContentIntent(contentPi)
            .addAction(0, "Confirmar", confirmPi)
            .addAction(0, "Posponer 10 min", snoozePi)
            .build()
        NotificationManagerCompat.from(context).notify(intakeId.toInt(), notification)
    }

    fun showCheckPending(context: Context, intakeId: Long, medicationName: String, scheduledTime: String) {
        if (!canPostNotifications(context)) return
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmScheduler.EXTRA_INTAKE_ID, intakeId)
        }
        val contentPi = PendingIntent.getActivity(
            context,
            AlarmScheduler.requestCode("open-check-$intakeId"),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val confirmPi = actionPendingIntent(context, MedicineActionReceiver.ACTION_CONFIRM, intakeId)
        val missedPi = actionPendingIntent(context, MedicineActionReceiver.ACTION_MARK_MISSED, intakeId)
        val snoozePi = actionPendingIntent(context, MedicineActionReceiver.ACTION_SNOOZE_10, intakeId)
        val notification = NotificationCompat.Builder(context, CHANNEL_MEDICINE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Confirma la toma")
            .setContentText("$medicationName · programada a las $scheduledTime")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(false)
            .setContentIntent(contentPi)
            .addAction(0, "Tomada", confirmPi)
            .addAction(0, "No tomada", missedPi)
            .addAction(0, "Posponer 10 min", snoozePi)
            .build()
        NotificationManagerCompat.from(context).notify(intakeId.toInt(), notification)
    }

    fun showStockLow(context: Context, medicationName: String, daysRemaining: Double) {
        if (!canPostNotifications(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_STOCK)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Stock bajo: $medicationName")
            .setContentText("Quedan aproximadamente ${String.format("%.1f", daysRemaining)} días. Revisa la reposición.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(AlarmScheduler.requestCode("stock-$medicationName"), notification)
    }

    fun dismiss(context: Context, intakeId: Long) {
        NotificationManagerCompat.from(context).cancel(intakeId.toInt())
    }

    private fun actionPendingIntent(context: Context, action: String, intakeId: Long): PendingIntent {
        val intent = Intent(context, MedicineActionReceiver::class.java).apply {
            this.action = action
            putExtra(AlarmScheduler.EXTRA_INTAKE_ID, intakeId)
        }
        return PendingIntent.getBroadcast(
            context,
            AlarmScheduler.requestCode("$action-$intakeId"),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}
