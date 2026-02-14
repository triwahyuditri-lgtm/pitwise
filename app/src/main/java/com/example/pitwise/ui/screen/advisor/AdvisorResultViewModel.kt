package com.example.pitwise.ui.screen.advisor

import android.content.Context
import androidx.lifecycle.ViewModel
import com.example.pitwise.domain.model.AdvisorInput
import com.example.pitwise.domain.model.AdvisorOutput
import com.example.pitwise.domain.model.ShareCardData
import com.example.pitwise.domain.reporting.ShareCardGenerator
import com.example.pitwise.domain.reporting.ShareIntentHelper
import com.example.pitwise.domain.ruleengine.AdvisorEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class AdvisorUiState(
    val output: AdvisorOutput? = null,
    val unitName: String = "",
    val shift: String = ""
)

@HiltViewModel
class AdvisorResultViewModel @Inject constructor(
    private val advisorEngine: AdvisorEngine,
    private val shareCardGenerator: ShareCardGenerator,
    private val shareIntentHelper: ShareIntentHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdvisorUiState())
    val uiState: StateFlow<AdvisorUiState> = _uiState.asStateFlow()

    init {
        // Generate a default advisory analysis
        val input = AdvisorInput(
            productivityResult = null,
            fishboneResult = null,
            resistanceResult = null,
            delayResult = null,
            unitName = "Field Unit",
            shift = "Day"
        )
        val output = advisorEngine.advise(input)
        _uiState.value = AdvisorUiState(
            output = output,
            unitName = input.unitName,
            shift = input.shift
        )
    }

    fun shareResult(context: Context) {
        val output = _uiState.value.output ?: return
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

        val resultLines = output.recommendations.mapIndexed { index, rec ->
            "Cause ${index + 1}" to rec.cause
        } + listOf("Action" to (output.recommendations.firstOrNull()?.actionItem ?: ""))

        val data = ShareCardData(
            title = "AI Advisor: ${output.summary}",
            resultLines = resultLines,
            timestamp = timestamp,
            shift = _uiState.value.shift,
            unitName = _uiState.value.unitName,
            aiRecommendation = output.recommendations.firstOrNull()?.actionItem
        )
        val bitmap = shareCardGenerator.generate(data)
        val intent = shareIntentHelper.createChooserIntent(context, bitmap, "PITWISE AI Advisory")
        context.startActivity(intent)
    }
}
