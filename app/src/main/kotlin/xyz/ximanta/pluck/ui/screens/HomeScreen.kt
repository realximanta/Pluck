package xyz.ximanta.pluck.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import xyz.ximanta.pluck.data.MediaFormat
import xyz.ximanta.pluck.data.ResolveResponse
import xyz.ximanta.pluck.ui.PluckColors
import xyz.ximanta.pluck.ui.viewmodel.HomeFetchState
import xyz.ximanta.pluck.ui.viewmodel.HomeResolveState
import xyz.ximanta.pluck.ui.viewmodel.SelectedFormat
import xyz.ximanta.pluck.util.Formatters

@Composable
fun HomeScreen(
    urlInput: String,
    resolveState: HomeResolveState,
    selected: SelectedFormat?,
    fetchState: HomeFetchState,
    onUrlChange: (String) -> Unit,
    onResolve: () -> Unit,
    onSelectFormat: (MediaFormat, String) -> Unit,
    onClearSelection: () -> Unit,
    onDownload: () -> Unit,
    onDismissFetch: () -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().background(PluckColors.Slate950).systemBarsPadding()
    ) {
        // Custom minimal header instead of TopAppBar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = PluckColors.Slate300)
            }
            Text(
                text = "Pluck",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = PluckColors.Slate100
            )
        }

        if (resolveState is HomeResolveState.Idle) {
            // Centered input when empty
            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Pull any media", fontSize = 24.sp, color = PluckColors.Slate100, fontWeight = FontWeight.Bold)
                    Text("from anywhere.", fontSize = 24.sp, color = PluckColors.Emerald400, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 32.dp))
                    UrlBar(urlInput, onUrlChange, onResolve, false)
                }
            }
        } else {
            // Top aligned when resolving or resolved
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                UrlBar(urlInput, onUrlChange, onResolve, resolveState is HomeResolveState.Loading)
                Spacer(Modifier.height(16.dp))

                when (resolveState) {
                    is HomeResolveState.Loading -> {
                        Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = PluckColors.Emerald500)
                        }
                    }
                    is HomeResolveState.Error -> {
                        Surface(color = PluckColors.Red900.copy(alpha = 0.3f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(resolveState.message, color = PluckColors.Red400, fontSize = 13.sp, modifier = Modifier.padding(14.dp), textAlign = TextAlign.Center)
                        }
                    }
                    is HomeResolveState.Success -> {
                        ResolvedContent(
                            data = resolveState.data,
                            selected = selected,
                            fetchState = fetchState,
                            onSelectFormat = onSelectFormat,
                            onClearSelection = onClearSelection,
                            onDownload = onDownload,
                            onDismissFetch = onDismissFetch
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun UrlBar(
    urlInput: String,
    onUrlChange: (String) -> Unit,
    onResolve: () -> Unit,
    isLoading: Boolean
) {
    OutlinedTextField(
        value = urlInput,
        onValueChange = onUrlChange,
        enabled = !isLoading,
        placeholder = { Text("Paste any link…", color = PluckColors.Slate600, fontSize = 14.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onResolve() }),
        shape = RoundedCornerShape(16.dp),
        trailingIcon = {
            if (urlInput.isNotBlank() && !isLoading) {
                IconButton(onClick = onResolve) { Icon(Icons.Filled.ArrowForward, "Resolve", tint = PluckColors.Emerald400) }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PluckColors.Emerald500,
            unfocusedBorderColor = PluckColors.Slate700,
            focusedContainerColor = PluckColors.Slate900,
            unfocusedContainerColor = PluckColors.Slate900,
            focusedTextColor = PluckColors.Slate100,
            unfocusedTextColor = PluckColors.Slate100,
            cursorColor = PluckColors.Emerald400
        )
    )
}

@Composable
private fun ResolvedContent(
    data: ResolveResponse,
    selected: SelectedFormat?,
    fetchState: HomeFetchState,
    onSelectFormat: (MediaFormat, String) -> Unit,
    onClearSelection: () -> Unit,
    onDownload: () -> Unit,
    onDismissFetch: () -> Unit
) {
    val tabs = remember(data) {
        buildList {
            if (data.video.isNotEmpty()) add("Video" to data.video)
            if (data.audio.isNotEmpty()) add("Audio" to data.audio)
            if (data.image.isNotEmpty()) add("Image" to data.image)
            val files = data.archive + data.document + data.code + data.text + data.other
            if (files.isNotEmpty()) add("Files" to files)
        }
    }
    var activeTab by remember(data.url) { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Media Header with Cover Image
        Surface(color = PluckColors.Slate900, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (data.thumbnail.isNotBlank()) {
                    AsyncImage(
                        model = data.thumbnail,
                        contentDescription = "Cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)).background(PluckColors.Slate800)
                    )
                } else {
                    Box(modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)).background(PluckColors.Slate800), contentAlignment = Alignment.Center) {
                        Text("No Img", color = PluckColors.Slate500, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(data.platform.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = PluckColors.Emerald400)
                    Spacer(Modifier.height(4.dp))
                    Text(data.title.ifBlank { "Untitled" }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PluckColors.Slate100, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Format Tabs
        if (tabs.isNotEmpty()) {
            TabRow(selectedTabIndex = activeTab, containerColor = PluckColors.Slate950, contentColor = PluckColors.Emerald400, divider = {}) {
                tabs.forEachIndexed { index, pair ->
                    Tab(
                        selected = activeTab == index,
                        onClick = { activeTab = index },
                        text = { Text("${pair.first} (${pair.second.size})", fontSize = 12.sp) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            val currentFormats = tabs[activeTab].second
            val mediaType = tabs[activeTab].first.lowercase()

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(currentFormats) { format ->
                    val isSelected = selected?.format?.formatId == format.formatId
                    FormatRow(format, isSelected) {
                        if (isSelected) onClearSelection() else onSelectFormat(format, mediaType)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        FetchFooter(selected, fetchState, onDownload, onDismissFetch)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun FormatRow(format: MediaFormat, isSelected: Boolean, onClick: () -> Unit) {
    val borderColor = if (isSelected) PluckColors.Emerald500 else Color.Transparent
    Surface(
        color = if (isSelected) PluckColors.Slate800 else PluckColors.Slate900,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().border(1.dp, borderColor, RoundedCornerShape(12.dp)).clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(format.quality.ifBlank { format.ext.uppercase() }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PluckColors.Slate100)
                    if (format.ext.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(format.ext.uppercase(), fontSize = 10.sp, color = PluckColors.Slate400, modifier = Modifier.background(PluckColors.Slate800, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                }
                if (format.note.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(format.note, fontSize = 11.sp, color = PluckColors.Slate500)
                }
            }
            Text(if (format.sizeBytes > 0) Formatters.humanBytes(format.sizeBytes) else "", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = PluckColors.Slate400)
            if (isSelected) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.Check, null, tint = PluckColors.Emerald400, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun FetchFooter(selected: SelectedFormat?, fetchState: HomeFetchState, onDownload: () -> Unit, onDismiss: () -> Unit) {
    when (fetchState) {
        is HomeFetchState.Idle -> {
            Button(
                onClick = onDownload,
                enabled = selected != null,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PluckColors.Emerald500, contentColor = PluckColors.Slate950, disabledContainerColor = PluckColors.Slate800),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Filled.Download, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (selected == null) "Pick a format" else "Download", fontWeight = FontWeight.SemiBold)
            }
        }
        is HomeFetchState.Downloading -> {
            Surface(color = PluckColors.Slate900, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Downloading", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PluckColors.Slate100, modifier = Modifier.weight(1f))
                        Text(fetchState.label, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = PluckColors.Slate400)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (fetchState.percent != null) {
                        LinearProgressIndicator(progress = { fetchState.percent / 100f }, color = PluckColors.Emerald500, trackColor = PluckColors.Slate800, modifier = Modifier.fillMaxWidth().height(6.dp))
                    } else {
                        LinearProgressIndicator(color = PluckColors.Emerald500, trackColor = PluckColors.Slate800, modifier = Modifier.fillMaxWidth().height(6.dp))
                    }
                }
            }
        }
        is HomeFetchState.Success -> {
            Surface(color = PluckColors.Slate900, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, null, tint = PluckColors.Emerald400, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Saved: ${fetchState.displayName}", fontSize = 13.sp, color = PluckColors.Slate100, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) { Icon(Icons.Filled.Close, "Dismiss", tint = PluckColors.Slate400, modifier = Modifier.size(16.dp)) }
                }
            }
        }
        is HomeFetchState.Error -> {
            Surface(color = PluckColors.Red900.copy(alpha = 0.3f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(fetchState.message, fontSize = 13.sp, color = PluckColors.Red400, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) { Icon(Icons.Filled.Close, "Dismiss", tint = PluckColors.Red400, modifier = Modifier.size(16.dp)) }
                }
            }
        }
    }
}
