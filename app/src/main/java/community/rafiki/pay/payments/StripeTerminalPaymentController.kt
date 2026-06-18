package community.rafiki.pay.payments

import android.content.Context
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.external.callable.Callback
import com.stripe.stripeterminal.external.callable.DiscoveryListener
import com.stripe.stripeterminal.external.callable.AppsOnDevicesListener
import com.stripe.stripeterminal.external.callable.InternetReaderListener
import com.stripe.stripeterminal.external.callable.PaymentIntentCallback
import com.stripe.stripeterminal.external.callable.ReaderCallback
import com.stripe.stripeterminal.external.callable.TerminalListener
import com.stripe.stripeterminal.external.models.CollectPaymentIntentConfiguration
import com.stripe.stripeterminal.external.models.ConfirmPaymentIntentConfiguration
import com.stripe.stripeterminal.external.models.ConnectionConfiguration.AppsOnDevicesConnectionConfiguration
import com.stripe.stripeterminal.external.models.ConnectionConfiguration.InternetConnectionConfiguration
import com.stripe.stripeterminal.external.models.ConnectionStatus
import com.stripe.stripeterminal.external.models.CustomerCancellation
import com.stripe.stripeterminal.external.models.DiscoveryConfiguration.AppsOnDevicesDiscoveryConfiguration
import com.stripe.stripeterminal.external.models.DiscoveryConfiguration.InternetDiscoveryConfiguration
import com.stripe.stripeterminal.external.models.DisconnectReason
import com.stripe.stripeterminal.external.models.PaymentIntent
import com.stripe.stripeterminal.external.models.PaymentStatus
import com.stripe.stripeterminal.external.models.Reader
import com.stripe.stripeterminal.external.models.ReaderEvent
import com.stripe.stripeterminal.external.models.SimulatedCard
import com.stripe.stripeterminal.external.models.SimulatedCardType
import com.stripe.stripeterminal.external.models.SimulatorConfiguration
import com.stripe.stripeterminal.external.models.TerminalErrorCode
import com.stripe.stripeterminal.external.models.TerminalException
import com.stripe.stripeterminal.log.LogLevel
import community.rafiki.pay.data.CreatePaymentIntentRequest
import community.rafiki.pay.data.RafikiPayApi
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume

