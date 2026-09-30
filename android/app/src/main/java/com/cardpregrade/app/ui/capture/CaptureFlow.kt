package com.cardpregrade.app.ui.capture

import com.cardpregrade.core.model.CaptureKind
import com.cardpregrade.core.model.CaptureProtocol
import com.cardpregrade.core.model.CaptureStep
import com.cardpregrade.core.model.CapturedImage
import com.cardpregrade.core.model.QualityVerdict

enum class StepStatus {
    /** Nothing captured yet (or discarded by retake). */
    PENDING,

    /** A photo was taken and is waiting for the user to accept or retake it. */
    REVIEWING,

    /** Photo is technically unusable (decode failure / catastrophic resolution); only retake is possible. */
    UNUSABLE,

    /** Photo accepted and stored. */
    CAPTURED,
}

data class StepCapture(
    val step: CaptureStep,
    val status: StepStatus = StepStatus.PENDING,
    /** Photo under review (not yet persisted). */
    val candidate: CapturedImage? = null,
    /** Accepted, persisted photo. */
    val accepted: CapturedImage? = null,
) {
    /** Photo currently shown for this step (candidate under review, else the accepted one). */
    val shown: CapturedImage? get() = candidate ?: accepted
}

/**
 * Pure state machine for guided capture: capture → review → accept (or retake) → next step.
 *
 * Nothing advances automatically: after a capture the step waits in REVIEWING until the user
 * decides. Photos with a WARNING or RETAKE_RECOMMENDED verdict can only be accepted with an
 * explicit override, which is recorded on the image. UNUSABLE photos cannot be accepted.
 */
data class CaptureFlowState(
    val protocol: CaptureProtocol,
    val captures: List<StepCapture>,
    val currentIndex: Int,
) {
    val current: StepCapture get() = captures[currentIndex]
    val capturedCount: Int get() = captures.count { it.status == StepStatus.CAPTURED }

    /** All required steps have an accepted photo. */
    val isComplete: Boolean
        get() = captures.filter { it.step.required }.all { it.status == StepStatus.CAPTURED }

    /** Verdict of the photo under review, or null when nothing is being reviewed. */
    val reviewVerdict: QualityVerdict?
        get() = current.candidate?.let { it.quality?.verdict ?: QualityVerdict.WARNING }

    /** True when the candidate can be accepted without an explicit override. */
    val canAcceptDirectly: Boolean get() = current.status == StepStatus.REVIEWING && reviewVerdict == QualityVerdict.GOOD

    fun onCaptured(image: CapturedImage): CaptureFlowState {
        require(image.kind == current.step.kind) { "Captured ${image.kind} while on ${current.step.kind}" }
        val usable = image.quality?.acceptable ?: true
        return update(current.copy(status = if (usable) StepStatus.REVIEWING else StepStatus.UNUSABLE, candidate = image))
    }

    /**
     * Accepts the photo under review. Returns null (no change) if acceptance is not allowed:
     * nothing under review, the photo is unusable, or it has warnings and [override] is false.
     */
    fun accept(override: Boolean): CaptureFlowState? {
        if (current.status != StepStatus.REVIEWING) return null
        val candidate = current.candidate ?: return null
        val needsOverride = reviewVerdict != QualityVerdict.GOOD
        if (needsOverride && !override) return null
        val accepted = candidate.copy(qualityOverridden = needsOverride)
        val updated = update(current.copy(status = StepStatus.CAPTURED, candidate = null, accepted = accepted))
        return updated.copy(currentIndex = updated.nextPendingIndex() ?: updated.currentIndex)
    }

    /** The photo that [accept] would store (with the override flag applied). */
    fun pendingAcceptance(override: Boolean): CapturedImage? = accept(override)?.captures?.get(currentIndex)?.accepted

    /** Discards the candidate/accepted photo for [kind] and makes it the current step. */
    fun retake(kind: CaptureKind): CaptureFlowState {
        val index = indexOf(kind)
        val newCaptures = captures.toMutableList().also { it[index] = StepCapture(captures[index].step) }
        return copy(captures = newCaptures, currentIndex = index)
    }

    fun select(kind: CaptureKind): CaptureFlowState = copy(currentIndex = indexOf(kind))

    private fun update(step: StepCapture): CaptureFlowState =
        copy(captures = captures.toMutableList().also { it[currentIndex] = step })

    private fun indexOf(kind: CaptureKind): Int =
        captures.indexOfFirst { it.step.kind == kind }.also { require(it >= 0) { "$kind is not in this protocol" } }

    private fun nextPendingIndex(): Int? {
        val after = (currentIndex + 1 until captures.size).firstOrNull { captures[it].status != StepStatus.CAPTURED }
        return after ?: captures.indices.firstOrNull { captures[it].status != StepStatus.CAPTURED }
    }

    companion object {
        fun start(protocol: CaptureProtocol) = CaptureFlowState(
            protocol = protocol,
            captures = protocol.steps.map { StepCapture(it) },
            currentIndex = 0,
        )

        /** Rebuilds state from accepted photos persisted earlier (e.g. after an app restart). */
        fun restore(protocol: CaptureProtocol, accepted: List<CapturedImage>): CaptureFlowState {
            val byKind = accepted.associateBy { it.kind }
            val captures = protocol.steps.map { step ->
                byKind[step.kind]?.let { StepCapture(step, StepStatus.CAPTURED, accepted = it) } ?: StepCapture(step)
            }
            val first = captures.indexOfFirst { it.status != StepStatus.CAPTURED }.takeIf { it >= 0 } ?: 0
            return CaptureFlowState(protocol, captures, first)
        }
    }
}
