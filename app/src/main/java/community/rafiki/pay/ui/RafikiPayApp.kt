package community.rafiki.pay.ui

import androidx.compose.animation.AnimatedContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import community.rafiki.pay.R
import community.rafiki.pay.data.DonationConfigRepository
import community.rafiki.pay.data.RafikiPayApi
import community.rafiki.pay.domain.DonationAmountValidator
import community.rafiki.pay.payments.DonationPaymentRequest
import community.rafiki.pay.payments.PaymentController
import community.rafiki.pay.payments.PaymentResult
import community.rafiki.pay.payments.SimulatedPaymentOutcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RafikiPayApp(
    configRepository: DonationConfigRepository,
    api: RafikiPayApi,
    paymentController: PaymentController,
    deviceId: String,
    appVersion: String,
    backendBaseUrl: String,
    simulatedReader: Boolean,
    requestLocationPermission: ((Boolean) -> Unit) -> Unit,
    openStripeSettings: () -> Unit,
) {
    val presets by configRepository.presetAmounts.collectAsState(initial = DonationConfigRepository.DEFAULT_PRESETS)
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf<RafikiScreen>(RafikiScreen.Select) }
    var selectedAmountPence by remember { mutableStateOf(0L) }
    var selectedDonationType by remember { mutableStateOf("preset") }
    var simulatedPaymentOutcome by remember {
        mutableStateOf(SimulatedPaymentOutcome.SUCCESS)
    }

    fun selectAmount(pounds: Int, type: String) {
        val amount = DonationAmountValidator.poundsToPenceOrNull(pounds) ?: return
        selectedAmountPence = amount
        selectedDonationType = type
        screen = RafikiScreen.Confirm
    }

    fun performPayment() {
        val amount = selectedAmountPence
        val type = selectedDonationType
        scope.launch {
            screen = RafikiScreen.Processing(amount)
            val result = paymentController.takePayment(
                DonationPaymentRequest(
                    amountPence = amount,
                    deviceId = deviceId,
                    appVersion = appVersion,
                    donationType = type,
                ),
            )
            screen = when (result) {
                is PaymentResult.Success -> RafikiScreen.Success(amount)
                PaymentResult.Cancelled -> RafikiScreen.Failure(
                    message = "Donation cancelled.",
                    amountPence = amount,
                    retryable = true,
                )
                is PaymentResult.Failure -> RafikiScreen.Failure(
                    message = result.message,
                    amountPence = amount,
                    retryable = result.retryable,
                )
            }
        }
    }

    fun startPayment() {
        requestLocationPermission { granted ->
            if (granted) {
                performPayment()
            } else {
                screen = RafikiScreen.Failure(
                    message = "Location permission is required to connect to the payment reader.",
                    amountPence = selectedAmountPence,
                    retryable = true,
                )
            }
        }
    }

    BackHandler(enabled = screen != RafikiScreen.Select) {
        screen = when (screen) {
            RafikiScreen.CustomAmount,
            RafikiScreen.Confirm,
            is RafikiScreen.Success,
            is RafikiScreen.Failure,
            RafikiScreen.AdminPin,
            RafikiScreen.Admin,
            -> RafikiScreen.Select
            is RafikiScreen.AdminStatus -> RafikiScreen.Admin
            is RafikiScreen.Processing -> screen
            RafikiScreen.Select -> RafikiScreen.Select
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = RafikiColors.White,
    ) {
        AnimatedContent(
            targetState = screen,
            label = "rafikipay-screen",
        ) { current ->
            when (current) {
                RafikiScreen.Select -> AmountSelectionScreen(
                    presets = presets,
                    onPreset = { selectAmount(it, "preset_$it") },
                    onCustom = { screen = RafikiScreen.CustomAmount },
                    onAdminGesture = { screen = RafikiScreen.AdminPin },
                )
                RafikiScreen.CustomAmount -> CustomAmountScreen(
                    onBack = { screen = RafikiScreen.Select },
                    onAmount = { selectAmount(it, "custom") },
                )
                RafikiScreen.Confirm -> ConfirmDonationScreen(
                    amountPence = selectedAmountPence,
                    onDonate = ::startPayment,
                    onChange = { screen = RafikiScreen.Select },
                )
                is RafikiScreen.Processing -> ProcessingScreen(current.amountPence)
                is RafikiScreen.Success -> SuccessScreen(
                    amountPence = current.amountPence,
                    onDone = { screen = RafikiScreen.Select },
                )
                is RafikiScreen.Failure -> FailureScreen(
                    message = current.message,
                    retryable = current.retryable,
                    onRetry = ::startPayment,
                    onChooseAnother = { screen = RafikiScreen.Select },
                )
                RafikiScreen.AdminPin -> AdminPinScreen(
                    onCancel = { screen = RafikiScreen.Select },
                    onUnlocked = { screen = RafikiScreen.Admin },
                )
                RafikiScreen.Admin -> AdminScreen(
                    presets = presets,
                    backendBaseUrl = backendBaseUrl,
                    appVersion = appVersion,
                    deviceId = deviceId,
                    simulatedReader = simulatedReader,
                    simulatedPaymentOutcome = simulatedPaymentOutcome,
                    onSimulatedPaymentOutcomeChange = { outcome ->
                        simulatedPaymentOutcome = outcome
                        paymentController.setSimulatedPaymentOutcome(outcome)
                    },
                    onSavePresets = { values ->
                        scope.launch { configRepository.savePresetAmounts(values) }
                    },
                    onResetPresets = {
                        scope.launch { configRepository.resetDefaults() }
                    },
                    onCheckBackend = {
                        scope.launch {
                            val health = runCatching { api.health() }.getOrNull()
                            screen = RafikiScreen.AdminStatus(
                                health?.message ?: "Backend unavailable",
                                health?.ok == true,
                            )
                        }
                    },
                    onOpenStripeSettings = openStripeSettings,
                    onClose = { screen = RafikiScreen.Select },
                )
                is RafikiScreen.AdminStatus -> AdminStatusScreen(
                    message = current.message,
                    ok = current.ok,
                    onBack = { screen = RafikiScreen.Admin },
                )
            }
        }
    }
}

