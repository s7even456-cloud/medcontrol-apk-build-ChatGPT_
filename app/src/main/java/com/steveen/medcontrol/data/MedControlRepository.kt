package com.steveen.medcontrol.data

import androidx.room.withTransaction
import com.steveen.medcontrol.util.DateTimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class MedControlRepository(private val db: MedControlDatabase) {
    private val medicationDao = db.medicationDao()
    private val intakeDao = db.intakeDao()
    private val stockEventDao = db.stockEventDao()
    private val settingsDao = db.settingsDao()

    val medications: Flow<List<MedicationEntity>> = medicationDao.observeAll()
    val intakes: Flow<List<IntakeScheduleEntity>> = intakeDao.observeAll()
    val stockEvents: Flow<List<StockEventEntity>> = stockEventDao.observeAll()
    val settings: Flow<SettingsEntity> = settingsDao.observe().map { it ?: SettingsEntity() }

    suspend fun seedInitialDataIfEmpty() {
        if (settingsDao.get() == null) settingsDao.upsert(SettingsEntity())
        if (medicationDao.count() == 0) {
            val now = System.currentTimeMillis()
            medicationDao.insert(
                MedicationEntity(
                    nombre = "Clopidogrel",
                    unidadesPorCaja = 28,
                    stockActual = 27,
                    horasToma = listOf("14:57"),
                    notas = "Dato inicial editable. No modifica dosis ni pauta médica.",
                    fechaCreacion = now,
                    fechaActualizacion = now
                )
            )
            medicationDao.insert(
                MedicationEntity(
                    nombre = "Ezetimiba",
                    unidadesPorCaja = 28,
                    stockActual = 27,
                    horasToma = listOf("14:57"),
                    notas = "Dato inicial editable. No modifica dosis ni pauta médica.",
                    fechaCreacion = now,
                    fechaActualizacion = now
                )
            )
            medicationDao.insert(
                MedicationEntity(
                    nombre = "Dercutane 5mg",
                    unidadesPorCaja = 50,
                    stockActual = 49,
                    horasToma = listOf("23:57"),
                    notas = "Dato inicial editable. No modifica dosis ni pauta médica.",
                    fechaCreacion = now,
                    fechaActualizacion = now
                )
            )
        }
        ensureSchedulesForDate(DateTimeUtils.todayString())
    }

    suspend fun getAllMedications(): List<MedicationEntity> = medicationDao.getAll()
    suspend fun getMedication(id: Long): MedicationEntity? = medicationDao.getById(id)
    suspend fun getAllIntakes(): List<IntakeScheduleEntity> = intakeDao.getAll()
    suspend fun getAllStockEvents(): List<StockEventEntity> = stockEventDao.getAll()
    suspend fun getSettings(): SettingsEntity = settingsDao.get() ?: SettingsEntity()

    suspend fun addMedication(nombre: String, unidadesPorCaja: Int, stockActual: Int, horas: List<String>, notas: String, activo: Boolean = true): Long {
        require(nombre.isNotBlank()) { "El nombre es obligatorio." }
        require(unidadesPorCaja > 0) { "Las unidades por caja deben ser mayores que cero." }
        require(stockActual >= 0) { "El stock no puede ser negativo." }
        require(horas.isNotEmpty()) { "Debe existir al menos una hora de toma." }
        require(horas.all { DateTimeUtils.validTime(it) }) { "Todas las horas deben tener formato HH:mm." }
        val now = System.currentTimeMillis()
        val id = medicationDao.insert(
            MedicationEntity(
                nombre = nombre.trim(),
                unidadesPorCaja = unidadesPorCaja,
                stockActual = stockActual,
                horasToma = horas.distinct().sorted(),
                activo = activo,
                notas = notas.trim(),
                fechaCreacion = now,
                fechaActualizacion = now
            )
        )
        ensureSchedulesForDate(DateTimeUtils.todayString())
        return id
    }

    suspend fun updateMedication(entity: MedicationEntity) {
        require(entity.nombre.isNotBlank()) { "El nombre es obligatorio." }
        require(entity.unidadesPorCaja > 0) { "Las unidades por caja deben ser mayores que cero." }
        require(entity.stockActual >= 0) { "El stock no puede ser negativo." }
        require(entity.horasToma.isNotEmpty()) { "Debe existir al menos una hora de toma." }
        require(entity.horasToma.all { DateTimeUtils.validTime(it) }) { "Todas las horas deben tener formato HH:mm." }
        medicationDao.update(entity.copy(fechaActualizacion = System.currentTimeMillis()))
        ensureSchedulesForDate(DateTimeUtils.todayString())
    }

    suspend fun deleteMedication(entity: MedicationEntity) = medicationDao.delete(entity)

    suspend fun pauseMedication(id: Long) {
        val med = medicationDao.getById(id) ?: return
        medicationDao.update(med.copy(activo = false, fechaActualizacion = System.currentTimeMillis()))
    }

    suspend fun activateMedication(id: Long) {
        val med = medicationDao.getById(id) ?: return
        medicationDao.update(med.copy(activo = true, fechaActualizacion = System.currentTimeMillis()))
        ensureSchedulesForDate(DateTimeUtils.todayString())
    }

    suspend fun ensureSchedulesForDate(date: String) {
        medicationDao.getActive().forEach { med ->
            med.horasToma.forEach { time ->
                getOrCreateSchedule(med.id, date, time)
            }
        }
    }

    suspend fun getOrCreateSchedule(medicationId: Long, date: String, time: String): IntakeScheduleEntity {
        val existing = intakeDao.getByUnique(medicationId, date, time)
        if (existing != null) return existing
        val newEntity = IntakeScheduleEntity(
            medicamentoId = medicationId,
            fechaProgramada = date,
            horaProgramada = time,
            estado = IntakeState.PROGRAMADA
        )
        val id = intakeDao.insert(newEntity)
        return newEntity.copy(id = id)
    }

    suspend fun onAlarmFired(medicationId: Long, date: String, time: String, checkDueAt: Long): IntakeScheduleEntity {
        val existing = getOrCreateSchedule(medicationId, date, time)
        val updated = existing.copy(
            estado = IntakeState.ALARMA_SONADA,
            alarmaLanzadaEn = System.currentTimeMillis(),
            checkProgramadoEn = checkDueAt
        )
        intakeDao.update(updated)
        return updated
    }

    suspend fun onCheckDue(intakeId: Long): IntakeScheduleEntity? {
        val intake = intakeDao.getById(intakeId) ?: return null
        if (intake.estado == IntakeState.TOMADA || intake.estado == IntakeState.OLVIDADA_NO_TOMADA) return intake
        val updated = intake.copy(estado = IntakeState.CHECK_PENDIENTE)
        intakeDao.update(updated)
        return updated
    }

    suspend fun confirmIntake(intakeId: Long, observation: String = ""): Boolean {
        return db.withTransaction {
            val intake = intakeDao.getById(intakeId) ?: return@withTransaction false
            if (intake.estado == IntakeState.TOMADA) return@withTransaction true
            if (intake.estado == IntakeState.OLVIDADA_NO_TOMADA) return@withTransaction false
            val med = medicationDao.getById(intake.medicamentoId) ?: return@withTransaction false
            val previousStock = med.stockActual
            val newStock = (previousStock - 1).coerceAtLeast(0)
            val now = System.currentTimeMillis()
            medicationDao.update(med.copy(stockActual = newStock, fechaActualizacion = now))
            intakeDao.update(
                intake.copy(
                    estado = IntakeState.TOMADA,
                    confirmadaEn = now,
                    stockRestanteTrasToma = newStock,
                    observaciones = observation.ifBlank { intake.observaciones }
                )
            )
            stockEventDao.insert(
                StockEventEntity(
                    medicamentoId = med.id,
                    tipoEvento = StockEventType.DESCUENTO_POR_TOMA,
                    cantidad = -1,
                    stockAnterior = previousStock,
                    stockPosterior = newStock,
                    fechaEvento = now,
                    nota = "Toma confirmada ${intake.fechaProgramada} ${intake.horaProgramada}"
                )
            )
            true
        }
    }

    suspend fun markMissed(intakeId: Long, observation: String = "") {
        val intake = intakeDao.getById(intakeId) ?: return
        if (intake.estado == IntakeState.TOMADA) return
        intakeDao.update(
            intake.copy(
                estado = IntakeState.OLVIDADA_NO_TOMADA,
                olvidadaEn = System.currentTimeMillis(),
                observaciones = observation.ifBlank { intake.observaciones }
            )
        )
    }

    suspend fun snoozeIntake(intakeId: Long, minutes: Int): IntakeScheduleEntity? {
        require(minutes in 1..1440) { "La posposición debe estar entre 1 minuto y 24 horas." }
        val intake = intakeDao.getById(intakeId) ?: return null
        if (intake.estado == IntakeState.TOMADA || intake.estado == IntakeState.OLVIDADA_NO_TOMADA) return intake
        val until = System.currentTimeMillis() + minutes * 60_000L
        val updated = intake.copy(
            estado = IntakeState.POSPUESTA,
            pospuestaHasta = until,
            numeroPosposiciones = intake.numeroPosposiciones + 1
        )
        intakeDao.update(updated)
        return updated
    }

    suspend fun addBox(medicationId: Long, boxes: Int = 1, note: String = "Reposición de caja") {
        require(boxes > 0) { "El número de cajas debe ser mayor que cero." }
        val med = medicationDao.getById(medicationId) ?: return
        val previous = med.stockActual
        val quantity = med.unidadesPorCaja * boxes
        val newStock = previous + quantity
        val now = System.currentTimeMillis()
        medicationDao.update(med.copy(stockActual = newStock, fechaActualizacion = now))
        stockEventDao.insert(
            StockEventEntity(
                medicamentoId = medicationId,
                tipoEvento = StockEventType.REPOSICION_CAJA,
                cantidad = quantity,
                stockAnterior = previous,
                stockPosterior = newStock,
                fechaEvento = now,
                nota = note
            )
        )
    }

    suspend fun adjustStock(medicationId: Long, newStock: Int, note: String = "Ajuste manual") {
        require(newStock >= 0) { "El stock no puede ser negativo." }
        val med = medicationDao.getById(medicationId) ?: return
        val previous = med.stockActual
        val now = System.currentTimeMillis()
        medicationDao.update(med.copy(stockActual = newStock, fechaActualizacion = now))
        stockEventDao.insert(
            StockEventEntity(
                medicamentoId = medicationId,
                tipoEvento = StockEventType.AJUSTE_MANUAL,
                cantidad = newStock - previous,
                stockAnterior = previous,
                stockPosterior = newStock,
                fechaEvento = now,
                nota = note
            )
        )
    }

    suspend fun updateSettings(settings: SettingsEntity) {
        require(settings.checkDelayMinutos in 1..120) { "El retraso del check debe estar entre 1 y 120 minutos." }
        require(settings.diasAvisoStock in 1..90) { "El aviso de stock debe estar entre 1 y 90 días." }
        settingsDao.upsert(settings)
    }

    suspend fun deleteAllDataAndReseed() {
        db.withTransaction {
            stockEventDao.deleteAll()
            intakeDao.deleteAll()
            medicationDao.deleteAll()
            settingsDao.deleteAll()
        }
        seedInitialDataIfEmpty()
    }

    fun daysRemaining(medication: MedicationEntity): Double {
        val dailyDoses = medication.horasToma.size.coerceAtLeast(1)
        return medication.stockActual.toDouble() / dailyDoses.toDouble()
    }

    fun isLowStock(medication: MedicationEntity, settings: SettingsEntity): Boolean = daysRemaining(medication) <= settings.diasAvisoStock

    suspend fun exportCsv(): String {
        val meds = medicationDao.getAll().associateBy { it.id }
        val rows = intakeDao.getAll()
        val header = "fecha,medicamento,hora_programada,alarma_lanzada_en,check_programado_en,confirmada_en,olvidada_en,estado,posposiciones,stock_restante,observaciones"
        val body = rows.joinToString("\n") { intake ->
            listOf(
                intake.fechaProgramada,
                meds[intake.medicamentoId]?.nombre.orEmpty(),
                intake.horaProgramada,
                DateTimeUtils.displayMillis(intake.alarmaLanzadaEn),
                DateTimeUtils.displayMillis(intake.checkProgramadoEn),
                DateTimeUtils.displayMillis(intake.confirmadaEn),
                DateTimeUtils.displayMillis(intake.olvidadaEn),
                intake.estado.name,
                intake.numeroPosposiciones.toString(),
                intake.stockRestanteTrasToma?.toString().orEmpty(),
                intake.observaciones
            ).joinToString(",") { escapeCsv(it) }
        }
        return "$header\n$body"
    }

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    suspend fun exportJson(): String {
        val root = JSONObject()
        root.put("schema_version", 1)
        root.put("exported_at", System.currentTimeMillis())
        val settings = getSettings()
        root.put("settings", JSONObject().apply {
            put("check_delay_minutos", settings.checkDelayMinutos)
            put("dias_aviso_stock", settings.diasAvisoStock)
            put("formato_hora", settings.formatoHora)
            put("notificaciones_activadas", settings.notificacionesActivadas)
            put("fecha_ultima_copia_seguridad", settings.fechaUltimaCopiaSeguridad)
        })
        root.put("medications", JSONArray().apply {
            medicationDao.getAll().forEach { med ->
                put(JSONObject().apply {
                    put("id", med.id)
                    put("nombre", med.nombre)
                    put("unidades_por_caja", med.unidadesPorCaja)
                    put("stock_actual", med.stockActual)
                    put("horas_toma", JSONArray(med.horasToma))
                    put("activo", med.activo)
                    put("notas", med.notas)
                    put("fecha_creacion", med.fechaCreacion)
                    put("fecha_actualizacion", med.fechaActualizacion)
                })
            }
        })
        root.put("intakes", JSONArray().apply {
            intakeDao.getAll().forEach { intake ->
                put(JSONObject().apply {
                    put("id", intake.id)
                    put("medicamento_id", intake.medicamentoId)
                    put("fecha_programada", intake.fechaProgramada)
                    put("hora_programada", intake.horaProgramada)
                    put("estado", intake.estado.name)
                    put("alarma_lanzada_en", intake.alarmaLanzadaEn)
                    put("check_programado_en", intake.checkProgramadoEn)
                    put("confirmada_en", intake.confirmadaEn)
                    put("olvidada_en", intake.olvidadaEn)
                    put("pospuesta_hasta", intake.pospuestaHasta)
                    put("numero_posposiciones", intake.numeroPosposiciones)
                    put("stock_restante_tras_toma", intake.stockRestanteTrasToma)
                    put("observaciones", intake.observaciones)
                })
            }
        })
        root.put("stock_events", JSONArray().apply {
            stockEventDao.getAll().forEach { event ->
                put(JSONObject().apply {
                    put("id", event.id)
                    put("medicamento_id", event.medicamentoId)
                    put("tipo_evento", event.tipoEvento.name)
                    put("cantidad", event.cantidad)
                    put("stock_anterior", event.stockAnterior)
                    put("stock_posterior", event.stockPosterior)
                    put("fecha_evento", event.fechaEvento)
                    put("nota", event.nota)
                })
            }
        })
        settingsDao.upsert(settings.copy(fechaUltimaCopiaSeguridad = System.currentTimeMillis()))
        return root.toString(2)
    }

    suspend fun importJson(json: String) {
        val root = JSONObject(json)
        val meds = mutableListOf<MedicationEntity>()
        val medArray = root.optJSONArray("medications") ?: JSONArray()
        for (i in 0 until medArray.length()) {
            val o = medArray.getJSONObject(i)
            val hours = mutableListOf<String>()
            val arr = o.optJSONArray("horas_toma") ?: JSONArray()
            for (j in 0 until arr.length()) hours.add(arr.getString(j))
            meds.add(
                MedicationEntity(
                    id = o.getLong("id"),
                    nombre = o.getString("nombre"),
                    unidadesPorCaja = o.getInt("unidades_por_caja"),
                    stockActual = o.getInt("stock_actual"),
                    horasToma = hours,
                    activo = o.optBoolean("activo", true),
                    notas = o.optString("notas", ""),
                    fechaCreacion = o.optLong("fecha_creacion", System.currentTimeMillis()),
                    fechaActualizacion = o.optLong("fecha_actualizacion", System.currentTimeMillis())
                )
            )
        }
        val intakes = mutableListOf<IntakeScheduleEntity>()
        val intakeArray = root.optJSONArray("intakes") ?: JSONArray()
        for (i in 0 until intakeArray.length()) {
            val o = intakeArray.getJSONObject(i)
            intakes.add(
                IntakeScheduleEntity(
                    id = o.getLong("id"),
                    medicamentoId = o.getLong("medicamento_id"),
                    fechaProgramada = o.getString("fecha_programada"),
                    horaProgramada = o.getString("hora_programada"),
                    estado = IntakeState.valueOf(o.getString("estado")),
                    alarmaLanzadaEn = o.optLongOrNull("alarma_lanzada_en"),
                    checkProgramadoEn = o.optLongOrNull("check_programado_en"),
                    confirmadaEn = o.optLongOrNull("confirmada_en"),
                    olvidadaEn = o.optLongOrNull("olvidada_en"),
                    pospuestaHasta = o.optLongOrNull("pospuesta_hasta"),
                    numeroPosposiciones = o.optInt("numero_posposiciones", 0),
                    stockRestanteTrasToma = o.optIntOrNull("stock_restante_tras_toma"),
                    observaciones = o.optString("observaciones", "")
                )
            )
        }
        val stockEvents = mutableListOf<StockEventEntity>()
        val stockArray = root.optJSONArray("stock_events") ?: JSONArray()
        for (i in 0 until stockArray.length()) {
            val o = stockArray.getJSONObject(i)
            stockEvents.add(
                StockEventEntity(
                    id = o.getLong("id"),
                    medicamentoId = o.getLong("medicamento_id"),
                    tipoEvento = StockEventType.valueOf(o.getString("tipo_evento")),
                    cantidad = o.getInt("cantidad"),
                    stockAnterior = o.getInt("stock_anterior"),
                    stockPosterior = o.getInt("stock_posterior"),
                    fechaEvento = o.optLong("fecha_evento", System.currentTimeMillis()),
                    nota = o.optString("nota", "")
                )
            )
        }
        val importedSettings = root.optJSONObject("settings")?.let {
            SettingsEntity(
                checkDelayMinutos = it.optInt("check_delay_minutos", 2),
                diasAvisoStock = it.optInt("dias_aviso_stock", 3),
                formatoHora = it.optString("formato_hora", "24h"),
                notificacionesActivadas = it.optBoolean("notificaciones_activadas", true),
                fechaUltimaCopiaSeguridad = it.optLongOrNull("fecha_ultima_copia_seguridad")
            )
        } ?: SettingsEntity()
        db.withTransaction {
            stockEventDao.deleteAll()
            intakeDao.deleteAll()
            medicationDao.deleteAll()
            settingsDao.deleteAll()
            medicationDao.insertAll(meds)
            intakeDao.insertAll(intakes)
            stockEventDao.insertAll(stockEvents)
            settingsDao.upsert(importedSettings)
        }
    }
}

private fun JSONObject.optLongOrNull(name: String): Long? = if (isNull(name) || !has(name)) null else optLong(name)
private fun JSONObject.optIntOrNull(name: String): Int? = if (isNull(name) || !has(name)) null else optInt(name)
