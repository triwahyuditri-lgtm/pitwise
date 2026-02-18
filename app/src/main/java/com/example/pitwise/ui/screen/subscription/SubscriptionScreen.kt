package com.example.pitwise.ui.screen.subscription

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pitwise.ui.theme.PitwiseBackground
import com.example.pitwise.ui.theme.PitwiseBorder
import com.example.pitwise.ui.theme.PitwiseError
import com.example.pitwise.ui.theme.PitwiseGray400
import com.example.pitwise.ui.theme.PitwisePrimary
import com.example.pitwise.ui.theme.PitwisePrimaryVariant
import com.example.pitwise.ui.theme.PitwiseSuccess
import com.example.pitwise.ui.theme.PitwiseSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    onBack: () -> Unit,
    viewModel: SubscriptionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // Open Midtrans Snap when redirect URL is available
    LaunchedEffect(uiState.snapRedirectUrl) {
        uiState.snapRedirectUrl?.let { url ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "PREMIUM",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshProfile() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = PitwisePrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // ── Premium Status Card ──
            StatusCard(
                subscriptionStatus = uiState.subscriptionStatus,
                subscriptionEnd = uiState.subscriptionEnd
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Features List ──
            if (uiState.subscriptionStatus != "premium") {
                Text(
                    text = "Fitur Premium",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                PremiumFeature(Icons.Default.Star, "Akses semua fitur kalkulasi")
                Spacer(modifier = Modifier.height(8.dp))
                PremiumFeature(Icons.Default.Star, "Sinkronisasi data cloud")
                Spacer(modifier = Modifier.height(8.dp))
                PremiumFeature(Icons.Default.Star, "AI Advisor tanpa batas")
                Spacer(modifier = Modifier.height(8.dp))
                PremiumFeature(Icons.Default.Star, "Dukungan prioritas")

                Spacer(modifier = Modifier.height(32.dp))

                // ── Price Card ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    PitwisePrimary.copy(alpha = 0.15f),
                                    PitwisePrimaryVariant.copy(alpha = 0.05f)
                                )
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(PitwisePrimary.copy(alpha = 0.5f), PitwisePrimaryVariant.copy(alpha = 0.3f))
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Diamond,
                            contentDescription = null,
                            tint = PitwisePrimary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Premium 1 Bulan",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (uiState.discountPercent > 0) {
                            // Show original price with strikethrough
                            Text(
                                text = "Rp 49.000",
                                style = MaterialTheme.typography.bodyLarge,
                                color = PitwiseGray400,
                                textDecoration = TextDecoration.LineThrough
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val formattedAmount = String.format("%,d", uiState.finalAmount).replace(',', '.')
                            Text(
                                text = "Rp $formattedAmount",
                                style = MaterialTheme.typography.headlineMedium,
                                color = PitwiseSuccess,
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Diskon ${uiState.discountPercent}% diterapkan!",
                                style = MaterialTheme.typography.bodySmall,
                                color = PitwiseSuccess,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = "Rp 49.000",
                                style = MaterialTheme.typography.headlineMedium,
                                color = PitwisePrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "/ bulan",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PitwiseGray400
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── Discount Voucher Input ──
                OutlinedTextField(
                    value = uiState.voucherCode,
                    onValueChange = { viewModel.onVoucherCodeChange(it) },
                    label = { Text("Kode Voucher Diskon (opsional)") },
                    placeholder = { Text("Contoh: DISKON50") },
                    singleLine = true,
                    enabled = !uiState.isLoading && !uiState.paymentStarted,
                    leadingIcon = {
                        Icon(
                            Icons.Default.Redeem,
                            contentDescription = null,
                            tint = PitwisePrimary
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PitwisePrimary,
                        unfocusedBorderColor = PitwiseBorder,
                        cursorColor = PitwisePrimary,
                        focusedLabelColor = PitwisePrimary,
                        unfocusedLabelColor = PitwiseGray400,
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedContainerColor = PitwiseSurface,
                        unfocusedContainerColor = PitwiseSurface
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ── Error message ──
                if (uiState.error != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PitwiseError.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = uiState.error ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = PitwiseError
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // ── Payment Started Info ──
                if (uiState.paymentStarted) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PitwiseSuccess.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Pembayaran dibuka di browser. Setelah selesai, tekan Refresh atau kembali ke halaman ini.",
                            style = MaterialTheme.typography.bodySmall,
                            color = PitwiseSuccess
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.onReturnFromPayment() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PitwiseSurface,
                            contentColor = PitwisePrimary
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cek Status Pembayaran")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // ── Subscribe Button ──
                Button(
                    onClick = { viewModel.createTransaction() },
                    enabled = !uiState.isLoading && !uiState.paymentStarted,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PitwisePrimary,
                        contentColor = Color.Black,
                        disabledContainerColor = PitwiseGray400,
                        disabledContentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (uiState.isLoading) "Memproses..." else "Berlangganan Sekarang",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Pembayaran diproses melalui Midtrans.\nPremium aktif setelah pembayaran berhasil diverifikasi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PitwiseGray400,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatusCard(subscriptionStatus: String, subscriptionEnd: String?) {
    val isPremium = subscriptionStatus == "premium"
    val statusColor = if (isPremium) PitwiseSuccess else PitwiseGray400
    val statusLabel = when (subscriptionStatus) {
        "premium" -> "PREMIUM AKTIF"
        "expired" -> "LANGGANAN KEDALUWARSA"
        else -> "FREE"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PitwiseSurface)
            .border(
                width = if (isPremium) 2.dp else 1.dp,
                color = if (isPremium) PitwiseSuccess.copy(alpha = 0.5f) else PitwiseBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = if (isPremium) Icons.Default.CheckCircle else Icons.Default.Lock,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
                if (isPremium && subscriptionEnd != null) {
                    // Format date for display
                    val displayDate = try {
                        subscriptionEnd.substring(0, 10)  // YYYY-MM-DD
                    } catch (_: Exception) {
                        subscriptionEnd
                    }
                    Text(
                        text = "Berlaku sampai: $displayDate",
                        style = MaterialTheme.typography.bodySmall,
                        color = PitwiseGray400
                    )
                } else if (subscriptionStatus == "expired") {
                    Text(
                        text = "Perpanjang langganan untuk melanjutkan",
                        style = MaterialTheme.typography.bodySmall,
                        color = PitwiseError
                    )
                } else {
                    Text(
                        text = "Upgrade untuk akses semua fitur",
                        style = MaterialTheme.typography.bodySmall,
                        color = PitwiseGray400
                    )
                }
            }
        }
    }
}

@Composable
private fun PremiumFeature(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(PitwiseSurface)
            .border(1.dp, PitwiseBorder, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PitwisePrimary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
