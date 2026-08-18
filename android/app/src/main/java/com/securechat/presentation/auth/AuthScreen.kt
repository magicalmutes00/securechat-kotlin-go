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
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.R

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
    var phoneNumber by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

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

        Text(
            text = "Enter your phone number",
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