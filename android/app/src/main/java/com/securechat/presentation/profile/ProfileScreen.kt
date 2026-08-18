package com.securechat.presentation.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.R
import com.securechat.presentation.components.Avatar
import com.securechat.presentation.theme.Theme

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    var displayName by remember { mutableStateOf("John Doe") }
    var username by remember { mutableStateOf("johndoe") }
    var bio by remember { mutableStateOf("SecureChat user") }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top
    ) {
        // Header with avatar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Avatar(url = null, name = "John Doe", size = 100)
                
                Spacer(modifier = Modifier.padding(16.dp))
                
                Text(text = "Edit Profile", fontSize = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }
        
        Divider()
        
        // Form Fields
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextField(
                value = displayName,
                onValueChange = { displayName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Display Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            
            TextField(
                value = username,
                onValueChange = { username = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Username") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                leadingIcon = { Text("@", fontSize = 16.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant) }
            )
            
            TextField(
                value = bio,
                onValueChange = { bio = it },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                label = { Text("Bio") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )
            
            Divider()
            
            // Avatar Section
            Text(text = "Profile Picture", fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            
            Avatar(url = null, name = "John Doe", size = 80)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = { /* Pick image */ }) {
                    Text("Change Photo")
                }
                OutlinedButton(onClick = { /* Remove photo */ }) {
                    Text("Remove")
                }
            }
            
            Spacer(modifier = Modifier.padding(24.dp))
            
            // Save Button
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = displayName.isNotBlank()
            ) {
                Text("Save Changes")
            }
        }
    }
}