package com.securechat.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securechat.domain.model.ThemeMode

/** Shared top bar with a working back button for the settings substack. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            content()
        }
    }
}

@Composable
fun ThemeScreen(onBack: () -> Unit) {
    val viewModel = hiltViewModel<SettingsViewModel>()
    val settings by viewModel.settings.collectAsState()
    val selected = settings?.theme ?: ThemeMode.SYSTEM

    SettingsScaffold(title = "Theme", onBack = onBack) {
        Text(
            text = "Choose how SecureChat looks. System follows your device setting.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )

        val options = listOf(
            ThemeMode.SYSTEM to "System default",
            ThemeMode.LIGHT to "Light",
            ThemeMode.DARK to "Dark"
        )

        options.forEach { (mode, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected == mode,
                        onClick = { viewModel.setTheme(mode) }
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RadioButton(selected = selected == mode, onClick = { viewModel.setTheme(mode) })
                Text(text = label, fontSize = 16.sp)
            }
            Divider(modifier = Modifier.padding(start = 52.dp))
        }
    }
}

@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val viewModel = hiltViewModel<SettingsViewModel>()
    val settings by viewModel.settings.collectAsState()
    val enabled = settings?.notificationsEnabled ?: true

    SettingsScaffold(title = "Notifications", onBack = onBack) {
        SettingToggleRow(
            title = "Enable notifications",
            subtitle = "Show alerts for new messages",
            checked = enabled,
            onCheckedChange = { viewModel.setNotificationsEnabled(it) }
        )
    }
}

@Composable
fun ChatSettingsScreen(onBack: () -> Unit) {
    val viewModel = hiltViewModel<SettingsViewModel>()
    val settings by viewModel.settings.collectAsState()
    val autoDownload = settings?.mediaAutoDownload ?: true

    SettingsScaffold(title = "Chat Settings", onBack = onBack) {
        SettingToggleRow(
            title = "Media auto-download",
            subtitle = "Automatically download photos and videos on Wi-Fi",
            checked = autoDownload,
            onCheckedChange = { viewModel.setMediaAutoDownload(it) }
        )
    }
}

@Composable
fun LanguageScreen(onBack: () -> Unit) {
    val viewModel = hiltViewModel<SettingsViewModel>()
    val settings by viewModel.settings.collectAsState()
    val selected = settings?.language ?: "en"

    val languages = listOf(
        "en" to "English",
        "hi" to "हिन्दी (Hindi)",
        "es" to "Español",
        "fr" to "Français",
        "de" to "Deutsch",
        "pt" to "Português",
        "ar" to "العربية",
        "zh" to "中文"
    )

    SettingsScaffold(title = "Language", onBack = onBack) {
        languages.forEach { (code, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected == code,
                        onClick = { viewModel.setLanguage(code) }
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RadioButton(selected = selected == code, onClick = { viewModel.setLanguage(code) })
                Text(text = label, fontSize = 16.sp)
            }
            Divider(modifier = Modifier.padding(start = 52.dp))
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}