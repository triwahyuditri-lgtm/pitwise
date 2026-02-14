package com.example.pitwise.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pitwise.ui.theme.PitwiseGray400
import com.example.pitwise.ui.theme.PitwisePrimary
import com.example.pitwise.ui.theme.PitwiseSurface

// ── Step definitions ──

data class OnboardingStep(
    val title: String,
    val description: String,
    val highlightNavIndex: Int? = null  // index in bottomNavItems, null = no highlight
)

val onboardingSteps = listOf(
    OnboardingStep(
        title = "Selamat Datang di PITWISE",
        description = "Aplikasi bantu keputusan cepat di lapangan tambang."
    ),
    OnboardingStep(
        title = "🗺️ MAP",
        description = "Digunakan untuk membuka peta PDF/DXF dan melihat posisi GPS.",
        highlightNavIndex = 1
    ),
    OnboardingStep(
        title = "📏 MEASURE",
        description = "Digunakan untuk mengukur jarak dan luas langsung dari peta.",
        highlightNavIndex = 2
    ),
    OnboardingStep(
        title = "🧮 CALCULATE",
        description = "Digunakan untuk menghitung OB, Coal, Grade dan Cut & Fill.\nAkses melalui tombol di Home screen."
    ),
    OnboardingStep(
        title = "⚙️ PRODUCTIVITY",
        description = "Digunakan untuk mengetahui produktivitas alat dan selisih target.",
        highlightNavIndex = 3
    ),
    OnboardingStep(
        title = "🤖 AI Advisor",
        description = "Memberikan penyebab utama dan rekomendasi teknis cepat.\nAkses melalui tombol \"WHY TARGET NOT ACHIEVED?\" di Productivity."
    ),
    OnboardingStep(
        title = "Sekarang Anda Siap Menggunakan PITWISE",
        description = ""
    )
)

// ── Overlay composable ──

@Composable
fun OnboardingGuide(
    currentStep: Int,
    navItemBounds: Map<Int, Rect>,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val step = onboardingSteps[currentStep]
    val totalSteps = onboardingSteps.size
    val isLastStep = currentStep == totalSteps - 1
    val isFirstStep = currentStep == 0
    val highlightRect = step.highlightNavIndex?.let { navItemBounds[it] }

    val density = LocalDensity.current
    val spotlightPadding = with(density) { 8.dp.toPx() }
    val cornerRadius = with(density) { 16.dp.toPx() }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* consume taps */ }
                .drawBehind {
                    if (highlightRect != null) {
                        // Draw scrim with spotlight cutout
                        val spotlightRect = Rect(
                            left = highlightRect.left - spotlightPadding,
                            top = highlightRect.top - spotlightPadding,
                            right = highlightRect.right + spotlightPadding,
                            bottom = highlightRect.bottom + spotlightPadding
                        )
                        val cutoutPath = Path().apply {
                            addRoundRect(
                                RoundRect(
                                    rect = spotlightRect,
                                    cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                                )
                            )
                        }
                        clipPath(cutoutPath, clipOp = ClipOp.Difference) {
                            drawRect(Color.Black.copy(alpha = 0.8f))
                        }
                        // Draw spotlight border
                        drawRoundRect(
                            color = PitwisePrimary.copy(alpha = 0.6f),
                            topLeft = Offset(spotlightRect.left, spotlightRect.top),
                            size = Size(spotlightRect.width, spotlightRect.height),
                            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                        )
                    } else {
                        drawRect(Color.Black.copy(alpha = 0.85f))
                    }
                }
        ) {
            // Description card — position above highlight or center
            val cardModifier = if (highlightRect != null) {
                // Position card above the highlighted element
                val cardBottomY = highlightRect.top - with(density) { 24.dp.toPx() }
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .offset {
                        IntOffset(0, (cardBottomY - 200f).toInt().coerceAtLeast(with(density) { 80.dp.toPx() }.toInt()))
                    }
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .align(Alignment.Center)
            }

            Column(
                modifier = cardModifier
                    .background(PitwiseSurface, RoundedCornerShape(20.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Progress indicator
                Text(
                    text = "${currentStep + 1} / $totalSteps",
                    style = MaterialTheme.typography.labelSmall,
                    color = PitwisePrimary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Title
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isFirstStep || isLastStep) 22.sp else 20.sp
                    ),
                    color = if (isFirstStep || isLastStep) PitwisePrimary else Color.White,
                    textAlign = TextAlign.Center
                )

                if (step.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = step.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PitwiseGray400,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
                    if (!isLastStep) {
                        OutlinedButton(
                            onClick = onSkip,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = PitwiseGray400
                            )
                        ) {
                            Text("Skip")
                        }
                    }

                    Button(
                        onClick = onNext,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PitwisePrimary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = when {
                                isLastStep -> "🚀 Mulai Sekarang"
                                isFirstStep -> "Next"
                                else -> "Next"
                            },
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}
