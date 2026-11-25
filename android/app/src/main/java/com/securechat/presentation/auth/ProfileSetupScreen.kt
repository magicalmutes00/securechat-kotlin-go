package com.securechat.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.securechat.presentation.components.AppleButton
import com.securechat.presentation.components.AppleFormField
import com.securechat.presentation.components.AppleInlineError
import com.securechat.presentation.components.AppleTextButton
import com.securechat.presentation.theme.AppleColor
import com.securechat.presentation.theme.AppleSpace
import com.securechat.presentation.theme.AppleType

@Composable
fun ProfileSetupScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit
) {
    val viewModel = androidx.hilt.navigation.compose.hiltViewModel<ProfileSetupViewModel>()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleColor.SystemBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppleSpace.Xxl)
    ) {
        Spacer(modifier = Modifier.height(96.dp))

        Text(text = "Set Up Your Profile", style = AppleType.LargeTitle, color = AppleColor.Label)
        Spacer(modifier = Modifier.height(AppleSpace.Sm))
        Text(
            text = "This will be visible to your contacts.",
            style = AppleType.Body2,
            color = AppleColor.Label3
        )

        Spacer(modifier = Modifier.height(AppleSpace.Xxl))

        AppleFormField(
            value = viewModel.displayName.value,
            onValueChange = { viewModel.onDisplayNameChanged(it) },
            placeholder = "Display name"
        )

        Spacer(modifier = Modifier.height(AppleSpace.Md))

        AppleFormField(
            value = viewModel.username.value,
            onValueChange = { viewModel.onUsernameChanged(it) },
            placeholder = "Username (optional)"
        )

        viewModel.errorMessage.value?.let { error ->
            Spacer(modifier = Modifier.height(AppleSpace.Md))
            AppleInlineError(message = error)
        }

        Spacer(modifier = Modifier.height(AppleSpace.Xxl))

        AppleButton(
            text = if (viewModel.isLoading.value) "Saving…" else "Complete Setup",
            enabled = viewModel.displayName.value.isNotBlank() && !viewModel.isLoading.value,
            onClick = { viewModel.completeProfile(onComplete) }
        )

        Spacer(modifier = Modifier.height(AppleSpace.Lg))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppleTextButton(
                text = "Skip for now",
                enabled = !viewModel.isLoading.value,
                onClick = onSkip
            )
        }
    }
}
