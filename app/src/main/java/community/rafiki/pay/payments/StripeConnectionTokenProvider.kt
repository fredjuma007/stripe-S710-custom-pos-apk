package community.rafiki.pay.payments

import com.stripe.stripeterminal.external.callable.ConnectionTokenCallback
import com.stripe.stripeterminal.external.callable.ConnectionTokenProvider
import com.stripe.stripeterminal.external.models.ConnectionTokenException
import community.rafiki.pay.data.RafikiPayApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StripeConnectionTokenProvider(
    private val api: RafikiPayApi,
) : ConnectionTokenProvider {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun fetchConnectionToken(callback: ConnectionTokenCallback) {
        scope.launch {
            try {
                callback.onSuccess(api.fetchConnectionToken())
            } catch (error: Throwable) {
                callback.onFailure(
                    ConnectionTokenException("Failed to fetch RafikiPay connection token", error),
                )
            }
        }
    }
}
