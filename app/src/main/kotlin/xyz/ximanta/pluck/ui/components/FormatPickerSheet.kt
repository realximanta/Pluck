package xyz.ximanta.pluck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.ximanta.pluck.data.MediaFormat
import xyz.ximanta.pluck.data.ResolveResponse
import xyz.ximanta.pluck.ui.PluckColors
import xyz.ximanta.pluck.util.Formatters

private data class FormatCategory(
    val label: String,
    val mediaType: String,
    val items: List<MediaFormat>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatPickerSheet(
    visible: Boolean,
    data: ResolveResponse,
    initialMediaType: String = "video",
    onDismiss: () -> Unit,
    onSelect: (MediaFormat, String) -> Unit
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val categories = remember(data) {
        buildList {
            if (data.video.isNotEmpty()) add(FormatCategory("Video", "video", data.video))
            if (data.audio.isNotEmpty()) add(FormatCategory("Audio", "audio", data.audio))
            if (data.image.isNotEmpty()) add(FormatCategory("Image", "image", data.image))
            if (data.archive.isNotEmpty()) add(FormatCategory("Archives", "archive", data.archive))
            if (data.document.isNotEmpty()) add(FormatCategory("Documents", "document", data.document))
            if (data.code.isNotEmpty()) add(FormatCategory("Code", "code", data.code))
            if (data.text.isNotEmpty()) add(FormatCategory("Text", "text", data.text))
            if (data.other.isNotEmpty()) add(FormatCategory("Other", "other", data.other))
        }
    }

    var filter by rememberSaveable { mutableStateOf(initialMediaType) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PluckColors.Slate950,
        scrimColor = PluckColors.Slate950.copy(alpha = 0.72f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .background(PluckColors.Slate700, RoundedCornerShape(2.dp))
                )
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SheetHeader(data = data, onClose = onDismiss)
            if (categories.isEmpty()) {
                EmptyFormats()
            } else {
                FilterRow(
                    categories = categories,
                    selected = filter,
                    onSelect = { filter = it }
                )
                Spacer(Modifier.height(8.dp))
                FormatList(
                    categories = categories,
                    filter = filter,
                    onSelect = { format, mediaType ->
                        onSelect(format, mediaType)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun SheetHeader(data: ResolveResponse, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = data.title.ifBlank { "Choose a format" },
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = PluckColors.Slate100,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = data.platform.uppercase().ifBlank { "WEB" },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PluckColors.Emerald400,
                    letterSpacing = 1.sp
                )
                if (data.durationSec > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "· ${Formatters.formatDuration(data.durationSec)}",
                        fontSize = 11.sp,
                        color = PluckColors.Slate500
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "· ${data.totalFormatCount} formats",
                    fontSize = 11.sp,
                    color = PluckColors.Slate500
                )
            }
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close",
                tint = PluckColors.Slate400
            )
        }
    }
}

@Composable
private fun EmptyFormats() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No formats available",
            fontSize = 13.sp,
            color = PluckColors.Slate500
        )
    }
}

@Composable
private fun FilterRow(
    categories: List<FormatCategory>,
    selected: String,
    onSelect: (String) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChipView(
                label = "All",
                count = categories.sumOf { it.items.size },
                isSelected = selected == "all",
                onClick = { onSelect("all") }
            )
        }
        items(categories) { cat ->
            FilterChipView(
                label = cat.label,
                count = cat.items.size,
                isSelected = selected == cat.mediaType,
                onClick = { onSelect(cat.mediaType) }
            )
        }
    }
}

@Composable
private fun FilterChipView(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) PluckColors.Emerald500.copy(alpha = 0.15f) else PluckColors.Slate900
    val borderColor = if (isSelected) PluckColors.Emerald500 else PluckColors.Slate800
    val textColor = if (isSelected) PluckColors.Emerald400 else PluckColors.Slate300

    Row(
        modifier = Modifier
            .background(bg, RoundedCornerShape(20.dp))
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = textColor
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = count.toString(),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = if (isSelected) PluckColors.Emerald400 else PluckColors.Slate500
        )
    }
}

@Composable
private fun FormatList(
    categories: List<FormatCategory>,
    filter: String,
    onSelect: (MediaFormat, String) -> Unit
) {
    val visible = remember(categories, filter) {
        if (filter == "all") {
            categories.flatMap { cat -> cat.items.map { it to cat.mediaType } }
        } else {
            categories
                .filter { it.mediaType == filter }
                .flatMap { cat -> cat.items.map { it to cat.mediaType } }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(visible) { pair ->
            SheetFormatRow(
                format = pair.first,
                mediaType = pair.second,
                onClick = { onSelect(pair.first, pair.second) }
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SheetFormatRow(
    format: MediaFormat,
    mediaType: String,
    onClick: () -> Unit
) {
    Surface(
        color = PluckColors.Slate900,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = format.quality.ifBlank {
                            format.ext.uppercase().ifBlank { "Format" }
                        },
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
                                .background(PluckColors.Slate800, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = mediaType.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = PluckColors.Slate500,
                        letterSpacing = 0.8.sp
                    )
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
            if (format.sizeBytes > 0) {
                Text(
                    text = Formatters.humanBytes(format.sizeBytes),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = PluckColors.Slate400
                )
            }
        }
    }
}
