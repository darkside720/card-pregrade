package com.cardpregrade.core.data.repository

import com.cardpregrade.core.model.Card
import com.cardpregrade.core.model.CaptureProtocol
import com.cardpregrade.core.model.CaptureKind
import com.cardpregrade.core.model.CapturedImage
import com.cardpregrade.core.model.InspectionResult
import com.cardpregrade.core.model.PredictionComparison
import com.cardpregrade.core.model.ProcessingMode
import com.cardpregrade.core.model.ProfessionalGrade
import com.cardpregrade.core.model.ScanSession
import com.cardpregrade.core.model.ScanStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Row shown in the Saved Scans list. */
data class ScanSessionSummary(
    val session: ScanSession,
    val cardLabel: String,
    val captureCount: Int,
)

/** Compact view of a stored inspection (full payload persistence arrives in Phase 4). */
data class InspectionSummary(
    val id: String,
    val sessionId: String,
    val cardLabel: String,
    val createdAt: Instant,
    val psaRangeLabel: String?,
    val bgsRangeLabel: String?,
    val overallConfidencePercent: Int,
    val defectCount: Int,
    val pipelineVersion: String,
)

interface ScanRepository {
    fun observeSessions(): Flow<List<ScanSessionSummary>>
    suspend fun createSession(card: Card, protocol: CaptureProtocol, processingMode: ProcessingMode = ProcessingMode.LOCAL_ONLY): ScanSession
    suspend fun getSession(id: String): ScanSession?
    suspend fun updateStatus(sessionId: String, status: ScanStatus)
    /** Stores an accepted photo (with its quality checks); replaces any earlier photo of the same kind. */
    suspend fun addCapture(image: CapturedImage)
    suspend fun removeCapture(sessionId: String, kind: CaptureKind)
    suspend fun deleteSession(sessionId: String)

    /** Session that most recently received an accepted photo, if any. */
    suspend fun latestCapturedSession(): ScanSession?
}

interface InspectionRepository {
    fun observeSummaries(): Flow<List<InspectionSummary>>

    /** Rejects demo results: they must never be persisted as real scans. */
    suspend fun save(result: InspectionResult)
}

/**
 * Professional-grade feedback loop: the user records the real grade after the card returns,
 * and a [PredictionComparison] is stored against the prediction that was shown.
 */
interface GradeFeedbackRepository {
    suspend fun recordProfessionalGrade(grade: ProfessionalGrade)
    suspend fun recordComparison(comparison: PredictionComparison)
    fun observeComparisons(): Flow<List<PredictionComparison>>
}
