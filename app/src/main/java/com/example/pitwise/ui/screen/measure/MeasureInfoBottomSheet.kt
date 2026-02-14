package com.example.pitwise.ui.screen.measure

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pitwise.ui.theme.PitwiseGray400
import com.example.pitwise.ui.theme.PitwisePrimary
import com.example.pitwise.ui.theme.PitwiseSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasureInfoBottomSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PitwiseSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            // Custom drag handle
            Spacer(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .height(4.dp)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            // ── Title ──
            Text(
                text = "📏 Cara Menggunakan Measure",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Section 1: DISTANCE ──
            SectionHeader(title = "DISTANCE")
            Spacer(modifier = Modifier.height(8.dp))

            SubSectionLabel("Digunakan untuk:")
            BulletItem("Mengukur jarak hauling")
            BulletItem("Mengukur panjang jalan")
            BulletItem("Mengukur panjang front")

            Spacer(modifier = Modifier.height(10.dp))

            SubSectionLabel("Cara pakai:")
            NumberedItem(1, "Tap titik pertama")
            NumberedItem(2, "Tap titik berikutnya")
            NumberedItem(3, "Total jarak otomatis dihitung")

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = PitwiseGray400.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))

            // ── Section 2: AREA ──
            SectionHeader(title = "AREA")
            Spacer(modifier = Modifier.height(8.dp))

            SubSectionLabel("Digunakan untuk:")
            BulletItem("Menghitung luas OB exposed")
            BulletItem("Menghitung luas coal exposed")

            Spacer(modifier = Modifier.height(10.dp))

            SubSectionLabel("Cara pakai:")
            NumberedItem(1, "Tap minimal 3 titik")
            NumberedItem(2, "Tutup polygon")
            NumberedItem(3, "Luas otomatis muncul")

            Spacer(modifier = Modifier.height(28.dp))

            // ── Dismiss Button ──
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PitwisePrimary,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "✔ Mengerti",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

// ── Helper composables ──

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        ),
        color = PitwisePrimary
    )
}

@Composable
private fun SubSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = PitwiseGray400
    )
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun BulletItem(text: String) {
    Text(
        text = "  •  $text",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.85f),
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
private fun NumberedItem(number: Int, text: String) {
    Text(
        text = "  $number.  $text",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White.copy(alpha = 0.85f),
        modifier = Modifier.padding(vertical = 2.dp)
    )
}
