package com.cardpregrade.core.model

import java.time.Instant

enum class CardSide { FRONT, BACK }

/**
 * Angle of the card relative to the camera/light for a capture. Angled captures (card rotated
 * slightly left or right) make surface texture visible for future surface analysis.
 */
enum class LightingAngle { STRAIGHT, LEFT, RIGHT }

/** Simple directional hint shown during capture. No exact angle is required in the MVP. */
enum class CaptureDirection { NONE, ROTATE_LEFT, ROTATE_RIGHT }

/**
 * The kind of photograph in a capture protocol. New kinds (macro corners, edge close-ups)
 * can be added without changing the pipeline contracts.
 */
enum class CaptureKind(val side: CardSide, val lighting: LightingAngle) {
    FRONT_STRAIGHT(CardSide.FRONT, LightingAngle.STRAIGHT),
    BACK_STRAIGHT(CardSide.BACK, LightingAngle.STRAIGHT),
    FRONT_ANGLE_LEFT(CardSide.FRONT, LightingAngle.LEFT),
    FRONT_ANGLE_RIGHT(CardSide.FRONT, LightingAngle.RIGHT),

    // --- Planned, not part of the V1 protocol ---
    BACK_ANGLE_LEFT(CardSide.BACK, LightingAngle.LEFT),
    BACK_ANGLE_RIGHT(CardSide.BACK, LightingAngle.RIGHT),
    FRONT_CORNER_MACRO(CardSide.FRONT, LightingAngle.STRAIGHT),
    BACK_CORNER_MACRO(CardSide.BACK, LightingAngle.STRAIGHT),
    FRONT_EDGE_MACRO(CardSide.FRONT, LightingAngle.STRAIGHT),
    BACK_EDGE_MACRO(CardSide.BACK, LightingAngle.STRAIGHT),
}

data class CaptureStep(
    val kind: CaptureKind,
    val title: String,
    val instructions: String,
    val required: Boolean = true,
    val direction: CaptureDirection = CaptureDirection.NONE,
)

/** An ordered, versioned list of photographs to collect for one scan. */
data class CaptureProtocol(
    val id: String,
    val version: Int,
    val steps: List<CaptureStep>,
) {
    init {
        require(steps.isNotEmpty()) { "A capture protocol needs at least one step" }
        require(steps.map { it.kind }.toSet().size == steps.size) { "Duplicate capture kinds" }
    }

    companion object {
        val V1_STANDARD = CaptureProtocol(
            id = "standard",
            version = 1,
            steps = listOf(
                CaptureStep(
                    CaptureKind.FRONT_STRAIGHT,
                    "Front — straight on",
                    "Place the card on a flat, non-reflective surface. Hold the phone parallel to the card and fill the guide.",
                ),
                CaptureStep(
                    CaptureKind.BACK_STRAIGHT,
                    "Back — straight on",
                    "Flip the card. Keep the phone parallel and fill the guide.",
                ),
                CaptureStep(
                    CaptureKind.FRONT_ANGLE_LEFT,
                    "Front — angled left",
                    "Front side up. Rotate the card slightly to the left so light rakes across the surface. No exact angle is needed.",
                    direction = CaptureDirection.ROTATE_LEFT,
                ),
                CaptureStep(
                    CaptureKind.FRONT_ANGLE_RIGHT,
                    "Front — angled right",
                    "Front side up. Rotate the card slightly to the right so light rakes across the surface. No exact angle is needed.",
                    direction = CaptureDirection.ROTATE_RIGHT,
                ),
            ),
        )
    }
}

/**
 * A photograph taken during a scan session. Pixels stay on-device.
 *
 * [widthPx]/[heightPx] are the dimensions of the *stored* pixels (as written by the camera);
 * apply [orientation] to get the upright image (see [ImageOrientation]).
 */
data class CapturedImage(
    val id: String,
    val sessionId: String,
    val kind: CaptureKind,
    /** App-private path relative to the app's files directory, e.g. `scans/{sessionId}/front-straight.jpg`. */
    val localUri: String,
    val widthPx: Int,
    val heightPx: Int,
    val capturedAt: Instant,
    val device: DeviceMetadata,
    val lighting: LightingMetadata? = null,
    val quality: ImageQualityResult? = null,
    val orientation: ImageOrientation = ImageOrientation.UPRIGHT,
    /** True when the user accepted the photo despite a WARNING / RETAKE_RECOMMENDED verdict. */
    val qualityOverridden: Boolean = false,
) {
    val side: CardSide get() = kind.side

    /** Upright width x height. */
    val orientedSize: Pair<Int, Int> get() = orientation.orientedSize(widthPx, heightPx)
}

/**
 * Captured with each photo so predictions can later be analysed per device/camera.
 * Per-shot values (ISO, exposure time, focal length, flash) come from the JPEG's EXIF and are
 * null when the camera did not write them — they are never estimated.
 */
data class DeviceMetadata(
    val manufacturer: String,
    val model: String,
    val osVersion: String,
    val appVersion: String,
    val cameraId: String? = null,
    val lensFocalLengthMm: Float? = null,
    val iso: Int? = null,
    val exposureTimeNs: Long? = null,
    val flashUsed: Boolean? = null,
    /** Exposure compensation applied by the user in the app, in EV. */
    val exposureCompensationEv: Float? = null,
    /** Whether the app's torch was on at capture time. */
    val torchOn: Boolean? = null,
)

/** User-declared lighting conditions (optional, used for dataset analysis). */
data class LightingMetadata(
    val angle: LightingAngle,
    val description: String? = null,
)
