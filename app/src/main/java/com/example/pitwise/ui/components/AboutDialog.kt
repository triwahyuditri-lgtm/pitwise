package com.example.pitwise.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.pitwise.ui.theme.PitwiseGray400
import com.example.pitwise.ui.theme.PitwisePrimary
import com.example.pitwise.ui.theme.PitwiseSurface

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "About PITWISE",
                style = MaterialTheme.typography.headlineSmall,
                color = PitwisePrimary
            )
        },
        text = {
            Column {
                Text(
                    text = "Version 1.0.0",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Start productive, stay wise.\n\nProductivity Improvement Tool for Mining Operations. Designed for offline-first usage in remote areas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Developers:",
                    style = MaterialTheme.typography.titleSmall,
                    color = PitwisePrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "• Tri Wahyudi\n• Vigo Najmiadhim",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Contact:",
                    style = MaterialTheme.typography.titleSmall,
                    color = PitwisePrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Email
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:admin@pitwise.web.id")
                        }
                        context.startActivity(intent)
                    }
                ) {
                    Icon(
                        Icons.Default.Email, null,
                        tint = PitwisePrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "admin@pitwise.web.id",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PitwisePrimary,
                        textDecoration = TextDecoration.Underline
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                // Website
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://pitwise.web.id"))
                        context.startActivity(intent)
                    }
                ) {
                    Icon(
                        Icons.Default.Language, null,
                        tint = PitwisePrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "pitwise.web.id",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PitwisePrimary,
                        textDecoration = TextDecoration.Underline
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "© 2026 Pitwise Team",
                    style = MaterialTheme.typography.labelSmall,
                    color = PitwiseGray400
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = PitwisePrimary)
            }
        },
        containerColor = PitwiseSurface,
        titleContentColor = PitwisePrimary,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}
