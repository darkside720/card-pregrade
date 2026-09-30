package com.cardpregrade.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardpregrade.app.AppContainer
import com.cardpregrade.app.ui.components.DisclaimerCard
import com.cardpregrade.app.ui.components.PRODUCT_TAGLINE

@Composable
fun HomeScreen(
    container: AppContainer,
    onNewScan: () -> Unit,
    onSavedScans: () -> Unit,
    onDemoResult: () -> Unit,
    onSettings: () -> Unit,
    onCalibration: () -> Unit,
) {
    val developerMode by container.developerMode.collectAsStateWithLifecycle()
    Scaffold(modifier = Modifier.testTag("screen_home")) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Card Pre-Grade", style = MaterialTheme.typography.headlineMedium)
            Text(PRODUCT_TAGLINE, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                "Guided photo capture and visual inspection of centering, corners, edges and surface for " +
                    "Pokémon and One Piece TCG cards, with an estimated PSA / BGS grade range.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Button(onClick = onNewScan, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                ButtonContent(Icons.Filled.Add, "New scan")
            }
            FilledTonalButton(onClick = onSavedScans, modifier = Modifier.fillMaxWidth()) {
                ButtonContent(Icons.AutoMirrored.Filled.List, "Saved scans")
            }
            OutlinedButton(onClick = onDemoResult, modifier = Modifier.fillMaxWidth()) {
                ButtonContent(Icons.Filled.PlayArrow, "View demo result")
            }
            OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                ButtonContent(Icons.Filled.Settings, "Settings & About")
            }
            if (developerMode) {
                OutlinedButton(onClick = onCalibration, modifier = Modifier.fillMaxWidth()) {
                    ButtonContent(Icons.Filled.Build, "Developer calibration")
                }
            }

            DisclaimerCard(Modifier.padding(top = 8.dp))
            Text(
                "Photos stay on this device. This build has no network access.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ButtonContent(icon: ImageVector, text: String) {
    Icon(icon, contentDescription = null)
    Text(text, modifier = Modifier.padding(start = 8.dp))
}
