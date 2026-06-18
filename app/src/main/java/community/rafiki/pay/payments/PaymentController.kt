package community.rafiki.pay.payments

data class DonationPaymentRequest(
    val amountPence: Long,
    val currency: String = "gbp",
    val deviceId: String,
    val appVersion: String,
    val donationType: String,
)

enum class SimulatedPaymentOutcome {
    SUCCESS,
    DECLINED,
    INSUFFICIENT_FUNDS,
}

sealed interface PaymentResult {
    data class Success(val paymentIntentId: String?) : PaymentResult
    data object Cancelled : PaymentResult
    data class Failure(val message: String, val retryable: Boolean = true) : PaymentResult
}

interface PaymentController {
    suspend fun takePayment(request: DonationPaymentRequest): PaymentResult

    fun setSimulatedPaymentOutcome(outcome: SimulatedPaymentOutcome) = Unit
}
