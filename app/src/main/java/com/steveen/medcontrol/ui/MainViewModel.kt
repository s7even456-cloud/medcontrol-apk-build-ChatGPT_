package com.steveen.medcontrol.ui

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.steveen.medcontrol.data.IntakeScheduleEntity
import com.steveen.medcontrol.data.MedControlRepository
import com.steveen.medcontrol.data.MedicationEntity
import com.steveen.medcontrol.data.SettingsEntity
import com.steveen.medcontrol.scheduler.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(
    private val repository: MedControlRepository,
    private val alarmScheduler: AlarmScheduler
) : ViewModel() {
    val medications: StateFlow<List<MedicationEntity>> = repository.medications.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val intakes: StateFlow<List<IntakeScheduleEntity>> = repository.intakes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings: StateFlow<SettingsEntity> = repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsEntity())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() { _message.value = null }

    fun addMedication(med: MedicationEntity) = safeLaunch("No se pudo añadir el medicamento") {
        repository.addMedication(med.nombre, med.unidadesPorCaja, med.stockActual, med.horasToma, med.notas, med.activo)
        alarmScheduler.scheduleAllActiveMedicationAlarms()
        _message.value = "Medicamento añadido."
    }

    fun updateMedication(med: MedicationEntity) = safeLaunch("No se pudo actualizar el medicamento") {
        repository.updateMedication(med)
        alarmScheduler.scheduleAllActiveMedicationAlarms()
        _message.value = "Medicamento actualizado."
    }

    fun deleteMedication(med: MedicationEntity) = safeLaunch("No se pudo eliminar") {
        repository.deleteMedication(med)
        alarmScheduler.scheduleAllActiveMedicationAlarms()
        _message.value = "Medicamento eliminado."
    }

    fun pauseMedication(id: Long) = safeLaunch("No se pudo pausar") {
        repository.pauseMedication(id)
        alarmScheduler.scheduleAllActiveMedicationAlarms()
        _message.value = "Medicamento pausado."
    }

    fun activateMedication(id: Long) = safeLaunch("No se pudo activar") {
        repository.activateMedication(id)
        alarmScheduler.scheduleAllActiveMedicationAlarms()
        _message.value = "Medicamento activado."
    }

    fun confirmIntake(id: Long) = safeLaunch("No se pudo confirmar la toma") {
        val confirmed = repository.confirmIntake(id)
        if (confirmed) _message.value = "Toma confirmada y stock descontado." else _message.value = "No se pudo confirmar: toma inexistente o ya marcada como no tomada."
    }

    fun markMissed(id: Long) = safeLaunch("No se pudo marcar como no tomada") {
        repository.markMissed(id)
        _message.value = "Toma marcada como no tomada."
    }

    fun snoozeIntake(id: Long, minutes: Int) = safeLaunch("No se pudo posponer") {
        val updated = repository.snoozeIntake(id, minutes)
        if (updated != null) alarmScheduler.scheduleSnoozedAlarm(updated)
        _message.value = "Toma pospuesta $minutes min."
    }

    fun addBox(medicationId: Long) = safeLaunch("No se pudo añadir caja") {
        repository.addBox(medicationId)
        _message.value = "Caja añadida al stock."
    }

    fun adjustStock(medicationId: Long, newStock: Int) = safeLaunch("No se pudo ajustar stock") {
        repository.adjustStock(medicationId, newStock)
        _message.value = "Stock ajustado."
    }

    fun updateSettings(settings: SettingsEntity) = safeLaunch("No se pudieron guardar ajustes") {
        repository.updateSettings(settings)
        alarmScheduler.scheduleAllActiveMedicationAlarms()
        _message.value = "Ajustes guardados."
    }

    fun deleteAllData() = safeLaunch("No se pudieron reiniciar los datos") {
        repository.deleteAllDataAndReseed()
        alarmScheduler.scheduleAllActiveMedicationAlarms()
        _message.value = "Datos reiniciados con medicamentos iniciales."
    }

    fun daysRemaining(med: MedicationEntity): Double = repository.daysRemaining(med)

    fun exportCsv() = safeLaunch("No se pudo exportar CSV") {
        val csv = repository.exportCsv()
        writeShareableFile("medcontrol_historial_${stamp()}.csv", csv)
        _message.value = "CSV exportado en carpeta privada de la app."
    }

    fun exportJson() = safeLaunch("No se pudo exportar JSON") {
        val json = repository.exportJson()
        writeShareableFile("medcontrol_backup_${stamp()}.json", json)
        _message.value = "Backup JSON exportado en carpeta privada de la app."
    }

    fun importJsonFromUri(context: Context, uri: Uri) = safeLaunch("No se pudo importar JSON") {
        val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Archivo vacío o no legible.")
        repository.importJson(json)
        alarmScheduler.scheduleAllActiveMedicationAlarms()
        _message.value = "Backup importado correctamente."
    }

    private fun safeLaunch(errorPrefix: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() }
            catch (e: Throwable) { _message.value = "$errorPrefix: ${e.message ?: "error desconocido"}" }
        }
    }

    private fun writeShareableFile(fileName: String, content: String) {
        val context = alarmSchedulerContext()
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "exports")
        dir.mkdirs()
        val file = File(dir, fileName)
        file.writeText(content, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = if (fileName.endsWith(".json")) "application/json" else "text/csv"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(android.content.Intent.createChooser(share, "Compartir exportación").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun alarmSchedulerContext(): Context = alarmScheduler.appContext

    private fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    companion object {
        fun factory(repository: MedControlRepository, alarmScheduler: AlarmScheduler): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(repository, alarmScheduler) as T
            }
        }
    }
}
