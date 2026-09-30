package com.cardpregrade.core.data.mapping

import com.cardpregrade.core.data.db.CapturedImageEntity
import com.cardpregrade.core.data.db.ImageQualityCheckEntity
import com.cardpregrade.core.data.db.CardDefectEntity
import com.cardpregrade.core.data.db.CardEntity
import com.cardpregrade.core.data.db.InspectionResultEntity
import com.cardpregrade.core.data.db.ScanSessionEntity
import com.cardpregrade.core.data.repository.InspectionSummary
import com.cardpregrade.core.model.AlgorithmVersion
import com.cardpregrade.core.model.CapturedImage
import com.cardpregrade.core.model.Card
import com.cardpregrade.core.model.CardDefect
import com.cardpregrade.core.model.CardGame
import com.cardpregrade.core.model.CardIdentification
import com.cardpregrade.core.model.CaptureKind
import com.cardpregrade.core.model.Confidence
import com.cardpregrade.core.model.DeviceMetadata
import com.cardpregrade.core.model.Grade
import com.cardpregrade.core.model.GradeRange
import com.cardpregrade.core.model.GradeScale
import com.cardpregrade.core.model.IdentificationSource
import com.cardpregrade.core.model.ImageOrientation
import com.cardpregrade.core.model.ImageQualityResult
import com.cardpregrade.core.model.InspectionResult
import com.cardpregrade.core.model.LightingAngle
import com.cardpregrade.core.model.LightingMetadata
import com.cardpregrade.core.model.ProcessingMode
import com.cardpregrade.core.model.QualityCheck
import com.cardpregrade.core.model.QualityMetric
import com.cardpregrade.core.model.QualityVerdict
import com.cardpregrade.core.model.RangeEstimate
import com.cardpregrade.core.model.ScanSession
import com.cardpregrade.core.model.ScanStatus
import java.time.Instant
import kotlin.math.roundToInt

fun Card.toEntity() = CardEntity(
    id = id,
    game = game.name,
    userLabel = userLabel,
    setName = identification?.setName,
    setCode = identification?.setCode,
    cardNumber = identification?.cardNumber,
    cardName = identification?.cardName,
    language = identification?.language,
    variant = identification?.variant,
    year = identification?.year,
    identificationSource = identification?.source?.name,
    identificationConfidence = identification?.confidence?.value,
)

fun CardEntity.toDomain(): Card {
    val game = CardGame.valueOf(game)
    val identification = identificationSource?.let { source ->
        CardIdentification(
            game = game,
            setName = setName,
            setCode = setCode,
            cardNumber = cardNumber,
            cardName = cardName,
            language = language,
            variant = variant,
            year = year,
            source = IdentificationSource.valueOf(source),
            confidence = identificationConfidence?.let(::Confidence),
        )
    }
    return Card(id = id, game = game, identification = identification, userLabel = userLabel)
}

fun ScanSession.toEntity() = ScanSessionEntity(
    id = id,
    cardId = cardId,
    createdAtMillis = createdAt.toEpochMilli(),
    status = status.name,
    protocolId = protocolId,
    protocolVersion = protocolVersion,
    processingMode = processingMode.name,
)

fun ScanSessionEntity.toDomain(captures: List<CapturedImage> = emptyList()) = ScanSession(
    id = id,
    cardId = cardId,
    createdAt = Instant.ofEpochMilli(createdAtMillis),
    status = ScanStatus.valueOf(status),
    protocolId = protocolId,
    protocolVersion = protocolVersion,
    processingMode = ProcessingMode.valueOf(processingMode),
    captures = captures,
)

fun CapturedImage.toEntity() = CapturedImageEntity(
    id = id,
    sessionId = sessionId,
    kind = kind.name,
    localUri = localUri,
    widthPx = widthPx,
    heightPx = heightPx,
    capturedAtMillis = capturedAt.toEpochMilli(),
    deviceManufacturer = device.manufacturer,
    deviceModel = device.model,
    osVersion = device.osVersion,
    appVersion = device.appVersion,
    cameraId = device.cameraId,
    lensFocalLengthMm = device.lensFocalLengthMm,
    iso = device.iso,
    exposureTimeNs = device.exposureTimeNs,
    flashUsed = device.flashUsed,
    lightingAngle = lighting?.angle?.name,
    qualityAcceptable = quality?.acceptable,
    qualityAlgorithmVersion = quality?.algorithmVersion?.toString(),
    exifOrientation = orientation.exifValue,
    rotationDegrees = orientation.rotationDegrees,
    qualityOverridden = qualityOverridden,
    qualityVerdict = quality?.verdict?.name,
    blurScore = quality?.blurScore,
    exposureScore = quality?.exposureScore,
    resolutionScore = quality?.resolutionScore,
    exposureCompensationEv = device.exposureCompensationEv,
    torchOn = device.torchOn,
)

