package com.securechat.presentation.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securechat.core.utils.DeviceInfo
import com.securechat.core.utils.FormatOtp
import com.securechat.presentation.components.AppleButton
import com.securechat.presentation.components.AppleFormField
import com.securechat.presentation.components.AppleInlineError
import com.securechat.presentation.components.AppleTextButton
import com.securechat.presentation.theme.AppleColor
import com.securechat.presentation.theme.AppleSpace
import com.securechat.presentation.theme.AppleType

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
fun OtpVerifyScreen(
    onVerifySuccess: () -> Unit,
    onResendOtp: () -> Unit
) {
    val context = LocalContext.current
    val viewModel = hiltViewModel<OtpVerifyViewModel>()
    var otp by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleColor.SystemBackground)
            .padding(horizontal = AppleSpace.Xxl)
    ) {
        Spacer(modifier = Modifier.height(96.dp))

        Text(text = "Enter Code", style = AppleType.LargeTitle, color = AppleColor.Label)
        Spacer(modifier = Modifier.height(AppleSpace.Sm))
        Text(
            text = "We sent a 6-digit code to ${viewModel.displayPhone}",
            style = AppleType.Body2,
            color = AppleColor.Label3
        )

        Spacer(modifier = Modifier.height(AppleSpace.Xxl))

        AppleFormField(
            value = otp,
            onValueChange = { newOtp ->
                otp = newOtp.filter { it.isDigit() }.take(6)
                viewModel.clearError()
            },
            placeholder = "000000",
            keyboardType = KeyboardType.Number,
            visualTransformation = VisualTransformation { text ->
                val formatted = FormatOtp.format(text.text)
                TransformedText(AnnotatedString(formatted), OffsetMapping.Identity)
            }
        )

        viewModel.errorMessage.value?.let { error ->
            Spacer(modifier = Modifier.height(AppleSpace.Md))
            AppleInlineError(message = error)
        }

        Spacer(modifier = Modifier.height(AppleSpace.Lg))

        AppleButton(
            text = if (viewModel.isLoading.value) "Verifying…" else "Verify",
            enabled = otp.length == 6 && !viewModel.isLoading.value,
            onClick = {
                viewModel.verifyOtp(
                    code = otp,
                    deviceName = DeviceInfo.deviceName(),
                    deviceIdentifier = DeviceInfo.deviceIdentifier(context),
                    onSuccess = { onVerifySuccess() },
                    onFailure = { otp = "" }
                )
            }
        )

        Spacer(modifier = Modifier.height(AppleSpace.Xl))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (viewModel.resendCooldown.value > 0) {
                Text(
                    text = "Resend in ${viewModel.resendCooldown.value}s",
                    style = AppleType.Footnote,
                    color = AppleColor.Label3
                )
            } else {
                AppleTextButton(
                    text = "Resend Code",
                    enabled = !viewModel.isLoading.value,
                    onClick = {
                        val activity = context.findActivity()
                        if (activity != null) {
                            viewModel.resendCode(
                                activity = activity,
                                deviceName = DeviceInfo.deviceName(),
                                deviceIdentifier = DeviceInfo.deviceIdentifier(context),
                                onResent = { onResendOtp() },
                                onAuthenticated = { onVerifySuccess() }
                            )
                        }
                    }
                )
            }
        }
    }
}
