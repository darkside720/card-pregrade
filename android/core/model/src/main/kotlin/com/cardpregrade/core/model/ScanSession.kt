package com.cardpregrade.core.model

import java.time.Instant

/**
 * Where analysis runs and therefore where pixels may travel.
 *
 * [LOCAL_ONLY] is the default and the only mode implemented. [CLOUD_ANALYSIS] is reserved:
 * it must only ever be entered through an explicit, per-scan user action (see docs/privacy.md).
 */
enum class ProcessingMode { LOCAL_ONLY, CLOUD_ANALYSIS }

enum class ScanStatus {
    CREATED,
    CAPTURING,
    CAPTURED,
    ANALYZING,
    ANALYZED,
    FAILED,
}

data class ScanSession(
    val id: String,
    val cardId: String,
    val createdAt: Instant,
    val status: ScanStatus,
    val protocolId: String,
    val protocolVersion: Int,
    val processingMode: ProcessingMode = ProcessingMode.LOCAL_ONLY,
    val captures: List<CapturedImage> = emptyList(),
)
