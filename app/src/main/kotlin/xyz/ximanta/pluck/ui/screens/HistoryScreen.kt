package xyz.ximanta.pluck.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.ximanta.pluck.data.AuthState
import xyz.ximanta.pluck.data.HistoryEntry
import xyz.ximanta.pluck.ui.PluckColors
import xyz.ximanta.pluck.ui.viewmodel.HomeHistoryState
import xyz.ximanta.pluck.util.Formatters

@Composable
fun HistoryScreen(
    authState: AuthState,
    historyState: HomeHistoryState,
    onRefresh: () -> Unit,
    onEntryClick: (HistoryEntry) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxSize().background(PluckColors.Slate950).systemBarsPadding()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("History", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = PluckColors.Slate100, modifier = Modifier.weight(1f))
                IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "Refresh", tint = PluckColors.Slate400) }
            }
            Text(accountLine(authState), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = PluckColors.Slate500)
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (historyState) {
                is HomeHistoryState.Idle, is HomeHistoryState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PluckColors.Emerald500) }
                is HomeHistoryState.Error -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(historyState.message, color = PluckColors.Red400) }
                is HomeHistoryState.Success -> {
                    if (historyState.entries.isEmpty()) {
                        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Filled.History, null, tint = PluckColors.Slate700, modifier = Modifier.size(48.dp))
                            Text("No downloads yet", fontSize = 13.sp, color = PluckColors.Slate500, modifier = Modifier.padding(top = 12.dp))
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(historyState.entries) { entry -> HistoryRow(entry) { onEntryClick(entry) } }
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            // Contact Developer Button
            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:official@ximanta.xyz")
                        putExtra(Intent.EXTRA_SUBJECT, "Pluck App Support")
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Email, null, tint = PluckColors.Slate400, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Contact Developer", fontSize = 13.sp, color = PluckColors.Slate400)
            }
            
            TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Logout, null, tint = PluckColors.Slate400, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sign out", fontSize = 13.sp, color = PluckColors.Slate400)
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit) {
    Surface(color = PluckColors.Slate900, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.platform.uppercase().ifBlank { "WEB" }, fontSize = 9.sp, color = PluckColors.Emerald400, modifier = Modifier.background(PluckColors.Emerald500.copy(alpha = 0.12f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                if (entry.quality.isNotBlank()) { Spacer(Modifier.width(6.dp)); Text(entry.quality, fontSize = 10.sp, color = PluckColors.Slate400) }
                Spacer(Modifier.weight(1f))
                Text(Formatters.relativeTime(entry.fetchedAt), fontSize = 10.sp, color = PluckColors.Slate500)
            }
            Spacer(Modifier.height(6.dp))
            Text(entry.title.ifBlank { entry.url }, fontSize = 13.sp, color = PluckColors.Slate100, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun accountLine(authState: AuthState): String {
    val key = authState.apiKey ?: ""
    val plan = authState.planName.ifBlank { "Pluck" }
    val expiry = Formatters.expiresLabel(authState.expiresAt)
    return if (key.isBlank()) plan else "$plan · ${key.take(3)}… · $expiry"
}
