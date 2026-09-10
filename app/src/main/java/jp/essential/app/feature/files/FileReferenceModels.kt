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
    val widthPixels: Int = 0,
    val heightPixels: Int = 0,
)

/** 壊れたメタデータでもレイアウトを破綻させないプレビュー縦横比。 */
internal fun adaptivePreviewAspectRatio(widthPixels: Int, heightPixels: Int): Float =
    (widthPixels.coerceAtLeast(1).toFloat() / heightPixels.coerceAtLeast(1)).coerceIn(0.45f, 2.4f)

internal fun isPortraitPreview(aspectRatio: Float): Boolean = aspectRatio < 0.9f

/** 回転メタデータを反映した、画面に表示すべき動画寸法。 */
internal fun displayVideoDimensions(encodedWidth: Int, encodedHeight: Int, rotationDegrees: Int): Pair<Int, Int> =
    if (rotationDegrees.mod(180) == 0) encodedWidth to encodedHeight else encodedHeight to encodedWidth

enum class GifPreset(val label: String, val fps: Int, val width: Int) {
    Compact("軽量", 8, 480),
    Standard("標準", 12, 720),
    Smooth("なめらか", 20, 960),
}