private sealed interface RafikiScreen {
    data object Select : RafikiScreen
    data object CustomAmount : RafikiScreen
    data object Confirm : RafikiScreen
    data class Processing(val amountPence: Long) : RafikiScreen
    data class Success(val amountPence: Long) : RafikiScreen
    data class Failure(val message: String, val amountPence: Long, val retryable: Boolean) : RafikiScreen
    data object AdminPin : RafikiScreen
    data object Admin : RafikiScreen
    data class AdminStatus(val message: String, val ok: Boolean) : RafikiScreen
}

@Composable
private fun AmountSelectionScreen(
    presets: List<Int>,
    onPreset: (Int) -> Unit,
    onCustom: () -> Unit,
    onAdminGesture: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RafikiLogo(
            modifier = Modifier
                .fillMaxWidth()
                .height(142.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onAdminGesture() })
                },
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Donate today",
            color = RafikiColors.Black,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            presets.forEach { pounds ->
                AmountTile(
                    label = "\u00A3$pounds",
                    modifier = Modifier.weight(1f),
                    onClick = { onPreset(pounds) },
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        PrimaryActionButton(
            text = "Other amount",
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            onClick = onCustom,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "Rafiki Community CIC",
            color = RafikiColors.Ink,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun AmountTile(label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .aspectRatio(0.9f)
            .clip(RoundedCornerShape(8.dp))
            .border(2.dp, RafikiColors.Black, RoundedCornerShape(8.dp))
            .background(RafikiColors.White)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            color = RafikiColors.Black,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(RafikiColors.Orange),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Donate",
                color = RafikiColors.Black,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
            )
        }
    }
}

