package com.cardpregrade.app.ui.saved

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cardpregrade.app.AppContainer
import com.cardpregrade.app.demo.DemoInspection
import com.cardpregrade.app.ui.components.AppScaffold
import com.cardpregrade.app.ui.components.ScrollingContent
import com.cardpregrade.app.ui.components.SectionTitle
import com.cardpregrade.app.ui.label
import com.cardpregrade.core.data.repository.ScanRepository
import com.cardpregrade.core.data.repository.ScanSessionSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class SavedScansViewModel(scans: ScanRepository) : ViewModel() {
    /** Null while loading. */
    val sessions: StateFlow<List<ScanSessionSummary>?> =
        scans.observeSessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

private val dateFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

@Composable
fun SavedScansScreen(container: AppContainer, onBack: () -> Unit, onOpenDemo: () -> Unit, onOpenSession: (String) -> Unit) {
    val vm: SavedScansViewModel = viewModel { SavedScansViewModel(container.scanRepository) }
    val sessions by vm.sessions.collectAsStateWithLifecycle()

    AppScaffold(title = "Saved scans", screenTag = "screen_saved", onBack = onBack) { padding ->
        ScrollingContent(padding) {
            SectionTitle("On this device")
            val list = sessions
            when {
                list == null -> Text("Loading…")
                list.isEmpty() -> Text("No scans yet. Start one from New scan.", style = MaterialTheme.typography.bodyMedium)
                else -> list.forEach { summary ->
                    OutlinedCard(Modifier.fillMaxWidth().clickable { onOpenSession(summary.session.id) }.testTag("saved_session")) {
                        Column(Modifier.padding(12.dp)) {
                            Text(summary.cardLabel, style = MaterialTheme.typography.titleSmall)
                            Text(
                                dateFormat.format(summary.session.createdAt.atZone(ZoneId.systemDefault())),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "${summary.session.status.label()} · ${summary.captureCount} of 4 photos · not analyzed · tap to open",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            SectionTitle("Sample")
            OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = onOpenDemo)) {
                Column(Modifier.padding(12.dp)) {
                    Text("DEMO · ${DemoInspection.create().cardLabel}", style = MaterialTheme.typography.titleSmall)
                    Text("Mock result for evaluating the results screen. Not a real scan.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
