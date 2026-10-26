package xyz.ximanta.pluck.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PluckColors.Slate950)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "History",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PluckColors.Slate100,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh",
                        tint = PluckColors.Slate400
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = accountLine(authState),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = PluckColors.Slate500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (historyState) {
                is HomeHistoryState.Idle,
                is HomeHistoryState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PluckColors.Emerald500)
                    }
                }
                is HomeHistoryState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = historyState.message,
                            color = PluckColors.Red400,
                            fontSize = 13.sp
                        )
                    }
                }
                is HomeHistoryState.Success -> {
                    if (historyState.entries.isEmpty()) {
                        EmptyHistory()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(historyState.entries) { entry ->
                                HistoryRow(entry = entry, onClick = { onEntryClick(entry) })
                            }
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            TextButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.Logout,
                    contentDescription = null,
                    tint = PluckColors.Slate400,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Sign out",
                    fontSize = 13.sp,
                    color = PluckColors.Slate400
                )
            }
        }
    }
}

@Composable
private fun EmptyHistory() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Icon(
            imageVector = Icons.Filled.History,
            contentDescription = null,
            tint = PluckColors.Slate700,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "No downloads yet",
            fontSize = 13.sp,
            color = PluckColors.Slate500
        )
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onClick: () -> Unit) {
    Surface(
        color = PluckColors.Slate900,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.platform.uppercase().ifBlank { entry.mediaType.uppercase().ifBlank { "WEB" } },
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PluckColors.Emerald400,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .background(
                            PluckColors.Emerald500.copy(alpha = 0.12f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                if (entry.quality.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = entry.quality,
                        fontSize = 10.sp,
                        color = PluckColors.Slate400
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = Formatters.relativeTime(entry.fetchedAt),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = PluckColors.Slate500
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = entry.title.ifBlank { entry.url },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = PluckColors.Slate100,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.sizeBytes > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = Formatters.humanBytes(entry.sizeBytes),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = PluckColors.Slate500
                )
            }
        }
    }
}

private fun accountLine(authState: AuthState): String {
    val key = authState.apiKey ?: ""
    val plan = authState.planName.ifBlank { "Pluck" }
    val expiry = Formatters.expiresLabel(authState.expiresAt)
    return if (key.isBlank()) plan else "$plan · ${key.take(3)}… · $expiry"
}
