package com.cardpregrade.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardpregrade.app.AppContainer
import com.cardpregrade.app.BuildConfig
import com.cardpregrade.app.ui.components.AppScaffold
import com.cardpregrade.app.ui.components.DisclaimerCard
import com.cardpregrade.app.ui.components.LabeledValue
import com.cardpregrade.app.ui.components.PRODUCT_TAGLINE
import com.cardpregrade.app.ui.components.ScrollingContent
import com.cardpregrade.app.ui.components.SectionTitle

@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit, onCalibration: () -> Unit) {
    val developerMode by container.developerMode.collectAsStateWithLifecycle()

    AppScaffold(title = "Settings & About", screenTag = "screen_settings", onBack = onBack) { padding ->
        ScrollingContent(padding) {
            SectionTitle("About")
            Text("Card Pre-Grade", style = MaterialTheme.typography.titleLarge)
            Text(PRODUCT_TAGLINE, style = MaterialTheme.typography.bodyMedium)
            LabeledValue("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            LabeledValue("Supported games", "Pokémon, One Piece TCG")
            LabeledValue("Analysis pipeline", "Not implemented yet")
            DisclaimerCard()
            Text(
                "This app is not affiliated with, endorsed by, or connected to PSA, Beckett (BGS), CGC, " +
                    "The Pokémon Company, Nintendo, or Bandai.",
                style = MaterialTheme.typography.bodySmall,
            )

            SectionTitle("Privacy")
            LabeledValue("Processing mode", "Local only")
            LabeledValue("Cloud analysis", "Not available")
            Text(
                "Card photos and scan history stay on this device. This build has no internet permission and " +
                    "device backup is disabled. Any future cloud analysis will require your explicit consent for each upload.",
                style = MaterialTheme.typography.bodySmall,
            )

            SectionTitle("Developer")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Developer calibration mode")
                    Text("Shows intermediate computer-vision output for tuning.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = developerMode,
                    onCheckedChange = { container.developerMode.value = it },
                    modifier = Modifier.testTag("developer_mode_switch"),
                )
            }
            if (developerMode) {
                OutlinedButton(onClick = onCalibration, modifier = Modifier.fillMaxWidth()) { Text("Open calibration screen") }
            }
        }
    }
}
