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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.R
import com.securechat.core.utils.FormatOtp
import com.securechat.presentation.theme.Theme

@Composable
fun OtpVerifyScreen(
    phoneNumber: String,
    deviceName: String,
    deviceIdentifier: String,
    onVerifySuccess: () -> Unit,
    onResendOtp: () -> Unit
) {
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<OtpVerifyViewModel>()
    var otp by remember { mutableStateOf("") }
    
    // Format OTP as user types (add space after 3 digits)
    val formattedOtp = FormatOtp.format(otp)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Enter OTP",
            fontSize = 28.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )

        Spacer(modifier = Modifier.padding(8.dp))

        Text(
            text = "We sent a 6-digit code to $phoneNumber",
            fontSize = 16.sp
        )

        Spacer(modifier = Modifier.padding(24.dp))

        TextField(
            value = otp,
            onValueChange = { newOtp ->
                // Only allow digits, max 6
                val filtered = newOtp.filter { it.isDigit() }.take(6)
                otp = filtered
                viewModel.onOtpChanged(filtered)
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            label = { Text("OTP Code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done
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
                viewModel.verifyOtp(phoneNumber, deviceName, deviceIdentifier) { success ->
                    if (success) onVerifySuccess()
                }
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
                onClick = onResendOtp,
                enabled = !viewModel.isLoading.value
            ) {
                Text("Resend OTP")
            }
        }
    }
}