package jp.essential.app.feature.files

import android.net.Uri

enum class ReferencedMediaType(val label: String) {
    Image("画像"),
    Video("動画"),
    Audio("音声"),
}

data class ReferencedFile(
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val type: ReferencedMediaType,
    val sizeBytes: Long,
    val durationMillis: Long = 0L,
    val frameRate: Float = 0f,
)

enum class GifPreset(val label: String, val fps: Int, val width: Int) {
    Compact("軽量", 8, 480),
    Standard("標準", 12, 720),
    Smooth("なめらか", 20, 960),
}
