package com.example.pitwise.ui.screen.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pitwise.data.remote.SupabaseConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import com.example.pitwise.data.remote.SupabaseAuthService

@Serializable
data class SnapResponse(
    @SerialName("snap_token") val snapToken: String,
    @SerialName("redirect_url") val redirectUrl: String,
    @SerialName("order_id") val orderId: String
)

@Serializable
data class ProfileResponse(
    val id: String? = null,
    val email: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    val role: String? = null,
    @SerialName("subscription_status") val subscriptionStatus: String? = null,
    @SerialName("subscription_source") val subscriptionSource: String? = null,
    @SerialName("subscription_start") val subscriptionStart: String? = null,
    @SerialName("subscription_end") val subscriptionEnd: String? = null,
    val error: String? = null
)

data class SubscriptionUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val subscriptionStatus: String = "free",
    val subscriptionEnd: String? = null,
    val snapRedirectUrl: String? = null,
    val orderId: String? = null,
    val paymentStarted: Boolean = false,
    val voucherCode: String = "",
    val discountPercent: Int = 0,
    val finalAmount: Int = 49000
)

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val httpClient: HttpClient,
    private val authService: SupabaseAuthService
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = _uiState.asStateFlow()

    init {
        refreshProfile()
    }

    /**
     * Fetch fresh profile data from Supabase (calls get_my_profile() RPC).
     * This also auto-evaluates subscription expiry.
     */
    fun refreshProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val token = authService.getAccessToken()
                if (token.isNullOrBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Silakan login terlebih dahulu"
                    )
                    return@launch
                }

                val response: ProfileResponse = httpClient.post(
                    "${SupabaseConfig.REST_ENDPOINT}/rpc/get_my_profile"
                ) {
                    headers {
                        append("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                        append("Authorization", "Bearer $token")
                    }
                    contentType(ContentType.Application.Json)
                    setBody("{}")
                }.body()

                if (response.error != null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = response.error
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        subscriptionStatus = response.subscriptionStatus ?: "free",
                        subscriptionEnd = response.subscriptionEnd
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Gagal memuat profil"
                )
            }
        }
    }

    fun onVoucherCodeChange(code: String) {
        _uiState.value = _uiState.value.copy(
            voucherCode = code.uppercase().trim(),
            discountPercent = 0,
            finalAmount = 49000
        )
    }

    /**
     * Create a Midtrans Snap transaction via the Edge Function.
     * Optionally applies a discount voucher code.
     */
    fun createTransaction() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val token = authService.getAccessToken()
                if (token.isNullOrBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Silakan login terlebih dahulu"
                    )
                    return@launch
                }

                val voucherCode = _uiState.value.voucherCode
                val bodyJson = if (voucherCode.isNotBlank()) {
                    """{"voucher_code": "$voucherCode"}"""
                } else {
                    "{}"
                }

                val response: JsonObject = httpClient.post(
                    "${SupabaseConfig.FUNCTIONS_ENDPOINT}/create-midtrans-transaction"
                ) {
                    headers {
                        append("Authorization", "Bearer $token")
                    }
                    contentType(ContentType.Application.Json)
                    setBody(bodyJson)
                }.body()

                // Check for error field
                val errorMsg = response["error"]?.jsonPrimitive?.content
                if (errorMsg != null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = errorMsg
                    )
                    return@launch
                }

                val redirectUrl = response["redirect_url"]?.jsonPrimitive?.content ?: ""
                val orderId = response["order_id"]?.jsonPrimitive?.content ?: ""
                val discountPct = response["discount_percent"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val finalAmt = response["final_amount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 49000

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    snapRedirectUrl = redirectUrl,
                    orderId = orderId,
                    paymentStarted = true,
                    discountPercent = discountPct,
                    finalAmount = finalAmt
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Gagal membuat transaksi"
                )
            }
        }
    }

    /**
     * Called when user returns from payment browser.
     * We refresh the profile to check if payment was processed.
     */
    fun onReturnFromPayment() {
        _uiState.value = _uiState.value.copy(
            snapRedirectUrl = null,
            paymentStarted = false
        )
        refreshProfile()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
