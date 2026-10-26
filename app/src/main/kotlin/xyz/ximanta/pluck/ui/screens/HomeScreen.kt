package xyz.ximanta.pluck.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.ximanta.pluck.data.MediaFormat
import xyz.ximanta.pluck.data.ResolveResponse
import xyz.ximanta.pluck.ui.PluckColors
import xyz.ximanta.pluck.ui.viewmodel.HomeFetchState
import xyz.ximanta.pluck.ui.viewmodel.HomeResolveState
import xyz.ximanta.pluck.ui.viewmodel.SelectedFormat
import xyz.ximanta.pluck.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
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
        modifier = modifier
            .fillMaxSize()
            .background(PluckColors.Slate950)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Pluck",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PluckColors.Slate100
                )
            },
            navigationIcon = {
                IconButton(onClick = onOpenDrawer) {
                    Icon(
                        imageVector = Icons.Filled.Menu,
                        contentDescription = "Menu",
                        tint = PluckColors.Slate300
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = PluckColors.Slate950
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            UrlBar(
                urlInput = urlInput,
                onUrlChange = onUrlChange,
                onResolve = onResolve,
                isLoading = resolveState is HomeResolveState.Loading
            )

            Spacer(Modifier.height(16.dp))

            when (resolveState) {
                is HomeResolveState.Idle -> EmptyStateHint()
                is HomeResolveState.Loading -> LoadingResolve()
                is HomeResolveState.Error -> ResolveError(resolveState.message)
                is HomeResolveState.Success -> ResolvedContent(
                    data = resolveState.data,
                    selected = selected,
                    fetchState = fetchState,
                    onSelectFormat = onSelectFormat,
                    onClearSelection = onClearSelection,
                    onDownload = onDownload,
                    onDismissFetch = onDismissFetch
                )
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
        placeholder = {
            Text(
                text = "Paste any link…",
                color = PluckColors.Slate600,
                fontSize = 14.sp
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go
        ),
        keyboardActions = KeyboardActions(onGo = { onResolve() }),
        shape = RoundedCornerShape(14.dp),
        trailingIcon = {
            if (urlInput.isNotBlank() && !isLoading) {
                IconButton(onClick = onResolve) {
                    Icon(
                        imageVector = Icons.Filled.ArrowForward,
                        contentDescription = "Resolve",
                        tint = PluckColors.Emerald400
                    )
                }
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
private fun EmptyStateHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Paste a link to begin",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = PluckColors.Slate400
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "YouTube · Instagram · TikTok · Drive · direct files",
            fontSize = 12.sp,
            color = PluckColors.Slate600,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LoadingResolve() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = PluckColors.Emerald500)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Resolving…",
            fontSize = 13.sp,
            color = PluckColors.Slate500
        )
    }
}

@Composable
private fun ResolveError(message: String) {
    Surface(
        color = PluckColors.Red900.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Text(
            text = message,
            color = PluckColors.Red400,
            fontSize = 13.sp,
            modifier = Modifier.padding(14.dp),
            textAlign = TextAlign.Center
        )
    }
}

private data class FormatTab(val label: String, val mediaType: String, val formats: List<MediaFormat>)

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
            if (data.video.isNotEmpty()) add(FormatTab("Video", "video", data.video))
            if (data.audio.isNotEmpty()) add(FormatTab("Audio", "audio", data.audio))
            if (data.image.isNotEmpty()) add(FormatTab("Image", "image", data.image))
            val files = data.archive + data.document + data.code + data.text + data.other
            if (files.isNotEmpty()) add(FormatTab("Files", "archive", files))
        }
    }

    var activeTab by rememberSaveable(data.url) { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        MediaHeader(data = data)

        Spacer(Modifier.height(16.dp))

        if (tabs.isNotEmpty()) {
            TabRow(
                selectedTabIndex = activeTab.coerceAtMost(tabs.lastIndex),
                containerColor = PluckColors.Slate950,
                contentColor = PluckColors.Emerald400,
                divider = {}
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = activeTab == index,
                        onClick = { activeTab = index },
                        text = {
                            Text(
                                text = "${tab.label} (${tab.formats.size})",
                                fontSize = 12.sp,
                                fontWeight = if (activeTab == index) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        selectedContentColor = PluckColors.Emerald400,
                        unselectedContentColor = PluckColors.Slate500
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            val current = tabs.getOrNull(activeTab)
            if (current != null) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(current.formats) { format ->
                        val isSelected = selected?.format?.formatId == format.formatId &&
                            selected.mediaType == current.mediaType
                        FormatRow(
                            format = format,
                            isSelected = isSelected,
                            onClick = {
                                if (isSelected) onClearSelection()
                                else onSelectFormat(format, current.mediaType)
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        FetchFooter(
            selected = selected,
            fetchState = fetchState,
            onDownload = onDownload,
            onDismiss = onDismissFetch
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MediaHeader(data: ResolveResponse) {
    Surface(
        color = PluckColors.Slate900,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = data.platform.uppercase().ifBlank { "WEB" },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PluckColors.Emerald400,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .background(
                            PluckColors.Emerald500.copy(alpha = 0.12f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
                if (data.durationSec > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = Formatters.formatDuration(data.durationSec),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = PluckColors.Slate400
                    )
                }
                if (data.uploader.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = data.uploader,
                        fontSize = 11.sp,
                        color = PluckColors.Slate500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = data.title.ifBlank { "Untitled" },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = PluckColors.Slate100,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FormatRow(
    format: MediaFormat,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) PluckColors.Emerald500 else Color.Transparent
    Surface(
        color = if (isSelected) PluckColors.Slate800 else PluckColors.Slate900,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = format.quality.ifBlank { format.ext.uppercase().ifBlank { "Format" } },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PluckColors.Slate100
                    )
                    if (format.ext.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = format.ext.uppercase(),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PluckColors.Slate400,
                            modifier = Modifier
                                .background(
                                    PluckColors.Slate800,
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (format.note.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = format.note,
                        fontSize = 11.sp,
                        color = PluckColors.Slate500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = if (format.sizeBytes > 0) Formatters.humanBytes(format.sizeBytes) else "",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = PluckColors.Slate400
            )
            if (isSelected) {
                Spacer(Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = PluckColors.Emerald400,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun FetchFooter(
    selected: SelectedFormat?,
    fetchState: HomeFetchState,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    when (fetchState) {
        is HomeFetchState.Idle -> {
            Button(
                onClick = onDownload,
                enabled = selected != null,
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
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (selected == null) "Pick a format" else "Download",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        is HomeFetchState.Downloading -> {
            Surface(
                color = PluckColors.Slate900,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Downloading",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PluckColors.Slate100,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = fetchState.label,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = PluckColors.Slate400
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    if (fetchState.percent != null) {
                        LinearProgressIndicator(
                            progress = { fetchState.percent / 100f },
                            color = PluckColors.Emerald500,
                            trackColor = PluckColors.Slate800,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "${fetchState.percent}%",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = PluckColors.Emerald400
                        )
                    } else {
                        LinearProgressIndicator(
                            color = PluckColors.Emerald500,
                            trackColor = PluckColors.Slate800,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                        )
                    }
                }
            }
        }
        is HomeFetchState.Success -> {
            Surface(
                color = PluckColors.Slate900,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = PluckColors.Emerald400,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Saved",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PluckColors.Emerald400,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Dismiss",
                                tint = PluckColors.Slate400,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = fetchState.displayName,
                        fontSize = 13.sp,
                        color = PluckColors.Slate100,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = fetchState.savedAt,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = PluckColors.Slate500,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        is HomeFetchState.Error -> {
            Surface(
                color = PluckColors.Red900.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = fetchState.message,
                        fontSize = 13.sp,
                        color = PluckColors.Red400,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss",
                            tint = PluckColors.Red400,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
