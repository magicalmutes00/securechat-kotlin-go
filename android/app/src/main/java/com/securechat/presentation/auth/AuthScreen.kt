package com.securechat.presentation.auth

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.securechat.R
import com.securechat.core.utils.DeviceInfo
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
    val context = LocalContext.current
    val phoneViewModel = viewModel<PhoneLoginViewModel>()
    val googleViewModel = viewModel<GoogleLoginViewModel>()
    val scope = rememberCoroutineScope()
    var phoneNumber by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val googleServerClientId = context.getString(R.string.google_server_client_id)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "SecureChat",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.padding(16.dp))

        // Google Sign-In button
        OutlinedButton(
            onClick = {
                googleViewModel.clearError()
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setServerClientId(googleServerClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(true)
                    .build()
                val request = androidx.credentials.GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                val credentialManager = androidx.credentials.CredentialManager.create(context)
                scope.launch {
                    try {
                        val result = credentialManager.getCredential(context, request)
                        val credential = result.credential
                        if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                            val deviceName = DeviceInfo.deviceName()
                            val deviceIdentifier = DeviceInfo.deviceIdentifier(context)
                            googleViewModel.googleSignIn(
                                idToken = googleIdTokenCredential.idToken,
                                deviceName = deviceName,
                                deviceIdentifier = deviceIdentifier,
                                onSuccess = { onAuthSuccess() }
                            )
                        } else {
                            googleViewModel.errorMessage.value = "Google sign-in failed. Please try again."
                        }
                    } catch (e: Exception) {
                        googleViewModel.errorMessage.value = "Google sign-in cancelled or failed."
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            enabled = !googleViewModel.isLoading.value
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Continue with Google", fontSize = 16.sp)
            }
        }

        googleViewModel.errorMessage.value?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                fontSize = 14.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.error
            )
        }

        if (googleViewModel.isLoading.value) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                text = "  or  ",
                fontSize = 14.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Continue with phone",
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.padding(24.dp))

        TextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            label = { Text("Phone Number (+1 555 123 4567)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone
            ),
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.padding(16.dp))

        Button(
            onClick = {
                if (phoneNumber.isNotBlank()) {
                    isLoading = true
                    // TODO: Call send OTP use case
                    // For now, simulate success
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        isLoading = false
                        onAuthSuccess()
                    }, 1000)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            enabled = phoneNumber.isNotBlank() && !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Text("Continue")
            }
        }
    }
}