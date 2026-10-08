package com.securechat.presentation.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securechat.presentation.components.Avatar

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Avatar(
                url = viewModel.avatarUrl.value,
                name = viewModel.displayName.value.ifBlank { "User" },
                size = 100
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
            ) {
                Button(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !viewModel.isUploadingAvatar.value
                ) {
                    Text("Change Photo")
                }
                OutlinedButton(
                    onClick = { viewModel.removeAvatar() },
                    enabled = viewModel.avatarUrl.value != null && !viewModel.isUploadingAvatar.value
                ) {
                    Text("Remove")
                }
            }

            if (viewModel.isUploadingAvatar.value) {
                LinearProgressIndicator(
                    progress = { viewModel.uploadProgress.value / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Uploading… ${viewModel.uploadProgress.value}%", fontSize = 13.sp)
            }

            Divider()

            TextField(
                value = viewModel.displayName.value,
                onValueChange = { viewModel.onDisplayNameChanged(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Display Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            TextField(
                value = viewModel.username.value,
                onValueChange = { viewModel.onUsernameChanged(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Username") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                leadingIcon = {
                    Text(
                        "@",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            viewModel.errorMessage.value?.let { error ->
                Text(
                    text = error,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            viewModel.successMessage.value?.let { message ->
                Text(
                    text = message,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.save(onSaved = onSave) },
                modifier = Modifier.fillMaxWidth(),
                enabled = viewModel.displayName.value.isNotBlank() &&
                    !viewModel.isSaving.value &&
                    !viewModel.isUploadingAvatar.value
            ) {
                if (viewModel.isSaving.value) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text("Save Changes")
                }
            }
        }
    }
}