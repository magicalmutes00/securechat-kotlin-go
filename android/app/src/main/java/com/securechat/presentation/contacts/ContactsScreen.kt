package com.securechat.presentation.contacts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.securechat.R
import com.securechat.presentation.components.Avatar
import com.securechat.presentation.theme.Theme

@Composable
fun ContactsScreen(
    onContactClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val searchText by remember { mutableStateOf("") }
    
    // Sample contacts - would come from ViewModel
    val contacts = listOf(
        Contact("John Doe", "+15551234567", "johndoe", null),
        Contact("Jane Smith", "+15559876543", "janesmith", null),
        Contact("Bob Wilson", "+15551112222", "bobw", null),
        Contact("Alice Brown", "+15553334444", "aliceb", null),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            TextField(
                value = searchText,
                onValueChange = { },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search contacts...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )
        }
        
        Divider()
        
        // Contacts List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(contacts) { contact ->
                ContactItem(contact = contact, onClick = { onContactClick(contact.phoneNumber) })
                Divider(modifier = Modifier.padding(start = 72.dp))
            }
        }
    }
}

data class Contact(
    val name: String,
    val phoneNumber: String,
    val username: String,
    val avatarUrl: String?
)

@Composable
fun ContactItem(
    contact: Contact,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        leadingContent = {
            Avatar(url = contact.avatarUrl, name = contact.name, size = 48)
        },
        headlineContent = {
            Text(text = contact.name, fontSize = 16.sp)
        },
        supportingContent = {
            Text(text = contact.username, fontSize = 14.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Text(text = contact.phoneNumber, fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    )
}