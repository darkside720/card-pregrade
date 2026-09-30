package com.cardpregrade.core.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Session row joined with the card label and number of captured photos. */
data class ScanSessionWithCard(
    @Embedded val session: ScanSessionEntity,
    val cardGame: String,
    val cardUserLabel: String?,
    val captureCount: Int,
)

@Dao
interface CardDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: CardEntity)

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun get(id: String): CardEntity?
}

@Dao
interface ScanSessionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: ScanSessionEntity)

    @Query("SELECT * FROM scan_sessions WHERE id = :id")
    suspend fun get(id: String): ScanSessionEntity?

    @Query("UPDATE scan_sessions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query(
        """
        SELECT s.*, c.game AS cardGame, c.userLabel AS cardUserLabel,
               (SELECT COUNT(*) FROM captured_images i WHERE i.sessionId = s.id) AS captureCount
        FROM scan_sessions s JOIN cards c ON c.id = s.cardId
        ORDER BY s.createdAtMillis DESC
        """,
    )
    fun observeWithCard(): Flow<List<ScanSessionWithCard>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: CapturedImageEntity)

    @Insert
    suspend fun insertChecks(checks: List<ImageQualityCheckEntity>)

    @Query("DELETE FROM captured_images WHERE sessionId = :sessionId AND kind = :kind")
    suspend fun deleteImage(sessionId: String, kind: String)

    /** Stores an accepted photo and its quality checks, replacing any previous photo of that kind. */
    @Transaction
    suspend fun upsertCapture(image: CapturedImageEntity, checks: List<ImageQualityCheckEntity>) {
        deleteImage(image.sessionId, image.kind)
        insertImage(image)
        if (checks.isNotEmpty()) insertChecks(checks)
    }

    @Query("SELECT * FROM image_quality_checks WHERE imageId = :imageId ORDER BY id")
    suspend fun checks(imageId: String): List<ImageQualityCheckEntity>

    @Query("SELECT sessionId FROM captured_images ORDER BY capturedAtMillis DESC LIMIT 1")
    suspend fun latestCapturedSessionId(): String?

    @Query("SELECT * FROM captured_images WHERE sessionId = :sessionId ORDER BY capturedAtMillis")
    suspend fun images(sessionId: String): List<CapturedImageEntity>

    @Query("DELETE FROM scan_sessions WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface InspectionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertResult(result: InspectionResultEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDefects(defects: List<CardDefectEntity>)

    @Transaction
    suspend fun insert(result: InspectionResultEntity, defects: List<CardDefectEntity>) {
        insertResult(result)
        insertDefects(defects)
    }

    @Query("SELECT * FROM inspection_results ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<InspectionResultEntity>>

    @Query("SELECT * FROM card_defects WHERE inspectionId = :inspectionId")
    suspend fun defects(inspectionId: String): List<CardDefectEntity>
}

@Dao
interface GradeFeedbackDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGrade(grade: ProfessionalGradeEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertComparison(comparison: PredictionComparisonEntity)

    @Query("SELECT * FROM prediction_comparisons ORDER BY comparedAtMillis DESC")
    fun observeComparisons(): Flow<List<PredictionComparisonEntity>>
}
