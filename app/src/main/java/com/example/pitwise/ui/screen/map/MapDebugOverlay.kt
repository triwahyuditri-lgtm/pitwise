package com.example.pitwise.ui.screen.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pitwise.domain.geopdf.GeoPdfDebugInfo

@Composable
fun MapDebugOverlay(
    info: GeoPdfDebugInfo?,
    modifier: Modifier = Modifier
) {
    // ── FPS Counter ──
    var fps by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        var lastTime = withFrameMillis { it }
        var frameCount = 0
        while (true) {
            withFrameMillis { time ->
                frameCount++
                if (time - lastTime >= 1000) {
                    fps = frameCount
                    frameCount = 0
                    lastTime = time
                }
            }
        }
    }

    Column(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(8.dp)
    ) {
        Text(
            text = "FPS: $fps",
            color = if (fps >= 55) Color.Green else if (fps >= 30) Color.Yellow else Color.Red,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = MaterialTheme.typography.bodySmall.fontFamily
        )

        if (info != null) {
            DebugText("GPS: %.6f, %.6f".format(info.rawLat, info.rawLng))
            DebugText("CRS: ${info.crsType} ${info.datum}")
            if (info.utmZone != null) {
                DebugText("Zone: ${info.utmZone}")
            }
            DebugText("Proj: %.1f, %.1f".format(info.projectedX, info.projectedY))
            DebugText("Pixel: %.1f, %.1f".format(info.pixelX, info.pixelY))
            
            // Matrix simplified
            // val matrix = info.affineMatrix.replace("\n", " | ")
            // DebugText("M: $matrix")
        }
    }
}

@Composable
private fun DebugText(text: String) {
    Text(
        text = text,
        color = Color.Yellow,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontFamily = MaterialTheme.typography.bodySmall.fontFamily
    )
}
