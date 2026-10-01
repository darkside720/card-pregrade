package com.cardpregrade.app.ui.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cardpregrade.app.AppContainer
import com.cardpregrade.app.ui.components.AppScaffold
import com.cardpregrade.app.ui.components.ScrollingContent

/** Pipeline stages in execution order with the phase that implements them. */
private val stages = listOf(
    "Card detection" to "Phase 4",
    "Perspective correction" to "Phase 4",
    "Image normalization" to "Phase 4",
    "Card-level quality checks (framing, perspective)" to "Phase 4",
    "Centering" to "Phase 4",
    "Corners" to "Phase 5",
    "Edges" to "Phase 5",
    "Whitening / chipping" to "Phase 5",
    "Surface (experimental)" to "Later",
    "Pre-grade estimate" to "Phase 6",
)

@Composable
fun AnalysisScreen(container: AppContainer, sessionId: String, onBack: () -> Unit, onViewDemo: () -> Unit, onHome: () -> Unit) {
    // Expanded form of keyed produceState, which AGP 8.7 lint misreports as ProduceStateDoesNotAssignValue.
    var photoCount by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(sessionId) {
        photoCount = container.scanRepository.getSession(sessionId)?.captures?.size ?: 0
    }
    AppScaffold(title = "Analysis", screenTag = "screen_analysis", onBack = onBack) { padding ->
        ScrollingContent(padding) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("No analysis was run", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${photoCount ?: "…"} photo(s) are stored in this app's private storage on this device. " +
                            "The computer-vision pipeline is not implemented in this build, so nothing about the " +
                            "card has been measured. Only capture quality (sharpness, exposure, resolution) was checked.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("analysis_status"),
                    )
                }
            }
            Text("Pipeline stages", style = MaterialTheme.typography.titleSmall)
            stages.forEach { (name, phase) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    Text(name, modifier = Modifier.weight(1f))
                    Text("Not implemented · $phase", style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(onClick = onViewDemo, modifier = Modifier.fillMaxWidth()) { Text("View demo result layout") }
            OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth()) { Text("Back to home") }
            Text(
                "Session ${sessionId.take(8)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
