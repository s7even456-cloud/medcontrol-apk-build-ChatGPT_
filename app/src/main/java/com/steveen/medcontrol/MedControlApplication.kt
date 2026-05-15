package com.steveen.medcontrol

import android.app.Application
import com.steveen.medcontrol.data.MedControlDatabase
import com.steveen.medcontrol.data.MedControlRepository
import com.steveen.medcontrol.notifications.NotificationHelper
import com.steveen.medcontrol.scheduler.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MedControlApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: MedControlDatabase by lazy { MedControlDatabase.getDatabase(this) }
    val repository: MedControlRepository by lazy { MedControlRepository(database) }
    val alarmScheduler: AlarmScheduler by lazy { AlarmScheduler(this, repository) }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        appScope.launch {
            repository.seedInitialDataIfEmpty()
            alarmScheduler.scheduleAllActiveMedicationAlarms()
        }
    }
}
