package com.steveen.medcontrol.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromTimeList(value: List<String>?): String = value?.joinToString("|") ?: ""

    @TypeConverter
    fun toTimeList(value: String?): List<String> = value
        ?.split("|")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?: emptyList()

    @TypeConverter
    fun fromIntakeState(value: IntakeState): String = value.name

    @TypeConverter
    fun toIntakeState(value: String): IntakeState = IntakeState.valueOf(value)

    @TypeConverter
    fun fromStockEventType(value: StockEventType): String = value.name

    @TypeConverter
    fun toStockEventType(value: String): StockEventType = StockEventType.valueOf(value)
}
