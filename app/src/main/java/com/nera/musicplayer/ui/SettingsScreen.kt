package com.nera.musicplayer.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nera.musicplayer.BuildConfig
import com.nera.musicplayer.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val currentTheme by settingsViewModel.theme.collectAsState()
    val showAnalysisBadges by settingsViewModel.showAnalysisBadges.collectAsState()
    val vinylEffectEnabled by settingsViewModel.vinylEffectEnabled.collectAsState()
    val bassPulseGlowEnabled by settingsViewModel.bassPulseGlowEnabled.collectAsState()
    val updateCheckState by settingsViewModel.updateCheckState.collectAsState()
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            ThemeOption(
                label = "Dark",
                selected = currentTheme == AppTheme.DARK,
                onClick = { settingsViewModel.setTheme(AppTheme.DARK) }
            )
            ThemeOption(
                label = "Light",
                selected = currentTheme == AppTheme.LIGHT,
                onClick = { settingsViewModel.setTheme(AppTheme.LIGHT) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text("Library", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show track analysis badges")
                Switch(
                    checked = showAnalysisBadges,
                    onCheckedChange = { settingsViewModel.setShowAnalysisBadges(it) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Vinyl record effect")
                Switch(
                    checked = vinylEffectEnabled,
                    onCheckedChange = { settingsViewModel.setVinylEffectEnabled(it) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).padding(start = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Bass pulse glow",
                    color = if (vinylEffectEnabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
                Switch(
                    checked = bassPulseGlowEnabled,
                    onCheckedChange = { settingsViewModel.setBassPulseGlowEnabled(it) },
                    enabled = vinylEffectEnabled
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text("Updates", style = MaterialTheme.typography.titleMedium)
            Text(
                "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            when (val state = updateCheckState) {
                is UpdateCheckUiState.Idle -> {
                    OutlinedButton(onClick = { settingsViewModel.checkForUpdate() }) {
                        Text("Check for updates")
                    }
                }
                is UpdateCheckUiState.Checking -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Checking for updates...")
                    }
                }
                is UpdateCheckUiState.UpToDate -> {
                    Text("You're up to date.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { settingsViewModel.checkForUpdate() }) {
                        Text("Check for updates")
                    }
                }
                is UpdateCheckUiState.Available -> {
                    Text(
                        "Update available: v${state.versionName}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.releaseUrl)))
                    }) {
                        Text("View release")
                    }
                }
                is UpdateCheckUiState.Error -> {
                    Text("Couldn't check for updates.", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { settingsViewModel.checkForUpdate() }) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}
