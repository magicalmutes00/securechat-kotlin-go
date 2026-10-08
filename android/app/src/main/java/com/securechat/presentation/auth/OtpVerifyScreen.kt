package com.securechat.presentation.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.securechat.core.utils.DeviceInfo
import com.securechat.core.utils.FormatOtp

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
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Enter OTP",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.padding(8.dp))

        Text(
            text = "We sent a 6-digit code to ${viewModel.displayPhone}",
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.padding(24.dp))

        TextField(
            value = otp,
            onValueChange = { newOtp ->
                otp = newOtp.filter { it.isDigit() }.take(6)
                viewModel.clearError()
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            label = { Text("OTP Code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            visualTransformation = VisualTransformation { text ->
                val formatted = FormatOtp.format(text.text)
                TransformedText(AnnotatedString(formatted), OffsetMapping.Identity)
            },
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

        Spacer(modifier = Modifier.padding(16.dp))

        Button(
            onClick = {
                viewModel.verifyOtp(
                    code = otp,
                    deviceName = DeviceInfo.deviceName(),
                    deviceIdentifier = DeviceInfo.deviceIdentifier(context),
                    onSuccess = { onVerifySuccess() },
                    onFailure = { otp = "" }
                )
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            enabled = otp.length == 6 && !viewModel.isLoading.value
        ) {
            if (viewModel.isLoading.value) {
                CircularProgressIndicator()
            } else {
                Text("Verify")
            }
        }

        Spacer(modifier = Modifier.padding(16.dp))

        if (viewModel.resendCooldown.value > 0) {
            Text(
                text = "Resend in ${viewModel.resendCooldown.value}s",
                fontSize = 14.sp
            )
        } else {
            TextButton(
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
                },
                enabled = !viewModel.isLoading.value
            ) {
                Text("Resend OTP")
            }
        }
    }
}