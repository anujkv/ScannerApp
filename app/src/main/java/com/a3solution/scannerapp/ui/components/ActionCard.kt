package com.a3solution.scannerapp.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.a3solution.scannerapp.R

@Composable
fun ActionCard(modifier: Modifier = Modifier, icon: Any, title: String, subtitle: String, onClick: () -> Unit, isLoading: Boolean = false) {
    Card(modifier = modifier.fillMaxWidth().clickable(enabled = !isLoading) { onClick() }, colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, Color(0xFFE0E0E0))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(modifier = Modifier.size(36.dp).background(Color(0xFFF1F4F9), shape = MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = colorResource(R.color.primary))
                else when (icon) {
                    is ImageVector -> Icon(icon, contentDescription = null, tint = colorResource(R.color.primary), modifier = Modifier.size(20.dp))
                    is Painter -> Icon(icon, contentDescription = null, tint = colorResource(R.color.primary), modifier = Modifier.size(20.dp))
                }
            }
            @Suppress("DEPRECATION")
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, color = colorResource(R.color.text_primary))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1)
        }
    }
}
