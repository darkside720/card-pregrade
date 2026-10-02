package com.cardpregrade.app.ui.results

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cardpregrade.app.demo.DemoInspection
import com.cardpregrade.app.ui.components.AppScaffold
import com.cardpregrade.app.ui.components.CardDiagram
import com.cardpregrade.app.ui.components.DemoBadge
import com.cardpregrade.app.ui.components.DemoBanner
import com.cardpregrade.app.ui.components.DisclaimerCard
import com.cardpregrade.app.ui.components.LabeledValue
import com.cardpregrade.app.ui.components.ScrollingContent
import com.cardpregrade.app.ui.components.SectionTitle
import com.cardpregrade.app.ui.components.severityColor
import com.cardpregrade.app.ui.label
import com.cardpregrade.app.ui.locationLabel
import com.cardpregrade.core.model.CandidateIndicator
import com.cardpregrade.core.model.CandidateStatus
import com.cardpregrade.core.model.CardDefect
import com.cardpregrade.core.model.CardSide
import com.cardpregrade.core.model.CenteringResult
import com.cardpregrade.core.model.DetectionMaturity
import com.cardpregrade.core.model.InspectionResult
import com.cardpregrade.core.model.RangeEstimate
import com.cardpregrade.core.model.SubgradeCategory

sealed interface ResultsUiState {
    data class Loaded(val result: InspectionResult) : ResultsUiState
    data object NotFound : ResultsUiState
}

class ResultsViewModel(resultId: String) : ViewModel() {
    /** Only the demo result exists until the pipeline produces real results (Phase 4+). */
    val state: ResultsUiState =
        if (resultId == DemoInspection.RESULT_ID) ResultsUiState.Loaded(DemoInspection.create()) else ResultsUiState.NotFound
}

@Composable
fun ResultsScreen(resultId: String, onBack: () -> Unit) {
    val vm: ResultsViewModel = viewModel { ResultsViewModel(resultId) }
    val state = vm.state
    // Driven by the data's provenance, not the route, so any demo result is labeled.
    val isDemo = state is ResultsUiState.Loaded && state.result.provenance.isDemo
    AppScaffold(
        title = "Pre-grade results",
        screenTag = "screen_results",
        onBack = onBack,
        titleBadge = if (isDemo) {
            { DemoBadge() }
        } else {
            null
        },
    ) { padding ->
        ScrollingContent(padding) {
            when (val s = state) {
                ResultsUiState.NotFound -> Text("This result is not available.")
                is ResultsUiState.Loaded -> ResultsContent(s.result)
            }
        }
    }
}

