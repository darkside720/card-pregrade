package com.cardpregrade.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2: capture orientation / quality columns, one photo per kind per session, and the
 * image_quality_checks table. SQL mirrors schemas/.../2.json exactly (Room validates it on open).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf(
            "`exifOrientation` INTEGER",
            "`rotationDegrees` INTEGER NOT NULL DEFAULT 0",
            "`qualityOverridden` INTEGER NOT NULL DEFAULT 0",
            "`qualityVerdict` TEXT",
            "`blurScore` REAL",
            "`exposureScore` REAL",
            "`resolutionScore` REAL",
            "`exposureCompensationEv` REAL",
            "`torchOn` INTEGER",
        ).forEach { db.execSQL("ALTER TABLE `captured_images` ADD COLUMN $it") }

        // v1 allowed several rows per (session, kind); keep only the most recent before enforcing uniqueness.
        db.execSQL(
            """
            DELETE FROM `captured_images` WHERE rowid NOT IN (
                SELECT rowid FROM `captured_images` c WHERE c.capturedAtMillis = (
                    SELECT MAX(capturedAtMillis) FROM `captured_images` d
                    WHERE d.sessionId = c.sessionId AND d.kind = c.kind
                ) GROUP BY sessionId, kind
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_captured_images_sessionId_kind` ON `captured_images` (`sessionId`, `kind`)")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `image_quality_checks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`imageId` TEXT NOT NULL, `metric` TEXT NOT NULL, `verdict` TEXT NOT NULL, `measuredValue` REAL, " +
                "`unit` TEXT, `message` TEXT NOT NULL, FOREIGN KEY(`imageId`) REFERENCES `captured_images`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_image_quality_checks_imageId` ON `image_quality_checks` (`imageId`)")
    }
}
