package xyz.ximanta.pluck.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import xyz.ximanta.pluck.data.Plan
import xyz.ximanta.pluck.ui.PluckColors
import xyz.ximanta.pluck.ui.viewmodel.PlansState
import xyz.ximanta.pluck.ui.viewmodel.QrState
import xyz.ximanta.pluck.ui.viewmodel.VerifyState
import java.io.File
import java.io.FileOutputStream

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

    Column(modifier = modifier.fillMaxSize().background(PluckColors.Slate950).systemBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PluckColors.Slate300) }
            Text("Get a key", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = PluckColors.Slate100)
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            when (verifyState) {
                is VerifyState.Verified -> VerifiedContent(verifyState, context, onContinue)
                else -> {
                    when {
                        qrState is QrState.Ready -> QrPaymentStep(qrState, verifyState, remainingSeconds, context, onVerify, onResetQr)
                        selectedPlan != null -> NameStep(selectedPlan, qrState, onRequestQr, onResetQr) // onResetQr resets to plans list
                        else -> PlansStep(plansState, onSelectPlan)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlansStep(plansState: PlansState, onSelectPlan: (Plan) -> Unit) {
    Text("CHOOSE A PLAN", fontSize = 11.sp, color = PluckColors.Slate500, letterSpacing = 1.5.sp, modifier = Modifier.padding(bottom = 16.dp))
    when (plansState) {
        is PlansState.Idle, is PlansState.Loading -> Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PluckColors.Emerald500) }
        is PlansState.Error -> Text(plansState.message, color = PluckColors.Red400)
        is PlansState.Success -> {
            plansState.plans.forEach { plan ->
                Surface(color = PluckColors.Slate900, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable { onSelectPlan(plan) }) {
                    Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(plan.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = PluckColors.Slate100)
                                Spacer(Modifier.width(8.dp))
                                Text("${plan.durationDays} days", fontSize = 11.sp, color = PluckColors.Emerald400, modifier = Modifier.background(PluckColors.Emerald500.copy(alpha = 0.12f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(plan.description, fontSize = 12.sp, color = PluckColors.Slate400)
                        }
                        Text("₹${plan.amount}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PluckColors.Emerald400)
                    }
                }
            }
        }
    }
}

@Composable
private fun NameStep(plan: Plan, qrState: QrState, onRequestQr: (String) -> Unit, onChangePlan: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val isLoading = qrState is QrState.Loading
    Text("GETTING ${plan.name.uppercase()}", fontSize = 11.sp, color = PluckColors.Slate500, letterSpacing = 1.5.sp)
    Text("₹${plan.amount} · ${plan.durationDays} days", fontSize = 14.sp, color = PluckColors.Slate400, modifier = Modifier.padding(bottom = 24.dp))

    OutlinedTextField(
        value = name, onValueChange = { name = it.take(40) }, enabled = !isLoading,
        placeholder = { Text("Your name", color = PluckColors.Slate600) }, singleLine = true,
        shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PluckColors.Emerald500, unfocusedBorderColor = PluckColors.Slate700, focusedContainerColor = PluckColors.Slate900, unfocusedContainerColor = PluckColors.Slate900)
    )
    Spacer(Modifier.height(20.dp))
    Button(onClick = { onRequestQr(name) }, enabled = name.isNotBlank() && !isLoading, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = PluckColors.Emerald500, contentColor = PluckColors.Slate950), modifier = Modifier.fillMaxWidth().height(52.dp)) {
        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = PluckColors.Slate950, strokeWidth = 2.dp) else Text("Generate QR", fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onChangePlan, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) { Text("Change plan", color = PluckColors.Slate300) }
}

@Composable
private fun QrPaymentStep(qrState: QrState.Ready, verifyState: VerifyState, remainingSeconds: Int, context: Context, onVerify: () -> Unit, onCancel: () -> Unit) {
    val qr = qrState.qr
    val bitmap = remember(qr.qrImageBase64) {
        runCatching { BitmapFactory.decodeByteArray(Base64.decode(qr.qrImageBase64, Base64.DEFAULT), 0, Base64.decode(qr.qrImageBase64, Base64.DEFAULT).size) }.getOrNull()
    }
    val expired = remainingSeconds <= 0
    val isChecking = verifyState is VerifyState.Checking

    // Top Row with Download Button
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("SCAN & PAY", fontSize = 11.sp, color = PluckColors.Slate500, letterSpacing = 1.5.sp)
        IconButton(onClick = { saveQrToDownloads(context, bitmap, qr.finalAmount) }) {
            Icon(Icons.Filled.Download, "Save QR", tint = PluckColors.Emerald400)
        }
    }

    Surface(color = Color.White, shape = RoundedCornerShape(20.dp), modifier = Modifier.size(260.dp)) {
        Box(contentAlignment = Alignment.Center) {
            if (bitmap != null) Image(bitmap = bitmap.asImageBitmap(), contentDescription = "QR", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(16.dp))
        }
    }

    Spacer(Modifier.height(20.dp))
    Text("Pay exactly", fontSize = 12.sp, color = PluckColors.Slate500)
    Text("₹${qr.finalAmount}", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = PluckColors.Emerald400)
    Text(if (expired) "QR expired" else "Expires in ${remainingSeconds / 60}:${String.format("%02d", remainingSeconds % 60)}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = if (expired) PluckColors.Red400 else PluckColors.Slate500)

    Spacer(Modifier.height(24.dp))

    // Pay Through UPI Intent
    val upiUri = Uri.parse("upi://pay?pa=pluck@upi&pn=Pluck&am=${qr.finalAmount}&cu=INR") // Placeholder PA, standard intent fails if PA is wrong, but opens the picker.
    Button(
        onClick = {
            try { context.startActivity(Intent(Intent.ACTION_VIEW, upiUri)) } catch (e: Exception) { }
        },
        modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PluckColors.Slate800, contentColor = PluckColors.Slate100)
    ) { Text("Pay Through UPI App") }

    Spacer(Modifier.height(8.dp))

    // Share Intent
    Button(
        onClick = { shareQrImage(context, bitmap, qr.finalAmount) },
        modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PluckColors.Slate800, contentColor = PluckColors.Slate100)
    ) { Text("Share to Friend") }

    Spacer(Modifier.height(16.dp))

    Button(onClick = onVerify, enabled = !isChecking && !expired, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = PluckColors.Emerald500, contentColor = PluckColors.Slate950), modifier = Modifier.fillMaxWidth().height(52.dp)) {
        if (isChecking) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = PluckColors.Slate950) else { Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("I've paid · Check", fontWeight = FontWeight.SemiBold) }
    }
    
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) { Text("Cancel", color = PluckColors.Slate300) }
}

