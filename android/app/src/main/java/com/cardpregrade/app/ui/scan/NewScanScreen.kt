package com.cardpregrade.app.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cardpregrade.app.AppContainer
import com.cardpregrade.app.ui.components.AppScaffold
import com.cardpregrade.app.ui.components.LabeledValue
import com.cardpregrade.app.ui.components.ScrollingContent
import com.cardpregrade.app.ui.components.SectionTitle
import com.cardpregrade.app.ui.label
import com.cardpregrade.core.data.repository.ScanRepository
import com.cardpregrade.core.model.CaptureProtocol
import com.cardpregrade.core.model.Card
import com.cardpregrade.core.model.CardGame
import com.cardpregrade.core.model.ScanStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class NewScanUiState(
    val game: CardGame = CardGame.UNKNOWN,
    val label: String = "",
    val creating: Boolean = false,
)

class NewScanViewModel(private val scans: ScanRepository) : ViewModel() {
    private val _state = MutableStateFlow(NewScanUiState())
    val state: StateFlow<NewScanUiState> = _state.asStateFlow()

    val protocol: CaptureProtocol = CaptureProtocol.V1_STANDARD

    fun setGame(game: CardGame) = _state.update { it.copy(game = game) }
    fun setLabel(label: String) = _state.update { it.copy(label = label) }

    /** Creates a local-only session. Card identity is optional: "Unknown Card" is fully supported. */
    fun start(onCreated: (String) -> Unit) {
        if (_state.value.creating) return
        _state.update { it.copy(creating = true) }
        viewModelScope.launch {
            val s = _state.value
            val card = Card(id = UUID.randomUUID().toString(), game = s.game, userLabel = s.label.trim().ifBlank { null })
            val session = scans.createSession(card, protocol)
            scans.updateStatus(session.id, ScanStatus.CAPTURING)
            _state.update { it.copy(creating = false) }
            onCreated(session.id)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewScanScreen(container: AppContainer, onBack: () -> Unit, onSessionCreated: (String) -> Unit) {
    val vm: NewScanViewModel = viewModel { NewScanViewModel(container.scanRepository) }
    val state by vm.state.collectAsStateWithLifecycle()

    AppScaffold(title = "New scan", screenTag = "screen_new_scan", onBack = onBack) { padding ->
        ScrollingContent(padding) {
            SectionTitle("Card game")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CardGame.entries.forEach { game ->
                    FilterChip(
                        selected = state.game == game,
                        onClick = { vm.setGame(game) },
                        label = { Text(game.label()) },
                    )
                }
            }
            OutlinedTextField(
                value = state.label,
                onValueChange = vm::setLabel,
                label = { Text("Card name / number (optional)") },
                placeholder = { Text(Card.UNKNOWN_CARD_NAME) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Identification is optional. Grading never depends on knowing which card it is.",
                style = MaterialTheme.typography.bodySmall,
            )

            SectionTitle("Photos you will take")
            vm.protocol.steps.forEachIndexed { i, step ->
                Text("${i + 1}. ${step.title}", style = MaterialTheme.typography.bodyMedium)
            }

            SectionTitle("Processing")
            LabeledValue("Mode", "Local only (on this device)")
            LabeledValue("Cloud analysis", "Not available")

            Button(
                onClick = { vm.start(onSessionCreated) },
                enabled = !state.creating,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text("Start guided capture")
            }
        }
    }
}