@Composable
private fun ResultsContent(result: InspectionResult) {
    var side by rememberSaveable { mutableStateOf(CardSide.BACK) }
    var zoomed by remember { mutableStateOf<CardDefect?>(null) }

    if (result.provenance.isDemo) DemoBanner()

    Text(result.cardLabel, style = MaterialTheme.typography.headlineSmall)
    Text(
        "${result.cardGame.label()} · Visual inspection from photographs" + if (result.provenance.isDemo) " (sample)" else "",
        style = MaterialTheme.typography.bodySmall,
    )

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GradeTile("Estimated PSA", result.estimate.psa, Modifier.weight(1f))
        GradeTile("Estimated BGS", result.estimate.bgs.overall, Modifier.weight(1f))
    }

    SectionTitle("Category estimates (BGS-style)")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SubgradeCategory.entries.forEach { category ->
            val sub = result.subgrade(category)
            OutlinedCard(Modifier.weight(1f)) {
                Column(Modifier.padding(8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(category.label(), style = MaterialTheme.typography.labelMedium)
                    Text(sub?.range?.format() ?: "—", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LabeledValue("Overall confidence", "${result.overallConfidence.percent}%")
        LinearProgressIndicator(progress = { result.overallConfidence.value.toFloat() }, modifier = Modifier.fillMaxWidth())
        LabeledValue("Potential defects", result.defects.size.toString())
    }

    SectionTitle("Defect map")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CardSide.entries.forEach { s ->
            val count = result.defects.count { it.side == s }
            FilterChip(selected = side == s, onClick = { side = s }, label = { Text("${s.label()} ($count)") })
        }
    }
    val sideDefects = result.defects.filter { it.side == side }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CardDiagram(
            modifier = Modifier.width(220.dp),
            defects = sideDefects,
            onDefectTap = { zoomed = it },
        )
    }
    Text(
        "Illustration only — part of the sample result layout; your photos are not shown or analyzed here. Tap a marker to zoom. " +
            "Dashed markers are experimental detections.",
        style = MaterialTheme.typography.bodySmall,
    )

    SectionTitle("Potential defects")
    result.defects.forEach { defect -> DefectRow(defect, onClick = { side = defect.side; zoomed = defect }) }

    SectionTitle("What limits the PSA range")
    LimitingFactors(result.estimate.psa)
    SectionTitle("What limits the BGS range")
    LimitingFactors(result.estimate.bgs.overall)

    SectionTitle("BGS candidate indicators")
    CandidateRow("Potential BGS 10 candidate", result.estimate.bgs.bgs10Candidate)
    CandidateRow("Potential Black Label candidate — experimental", result.estimate.bgs.blackLabelCandidate)
    Text(
        "Candidate indicators are not grades. A candidate would require exceptionally high confidence " +
            "in every category; professional graders may still find issues photos cannot show.",
        style = MaterialTheme.typography.bodySmall,
    )

    SectionTitle("Centering")
    result.centering.forEach { c ->
        when (c) {
            is CenteringResult.Measured -> {
                Text(c.side.label(), style = MaterialTheme.typography.labelLarge)
                LabeledValue("L/R", c.leftRight.format())
                LabeledValue("T/B", c.topBottom.format())
                LabeledValue("Confidence", c.confidence.label())
            }
            is CenteringResult.Unknown -> LabeledValue(c.side.label(), "Unknown — ${c.reason.name.lowercase().replace('_', ' ')}")
        }
    }

    SectionTitle("Surface checks")
    result.surface.forEach { s ->
        Text("${s.side.label()} (${s.sourceImageIds.size} photo${if (s.sourceImageIds.size == 1) "" else "s"})", style = MaterialTheme.typography.labelLarge)
        s.checks.forEach { (check, maturity) ->
            LabeledValue(check.label(), maturity.label())
        }
    }

    SectionTitle("Photo quality")
    result.imageQuality.forEach { q ->
        LabeledValue(
            q.sourceImageId.removePrefix("demo-"),
            if (!q.acceptable) "Retake needed" else if (q.warnings.isEmpty()) "OK" else q.warnings.joinToString { it.type.name.lowercase() },
        )
    }

    SectionTitle("Warnings")
    result.warnings.forEach { Text("• ${it.message}", style = MaterialTheme.typography.bodyMedium) }

    HorizontalDivider()
    Text(
        "Pipeline ${result.provenance.pipelineVersion} · rules ${result.estimate.ruleSetVersion} · ${result.provenance.processingMode}",
        style = MaterialTheme.typography.bodySmall,
    )
    DisclaimerCard()

    zoomed?.let { defect -> DefectZoomDialog(defect, isDemo = result.provenance.isDemo, onDismiss = { zoomed = null }) }
}

@Composable
private fun GradeTile(title: String, estimate: RangeEstimate, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            when (estimate) {
                is RangeEstimate.Available -> Text(estimate.range.format(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                is RangeEstimate.Unavailable -> Text("Not available", style = MaterialTheme.typography.titleMedium)
            }
            Text("Predicted range", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DefectRow(defect: CardDefect, onClick: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(Modifier.padding(top = 4.dp).size(14.dp), shape = CircleShape, color = severityColor(defect.severity)) {}
            Column(Modifier.weight(1f)) {
                Text(defect.locationLabel(), style = MaterialTheme.typography.labelLarge)
                Text(defect.description, style = MaterialTheme.typography.bodyLarge)
                Text("Severity: ${defect.severity.label()} · Confidence: ${defect.confidence.label()}", style = MaterialTheme.typography.bodySmall)
            }
            if (defect.maturity != DetectionMaturity.SUPPORTED) {
                AssistChip(onClick = onClick, label = { Text(defect.maturity.label()) })
            }
        }
    }
}

@Composable
private fun LimitingFactors(estimate: RangeEstimate) {
    when (estimate) {
        is RangeEstimate.Available ->
            if (estimate.limitingFactors.isEmpty()) {
                Text("No limiting defects identified.", style = MaterialTheme.typography.bodyMedium)
            } else {
                estimate.limitingFactors.forEach { Text("• ${it.description}", style = MaterialTheme.typography.bodyMedium) }
            }
        is RangeEstimate.Unavailable -> Text(estimate.reason, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CandidateRow(title: String, indicator: CandidateIndicator) {
    val status = when (indicator.status) {
        CandidateStatus.POTENTIAL_CANDIDATE -> "Potential candidate"
        CandidateStatus.NOT_INDICATED -> "Not indicated"
        CandidateStatus.NOT_EVALUATED -> "Not evaluated"
    }
    Column {
        LabeledValue(title, status)
        indicator.reasons.forEach { Text("  – $it", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun DefectZoomDialog(defect: CardDefect, isDemo: Boolean, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(defect.description, style = MaterialTheme.typography.titleMedium)
                Text(defect.locationLabel(), style = MaterialTheme.typography.labelLarge)
                CardDiagram(
                    modifier = Modifier.fillMaxWidth(),
                    defects = listOf(defect),
                    selectedDefectId = defect.id,
                    focus = defect.location.boundingBox,
                )
                Text(
                    if (isDemo) "Zoomed illustration of the sample location (no photo)." else "Zoomed view of the affected area.",
                    style = MaterialTheme.typography.bodySmall,
                )
                LabeledValue("Category", defect.category.label())
                LabeledValue("Severity", defect.severity.label())
                LabeledValue("Confidence", defect.confidence.label())
                // A demo defect was never detected in any photo, so don't present detector evidence or a source photo.
                if (isDemo) {
                    LabeledValue("Evidence", "Demo sample")
                    LabeledValue("Source", "Demo sample")
                } else {
                    LabeledValue("Detector", "${defect.algorithmVersion} (${defect.maturity.label().lowercase()})")
                    LabeledValue("Source photo", defect.sourceImageId.removePrefix("demo-"))
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
            }
        }
    }
}