@Composable
private fun CustomAmountScreen(
    onBack: () -> Unit,
    onAmount: (Int) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val pounds = input.toIntOrNull()
    val valid = pounds != null && DonationAmountValidator.poundsToPenceOrNull(pounds) != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HeaderMark()
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Choose amount",
            color = RafikiColors.Black,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (input.isBlank()) "\u00A30" else "\u00A3$input",
            color = RafikiColors.Red,
            fontSize = 52.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(14.dp))
        Keypad(
            onDigit = { digit ->
                if (input.length < 3) input += digit
            },
            onDelete = { input = input.dropLast(1) },
            onClear = { input = "" },
        )
        Spacer(Modifier.height(14.dp))
        PrimaryActionButton(
            text = "Donate",
            enabled = valid,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            onClick = { pounds?.let(onAmount) },
        )
        Spacer(Modifier.height(10.dp))
        SecondaryActionButton(
            text = "Back",
            modifier = Modifier.fillMaxWidth().height(52.dp),
            onClick = onBack,
        )
    }
}

@Composable
private fun Keypad(
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("Clear", "0", "Del"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    OutlinedButton(
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = RafikiColors.Black,
                        ),
                        onClick = {
                            when (key) {
                                "Clear" -> onClear()
                                "Del" -> onDelete()
                                else -> onDigit(key)
                            }
                        },
                    ) {
                        Text(
                            text = key,
                            fontSize = if (key.length > 1) 16.sp else 24.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmDonationScreen(
    amountPence: Long,
    onDonate: () -> Unit,
    onChange: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HeaderMark()
        Spacer(Modifier.height(36.dp))
        Text(
            text = "Your donation",
            color = RafikiColors.Ink,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = DonationAmountValidator.displayPounds(amountPence),
            color = RafikiColors.Red,
            fontSize = 76.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(32.dp))
        PrimaryActionButton(
            text = "Donate",
            modifier = Modifier.fillMaxWidth().height(64.dp),
            onClick = onDonate,
        )
        Spacer(Modifier.height(12.dp))
        SecondaryActionButton(
            text = "Change amount",
            modifier = Modifier.fillMaxWidth().height(54.dp),
            onClick = onChange,
        )
        Spacer(Modifier.weight(1f))
        BrandAccentBar()
    }
}

@Composable
private fun ProcessingScreen(amountPence: Long) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(58.dp),
            color = RafikiColors.Red,
            strokeWidth = 6.dp,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Opening secure payment",
            color = RafikiColors.Black,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = DonationAmountValidator.displayPounds(amountPence),
            color = RafikiColors.Ink,
            fontSize = 36.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun SuccessScreen(
    amountPence: Long,
    onDone: () -> Unit,
) {
    LaunchedEffect(amountPence) {
        delay(3200)
        onDone()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        HeartCheck(modifier = Modifier.size(132.dp))
        Spacer(Modifier.height(28.dp))
        Text(
            text = "Thank you for supporting Rafiki",
            color = RafikiColors.Black,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 40.sp,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = DonationAmountValidator.displayPounds(amountPence),
            color = RafikiColors.Red,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun FailureScreen(
    message: String,
    retryable: Boolean,
    onRetry: () -> Unit,
    onChooseAnother: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        HeaderMark()
        Spacer(Modifier.height(28.dp))
        Text(
            text = message,
            color = RafikiColors.Black,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = 34.sp,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(28.dp))
        if (retryable) {
            PrimaryActionButton(
                text = "Try again",
                modifier = Modifier.fillMaxWidth().height(60.dp),
                onClick = onRetry,
            )
            Spacer(Modifier.height(10.dp))
        }
        SecondaryActionButton(
            text = "Choose another amount",
            modifier = Modifier.fillMaxWidth().height(54.dp),
            onClick = onChooseAnother,
        )
    }
}

@Composable
private fun AdminPinScreen(
    onCancel: () -> Unit,
    onUnlocked: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HeaderMark()
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Admin PIN",
            color = RafikiColors.Black,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "*".repeat(pin.length).ifBlank { "----" },
            color = RafikiColors.Red,
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(12.dp))
        Keypad(
            onDigit = { digit ->
                if (pin.length < 4) {
                    pin += digit
                    if (pin == "2468") onUnlocked()
                }
            },
            onDelete = { pin = pin.dropLast(1) },
            onClear = { pin = "" },
        )
        Spacer(Modifier.height(12.dp))
        SecondaryActionButton(
            text = "Cancel",
            modifier = Modifier.fillMaxWidth().height(52.dp),
            onClick = onCancel,
        )
    }
}

@Composable
private fun AdminScreen(
    presets: List<Int>,
    backendBaseUrl: String,
    appVersion: String,
    deviceId: String,
    simulatedReader: Boolean,
    simulatedPaymentOutcome: SimulatedPaymentOutcome,
    onSimulatedPaymentOutcomeChange: (SimulatedPaymentOutcome) -> Unit,
    onSavePresets: (List<Int>) -> Unit,
    onResetPresets: () -> Unit,
    onCheckBackend: () -> Unit,
    onOpenStripeSettings: () -> Unit,
    onClose: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var first by remember(presets) { mutableStateOf(presets.getOrElse(0) { 5 }.toString()) }
    var second by remember(presets) { mutableStateOf(presets.getOrElse(1) { 10 }.toString()) }
    var third by remember(presets) { mutableStateOf(presets.getOrElse(2) { 15 }.toString()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "RafikiPay Admin",
            color = RafikiColors.Black,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PresetField("One", first, { first = it }, Modifier.weight(1f))
            PresetField("Two", second, { second = it }, Modifier.weight(1f))
            PresetField("Three", third, { third = it }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        PrimaryActionButton(
            text = "Save amounts",
            modifier = Modifier.fillMaxWidth().height(56.dp),
            onClick = {
                focusManager.clearFocus()
                onSavePresets(
                    listOfNotNull(
                        first.toIntOrNull(),
                        second.toIntOrNull(),
                        third.toIntOrNull(),
                    ),
                )
            },
        )
        Spacer(Modifier.height(10.dp))
        SecondaryActionButton(
            text = "Reset defaults",
            modifier = Modifier.fillMaxWidth().height(52.dp),
            onClick = onResetPresets,
        )
        Spacer(Modifier.height(18.dp))
        InfoLine("Backend", backendBaseUrl)
        InfoLine("Version", appVersion)
        InfoLine("Device", deviceId)
        InfoLine("Reader mode", if (simulatedReader) "Simulated" else "S710 handoff")
        if (simulatedReader) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Next simulated payment",
                modifier = Modifier.fillMaxWidth(),
                color = RafikiColors.Black,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SimulatorOutcomeButton(
                    text = "Success",
                    selected = simulatedPaymentOutcome == SimulatedPaymentOutcome.SUCCESS,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onSimulatedPaymentOutcomeChange(SimulatedPaymentOutcome.SUCCESS)
                    },
                )
                SimulatorOutcomeButton(
                    text = "Decline",
                    selected = simulatedPaymentOutcome == SimulatedPaymentOutcome.DECLINED,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onSimulatedPaymentOutcomeChange(SimulatedPaymentOutcome.DECLINED)
                    },
                )
                SimulatorOutcomeButton(
                    text = "No funds",
                    selected = simulatedPaymentOutcome ==
                        SimulatedPaymentOutcome.INSUFFICIENT_FUNDS,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onSimulatedPaymentOutcomeChange(
                            SimulatedPaymentOutcome.INSUFFICIENT_FUNDS,
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        SecondaryActionButton(
            text = "Check backend",
            modifier = Modifier.fillMaxWidth().height(52.dp),
            onClick = onCheckBackend,
        )
        Spacer(Modifier.height(8.dp))
        SecondaryActionButton(
            text = if (simulatedReader) "Stripe settings (S710 only)" else "Stripe settings",
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = !simulatedReader,
            onClick = onOpenStripeSettings,
        )
        Spacer(Modifier.height(8.dp))
        PrimaryActionButton(
            text = "Close",
            modifier = Modifier.fillMaxWidth().height(56.dp),
            onClick = onClose,
        )
    }
}

@Composable
private fun SimulatorOutcomeButton(
    text: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            modifier = modifier.height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = RafikiColors.Green,
                contentColor = RafikiColors.White,
            ),
            onClick = onClick,
        ) {
            Text(text = text, fontSize = 13.sp, letterSpacing = 0.sp)
        }
    } else {
        OutlinedButton(
            modifier = modifier.height(48.dp),
            shape = RoundedCornerShape(8.dp),
            onClick = onClick,
        ) {
            Text(text = text, fontSize = 13.sp, letterSpacing = 0.sp)
        }
    }
}

@Composable
private fun PresetField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
) {
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() }.take(3)) },
        label = { Text(label) },
        prefix = { Text("\u00A3") },
        singleLine = true,
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(
            text = label,
            color = RafikiColors.DeepRed,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
        Text(
            text = value,
            color = RafikiColors.Ink,
            fontSize = 15.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun AdminStatusScreen(
    message: String,
    ok: Boolean,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (ok) "Backend OK" else "Backend issue",
            color = if (ok) RafikiColors.Green else RafikiColors.Red,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = message,
            color = RafikiColors.Ink,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
            lineHeight = 28.sp,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(28.dp))
        PrimaryActionButton(
            text = "Back",
            modifier = Modifier.fillMaxWidth().height(56.dp),
            onClick = onBack,
        )
    }
}

