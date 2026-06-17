package community.rafiki.pay.ui

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import community.rafiki.pay.data.DonationConfigRepository
import community.rafiki.pay.data.RafikiPayApi
import community.rafiki.pay.domain.DonationAmountValidator
import community.rafiki.pay.payments.DonationPaymentRequest
import community.rafiki.pay.payments.PaymentController
import community.rafiki.pay.payments.PaymentResult
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
    openStripeSettings: () -> Unit,
) {
    val presets by configRepository.presetAmounts.collectAsState(initial = DonationConfigRepository.DEFAULT_PRESETS)
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf<RafikiScreen>(RafikiScreen.Select) }
    var selectedAmountPence by remember { mutableStateOf(0L) }
    var selectedDonationType by remember { mutableStateOf("preset") }

    fun selectAmount(pounds: Int, type: String) {
        val amount = DonationAmountValidator.poundsToPenceOrNull(pounds) ?: return
        selectedAmountPence = amount
        selectedDonationType = type
        screen = RafikiScreen.Confirm
    }

    fun startPayment() {
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
            .padding(horizontal = 64.dp, vertical = 54.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RafikiLogo(
            modifier = Modifier
                .fillMaxWidth()
                .height(310.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onAdminGesture() })
                },
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Donate today",
            color = RafikiColors.Black,
            fontSize = 74.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(42.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            presets.forEach { pounds ->
                AmountTile(
                    label = "\u00A3$pounds",
                    modifier = Modifier.weight(1f),
                    onClick = { onPreset(pounds) },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        PrimaryActionButton(
            text = "Other amount",
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp),
            onClick = onCustom,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "Rafiki Community CIC",
            color = RafikiColors.Ink,
            fontSize = 28.sp,
            textAlign = TextAlign.Center,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun AmountTile(label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .aspectRatio(0.78f)
            .clip(RoundedCornerShape(8.dp))
            .border(3.dp, RafikiColors.Black, RoundedCornerShape(8.dp))
            .background(RafikiColors.White)
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            color = RafikiColors.Black,
            fontSize = 58.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(RafikiColors.Orange),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Donate",
                color = RafikiColors.Black,
                fontSize = 24.sp,
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
            .padding(horizontal = 68.dp, vertical = 54.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HeaderMark()
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Choose amount",
            color = RafikiColors.Black,
            fontSize = 58.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(22.dp))
        Text(
            text = if (input.isBlank()) "\u00A30" else "\u00A3$input",
            color = RafikiColors.Red,
            fontSize = 94.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(28.dp))
        Keypad(
            onDigit = { digit ->
                if (input.length < 3) input += digit
            },
            onDelete = { input = input.dropLast(1) },
            onClear = { input = "" },
        )
        Spacer(Modifier.height(28.dp))
        PrimaryActionButton(
            text = "Donate",
            enabled = valid,
            modifier = Modifier.fillMaxWidth().height(88.dp),
            onClick = { pounds?.let(onAmount) },
        )
        Spacer(Modifier.height(18.dp))
        SecondaryActionButton(
            text = "Back",
            modifier = Modifier.fillMaxWidth().height(78.dp),
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
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { key ->
                    OutlinedButton(
                        modifier = Modifier
                            .weight(1f)
                            .height(84.dp),
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
                            fontSize = if (key.length > 1) 24.sp else 36.sp,
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
            .padding(horizontal = 68.dp, vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HeaderMark()
        Spacer(Modifier.height(64.dp))
        Text(
            text = "Your donation",
            color = RafikiColors.Ink,
            fontSize = 48.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = DonationAmountValidator.displayPounds(amountPence),
            color = RafikiColors.Red,
            fontSize = 124.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(50.dp))
        PrimaryActionButton(
            text = "Donate",
            modifier = Modifier.fillMaxWidth().height(96.dp),
            onClick = onDonate,
        )
        Spacer(Modifier.height(20.dp))
        SecondaryActionButton(
            text = "Change amount",
            modifier = Modifier.fillMaxWidth().height(82.dp),
            onClick = onChange,
        )
        Spacer(Modifier.weight(1f))
        PatternBand()
    }
}

@Composable
private fun ProcessingScreen(amountPence: Long) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 68.dp, vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(94.dp),
            color = RafikiColors.Red,
            strokeWidth = 8.dp,
        )
        Spacer(Modifier.height(44.dp))
        Text(
            text = "Opening secure payment",
            color = RafikiColors.Black,
            fontSize = 46.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = DonationAmountValidator.displayPounds(amountPence),
            color = RafikiColors.Ink,
            fontSize = 52.sp,
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
            .padding(horizontal = 68.dp, vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        HeartCheck(modifier = Modifier.size(210.dp))
        Spacer(Modifier.height(46.dp))
        Text(
            text = "Thank you for supporting Rafiki",
            color = RafikiColors.Black,
            fontSize = 54.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 62.sp,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(22.dp))
        Text(
            text = DonationAmountValidator.displayPounds(amountPence),
            color = RafikiColors.Red,
            fontSize = 62.sp,
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
            .padding(horizontal = 68.dp, vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        HeaderMark()
        Spacer(Modifier.height(52.dp))
        Text(
            text = message,
            color = RafikiColors.Black,
            fontSize = 42.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = 50.sp,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(46.dp))
        if (retryable) {
            PrimaryActionButton(
                text = "Try again",
                modifier = Modifier.fillMaxWidth().height(90.dp),
                onClick = onRetry,
            )
            Spacer(Modifier.height(18.dp))
        }
        SecondaryActionButton(
            text = "Choose another amount",
            modifier = Modifier.fillMaxWidth().height(82.dp),
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
            .padding(horizontal = 68.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HeaderMark()
        Spacer(Modifier.height(58.dp))
        Text(
            text = "Admin PIN",
            color = RafikiColors.Black,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(30.dp))
        Text(
            text = "*".repeat(pin.length).ifBlank { "----" },
            color = RafikiColors.Red,
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(24.dp))
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
        Spacer(Modifier.height(24.dp))
        SecondaryActionButton(
            text = "Cancel",
            modifier = Modifier.fillMaxWidth().height(78.dp),
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
            .padding(horizontal = 46.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "RafikiPay Admin",
            color = RafikiColors.Black,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PresetField("One", first, { first = it }, Modifier.weight(1f))
            PresetField("Two", second, { second = it }, Modifier.weight(1f))
            PresetField("Three", third, { third = it }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))
        PrimaryActionButton(
            text = "Save amounts",
            modifier = Modifier.fillMaxWidth().height(76.dp),
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
        Spacer(Modifier.height(14.dp))
        SecondaryActionButton(
            text = "Reset defaults",
            modifier = Modifier.fillMaxWidth().height(70.dp),
            onClick = onResetPresets,
        )
        Spacer(Modifier.height(28.dp))
        InfoLine("Backend", backendBaseUrl)
        InfoLine("Version", appVersion)
        InfoLine("Device", deviceId)
        InfoLine("Reader mode", if (simulatedReader) "Simulated" else "S710 handoff")
        Spacer(Modifier.weight(1f))
        SecondaryActionButton(
            text = "Check backend",
            modifier = Modifier.fillMaxWidth().height(70.dp),
            onClick = onCheckBackend,
        )
        Spacer(Modifier.height(12.dp))
        SecondaryActionButton(
            text = "Stripe settings",
            modifier = Modifier.fillMaxWidth().height(70.dp),
            onClick = onOpenStripeSettings,
        )
        Spacer(Modifier.height(12.dp))
        PrimaryActionButton(
            text = "Close",
            modifier = Modifier.fillMaxWidth().height(76.dp),
            onClick = onClose,
        )
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
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(
            text = label,
            color = RafikiColors.DeepRed,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.sp,
        )
        Text(
            text = value,
            color = RafikiColors.Ink,
            fontSize = 22.sp,
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
            .padding(horizontal = 68.dp, vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (ok) "Backend OK" else "Backend issue",
            color = if (ok) RafikiColors.Green else RafikiColors.Red,
            fontSize = 54.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(28.dp))
        Text(
            text = message,
            color = RafikiColors.Ink,
            fontSize = 32.sp,
            textAlign = TextAlign.Center,
            lineHeight = 40.sp,
            letterSpacing = 0.sp,
        )
        Spacer(Modifier.height(42.dp))
        PrimaryActionButton(
            text = "Back",
            modifier = Modifier.fillMaxWidth().height(82.dp),
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
            fontSize = 30.sp,
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
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = RafikiColors.Black,
        ),
        onClick = onClick,
    ) {
        Text(
            text = text,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun HeaderMark() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "RAFIKI",
            color = RafikiColors.Black,
            fontSize = 64.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp,
        )
        Text(
            text = "a friend in deed",
            color = RafikiColors.Ink,
            fontSize = 24.sp,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun RafikiLogo(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        PatternBand(modifier = Modifier.matchParentSize())
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "RAFIKI",
                color = RafikiColors.Black,
                fontSize = 130.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                letterSpacing = 0.sp,
            )
            Text(
                text = "a friend in deed",
                color = RafikiColors.Ink,
                fontSize = 42.sp,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                letterSpacing = 0.sp,
            )
        }
        Canvas(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(74.dp),
        ) {
            drawHeart(RafikiColors.Coral)
        }
        Canvas(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(72.dp),
        ) {
            drawHeart(RafikiColors.Coral)
        }
    }
}

@Composable
private fun PatternBand(modifier: Modifier = Modifier.fillMaxWidth().height(66.dp)) {
    Canvas(modifier = modifier) {
        val colors = listOf(RafikiColors.Green, RafikiColors.Orange, RafikiColors.Coral)
        val stripeHeight = size.height.coerceAtLeast(1f)
        for (index in 0..8) {
            val left = index * size.width / 8f
            val color = colors[index % colors.size]
            drawCircle(color, radius = stripeHeight * 0.16f, center = Offset(left + 30f, stripeHeight * 0.25f))
            drawCircle(color, radius = stripeHeight * 0.16f, center = Offset(left + 84f, stripeHeight * 0.75f))
            val path = Path().apply {
                moveTo(left, stripeHeight * 0.58f)
                lineTo(left + 38f, stripeHeight * 0.22f)
                lineTo(left + 78f, stripeHeight * 0.58f)
            }
            drawPath(path, color = color, style = Stroke(width = 12f))
        }
    }
}

@Composable
private fun HeartCheck(modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawHeart(RafikiColors.Coral)
        }
        Canvas(modifier = Modifier.size(105.dp)) {
            val path = Path().apply {
                moveTo(size.width * 0.2f, size.height * 0.55f)
                lineTo(size.width * 0.43f, size.height * 0.75f)
                lineTo(size.width * 0.82f, size.height * 0.25f)
            }
            drawPath(path, color = RafikiColors.White, style = Stroke(width = 16f))
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
