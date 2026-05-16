package com.steveen.medcontrol.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [MedicationEntity::class, IntakeScheduleEntity::class, StockEventEntity::class, SettingsEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MedControlDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun intakeDao(): IntakeDao
    abstract fun stockEventDao(): StockEventDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile private var INSTANCE: MedControlDatabase? = null

        fun getDatabase(context: Context): MedControlDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MedControlDatabase::class.java,
                    "medcontrol.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
