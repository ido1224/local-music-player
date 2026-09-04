package com.nera.musicplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
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