fun CapturedImage.qualityCheckEntities(): List<ImageQualityCheckEntity> =
    quality?.checks.orEmpty().map { check ->
        ImageQualityCheckEntity(
            imageId = id,
            metric = check.metric.name,
            verdict = check.verdict.name,
            measuredValue = check.measuredValue,
            unit = check.unit,
            message = check.message,
        )
    }

/** Parses "name@version" as written by [AlgorithmVersion.toString]. */
internal fun parseAlgorithmVersion(value: String): AlgorithmVersion {
    val at = value.lastIndexOf('@')
    return if (at <= 0) AlgorithmVersion(value, "unknown") else AlgorithmVersion(value.substring(0, at), value.substring(at + 1))
}

fun CapturedImageEntity.toDomain(checks: List<ImageQualityCheckEntity> = emptyList()): CapturedImage {
    val quality = qualityAlgorithmVersion?.let { version ->
        ImageQualityResult.fromChecks(
            sourceImageId = id,
            checks = checks.map {
                QualityCheck(QualityMetric.valueOf(it.metric), QualityVerdict.valueOf(it.verdict), it.measuredValue, it.unit, it.message)
            },
            algorithmVersion = parseAlgorithmVersion(version),
            blurScore = blurScore,
            exposureScore = exposureScore,
            resolutionScore = resolutionScore,
        )
    }
    return CapturedImage(
        id = id,
        sessionId = sessionId,
        kind = CaptureKind.valueOf(kind),
        localUri = localUri,
        widthPx = widthPx,
        heightPx = heightPx,
        capturedAt = Instant.ofEpochMilli(capturedAtMillis),
        device = DeviceMetadata(
            manufacturer = deviceManufacturer,
            model = deviceModel,
            osVersion = osVersion,
            appVersion = appVersion,
            cameraId = cameraId,
            lensFocalLengthMm = lensFocalLengthMm,
            iso = iso,
            exposureTimeNs = exposureTimeNs,
            flashUsed = flashUsed,
            exposureCompensationEv = exposureCompensationEv,
            torchOn = torchOn,
        ),
        lighting = lightingAngle?.let { LightingMetadata(LightingAngle.valueOf(it)) },
        quality = quality,
        orientation = ImageOrientation.fromExif(exifOrientation),
        qualityOverridden = qualityOverridden,
    )
}

private fun RangeEstimate.rangeOrNull(): GradeRange? = (this as? RangeEstimate.Available)?.range

fun InspectionResult.toEntity(): InspectionResultEntity {
    val psa = estimate.psa.rangeOrNull()
    val bgs = estimate.bgs.overall.rangeOrNull()
    return InspectionResultEntity(
        id = id,
        sessionId = sessionId,
        cardId = cardId,
        cardLabel = cardLabel,
        createdAtMillis = createdAt.toEpochMilli(),
        psaLowHalfPoints = psa?.low?.halfPoints,
        psaHighHalfPoints = psa?.high?.halfPoints,
        bgsLowHalfPoints = bgs?.low?.halfPoints,
        bgsHighHalfPoints = bgs?.high?.halfPoints,
        overallConfidence = overallConfidence.value,
        defectCount = defects.size,
        warningCount = warnings.size,
        pipelineVersion = provenance.pipelineVersion,
        gradingRuleSetVersion = estimate.ruleSetVersion.toString(),
        processingMode = provenance.processingMode.name,
        payloadJson = null,
    )
}

fun CardDefect.toEntity(inspectionId: String) = CardDefectEntity(
    id = id,
    inspectionId = inspectionId,
    side = side.name,
    category = category.name,
    region = location.region.name,
    boxLeft = location.boundingBox.left,
    boxTop = location.boundingBox.top,
    boxRight = location.boundingBox.right,
    boxBottom = location.boundingBox.bottom,
    severity = severity.name,
    confidence = confidence.value,
    description = description,
    sourceImageId = sourceImageId,
    algorithmName = algorithmVersion.name,
    algorithmVersion = algorithmVersion.version,
    maturity = maturity.name,
)

private fun rangeLabel(scale: GradeScale, low: Int?, high: Int?): String? {
    if (low == null || high == null) return null
    return GradeRange(scale, Grade.of(low / 2.0), Grade.of(high / 2.0)).format()
}

fun InspectionResultEntity.toSummary() = InspectionSummary(
    id = id,
    sessionId = sessionId,
    cardLabel = cardLabel,
    createdAt = Instant.ofEpochMilli(createdAtMillis),
    psaRangeLabel = rangeLabel(GradeScale.PSA, psaLowHalfPoints, psaHighHalfPoints),
    bgsRangeLabel = rangeLabel(GradeScale.BGS, bgsLowHalfPoints, bgsHighHalfPoints),
    overallConfidencePercent = (overallConfidence * 100).roundToInt(),
    defectCount = defectCount,
    pipelineVersion = pipelineVersion,
)
