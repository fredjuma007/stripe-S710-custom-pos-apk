package community.rafiki.pay

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import community.rafiki.pay.data.DonationConfigRepository
import community.rafiki.pay.data.RafikiPayApi
import community.rafiki.pay.payments.StripeTerminalPaymentController
import community.rafiki.pay.ui.RafikiPayApp
import community.rafiki.pay.ui.RafikiPayTheme

class MainActivity : ComponentActivity() {
    private val configRepository by lazy { DonationConfigRepository(applicationContext) }
    private val api by lazy {
        RafikiPayApi(
            baseUrl = BuildConfig.BACKEND_BASE_URL,
            deviceToken = BuildConfig.DEVICE_TOKEN,
        )
    }
    private val deviceId by lazy {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown-device"
    }
    private val paymentController by lazy {
        StripeTerminalPaymentController(
            context = applicationContext,
            api = api,
            simulatedReader = BuildConfig.SIMULATED_READER,
            terminalLocationId = BuildConfig.TERMINAL_LOCATION_ID,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        setContent {
            var pendingPermissionResult by remember {
                mutableStateOf<((Boolean) -> Unit)?>(null)
            }
            val locationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions(),
            ) { permissions ->
                val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
                pendingPermissionResult?.invoke(granted)
                pendingPermissionResult = null
            }

            RafikiPayTheme {
                RafikiPayApp(
                    configRepository = configRepository,
                    api = api,
                    paymentController = paymentController,
                    deviceId = deviceId,
                    appVersion = BuildConfig.VERSION_NAME,
                    backendBaseUrl = BuildConfig.BACKEND_BASE_URL,
                    simulatedReader = BuildConfig.SIMULATED_READER,
                    requestLocationPermission = { onResult ->
                        val alreadyGranted = ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (alreadyGranted) {
                            onResult(true)
                        } else {
                            pendingPermissionResult = onResult
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                        }
                    },
                    openStripeSettings = {
                        startActivity(
                            Intent(Intent.ACTION_VIEW).setData(Uri.parse("stripe://settings/")),
                        )
                    },
                )
            }
        }
    }
}
