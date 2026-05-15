package com.steveen.medcontrol.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class IntakeState {
    PROGRAMADA,
    ALARMA_SONADA,
    POSPUESTA,
    CHECK_PENDIENTE,
    TOMADA,
    OLVIDADA_NO_TOMADA
}

enum class StockEventType {
    DESCUENTO_POR_TOMA,
    REPOSICION_CAJA,
    AJUSTE_MANUAL
}

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    @ColumnInfo(name = "unidades_por_caja") val unidadesPorCaja: Int,
    @ColumnInfo(name = "stock_actual") val stockActual: Int,
    @ColumnInfo(name = "horas_toma") val horasToma: List<String>,
    val activo: Boolean = true,
    val notas: String = "",
    @ColumnInfo(name = "fecha_creacion") val fechaCreacion: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "fecha_actualizacion") val fechaActualizacion: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "intake_schedules",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicamento_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["medicamento_id"]),
        Index(value = ["medicamento_id", "fecha_programada", "hora_programada"], unique = true),
        Index(value = ["fecha_programada"]),
        Index(value = ["estado"])
    ]
)
data class IntakeScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "medicamento_id") val medicamentoId: Long,
    @ColumnInfo(name = "fecha_programada") val fechaProgramada: String,
    @ColumnInfo(name = "hora_programada") val horaProgramada: String,
    val estado: IntakeState = IntakeState.PROGRAMADA,
    @ColumnInfo(name = "alarma_lanzada_en") val alarmaLanzadaEn: Long? = null,
    @ColumnInfo(name = "check_programado_en") val checkProgramadoEn: Long? = null,
    @ColumnInfo(name = "confirmada_en") val confirmadaEn: Long? = null,
    @ColumnInfo(name = "olvidada_en") val olvidadaEn: Long? = null,
    @ColumnInfo(name = "pospuesta_hasta") val pospuestaHasta: Long? = null,
    @ColumnInfo(name = "numero_posposiciones") val numeroPosposiciones: Int = 0,
    @ColumnInfo(name = "stock_restante_tras_toma") val stockRestanteTrasToma: Int? = null,
    val observaciones: String = ""
)

@Entity(
    tableName = "stock_events",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicamento_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["medicamento_id"]), Index(value = ["fecha_evento"])]
)
data class StockEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "medicamento_id") val medicamentoId: Long,
    @ColumnInfo(name = "tipo_evento") val tipoEvento: StockEventType,
    val cantidad: Int,
    @ColumnInfo(name = "stock_anterior") val stockAnterior: Int,
    @ColumnInfo(name = "stock_posterior") val stockPosterior: Int,
    @ColumnInfo(name = "fecha_evento") val fechaEvento: Long = System.currentTimeMillis(),
    val nota: String = ""
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "check_delay_minutos") val checkDelayMinutos: Int = 2,
    @ColumnInfo(name = "dias_aviso_stock") val diasAvisoStock: Int = 3,
    @ColumnInfo(name = "formato_hora") val formatoHora: String = "24h",
    @ColumnInfo(name = "notificaciones_activadas") val notificacionesActivadas: Boolean = true,
    @ColumnInfo(name = "fecha_ultima_copia_seguridad") val fechaUltimaCopiaSeguridad: Long? = null
)
