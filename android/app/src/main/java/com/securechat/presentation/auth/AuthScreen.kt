package com.securechat.presentation.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.securechat.R
import com.securechat.core.utils.DeviceInfo
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

/** Unwraps the Activity from a (possibly wrapped) Compose context. */
private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    onOtpSent: (phoneNumber: String) -> Unit
) {
    val context = LocalContext.current
    val phoneViewModel = hiltViewModel<PhoneLoginViewModel>()
    val googleViewModel = hiltViewModel<GoogleLoginViewModel>()
    val scope = rememberCoroutineScope()

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
                        android.util.Log.e("GoogleAuth", "Google sign-in failed", e)
                        googleViewModel.errorMessage.value = when (e) {
                            is androidx.credentials.exceptions.NoCredentialException ->
                                "No Google account is available. If this is a debug build, make sure its " +
                                    "SHA-1 is registered and uninstall any older build of this app."
                            is androidx.credentials.exceptions.GetCredentialCancellationException ->
                                "Google sign-in cancelled."
                            // Play Services errors (e.g. "Developer console is not set up
                            // correctly") carry the actual fix in their message — surface it.
                            is androidx.credentials.exceptions.GetCredentialCustomException ->
                                "Google sign-in failed: ${e.message ?: "credential provider error"}"
                            else -> "Google sign-in failed: ${e.message ?: "please try again"}"
                        }
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

        // Country dial code picker (defaults to India +91) beside the number.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CountryCodeDropdown(
                selected = phoneViewModel.countryCode.value,
                onSelected = { phoneViewModel.onCountryCodeChanged(it) },
                enabled = !phoneViewModel.isLoading.value
            )

            TextField(
                value = phoneViewModel.phoneNumber.value,
                onValueChange = { phoneViewModel.onPhoneNumberChanged(it.filter { c -> c.isDigit() }) },
                modifier = Modifier.weight(1f),
                label = { Text("Phone number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                enabled = !phoneViewModel.isLoading.value
            )
        }

        Spacer(modifier = Modifier.padding(16.dp))

        Button(
            onClick = {
                val activity = context.findActivity()
                if (activity == null) {
                    phoneViewModel.errorMessage.value = "Unable to start phone sign-in."
                    return@Button
                }
                phoneViewModel.sendOtp(
                    activity = activity,
                    deviceName = DeviceInfo.deviceName(),
                    deviceIdentifier = DeviceInfo.deviceIdentifier(context),
                    onCodeSent = { onOtpSent(phoneViewModel.buildE164()) },
                    onAuthenticated = { onAuthSuccess() }
                )
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            enabled = phoneViewModel.phoneNumber.value.isNotBlank() && !phoneViewModel.isLoading.value
        ) {
            if (phoneViewModel.isLoading.value) {
                CircularProgressIndicator()
            } else {
                Text("Continue")
            }
        }

        phoneViewModel.errorMessage.value?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                fontSize = 14.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.error
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryCodeDropdown(
    selected: String,
    onSelected: (String) -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedCountry = CountryCodes.all.firstOrNull { it.code == selected }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = Modifier.width(132.dp)
    ) {
        OutlinedTextField(
            value = selectedCountry?.code ?: selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("Code") },
            singleLine = true,
            enabled = enabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            CountryCodes.all.forEach { country ->
                DropdownMenuItem(
                    text = { Text("${country.name} (${country.code})") },
                    onClick = {
                        onSelected(country.code)
                        expanded = false
                    }
                )
            }
        }
    }
}