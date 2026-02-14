package com.example.pitwise.domain.model

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Data model for the Share Card Generator.
 */
data class ShareCardData(
    val title: String,
    val resultLines: List<Pair<String, String>>, // Label, Value
    val aiRecommendation: String? = null,
    val unitName: String,
    val shift: String,
    val timestamp: String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"))
)
