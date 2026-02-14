package com.example.pitwise.ui.screen.map

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
fun MapInfoBottomSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PitwiseSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
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
                text = "🗺️ Cara Menggunakan Map",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Section 1: Open Map ──
            SectionHeader("BUKA MAP")
            Spacer(modifier = Modifier.height(8.dp))

            SubSectionLabel("Format yang didukung:")
            BulletItem("DXF — peta survei (AutoCAD)")
            BulletItem("PDF — peta cetak / gambar")

            Spacer(modifier = Modifier.height(10.dp))

            SubSectionLabel("Cara pakai:")
            NumberedItem(1, "Tap tombol 📂 MANAGE MAPS")
            NumberedItem(2, "Tap + untuk menambah file")
            NumberedItem(3, "Pilih map aktif (radio button)")
            NumberedItem(4, "Kembali ke Map View")

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = PitwiseGray400.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))

            // ── Section 2: Navigasi ──
            SectionHeader("NAVIGASI MAP")
            Spacer(modifier = Modifier.height(8.dp))

            SubSectionLabel("Gerakan:")
            BulletItem("Geser — drag 1 jari untuk pan")
            BulletItem("Pinch — cubit 2 jari untuk zoom")
            BulletItem("Double tap — zoom in")
            BulletItem("Tap 2 jari — zoom out")

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = PitwiseGray400.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))

            // ── Section 3: Koordinat ──
            SectionHeader("KOORDINAT")
            Spacer(modifier = Modifier.height(8.dp))

            SubSectionLabel("Cara melihat koordinat:")
            NumberedItem(1, "Tap di titik peta manapun")
            NumberedItem(2, "Marker biru muncul di titik tap")
            NumberedItem(3, "Tooltip menampilkan X, Y, Z")

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = PitwiseGray400.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))

            // ── Section 4: GPS ──
            SectionHeader("GPS")
            Spacer(modifier = Modifier.height(8.dp))

            SubSectionLabel("Fitur GPS:")
            BulletItem("Titik merah = posisi anda")
            BulletItem("Perlu kalibrasi di Settings → GPS Calibration")
            BulletItem("Minimal 1 titik survei untuk kalibrasi")

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
