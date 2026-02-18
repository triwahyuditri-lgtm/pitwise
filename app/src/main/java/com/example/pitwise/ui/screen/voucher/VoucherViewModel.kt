package com.example.pitwise.ui.screen.voucher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pitwise.data.remote.SupabaseConfig
import com.example.pitwise.data.remote.SupabaseAuthService
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

data class VoucherUiState(
    val code: String = "",
    val isLoading: Boolean = false,
    val success: Boolean = false,
    val message: String? = null,
    val subscriptionEnd: String? = null,
    val durationDays: Int? = null
)

@HiltViewModel
class VoucherViewModel @Inject constructor(
    private val httpClient: HttpClient,
    private val authService: SupabaseAuthService
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoucherUiState())
    val uiState: StateFlow<VoucherUiState> = _uiState.asStateFlow()

    fun onCodeChange(code: String) {
        _uiState.value = _uiState.value.copy(
            code = code.uppercase().trim(),
            success = false,
            message = null
        )
    }

    /**
     * Call the secure `redeem_voucher` RPC function.
     * All validation runs server-side in a DB transaction.
     */
    fun redeemVoucher() {
        val code = _uiState.value.code
        if (code.isBlank()) {
            _uiState.value = _uiState.value.copy(message = "Masukkan kode voucher")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null, success = false)

            try {
                val token = authService.getAccessToken()
                if (token.isNullOrBlank()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        message = "Silakan login terlebih dahulu"
                    )
                    return@launch
                }

                val response: JsonObject = httpClient.post(
                    "${SupabaseConfig.REST_ENDPOINT}/rpc/redeem_voucher"
                ) {
                    headers {
                        append("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                        append("Authorization", "Bearer $token")
                    }
                    contentType(ContentType.Application.Json)
                    setBody("""{"p_voucher_code": "$code"}""")
                }.body()

                val isSuccess = response["success"]?.jsonPrimitive?.boolean ?: false

                if (isSuccess) {
                    val subEnd = response["subscription_end"]?.jsonPrimitive?.content
                    val days = response["duration_days"]?.jsonPrimitive?.int
                    val msg = response["message"]?.jsonPrimitive?.content ?: "Voucher berhasil ditukarkan!"

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        success = true,
                        message = msg,
                        subscriptionEnd = subEnd,
                        durationDays = days
                    )
                } else {
                    val error = response["error"]?.jsonPrimitive?.content ?: "Gagal menukarkan voucher"
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        success = false,
                        message = error
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    success = false,
                    message = e.message ?: "Terjadi kesalahan jaringan"
                )
            }
        }
    }

    fun resetState() {
        _uiState.value = VoucherUiState()
    }
}
