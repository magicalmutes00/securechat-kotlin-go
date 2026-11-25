package com.securechat.presentation.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.securechat.R
import com.securechat.core.utils.DeviceInfo
import com.securechat.presentation.components.AppleActionSheet
import com.securechat.presentation.components.AppleButton
import com.securechat.presentation.components.AppleFormField
import com.securechat.presentation.components.AppleInlineError
import com.securechat.presentation.components.AppleSheetAction
import com.securechat.presentation.components.AppleTertiaryButton
import com.securechat.presentation.theme.AppleColor
import com.securechat.presentation.theme.AppleSpace
import com.securechat.presentation.theme.AppleType
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

    var countryPickerOpen by remember { mutableStateOf(false) }
    val phoneLoading = phoneViewModel.isLoading.value
    val googleLoading = googleViewModel.isLoading.value

    val continueWithGoogle: () -> Unit = {
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
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppleColor.SystemBackground)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppleSpace.Xxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(72.dp))

            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(84.dp)
            )
            Spacer(modifier = Modifier.height(AppleSpace.Lg))
            Text(text = "SecureChat", style = AppleType.LargeTitle, color = AppleColor.Label)
            Spacer(modifier = Modifier.height(AppleSpace.Xs))
            Text(
                text = "Private, end-to-end encrypted messaging",
                style = AppleType.Footnote,
                color = AppleColor.Label3
            )

            Spacer(modifier = Modifier.height(AppleSpace.Huge))

            /* Phone number: dial code beside the national number. */
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppleSpace.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CountryField(
                    code = phoneViewModel.countryCode.value,
                    enabled = !phoneLoading,
                    onClick = { countryPickerOpen = true }
                )
                Box(modifier = Modifier.weight(1f)) {
                    AppleFormField(
                        value = phoneViewModel.phoneNumber.value,
                        onValueChange = {
                            phoneViewModel.onPhoneNumberChanged(it.filter { c -> c.isDigit() })
                        },
                        placeholder = "Phone number",
                        keyboardType = KeyboardType.Phone
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppleSpace.Lg))

            AppleButton(
                text = if (phoneLoading) "Sending…" else "Continue",
                enabled = phoneViewModel.phoneNumber.value.isNotBlank() && !phoneLoading,
                onClick = {
                    val activity = context.findActivity()
                    if (activity == null) {
                        phoneViewModel.errorMessage.value = "Unable to start phone sign-in."
                        return@AppleButton
                    }
                    phoneViewModel.sendOtp(
                        activity = activity,
                        deviceName = DeviceInfo.deviceName(),
                        deviceIdentifier = DeviceInfo.deviceIdentifier(context),
                        onCodeSent = { onOtpSent(phoneViewModel.buildE164()) },
                        onAuthenticated = { onAuthSuccess() }
                    )
                }
            )

            phoneViewModel.errorMessage.value?.let { error ->
                Spacer(modifier = Modifier.height(AppleSpace.Md))
                AppleInlineError(message = error)
            }

            Spacer(modifier = Modifier.height(AppleSpace.Xxl))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(0.5.dp)
                        .background(AppleColor.Separator)
                )
                Text(
                    text = "or",
                    style = AppleType.Footnote,
                    color = AppleColor.Label3,
                    modifier = Modifier.padding(horizontal = AppleSpace.Md)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(0.5.dp)
                        .background(AppleColor.Separator)
                )
            }

            Spacer(modifier = Modifier.height(AppleSpace.Xxl))

            AppleTertiaryButton(
                text = if (googleLoading) "Signing in…" else "Continue with Google",
                onClick = { if (!googleLoading) continueWithGoogle() }
            )

            googleViewModel.errorMessage.value?.let { error ->
                Spacer(modifier = Modifier.height(AppleSpace.Md))
                AppleInlineError(message = error)
            }

            Spacer(modifier = Modifier.height(AppleSpace.Huge))
        }

        AppleActionSheet(
            visible = countryPickerOpen,
            onDismiss = { countryPickerOpen = false },
            title = "Country",
            actions = CountryCodes.all.map { country ->
                AppleSheetAction(
                    label = "${country.name}  ${country.code}",
                    onClick = {
                        phoneViewModel.onCountryCodeChanged(country.code)
                        countryPickerOpen = false
                    }
                )
            }
        )
    }
}

/** Tappable dial-code field that matches [AppleFormField]'s surface. */
@Composable
private fun CountryField(
    code: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .width(104.dp)
            .height(45.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AppleColor.SystemSecondaryBackground)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = AppleSpace.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = code, style = AppleType.Body, color = AppleColor.Label)
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = "Country code",
            tint = AppleColor.Chevron,
            modifier = Modifier.size(16.dp)
        )
    }
}
