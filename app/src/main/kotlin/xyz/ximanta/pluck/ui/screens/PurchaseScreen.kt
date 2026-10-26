package xyz.ximanta.pluck.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.ximanta.pluck.data.Plan
import xyz.ximanta.pluck.ui.PluckColors
import xyz.ximanta.pluck.ui.viewmodel.PlansState
import xyz.ximanta.pluck.ui.viewmodel.QrState
import xyz.ximanta.pluck.ui.viewmodel.VerifyState

@Composable
fun PurchaseScreen(
    plansState: PlansState,
    selectedPlan: Plan?,
    qrState: QrState,
    verifyState: VerifyState,
    remainingSeconds: Int,
    onBack: () -> Unit,
    onLoadPlans: () -> Unit,
    onSelectPlan: (Plan) -> Unit,
    onRequestQr: (String) -> Unit,
    onVerify: () -> Unit,
    onResetQr: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) { onLoadPlans() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PluckColors.Slate950)
    ) {
        PurchaseHeader(onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (verifyState) {
                is VerifyState.Verified -> VerifiedContent(
                    verifyState = verifyState,
                    context = context,
                    onContinue = onContinue
                )
                else -> PurchaseFlow(
                    plansState = plansState,
                    selectedPlan = selectedPlan,
                    qrState = qrState,
                    verifyState = verifyState,
                    remainingSeconds = remainingSeconds,
                    onSelectPlan = onSelectPlan,
                    onRequestQr = onRequestQr,
                    onVerify = onVerify,
                    onResetQr = onResetQr
                )
            }
        }
    }
}

@Composable
private fun PurchaseHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = PluckColors.Slate300
            )
        }
        Text(
            text = "Get a key",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = PluckColors.Slate100
        )
    }
}

@Composable
private fun PurchaseFlow(
    plansState: PlansState,
    selectedPlan: Plan?,
    qrState: QrState,
    verifyState: VerifyState,
    remainingSeconds: Int,
    onSelectPlan: (Plan) -> Unit,
    onRequestQr: (String) -> Unit,
    onVerify: () -> Unit,
    onResetQr: () -> Unit
) {
    when {
        qrState is QrState.Ready -> QrPaymentStep(
            qrState = qrState,
            verifyState = verifyState,
            remainingSeconds = remainingSeconds,
            onVerify = onVerify,
            onResetQr = onResetQr
        )
        selectedPlan != null -> NameStep(
            plan = selectedPlan,
            qrState = qrState,
            onRequestQr = onRequestQr,
            onBack = onResetQr
        )
        else -> PlansStep(
            plansState = plansState,
            onSelectPlan = onSelectPlan
        )
    }
}