@Composable
private fun PrimaryActionButton(
    text: String,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = RafikiColors.Red,
            contentColor = RafikiColors.White,
            disabledContainerColor = RafikiColors.Soft,
            disabledContentColor = RafikiColors.Ink,
        ),
        onClick = onClick,
    ) {
        Text(
            text = text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun SecondaryActionButton(
    text: String,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = RafikiColors.Black,
        ),
        onClick = onClick,
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun HeaderMark() {
    Image(
        painter = painterResource(R.drawable.rafiki_logo),
        contentDescription = "Rafiki - a friend in deed",
        modifier = Modifier
            .fillMaxWidth(0.72f)
            .height(76.dp),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun RafikiLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.rafiki_logo),
        contentDescription = "Rafiki - a friend in deed",
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun BrandAccentBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp)),
    ) {
        listOf(RafikiColors.Green, RafikiColors.Orange, RafikiColors.Coral).forEach { color ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(color),
            )
        }
    }
}

@Composable
private fun HeartCheck(modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawHeart(RafikiColors.Coral)
        }
        Canvas(modifier = Modifier.size(66.dp)) {
            val path = Path().apply {
                moveTo(size.width * 0.2f, size.height * 0.55f)
                lineTo(size.width * 0.43f, size.height * 0.75f)
                lineTo(size.width * 0.82f, size.height * 0.25f)
            }
            drawPath(path, color = RafikiColors.White, style = Stroke(width = 10f))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHeart(color: androidx.compose.ui.graphics.Color) {
    val width = size.width
    val height = size.height
    val path = Path().apply {
        moveTo(width * 0.5f, height * 0.88f)
        cubicTo(width * 0.08f, height * 0.62f, width * 0.08f, height * 0.24f, width * 0.32f, height * 0.18f)
        cubicTo(width * 0.44f, height * 0.15f, width * 0.5f, height * 0.28f, width * 0.5f, height * 0.28f)
        cubicTo(width * 0.5f, height * 0.28f, width * 0.56f, height * 0.15f, width * 0.68f, height * 0.18f)
        cubicTo(width * 0.92f, height * 0.24f, width * 0.92f, height * 0.62f, width * 0.5f, height * 0.88f)
        close()
    }
    drawPath(path, color)
}
