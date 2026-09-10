package jp.essential.app.feature.files

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class MediaFileEngine(private val context: Context) {
    fun inspect(uri: Uri): ReferencedFile {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri).orEmpty()
        var name = "Essential-file"
        var size = 0L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.getString(0) ?: name
                size = if (cursor.isNull(1)) 0L else cursor.getLong(1)
            }
        }
        val type = when {
            mime.startsWith("image/") -> ReferencedMediaType.Image
            mime.startsWith("video/") -> ReferencedMediaType.Video
            mime.startsWith("audio/") -> ReferencedMediaType.Audio
            else -> error("画像・動画・音声ファイルだけを参照できます")
        }
        var duration = 0L
        var frameRate = 0f
        var widthPixels = 0
        var heightPixels = 0
        if (type != ReferencedMediaType.Image) {
            jp.essential.app.core.withMetadataRetriever { retriever ->
                retriever.setDataSource(context, uri)
                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                if (type == ReferencedMediaType.Video) {
                    frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 0f
                    val encodedWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val encodedHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    val displayDimensions = displayVideoDimensions(encodedWidth, encodedHeight, rotation)
                    widthPixels = displayDimensions.first
                    heightPixels = displayDimensions.second
                }
            }
        }
        return ReferencedFile(uri, name, mime, type, size, duration, frameRate, widthPixels, heightPixels)
    }

    suspend fun compress(file: ReferencedFile, targetMegabytes: Int): String = withContext(Dispatchers.IO) {
        require(targetMegabytes in 1..500) { "目標サイズは1〜500MBで指定してください" }
        if (file.sizeBytes in 1..(targetMegabytes.toLong() * MEBIBYTE)) {
            return@withContext publishFile(copyToCache(file), mimeFor(file.name.substringAfterLast('.', "")))
        }
        when (file.type) {
            ReferencedMediaType.Image -> compressImage(file, targetMegabytes)
            ReferencedMediaType.Video -> compressVideo(file, targetMegabytes)
            ReferencedMediaType.Audio -> compressAudio(file, targetMegabytes)
        }
    }

    suspend fun removeBackground(file: ReferencedFile): String {
        require(file.type == ReferencedMediaType.Image) { "背景透過は画像だけで使用できます" }
        val input = InputImage.fromFilePath(context, file.uri)
        val options = SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
        val segmenter = SubjectSegmentation.getClient(options)
        val bitmap = try {
            suspendCancellableCoroutine { continuation ->
                segmenter.process(input)
                    .addOnSuccessListener { result ->
                        val foreground = result.foregroundBitmap
                        if (foreground != null) continuation.resume(foreground)
                        else continuation.resumeWithException(IllegalStateException("前景を認識できませんでした"))
                    }
                    .addOnFailureListener(continuation::resumeWithException)
            }
        } finally {
            segmenter.close()
        }
        val output = tempFile("background-removed", "png")
        try {
            FileOutputStream(output).use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "透過PNGを書き出せませんでした" }
            }
            bitmap.recycle()
            return publishFile(output, "image/png")
        } finally {
            output.delete()
        }
    }

    suspend fun extractFrame(file: ReferencedFile, positionMillis: Long): String = withContext(Dispatchers.IO) {
        require(file.type == ReferencedMediaType.Video) { "フレーム切り取りは動画だけで使用できます" }
        val source = copyToCache(file)
        val output = tempFile("frame-${positionMillis}ms", "png")
        try {
            runFfmpeg(listOf("-ss", seconds(positionMillis), "-i", source.absolutePath, "-frames:v", "1", "-compression_level", "2", "-y", output.absolutePath))
            publishFile(output, "image/png")
        } finally {
            source.delete()
            output.delete()
        }
    }

    /** プレイヤーの対応状況に依存せず、選択時刻の確認用フレームを小さく読み出す。 */
    suspend fun previewFrame(file: ReferencedFile, positionMillis: Long): Bitmap = withContext(Dispatchers.IO) {
        require(file.type == ReferencedMediaType.Video) { "プレビューは動画だけで使用できます" }
        val frame = jp.essential.app.core.withMetadataRetriever { retriever ->
            retriever.setDataSource(context, file.uri)
            retriever.getFrameAtTime(
                positionMillis.coerceAtLeast(0L) * 1_000L,
                MediaMetadataRetriever.OPTION_CLOSEST,
            )
        } ?: error("この時刻の映像を読み取れませんでした")
        val maxDimension = maxOf(frame.width, frame.height)
        if (maxDimension <= 1_280) return@withContext frame
        val scale = 1_280f / maxDimension
        val scaled = Bitmap.createScaledBitmap(
            frame,
            (frame.width * scale).toInt().coerceAtLeast(1),
            (frame.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        frame.recycle()
        scaled
    }

    suspend fun convertVideoToMp3(file: ReferencedFile): String = withContext(Dispatchers.IO) {
        require(file.type == ReferencedMediaType.Video) { "MP3変換は動画だけで使用できます" }
        val source = copyToCache(file)
        val output = tempFile("audio", "mp3")
        try {
            runFfmpeg(listOf("-i", source.absolutePath, "-vn", "-c:a", "libmp3lame", "-q:a", "2", "-y", output.absolutePath))
            publishFile(output, "audio/mpeg")
        } finally {
            source.delete()
            output.delete()
        }
    }

    suspend fun makeGif(file: ReferencedFile, preset: GifPreset): String = withContext(Dispatchers.IO) {
        require(file.type == ReferencedMediaType.Video) { "GIF化は動画だけで使用できます" }
        val source = copyToCache(file)
        val output = tempFile("${preset.label}-gif", "gif")
        val filter = "fps=${preset.fps},scale=${preset.width}:-1:flags=lanczos,split[s0][s1];[s0]palettegen=max_colors=256[p];[s1][p]paletteuse=dither=sierra2_4a"
        try {
            runFfmpeg(listOf("-i", source.absolutePath, "-filter_complex", filter, "-loop", "0", "-y", output.absolutePath))
            publishFile(output, "image/gif")
        } finally {
            source.delete()
            output.delete()
        }
    }

    suspend fun trimAudio(file: ReferencedFile, startMillis: Long, endMillis: Long): String = withContext(Dispatchers.IO) {
        require(file.type == ReferencedMediaType.Audio) { "音声切り取りは音声ファイルだけで使用できます" }
        require(endMillis > startMillis) { "終了地点は開始地点より後にしてください" }
        val source = copyToCache(file)
        val extension = file.name.substringAfterLast('.', "m4a").lowercase().takeIf { it in setOf("mp3", "m4a", "aac", "wav", "flac", "ogg") } ?: "m4a"
        val output = tempFile("trimmed-audio", extension)
        try {
            runFfmpeg(listOf("-ss", seconds(startMillis), "-to", seconds(endMillis), "-i", source.absolutePath, "-c", "copy", "-y", output.absolutePath))
            publishFile(output, mimeFor(extension))
        } finally {
            source.delete()
            output.delete()
        }
    }

    private fun compressImage(file: ReferencedFile, targetMegabytes: Int): String {
        val source = copyToCache(file)
        val output = tempFile("compressed-image", "jpg")
        try {
            val bitmap = BitmapFactory.decodeFile(source.absolutePath) ?: error("画像を読み取れませんでした")
            var working = bitmap
            var quality = 92
            val target = targetMegabytes.toLong() * MEBIBYTE
            while (true) {
                FileOutputStream(output).use { stream -> working.compress(Bitmap.CompressFormat.JPEG, quality, stream) }
                if (output.length() <= target || (quality <= 35 && working.width <= 960)) break
                if (quality > 35) {
                    quality -= 10
                } else {
                    val scaled = Bitmap.createScaledBitmap(working, (working.width * 0.82f).toInt(), (working.height * 0.82f).toInt(), true)
                    if (working !== bitmap) working.recycle()
                    working = scaled
                    quality = 82
                }
            }
            if (working !== bitmap) working.recycle()
            bitmap.recycle()
            return publishFile(output, "image/jpeg")
        } finally {
            source.delete()
            output.delete()
        }
    }

    private fun compressVideo(file: ReferencedFile, targetMegabytes: Int): String {
        val durationSeconds = (file.durationMillis / 1000.0).coerceAtLeast(1.0)
        // MPEG-4 Part 2 のビットレート変動と MP4 コンテナのオーバーヘッドを見込み、指定上限を超えないよう余裕を持たせる。
        val totalBitRate = ((targetMegabytes * MEBIBYTE * 8 * 0.82) / durationSeconds).toLong()
        val audioBitRate = minOf(128_000L, (totalBitRate * 0.18).toLong()).coerceAtLeast(48_000L)
        val videoBitRate = (totalBitRate - audioBitRate).coerceAtLeast(120_000L)
        val source = copyToCache(file)
        val output = tempFile("compressed-video", "mp4")
        try {
            runFfmpeg(
                listOf(
                    "-i", source.absolutePath, "-c:v", "mpeg4",
                    "-b:v", videoBitRate.toString(), "-maxrate", videoBitRate.toString(),
                    "-bufsize", (videoBitRate * 2).toString(), "-c:a", "aac", "-b:a", audioBitRate.toString(),
                    "-movflags", "+faststart", "-y", output.absolutePath,
                ),
            )
            return publishFile(output, "video/mp4")
        } finally {
            source.delete()
            output.delete()
        }
    }

    private fun compressAudio(file: ReferencedFile, targetMegabytes: Int): String {
        val durationSeconds = (file.durationMillis / 1000.0).coerceAtLeast(1.0)
        val bitRate = ((targetMegabytes * MEBIBYTE * 8 * 0.94) / durationSeconds).toLong().coerceIn(32_000L, 320_000L)
        val source = copyToCache(file)
        val output = tempFile("compressed-audio", "m4a")
        try {
            runFfmpeg(listOf("-i", source.absolutePath, "-vn", "-c:a", "aac", "-b:a", bitRate.toString(), "-y", output.absolutePath))
            return publishFile(output, "audio/mp4")
        } finally {
            source.delete()
            output.delete()
        }
    }

    private fun runFfmpeg(arguments: List<String>) {
        FfmpegRunner.execute(context, arguments)
    }

    private fun copyToCache(file: ReferencedFile): File {
        val extension = file.name.substringAfterLast('.', "bin").replace(Regex("[^A-Za-z0-9]"), "").ifBlank { "bin" }
        val output = tempFile("source", extension)
        context.contentResolver.openInputStream(file.uri)?.use { input ->
            FileOutputStream(output).use(input::copyTo)
        } ?: error("参照ファイルを開けませんでした")
        check(output.length() > 0L) { "参照ファイルが空です" }
        return output
    }

    private fun publishFile(source: File, mimeType: String): String {
        check(source.exists() && source.length() > 0L) { "出力ファイルが生成されませんでした" }
        val displayName = source.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Essential")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("保存先を作成できませんでした")
            try {
                context.contentResolver.openOutputStream(uri, "w")?.use { output -> source.inputStream().use { it.copyTo(output) } }
                    ?: error("保存先を開けませんでした")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            } catch (error: Throwable) {
                context.contentResolver.delete(uri, null, null)
                throw error
            }
        } else {
            val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Essential").apply { mkdirs() }
            source.copyTo(File(directory, displayName), overwrite = true)
        }
        return displayName
    }

    private fun tempFile(label: String, extension: String) = File(context.cacheDir, "Essential-$label-${UUID.randomUUID()}.$extension")
    private fun seconds(millis: Long) = "%.3f".format(java.util.Locale.US, millis / 1000.0)
    private fun mimeFor(extension: String) = when (extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "mp4" -> "video/mp4"
        "mov" -> "video/quicktime"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        else -> "application/octet-stream"
    }

    companion object {
        private const val MEBIBYTE = 1024L * 1024L
    }
}