@Composable
private fun PlansStep(
    plansState: PlansState,
    onSelectPlan: (Plan) -> Unit
) {
    Text(
        text = "CHOOSE A PLAN",
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = PluckColors.Slate500,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(bottom = 16.dp)
    )

    when (plansState) {
        is PlansState.Idle, is PlansState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PluckColors.Emerald500)
            }
        }
        is PlansState.Error -> {
            Surface(
                color = PluckColors.Red900.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = plansState.message,
                    color = PluckColors.Red400,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
        is PlansState.Success -> {
            plansState.plans.forEach { plan ->
                PlanCard(plan = plan, onClick = { onSelectPlan(plan) })
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun PlanCard(plan: Plan, onClick: () -> Unit) {
    Surface(
        color = PluckColors.Slate900,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = plan.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PluckColors.Slate100
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${plan.durationDays} days",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = PluckColors.Emerald400,
                        modifier = Modifier
                            .background(
                                PluckColors.Emerald500.copy(alpha = 0.12f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = plan.description,
                    fontSize = 12.sp,
                    color = PluckColors.Slate400
                )
            }
            Text(
                text = "₹${plan.amount}",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = PluckColors.Emerald400
            )
        }
    }
}

@Composable
private fun NameStep(
    plan: Plan,
    qrState: QrState,
    onRequestQr: (String) -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    val isLoading = qrState is QrState.Loading
    val errorMessage = (qrState as? QrState.Error)?.message

    Text(
        text = "GETTING ${plan.name.uppercase()}",
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = PluckColors.Slate500,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    Text(
        text = "₹${plan.amount} · ${plan.durationDays} days",
        fontSize = 14.sp,
        color = PluckColors.Slate400,
        modifier = Modifier.padding(bottom = 24.dp)
    )

    Text(
        text = "Your name",
        fontSize = 13.sp,
        color = PluckColors.Slate400,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
    )

    OutlinedTextField(
        value = name,
        onValueChange = { name = it.take(40) },
        enabled = !isLoading,
        placeholder = { Text("Shown on your key", color = PluckColors.Slate600) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PluckColors.Emerald500,
            unfocusedBorderColor = PluckColors.Slate700,
            focusedContainerColor = PluckColors.Slate900,
            unfocusedContainerColor = PluckColors.Slate900,
            cursorColor = PluckColors.Emerald400,
            focusedTextColor = PluckColors.Slate100,
            unfocusedTextColor = PluckColors.Slate100
        )
    )

    Spacer(Modifier.height(20.dp))

    Button(
        onClick = { onRequestQr(name) },
        enabled = name.isNotBlank() && !isLoading,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PluckColors.Emerald500,
            contentColor = PluckColors.Slate950,
            disabledContainerColor = PluckColors.Slate800,
            disabledContentColor = PluckColors.Slate600
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = PluckColors.Slate950,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(10.dp))
            Text("Generating QR…", fontWeight = FontWeight.SemiBold)
        } else {
            Text("Generate QR", fontWeight = FontWeight.SemiBold)
        }
    }

    if (!errorMessage.isNullOrBlank()) {
        Spacer(Modifier.height(14.dp))
        Text(
            text = errorMessage,
            color = PluckColors.Red400,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    Spacer(Modifier.height(8.dp))

    OutlinedButton(
        onClick = onBack,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Change plan", color = PluckColors.Slate300)
    }
}

@Composable
private fun QrPaymentStep(
    qrState: QrState.Ready,
    verifyState: VerifyState,
    remainingSeconds: Int,
    onVerify: () -> Unit,
    onResetQr: () -> Unit
) {
    val qr = qrState.qr
    val bitmap = remember(qr.qrImageBase64) {
        runCatching {
            val bytes = Base64.decode(qr.qrImageBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
    val isChecking = verifyState is VerifyState.Checking
    val notFoundMsg = (verifyState as? VerifyState.NotFound)?.message
    val errorMsg = (verifyState as? VerifyState.Error)?.message
    val expired = remainingSeconds <= 0

    Text(
        text = "SCAN & PAY",
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = PluckColors.Slate500,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(bottom = 16.dp)
    )

    Surface(
        color = androidx.compose.ui.graphics.Color.White,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.size(280.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Payment QR",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = null,
                    tint = PluckColors.Slate400,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }

    Spacer(Modifier.height(20.dp))

    Text(
        text = "Pay exactly",
        fontSize = 12.sp,
        color = PluckColors.Slate500
    )
    Text(
        text = "₹${qr.finalAmount}",
        fontSize = 36.sp,
        fontWeight = FontWeight.Bold,
        color = PluckColors.Emerald400,
        modifier = Modifier.padding(top = 4.dp)
    )

    Spacer(Modifier.height(6.dp))

    Text(
        text = if (expired) "QR expired" else "Expires in ${mmss(remainingSeconds)}",
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        color = if (expired) PluckColors.Red400 else PluckColors.Slate500
    )

    Spacer(Modifier.height(28.dp))

    Button(
        onClick = onVerify,
        enabled = !isChecking && !expired,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PluckColors.Emerald500,
            contentColor = PluckColors.Slate950,
            disabledContainerColor = PluckColors.Slate800,
            disabledContentColor = PluckColors.Slate600
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        if (isChecking) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = PluckColors.Slate950,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(10.dp))
            Text("Checking…", fontWeight = FontWeight.SemiBold)
        } else {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("I've paid · Check", fontWeight = FontWeight.SemiBold)
        }
    }

    if (!notFoundMsg.isNullOrBlank()) {
        Spacer(Modifier.height(14.dp))
        Text(
            text = notFoundMsg,
            color = PluckColors.Slate400,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (!errorMsg.isNullOrBlank()) {
        Spacer(Modifier.height(14.dp))
        Text(
            text = errorMsg,
            color = PluckColors.Red400,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    Spacer(Modifier.height(12.dp))

    OutlinedButton(
        onClick = onResetQr,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = if (expired) "Generate new QR" else "Cancel",
            color = PluckColors.Slate300
        )
    }
}

@Composable
private fun VerifiedContent(
    verifyState: VerifyState.Verified,
    context: Context,
    onContinue: () -> Unit
) {
    Spacer(Modifier.height(24.dp))

    Box(
        modifier = Modifier
            .size(72.dp)
            .background(
                PluckColors.Emerald500.copy(alpha = 0.18f),
                RoundedCornerShape(20.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = PluckColors.Emerald400,
            modifier = Modifier.size(40.dp)
        )
    }

    Spacer(Modifier.height(20.dp))

    Text(
        text = "Payment verified",
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        color = PluckColors.Slate100
    )
    Text(
        text = "Save your key — you'll need it to log in",
        fontSize = 13.sp,
        color = PluckColors.Slate400,
        modifier = Modifier.padding(top = 6.dp, bottom = 28.dp),
        textAlign = TextAlign.Center
    )

    Surface(
        color = PluckColors.Slate900,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "YOUR KEY",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = PluckColors.Slate500,
                letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = verifyState.key,
                fontSize = 26.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = PluckColors.Emerald400,
                letterSpacing = 4.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Pluck key", verifyState.key))
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy key",
                        tint = PluckColors.Emerald400,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "Copy",
                    fontSize = 13.sp,
                    color = PluckColors.Emerald400
                )
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    Text(
        text = "${verifyState.planName} · expires ${formatExpiry(verifyState.expiresAt)}",
        fontSize = 12.sp,
        color = PluckColors.Slate500,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(32.dp))

    Button(
        onClick = onContinue,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PluckColors.Emerald500,
            contentColor = PluckColors.Slate950
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        Text("Continue to app", fontWeight = FontWeight.SemiBold)
    }

    Spacer(Modifier.height(24.dp))
}

private fun mmss(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

private fun formatExpiry(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return "never"
    val fmt = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.US)
    return fmt.format(java.util.Date(epochSeconds * 1000L))
}
