package com.example.pitwise.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pitwise.ui.theme.PitwiseGray400
import com.example.pitwise.ui.theme.PitwisePrimary
import com.example.pitwise.ui.theme.PitwiseSurface

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
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
