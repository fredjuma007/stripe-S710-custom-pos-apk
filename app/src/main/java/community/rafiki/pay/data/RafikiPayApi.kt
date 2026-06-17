package community.rafiki.pay.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

class RafikiPayApi(
    baseUrl: String,
    private val deviceToken: String,
) {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchConnectionToken(): String = withContext(Dispatchers.IO) {
        val response = postJson("/api/terminal/connection-token", JSONObject())
        response.getString("secret")
    }

    suspend fun createPaymentIntent(request: CreatePaymentIntentRequest): CreatedPaymentIntent =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("amount", request.amountPence)
                .put("currency", request.currency)
                .put("deviceId", request.deviceId)
                .put("appVersion", request.appVersion)
                .put("donationType", request.donationType)
                .put("idempotencyKey", request.idempotencyKey)

            val response = postJson("/api/payment-intents", body)
            CreatedPaymentIntent(
                id = response.getString("id"),
                clientSecret = response.getString("clientSecret"),
            )
        }

    suspend fun health(): BackendHealth = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$normalizedBaseUrl/api/health")
            .header("Authorization", "Bearer $deviceToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@withContext BackendHealth(false, "Backend returned HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val json = JSONObject(body)
            BackendHealth(
                ok = json.optBoolean("ok", false),
                message = json.optString("service", "RafikiPay backend"),
            )
        }
    }

    private fun postJson(path: String, body: JSONObject): JSONObject {
        val request = Request.Builder()
            .url("$normalizedBaseUrl$path")
            .header("Authorization", "Bearer $deviceToken")
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException(responseBody.ifBlank { "Backend returned HTTP ${response.code}" })
            }
            return JSONObject(responseBody)
        }
    }
}

data class CreatePaymentIntentRequest(
    val amountPence: Long,
    val currency: String = "gbp",
    val deviceId: String,
    val appVersion: String,
    val donationType: String,
    val idempotencyKey: String = UUID.randomUUID().toString(),
)

data class CreatedPaymentIntent(
    val id: String,
    val clientSecret: String,
)

data class BackendHealth(
    val ok: Boolean,
    val message: String,
)
