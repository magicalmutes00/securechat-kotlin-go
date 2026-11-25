package com.securechat.presentation.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securechat.presentation.components.AppleAvatar
import com.securechat.presentation.components.AppleButton
import com.securechat.presentation.components.AppleFormField
import com.securechat.presentation.components.AppleInlineError
import com.securechat.presentation.components.AppleProgressBar
import com.securechat.presentation.components.AppleTextButton
import com.securechat.presentation.components.AppleTopBar
import com.securechat.presentation.theme.AppleColor
import com.securechat.presentation.theme.AppleSpace
import com.securechat.presentation.theme.AppleType

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onSave: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.uploadAvatar(uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleColor.SystemGroupedBackground)
    ) {
        AppleTopBar(title = "Edit Profile", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppleSpace.Lg)
        ) {
            /* Avatar block: tap the photo or the text button to change it. */
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppleSpace.Xxl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = !viewModel.isUploadingAvatar.value) {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppleAvatar(
                        url = viewModel.avatarUrl.value,
                        name = viewModel.displayName.value.ifBlank { "User" },
                        size = 100
                    )
                }

                Spacer(modifier = Modifier.height(AppleSpace.Xs))

                AppleTextButton(
                    text = "Change Photo",
                    enabled = !viewModel.isUploadingAvatar.value,
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )

                if (viewModel.avatarUrl.value != null) {
                    AppleTextButton(
                        text = "Remove Photo",
                        enabled = !viewModel.isUploadingAvatar.value,
                        color = AppleColor.SystemRed,
                        onClick = { viewModel.removeAvatar() }
                    )
                }

                if (viewModel.isUploadingAvatar.value) {
                    Spacer(modifier = Modifier.height(AppleSpace.Sm))
                    AppleProgressBar(
                        progress = viewModel.uploadProgress.value / 100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(AppleSpace.Xs))
                    Text(
                        text = "Uploading… ${viewModel.uploadProgress.value}%",
                        style = AppleType.Footnote,
                        color = AppleColor.Label3
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppleSpace.Xxl))

            Text(
                text = "NAME",
                style = AppleType.SectionTitle,
                color = AppleColor.Label3,
                modifier = Modifier.padding(start = AppleSpace.Md, bottom = AppleSpace.Sm)
            )

            AppleFormField(
                value = viewModel.displayName.value,
                onValueChange = { viewModel.onDisplayNameChanged(it) },
                placeholder = "Display name"
            )

            Spacer(modifier = Modifier.height(AppleSpace.Md))

            AppleFormField(
                value = viewModel.username.value,
                onValueChange = { viewModel.onUsernameChanged(it) },
                placeholder = "@username"
            )

            viewModel.errorMessage.value?.let { error ->
                Spacer(modifier = Modifier.height(AppleSpace.Md))
                AppleInlineError(message = error)
            }

            viewModel.successMessage.value?.let { message ->
                Spacer(modifier = Modifier.height(AppleSpace.Md))
                Text(
                    text = message,
                    style = AppleType.Footnote,
                    color = AppleColor.SystemGreen
                )
            }

            Spacer(modifier = Modifier.height(AppleSpace.Xxl))

            AppleButton(
                text = if (viewModel.isSaving.value) "Saving…" else "Save Changes",
                enabled = viewModel.displayName.value.isNotBlank() &&
                    !viewModel.isSaving.value &&
                    !viewModel.isUploadingAvatar.value,
                onClick = { viewModel.save(onSaved = onSave) }
            )

            Spacer(modifier = Modifier.height(AppleSpace.Xxxl))
        }
    }
}
