package com.steveen.medcontrol.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications ORDER BY activo DESC, nombre COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications ORDER BY activo DESC, nombre COLLATE NOCASE ASC")
    suspend fun getAll(): List<MedicationEntity>

    @Query("SELECT * FROM medications WHERE activo = 1 ORDER BY nombre COLLATE NOCASE ASC")
    suspend fun getActive(): List<MedicationEntity>

    @Query("SELECT * FROM medications WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MedicationEntity?

    @Query("SELECT COUNT(*) FROM medications")
    suspend fun count(): Int

    @Insert
    suspend fun insert(medication: MedicationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(medications: List<MedicationEntity>)

    @Update
    suspend fun update(medication: MedicationEntity)

    @Delete
    suspend fun delete(medication: MedicationEntity)

    @Query("DELETE FROM medications")
    suspend fun deleteAll()
}

@Dao
interface IntakeDao {
    @Query("SELECT * FROM intake_schedules ORDER BY fecha_programada DESC, hora_programada DESC")
    fun observeAll(): Flow<List<IntakeScheduleEntity>>

    @Query("SELECT * FROM intake_schedules WHERE fecha_programada = :date ORDER BY hora_programada ASC")
    fun observeByDate(date: String): Flow<List<IntakeScheduleEntity>>

    @Query("SELECT * FROM intake_schedules WHERE medicamento_id = :medicationId ORDER BY fecha_programada DESC, hora_programada DESC")
    fun observeByMedication(medicationId: Long): Flow<List<IntakeScheduleEntity>>

    @Query("SELECT * FROM intake_schedules WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): IntakeScheduleEntity?

    @Query("SELECT * FROM intake_schedules WHERE medicamento_id = :medicationId AND fecha_programada = :date AND hora_programada = :time LIMIT 1")
    suspend fun getByUnique(medicationId: Long, date: String, time: String): IntakeScheduleEntity?

    @Query("SELECT * FROM intake_schedules WHERE estado IN ('PROGRAMADA','ALARMA_SONADA','POSPUESTA','CHECK_PENDIENTE') ORDER BY fecha_programada ASC, hora_programada ASC")
    suspend fun getPending(): List<IntakeScheduleEntity>

    @Query("SELECT * FROM intake_schedules WHERE fecha_programada BETWEEN :from AND :to ORDER BY fecha_programada DESC, hora_programada DESC")
    suspend fun getBetweenDates(from: String, to: String): List<IntakeScheduleEntity>

    @Query("SELECT * FROM intake_schedules ORDER BY fecha_programada DESC, hora_programada DESC")
    suspend fun getAll(): List<IntakeScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(schedule: IntakeScheduleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(schedules: List<IntakeScheduleEntity>)

    @Update
    suspend fun update(schedule: IntakeScheduleEntity)

    @Query("DELETE FROM intake_schedules")
    suspend fun deleteAll()
}

@Dao
interface StockEventDao {
    @Query("SELECT * FROM stock_events ORDER BY fecha_evento DESC")
    fun observeAll(): Flow<List<StockEventEntity>>

    @Query("SELECT * FROM stock_events ORDER BY fecha_evento DESC")
    suspend fun getAll(): List<StockEventEntity>

    @Insert
    suspend fun insert(event: StockEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<StockEventEntity>)

    @Query("DELETE FROM stock_events")
    suspend fun deleteAll()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    fun observe(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1 LIMIT 1")
    suspend fun get(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: SettingsEntity)

    @Query("DELETE FROM settings")
    suspend fun deleteAll()
}
