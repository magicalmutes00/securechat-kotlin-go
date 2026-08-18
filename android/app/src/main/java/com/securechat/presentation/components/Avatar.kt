package com.securechat.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.securechat.R
import kotlin.math.absoluteValue

@Composable
fun Avatar(
    url: String?,
    name: String,
    modifier: Modifier = Modifier,
    size: Int = 40
) {
    val backgroundColor = getColorForName(name)
    val initials = getInitials(name)
    
    Box(
        modifier = modifier.size(size.dp),
        contentAlignment = Alignment.Center
    ) {
        url?.let { imageUrl ->
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } ?: run {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
                    .clip(CircleShape)
            ) {
                Text(
                    text = initials,
                    color = Color.White,
                    fontSize = (size / 2).sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun getColorForName(name: String): Color {
    val colors = listOf(
        Color(0xFF006874), // Teal
        Color(0xFF388E3C), // Green
        Color(0xFFD32F2F), // Red
        Color(0xFFF57C00), // Orange
        Color(0xFF7B1FA2), // Purple
        Color(0xFF1976D2), // Blue
        Color(0xFFC2185B), // Pink
        Color(0xFF5D4037), // Brown
    )
    return colors[name.hashCode().absoluteValue % colors.size]
}

private fun getInitials(name: String): String {
    val parts = name.trim().split(" ")
    if (parts.size >= 2) {
        return "${parts[0][0]}${parts[1][0]}".uppercase()
    } else if (parts.size == 1 && parts[0].length >= 2) {
        return parts[0].substring(0, 2).uppercase()
    } else {
        return name.substring(0, 1).uppercase()
    }
}