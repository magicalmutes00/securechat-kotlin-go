package com.securechat.presentation.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.securechat.R
import com.securechat.presentation.components.AppleCell
import com.securechat.presentation.components.AppleGroupedSection
import com.securechat.presentation.components.AppleIconTile
import com.securechat.presentation.components.AppleTopBar
import com.securechat.presentation.theme.AppleColor
import com.securechat.presentation.theme.AppleSpace
import com.securechat.presentation.theme.AppleType

@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleColor.SystemGroupedBackground)
    ) {
        AppleTopBar(title = "About", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            /* App identity block, centred like iOS About pages. */
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppleSpace.Xxxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(AppleSpace.Xs)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp)
                )
                Spacer(modifier = Modifier.height(AppleSpace.Sm))
                Text(
                    text = "SecureChat",
                    style = AppleType.Title1,
                    color = AppleColor.Label
                )
                Text(
                    text = "Version 1.0.0",
                    style = AppleType.Footnote,
                    color = AppleColor.Label3
                )
                Text(
                    text = "Private & Secure Messaging",
                    style = AppleType.Footnote,
                    color = AppleColor.Label3
                )
            }

            AppleGroupedSection(
                header = "Security",
                footer = "SecureChat is a private messaging application built with security and " +
                    "privacy as top priorities. Messages are encrypted end-to-end and media is " +
                    "stored securely with signed URLs."
            ) {
                AppleCell(
                    title = "End-to-End Encryption",
                    subtitle = "Messages encrypted on device",
                    leading = { AppleIconTile(Icons.Filled.Lock, background = AppleColor.SystemGreen) }
                )
                AppleCell(
                    title = "Secure Media Storage",
                    subtitle = "Stored with signed URLs",
                    leading = { AppleIconTile(Icons.Filled.Cloud, background = AppleColor.SystemBlue) }
                )
                AppleCell(
                    title = "Offline-First",
                    subtitle = "Works without internet",
                    leading = { AppleIconTile(Icons.Filled.OfflinePin, background = AppleColor.SystemOrange) }
                )
                AppleCell(
                    title = "Disappearing Messages",
                    subtitle = "Optional self-destructing messages",
                    leading = { AppleIconTile(Icons.Filled.Delete, background = AppleColor.SystemRed) },
                    showDivider = false
                )
            }

            AppleGroupedSection(header = "Legal & Links") {
                AppleCell(
                    title = "Privacy Policy",
                    leading = { AppleIconTile(Icons.Filled.Policy, background = AppleColor.SystemGray) },
                    showChevron = true,
                    dividerInset = 57.dp,
                    onClick = { /* Open privacy policy */ }
                )
                AppleCell(
                    title = "Terms of Service",
                    leading = { AppleIconTile(Icons.Filled.Description, background = AppleColor.SystemGray) },
                    showChevron = true,
                    dividerInset = 57.dp,
                    onClick = { /* Open terms */ }
                )
                AppleCell(
                    title = "Open Source Licenses",
                    leading = { AppleIconTile(Icons.Filled.Code, background = AppleColor.SystemGray) },
                    showChevron = true,
                    dividerInset = 57.dp,
                    onClick = { /* Open licenses */ }
                )
                AppleCell(
                    title = "Report a Bug",
                    leading = { AppleIconTile(Icons.Filled.BugReport, background = AppleColor.SystemGray) },
                    showChevron = true,
                    showDivider = false,
                    dividerInset = 57.dp,
                    onClick = { /* Open bug report */ }
                )
            }

            Text(
                text = "Built with ❤️ using Kotlin, Jetpack Compose, Go, and PostgreSQL",
                style = AppleType.Caption2,
                color = AppleColor.Label4,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppleSpace.Xxxl, vertical = AppleSpace.Xxl)
            )
        }
    }
}
