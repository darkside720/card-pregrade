package com.cardpregrade.core.data.repository

import com.cardpregrade.core.data.db.CardDao
import com.cardpregrade.core.data.db.InspectionDao
import com.cardpregrade.core.data.db.ScanSessionDao
import com.cardpregrade.core.data.mapping.toDomain
import com.cardpregrade.core.data.mapping.toEntity
import com.cardpregrade.core.data.mapping.qualityCheckEntities
import com.cardpregrade.core.data.mapping.toSummary
import com.cardpregrade.core.model.CaptureKind
import com.cardpregrade.core.model.CapturedImage
import com.cardpregrade.core.model.Card
import com.cardpregrade.core.model.CaptureProtocol
import com.cardpregrade.core.model.InspectionResult
import com.cardpregrade.core.model.ProcessingMode
import com.cardpregrade.core.model.ScanSession
import com.cardpregrade.core.model.ScanStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID

class RoomScanRepository(
    private val cardDao: CardDao,
    private val sessionDao: ScanSessionDao,
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ScanRepository {

    override fun observeSessions(): Flow<List<ScanSessionSummary>> =
        sessionDao.observeWithCard().map { rows ->
            rows.map { row ->
                ScanSessionSummary(
                    session = row.session.toDomain(),
                    cardLabel = row.cardUserLabel?.takeIf { it.isNotBlank() } ?: Card.UNKNOWN_CARD_NAME,
                    captureCount = row.captureCount,
                )
            }
        }

    override suspend fun createSession(card: Card, protocol: CaptureProtocol, processingMode: ProcessingMode): ScanSession {
        cardDao.upsert(card.toEntity())
        val session = ScanSession(
            id = newId(),
            cardId = card.id,
            createdAt = clock.instant(),
            status = ScanStatus.CREATED,
            protocolId = protocol.id,
            protocolVersion = protocol.version,
            processingMode = processingMode,
        )
        sessionDao.insert(session.toEntity())
        return session
    }

    override suspend fun getSession(id: String): ScanSession? {
        val entity = sessionDao.get(id) ?: return null
        return entity.toDomain(sessionDao.images(id).map { it.toDomain(sessionDao.checks(it.id)) })
    }

    override suspend fun updateStatus(sessionId: String, status: ScanStatus) =
        sessionDao.updateStatus(sessionId, status.name)

    override suspend fun addCapture(image: CapturedImage) =
        sessionDao.upsertCapture(image.toEntity(), image.qualityCheckEntities())

    override suspend fun removeCapture(sessionId: String, kind: CaptureKind) =
        sessionDao.deleteImage(sessionId, kind.name)

    override suspend fun latestCapturedSession(): ScanSession? =
        sessionDao.latestCapturedSessionId()?.let { getSession(it) }

    override suspend fun deleteSession(sessionId: String) = sessionDao.delete(sessionId)
}

class RoomInspectionRepository(private val dao: InspectionDao) : InspectionRepository {

    override fun observeSummaries(): Flow<List<InspectionSummary>> =
        dao.observeAll().map { rows -> rows.map { it.toSummary() } }

    override suspend fun save(result: InspectionResult) {
        require(!result.provenance.isDemo) { "Demo results must not be persisted" }
        dao.insert(result.toEntity(), result.defects.map { it.toEntity(result.id) })
    }
}
