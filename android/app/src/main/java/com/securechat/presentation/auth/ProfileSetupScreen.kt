package com.securechat.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.R

@Composable
fun ProfileSetupScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit
) {
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<ProfileSetupViewModel>()

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Set up your profile",
            fontSize = 28.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )

        Spacer(modifier = Modifier.padding(8.dp))

        Text(
            text = "This will be visible to your contacts",
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.padding(24.dp))

        TextField(
            value = viewModel.displayName.value,
            onValueChange = { viewModel.onDisplayNameChanged(it) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            label = { Text("Display Name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            enabled = !viewModel.isLoading.value
        )

        Spacer(modifier = Modifier.padding(16.dp))

        TextField(
            value = viewModel.username.value,
            onValueChange = { viewModel.onUsernameChanged(it) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            label = { Text("Username (optional)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            enabled = !viewModel.isLoading.value
        )

        viewModel.errorMessage.value?.let { error ->
            Spacer(modifier = Modifier.padding(8.dp))
            Text(
                text = error,
                fontSize = 14.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.padding(24.dp))

        Button(
            onClick = { viewModel.completeProfile(onComplete) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            enabled = viewModel.displayName.value.isNotBlank() && !viewModel.isLoading.value
        ) {
            if (viewModel.isLoading.value) {
                CircularProgressIndicator()
            } else {
                Text("Complete Setup")
            }
        }

        Spacer(modifier = Modifier.padding(16.dp))

        TextButton(
            onClick = onSkip,
            enabled = !viewModel.isLoading.value
        ) {
            Text("Skip for now")
        }
    }
}