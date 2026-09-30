package com.cardpregrade.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Room schema (v2: capture orientation/quality columns + image_quality_checks). Enums are stored by name and timestamps as epoch millis so rows remain
 * readable/exportable. Grades are stored as half-point integers (see Grade.halfPoints).
 *
 * Every analysis row carries pipeline/algorithm versions so historical predictions remain
 * reproducible after algorithms change.
 */

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val id: String,
    val game: String,
    val userLabel: String?,
    val setName: String?,
    val setCode: String?,
    val cardNumber: String?,
    val cardName: String?,
    val language: String?,
    val variant: String?,
    val year: Int?,
    val identificationSource: String?,
    val identificationConfidence: Double?,
)

@Entity(
    tableName = "scan_sessions",
    foreignKeys = [
        ForeignKey(entity = CardEntity::class, parentColumns = ["id"], childColumns = ["cardId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("cardId")],
)
data class ScanSessionEntity(
    @PrimaryKey val id: String,
    val cardId: String,
    val createdAtMillis: Long,
    val status: String,
    val protocolId: String,
    val protocolVersion: Int,
    val processingMode: String,
)

@Entity(
    tableName = "captured_images",
    foreignKeys = [
        ForeignKey(entity = ScanSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
    ],
    // One accepted photo per capture kind per session; a retake replaces it.
    indices = [Index("sessionId"), Index(value = ["sessionId", "kind"], unique = true)],
)
data class CapturedImageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val kind: String,
    /** Path relative to the app's files dir. Pixels never leave the device in LOCAL_ONLY mode. */
    val localUri: String,
    /** Stored (sensor) pixel dimensions; apply the EXIF orientation for upright dimensions. */
    val widthPx: Int,
    val heightPx: Int,
    val capturedAtMillis: Long,
    val deviceManufacturer: String,
    val deviceModel: String,
    val osVersion: String,
    val appVersion: String,
    val cameraId: String?,
    val lensFocalLengthMm: Float?,
    val iso: Int?,
    val exposureTimeNs: Long?,
    val flashUsed: Boolean?,
    val lightingAngle: String?,
    val qualityAcceptable: Boolean?,
    val qualityAlgorithmVersion: String?,
    // --- v2 ---
    val exifOrientation: Int?,
    @ColumnInfo(defaultValue = "0") val rotationDegrees: Int,
    @ColumnInfo(defaultValue = "0") val qualityOverridden: Boolean,
    val qualityVerdict: String?,
    val blurScore: Double?,
    val exposureScore: Double?,
    val resolutionScore: Double?,
    val exposureCompensationEv: Float?,
    val torchOn: Boolean?,
)

/** Per-metric capture-quality measurement for a stored photo (schema v2). */
@Entity(
    tableName = "image_quality_checks",
    foreignKeys = [
        ForeignKey(entity = CapturedImageEntity::class, parentColumns = ["id"], childColumns = ["imageId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("imageId")],
)
data class ImageQualityCheckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imageId: String,
    val metric: String,
    val verdict: String,
    val measuredValue: Double?,
    val unit: String?,
    val message: String,
)

@Entity(
    tableName = "inspection_results",
    foreignKeys = [
        ForeignKey(entity = ScanSessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("sessionId")],
)
data class InspectionResultEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val cardId: String,
    val cardLabel: String,
    val createdAtMillis: Long,
    val psaLowHalfPoints: Int?,
    val psaHighHalfPoints: Int?,
    val bgsLowHalfPoints: Int?,
    val bgsHighHalfPoints: Int?,
    val overallConfidence: Double,
    val defectCount: Int,
    val warningCount: Int,
    val pipelineVersion: String,
    val gradingRuleSetVersion: String,
    val processingMode: String,
    /**
     * Reserved for the full serialized result (region results, quality, warnings). A
     * serialization format will be chosen with the first real pipeline (Phase 4).
     */
    val payloadJson: String?,
)

@Entity(
    tableName = "card_defects",
    foreignKeys = [
        ForeignKey(entity = InspectionResultEntity::class, parentColumns = ["id"], childColumns = ["inspectionId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("inspectionId")],
)
data class CardDefectEntity(
    @PrimaryKey val id: String,
    val inspectionId: String,
    val side: String,
    val category: String,
    val region: String,
    val boxLeft: Double,
    val boxTop: Double,
    val boxRight: Double,
    val boxBottom: Double,
    val severity: String,
    val confidence: Double,
    val description: String,
    val sourceImageId: String,
    val algorithmName: String,
    val algorithmVersion: String,
    val maturity: String,
)

@Entity(
    tableName = "professional_grades",
    foreignKeys = [
        ForeignKey(entity = CardEntity::class, parentColumns = ["id"], childColumns = ["cardId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("cardId")],
)
data class ProfessionalGradeEntity(
    @PrimaryKey val id: String,
    val cardId: String,
    val company: String,
    val overallHalfPoints: Int,
    val qualifier: String?,
    val centeringHalfPoints: Int?,
    val cornersHalfPoints: Int?,
    val edgesHalfPoints: Int?,
    val surfaceHalfPoints: Int?,
    val certificationNumber: String?,
    val submittedOnEpochDay: Long?,
    val gradedOnEpochDay: Long?,
    val recordedAtMillis: Long,
    val notes: String?,
)

@Entity(
    tableName = "prediction_comparisons",
    foreignKeys = [
        ForeignKey(entity = InspectionResultEntity::class, parentColumns = ["id"], childColumns = ["inspectionResultId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ProfessionalGradeEntity::class, parentColumns = ["id"], childColumns = ["professionalGradeId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("inspectionResultId"), Index("professionalGradeId")],
)
data class PredictionComparisonEntity(
    @PrimaryKey val id: String,
    val inspectionResultId: String,
    val professionalGradeId: String,
    val company: String,
    val predictedLowHalfPoints: Int?,
    val predictedHighHalfPoints: Int?,
    val actualHalfPoints: Int,
    val outcome: String,
    val pipelineVersion: String,
    val comparedAtMillis: Long,
)