class StripeTerminalPaymentController(
    private val context: Context,
    private val api: RafikiPayApi,
    private val simulatedReader: Boolean,
    private val terminalLocationId: String,
) : PaymentController {
    private var connectedReader: Reader? = null
    private var reusableIntent: PendingIntentForRetry? = null
    private var simulatedPaymentOutcome = SimulatedPaymentOutcome.SUCCESS

    override fun setSimulatedPaymentOutcome(outcome: SimulatedPaymentOutcome) {
        simulatedPaymentOutcome = outcome
        if (simulatedReader && Terminal.isInitialized()) {
            applySimulatorConfiguration()
        }
    }

    override suspend fun takePayment(request: DonationPaymentRequest): PaymentResult {
        return try {
            ensureTerminalInitialized()
            if (simulatedReader) applySimulatorConfiguration()
            ensureReaderConnected()

            val paymentIntent = retrieveOrCreatePaymentIntent(request)
            val processedIntent = processPaymentIntent(paymentIntent)
            reusableIntent = null
            PaymentResult.Success(processedIntent.id)
        } catch (error: TerminalException) {
            if (error.paymentIntent != null) {
                reusableIntent = PendingIntentForRetry(
                    amountPence = request.amountPence,
                    donationType = request.donationType,
                    paymentIntent = error.paymentIntent!!,
                )
            }
            if (error.errorCode == TerminalErrorCode.CANCELED) {
                PaymentResult.Cancelled
            } else {
                PaymentResult.Failure(error.errorMessage.ifBlank { "Payment could not be completed." })
            }
        } catch (error: Throwable) {
            PaymentResult.Failure(error.message ?: "Payment could not be completed.")
        }
    }

    private fun applySimulatorConfiguration() {
        val cardType = when (simulatedPaymentOutcome) {
            SimulatedPaymentOutcome.SUCCESS -> SimulatedCardType.VISA
            SimulatedPaymentOutcome.DECLINED -> SimulatedCardType.CHARGE_DECLINED
            SimulatedPaymentOutcome.INSUFFICIENT_FUNDS ->
                SimulatedCardType.CHARGE_DECLINED_INSUFFICIENT_FUNDS
        }
        Terminal.getInstance().simulatorConfiguration = SimulatorConfiguration(
            simulatedCard = SimulatedCard(cardType),
        )
    }

    private fun ensureTerminalInitialized() {
        if (Terminal.isInitialized()) return

        val listener = object : TerminalListener {
            override fun onConnectionStatusChange(status: ConnectionStatus) = Unit
            override fun onPaymentStatusChange(status: PaymentStatus) = Unit
        }

        Terminal.init(
            context,
            LogLevel.VERBOSE,
            StripeConnectionTokenProvider(api),
            listener,
            null,
        )
    }

    private suspend fun ensureReaderConnected() {
        val existing = connectedReader
        if (existing != null && Terminal.getInstance().connectedReader != null) return
        connectedReader = discoverAndConnectReader()
    }

    private suspend fun discoverAndConnectReader(): Reader = suspendCancellableCoroutine { continuation ->
        var hasStartedConnecting = false

        val discoveryConfig = if (simulatedReader) {
            InternetDiscoveryConfiguration(
                timeout = 15,
                isSimulated = true,
                location = terminalLocationId,
            )
        } else {
            AppsOnDevicesDiscoveryConfiguration()
        }

        Terminal.getInstance().discoverReaders(
            discoveryConfig,
            object : DiscoveryListener {
                override fun onUpdateDiscoveredReaders(readers: List<Reader>) {
                    val reader = readers.firstOrNull() ?: return
                    if (hasStartedConnecting || continuation.isCompleted) return
                    hasStartedConnecting = true

                    val connectionConfig = if (simulatedReader) {
                        InternetConnectionConfiguration(
                            object : InternetReaderListener {
                                override fun onDisconnect(reason: DisconnectReason) {
                                    connectedReader = null
                                }
                            },
                            true,
                        )
                    } else {
                        AppsOnDevicesConnectionConfiguration(
                            object : AppsOnDevicesListener {
                                override fun onDisconnect(reason: DisconnectReason) {
                                    connectedReader = null
                                }

                                override fun onReportReaderEvent(event: ReaderEvent) = Unit
                            },
                        )
                    }

                    Terminal.getInstance().connectReader(
                        reader,
                        connectionConfig,
                        object : ReaderCallback {
                            override fun onSuccess(reader: Reader) {
                                if (!continuation.isCompleted) continuation.resume(reader)
                            }

                            override fun onFailure(e: TerminalException) {
                                if (!continuation.isCompleted) continuation.cancel(e)
                            }
                        },
                    )
                }
            },
            object : Callback {
                override fun onSuccess() {
                    if (!hasStartedConnecting && !continuation.isCompleted) {
                        continuation.cancel(IllegalStateException("No Stripe reader was discovered."))
                    }
                }

                override fun onFailure(e: TerminalException) {
                    if (!continuation.isCompleted) continuation.cancel(e)
                }
            },
        )
    }

    private suspend fun retrieveOrCreatePaymentIntent(request: DonationPaymentRequest): PaymentIntent {
        val pending = reusableIntent
        if (pending != null && pending.matches(request)) {
            return pending.paymentIntent
        }

        val created = api.createPaymentIntent(
            CreatePaymentIntentRequest(
                amountPence = request.amountPence,
                currency = request.currency,
                deviceId = request.deviceId,
                appVersion = request.appVersion,
                donationType = request.donationType,
                idempotencyKey = UUID.randomUUID().toString(),
            ),
        )

        return retrievePaymentIntent(created.clientSecret)
    }

    private suspend fun retrievePaymentIntent(clientSecret: String): PaymentIntent =
        suspendCancellableCoroutine { continuation ->
            Terminal.getInstance().retrievePaymentIntent(
                clientSecret,
                object : PaymentIntentCallback {
                    override fun onSuccess(paymentIntent: PaymentIntent) {
                        continuation.resume(paymentIntent)
                    }

                    override fun onFailure(e: TerminalException) {
                        continuation.cancel(e)
                    }
                },
            )
        }

    private suspend fun processPaymentIntent(paymentIntent: PaymentIntent): PaymentIntent =
        suspendCancellableCoroutine { continuation ->
            Terminal.getInstance().processPaymentIntent(
                intent = paymentIntent,
                collectConfig = CollectPaymentIntentConfiguration.Builder()
                    .setCustomerCancellation(CustomerCancellation.ENABLE_IF_AVAILABLE)
                    .build(),
                confirmConfig = ConfirmPaymentIntentConfiguration.Builder().build(),
                callback = object : PaymentIntentCallback {
                    override fun onSuccess(paymentIntent: PaymentIntent) {
                        continuation.resume(paymentIntent)
                    }

                    override fun onFailure(e: TerminalException) {
                        continuation.cancel(e)
                    }
                },
            )
        }

    private data class PendingIntentForRetry(
        val amountPence: Long,
        val donationType: String,
        val paymentIntent: PaymentIntent,
    ) {
        fun matches(request: DonationPaymentRequest): Boolean {
            return amountPence == request.amountPence && donationType == request.donationType
        }
    }
}
