package com.securechat.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.securechat.domain.model.ThemeMode
import com.securechat.presentation.components.AppleCell
import com.securechat.presentation.components.AppleGroupedSection
import com.securechat.presentation.components.AppleSelectRow
import com.securechat.presentation.components.AppleToggle
import com.securechat.presentation.components.AppleTopBar
import com.securechat.presentation.theme.AppleColor
import com.securechat.presentation.theme.AppleSpace

/** Shared iOS-style page for the settings substack: nav bar plus a scrolling body. */
@Composable
private fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleColor.SystemGroupedBackground)
    ) {
        AppleTopBar(title = title, onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            content()
            Spacer(modifier = Modifier.padding(bottom = AppleSpace.Xxxl))
        }
    }
}

@Composable
fun ThemeScreen(onBack: () -> Unit) {
    val viewModel = hiltViewModel<SettingsViewModel>()

    SettingsPage(title = "Appearance", onBack = onBack) {
        AppleGroupedSection(
            header = "Theme",
            footer = "SecureChat currently ships a dark appearance only. " +
                "Light and system themes are on the way."
        ) {
            /* Only the dark appearance is implemented, so the other rows are
               shown but not selectable rather than pretending to work. */
            AppleSelectRow(
                title = "System",
                subtitle = "Follows your device setting",
                selected = false,
                enabled = false,
                onClick = {}
            )
            AppleSelectRow(
                title = "Light",
                selected = false,
                enabled = false,
                onClick = {}
            )
            AppleSelectRow(
                title = "Dark",
                selected = true,
                showDivider = false,
                onClick = { viewModel.setTheme(ThemeMode.DARK) }
            )
        }
    }
}

@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val viewModel = hiltViewModel<SettingsViewModel>()
    val settings by viewModel.settings.collectAsState()
    val enabled = settings?.notificationsEnabled ?: true

    SettingsPage(title = "Notifications", onBack = onBack) {
        AppleGroupedSection(
            header = "Messages",
            footer = "Push notifications require the notification permission on this device."
        ) {
            AppleCell(
                title = "Allow Notifications",
                subtitle = "Show alerts for new messages",
                trailing = {
                    AppleToggle(
                        checked = enabled,
                        onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                    )
                },
                showDivider = false,
                onClick = { viewModel.setNotificationsEnabled(!enabled) }
            )
        }
    }
}

@Composable
fun ChatSettingsScreen(onBack: () -> Unit) {
    val viewModel = hiltViewModel<SettingsViewModel>()
    val settings by viewModel.settings.collectAsState()
    val autoDownload = settings?.mediaAutoDownload ?: true

    SettingsPage(title = "Chat Settings", onBack = onBack) {
        AppleGroupedSection(
            header = "Media",
            footer = "When off, photos and videos download only when you tap them."
        ) {
            AppleCell(
                title = "Auto-Download Media",
                subtitle = "Automatically download photos and videos",
                trailing = {
                    AppleToggle(
                        checked = autoDownload,
                        onCheckedChange = { viewModel.setMediaAutoDownload(it) }
                    )
                },
                showDivider = false,
                onClick = { viewModel.setMediaAutoDownload(!autoDownload) }
            )
        }
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

    SettingsPage(title = "Language", onBack = onBack) {
        AppleGroupedSection(
            header = "App Language",
            footer = "Message content is translated by neither us nor the app — " +
                "this only changes the app's interface language."
        ) {
            languages.forEachIndexed { index, (code, label) ->
                AppleSelectRow(
                    title = label,
                    selected = selected == code,
                    showDivider = index != languages.lastIndex,
                    onClick = { viewModel.setLanguage(code) }
                )
            }
        }
    }
}
