package com.cardpregrade.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CardEntity::class,
        ScanSessionEntity::class,
        CapturedImageEntity::class,
        ImageQualityCheckEntity::class,
        InspectionResultEntity::class,
        CardDefectEntity::class,
        ProfessionalGradeEntity::class,
        PredictionComparisonEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class CardPregradeDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun scanSessionDao(): ScanSessionDao
    abstract fun inspectionDao(): InspectionDao
    abstract fun gradeFeedbackDao(): GradeFeedbackDao

    companion object {
        private const val NAME = "card_pregrade.db"

        fun create(context: Context): CardPregradeDatabase =
            Room.databaseBuilder(context.applicationContext, CardPregradeDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