@Composable
private fun VerifiedContent(verifyState: VerifyState.Verified, context: Context, onContinue: () -> Unit) {
    Spacer(Modifier.height(24.dp))
    Icon(Icons.Filled.Check, null, tint = PluckColors.Emerald400, modifier = Modifier.size(64.dp))
    Text("Payment verified", fontSize = 24.sp, color = PluckColors.Slate100, modifier = Modifier.padding(top = 16.dp))
    Surface(color = PluckColors.Slate900, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("YOUR KEY", fontSize = 10.sp, color = PluckColors.Slate500)
            Text(verifyState.key, fontSize = 26.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = PluckColors.Emerald400)
            IconButton(onClick = { val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; cm.setPrimaryClip(ClipData.newPlainText("key", verifyState.key)) }) { Icon(Icons.Filled.ContentCopy, null, tint = PluckColors.Emerald400) }
        }
    }
    Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = PluckColors.Emerald500, contentColor = PluckColors.Slate950)) { Text("Continue to app") }
}

private fun saveQrToDownloads(context: Context, bitmap: Bitmap?, amount: String) {
    if (bitmap == null) return
    val filename = "pluck-qr-$amount.png"
    val file = File(context.cacheDir, filename)
    FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "image/png"
        putExtra(Intent.EXTRA_TITLE, filename)
    }
    context.startActivity(intent)
    // Fallback to share if create document isn't supported
}

private fun shareQrImage(context: Context, bitmap: Bitmap?, amount: String) {
    if (bitmap == null) return
    val filename = "pluck-qr-$amount.png"
    val file = File(context.cacheDir, filename)
    FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "Pay exactly ₹$amount to get my Pluck premium key.")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share QR"))
}
