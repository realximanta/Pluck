package xyz.ximanta.pluck.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Plan(
    val id: String = "",
    val name: String = "",
    val amount: Int = 0,
    val durationDays: Int = 0,
    val description: String = ""
)

@Serializable
data class QRRequest(
    val planId: String,
    val name: String
)

@Serializable
data class QRResponse(
    val planId: String = "",
    val planName: String = "",
    val baseAmount: Int = 0,
    val finalAmount: String = "",
    val qrImageBase64: String = "",
    val expiresIn: Int = 0
)

@Serializable
data class VerifyRequest(
    val finalAmount: String
)

@Serializable
data class VerifyResponse(
    val verified: Boolean = false,
    val status: String = "",
    val key: String? = null,
    val planName: String? = null,
    val expiresAt: Long = 0L,
    val received: String? = null,
    val expected: String? = null,
    val message: String? = null,
    val retryAfterSeconds: Int = 0
)

@Serializable
data class ResolveRequest(
    val url: String
)

@Serializable
data class MediaFormat(
    val formatId: String = "",
    val quality: String = "",
    val ext: String = "",
    val sizeBytes: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val bitrate: Int = 0,
    val hasVideo: Boolean = false,
    val hasAudio: Boolean = false,
    @SerialName("class") val klass: String = "",
    val filename: String = "",
    val directUrl: String = "",
    val contentType: String = "",
    val note: String = ""
)

@Serializable
data class ResolveResponse(
    val url: String = "",
    val platform: String = "",
    val title: String = "",
    val thumbnail: String = "",
    val durationSec: Int = 0,
    val uploader: String = "",
    val method: String = "",
    val video: List<MediaFormat> = emptyList(),
    val audio: List<MediaFormat> = emptyList(),
    val image: List<MediaFormat> = emptyList(),
    val archive: List<MediaFormat> = emptyList(),
    val document: List<MediaFormat> = emptyList(),
    val code: List<MediaFormat> = emptyList(),
    val text: List<MediaFormat> = emptyList(),
    val other: List<MediaFormat> = emptyList()
) {
    val hasAnyFormats: Boolean
        get() = video.isNotEmpty() || audio.isNotEmpty() || image.isNotEmpty() ||
                archive.isNotEmpty() || document.isNotEmpty() || code.isNotEmpty() ||
                text.isNotEmpty() || other.isNotEmpty()

    val totalFormatCount: Int
        get() = video.size + audio.size + image.size + archive.size +
                document.size + code.size + text.size + other.size
}

@Serializable
data class FetchRequest(
    val url: String = "",
    val formatId: String? = null,
    val directUrl: String? = null,
    val mediaType: String = "other",
    val mergeAudio: Boolean = false,
    val filename: String? = null,
    val title: String? = null,
    val platform: String? = null,
    val thumbnail: String? = null,
    val quality: String? = null,
    val ext: String? = null,
    val durationSec: Int = 0
)

@Serializable
data class HistoryEntry(
    val id: String = "",
    val url: String = "",
    val platform: String = "",
    val title: String = "",
    val thumbnail: String = "",
    val mediaType: String = "",
    val quality: String = "",
    val fetchedAt: Long = 0L,
    val sizeBytes: Long = 0L,
    val durationSec: Int = 0
)

@Serializable
data class HistoryResponse(
    val entries: List<HistoryEntry> = emptyList(),
    val count: Int = 0,
    val hasMore: Boolean = false
)
