package com.cardpregrade.core.cv.analyzers

import com.cardpregrade.core.cv.image.CorrectedCardImage
import com.cardpregrade.core.model.AlgorithmVersion
import com.cardpregrade.core.model.AnalysisStatus
import com.cardpregrade.core.model.CardSide
import com.cardpregrade.core.model.CenteringResult
import com.cardpregrade.core.model.CenteringUnavailableReason
import com.cardpregrade.core.model.Confidence
import com.cardpregrade.core.model.DetectionMaturity
import com.cardpregrade.core.model.SurfaceCheck
import com.cardpregrade.core.model.SurfaceResult

/*
 * Honest placeholders used until real implementations land. They return explicit
 * "not implemented / unknown" results rather than plausible-looking numbers.
 */

class NotImplementedCenteringAnalyzer : CenteringAnalyzer {
    override val version = AlgorithmVersion("centering-not-implemented", "0")

    override fun analyze(image: CorrectedCardImage, side: CardSide): CenteringResult =
        CenteringResult.Unknown(side, CenteringUnavailableReason.NOT_IMPLEMENTED)
}

class NotImplementedSurfaceAnalyzer : SurfaceAnalyzer {
    override val version = AlgorithmVersion("surface-not-implemented", "0")

    override val capabilities: Map<SurfaceCheck, DetectionMaturity> =
        SurfaceCheck.entries.associateWith { DetectionMaturity.NOT_IMPLEMENTED }

    override fun analyze(input: SurfaceAnalysisInput): SurfaceResult = SurfaceResult(
        side = input.side,
        status = AnalysisStatus.NOT_IMPLEMENTED,
        checks = capabilities,
        sourceImageIds = listOfNotNull(input.straight, input.lightFromLeft, input.lightFromRight)
            .map { it.sourceImageId },
        defects = emptyList(),
        confidence = Confidence.NONE,
    )
}
