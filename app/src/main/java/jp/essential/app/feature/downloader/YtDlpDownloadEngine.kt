package jp.essential.app.feature.downloader

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID
import jp.essential.app.feature.files.FfmpegRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class YtDlpDownloadEngine(private val context: Context) {
    suspend fun analyzeImages(url: String): Result<List<ImageCandidate>> = withContext(Dispatchers.IO) {
        runCatching {
            val safeUrl = validatePublicUrl(url)
            initializeLibraries()
            val request = YoutubeDLRequest(safeUrl).apply {
                addOption("--dump-single-json")
                addOption("--skip-download")
                addOption("--no-playlist")
            }
            val response = YoutubeDL.getInstance().execute(request)
            val json = extractJsonObject(response.out)
            buildList {
                collectThumbnails(json.optJSONArray("thumbnails"), "メイン", this)
                val entries = json.optJSONArray("entries")
                if (entries != null) {
                    for (index in 0 until entries.length()) {
                        val entry = entries.optJSONObject(index) ?: continue
                        val title = entry.optString("title").ifBlank { "画像 ${index + 1}" }
                        collectThumbnails(entry.optJSONArray("thumbnails"), title, this)
                    }
                }
            }
                .distinctBy(ImageCandidate::url)
                .sortedWith(compareByDescending<ImageCandidate> { (it.width ?: 0) * (it.height ?: 0) })
                .take(50)
                .ifEmpty { error("このURLから選択可能な画像を取得できませんでした") }
        }
    }

    suspend fun download(
        selection: DownloaderSelection,
        onState: (DownloadState) -> Unit,
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            validatePublicUrl(selection.url)
            when (selection.mediaType) {
                DownloadMediaType.Image -> downloadImages(selection, onState)
                DownloadMediaType.Video,
                DownloadMediaType.Audio,
                -> downloadWithYtDlp(selection, onState)
            }
        }.onFailure { error ->
            onState(DownloadState.Failed(error.message ?: "ダウンロードに失敗しました"))
        }
    }

    private fun initializeLibraries() {
        YoutubeDL.getInstance().init(context.applicationContext)
        FFmpeg.getInstance().init(context.applicationContext)
    }

    private fun downloadWithYtDlp(
        selection: DownloaderSelection,
        onState: (DownloadState) -> Unit,
    ): List<String> {
        initializeLibraries()
        val stagingDirectory = File(context.cacheDir, "essential-download-${UUID.randomUUID()}").apply {
            mkdirs()
        }
        try {
            onState(DownloadState.Preparing("yt-dlpとFFmpegを準備しています"))
            val request = YoutubeDLRequest(selection.url).apply {
                addOption("--no-playlist")
                addOption("--no-part")
                addOption("--restrict-filenames")
                val template = if (selection.mediaType == DownloadMediaType.Video && usesMaintainedFfmpegKit()) {
                    "%(title).140B-%(id)s-%(format_id)s.%(ext)s"
                } else {
                    "%(title).160B-%(id)s.%(ext)s"
                }
                addOption("-o", File(stagingDirectory, template).absolutePath)
                when (selection.mediaType) {
                    DownloadMediaType.Video -> addVideoOptions(selection)
                    DownloadMediaType.Audio -> addAudioOptions(selection)
                    DownloadMediaType.Image -> Unit
                }
            }
            val processId = "essential-${UUID.randomUUID()}"
            YoutubeDL.getInstance().execute(
                request,
                processId,
                { progress: Float, etaSeconds: Long, _: String ->
                    val safeProgress = progress.takeIf { it in 0f..100f }
                    val eta = etaSeconds.takeIf { it > 0 }?.let { "・残り約${it}秒" }.orEmpty()
                    onState(DownloadState.Running(safeProgress, "取得・変換中$eta"))
                },
            )

            val stagedFiles = if (selection.mediaType == DownloadMediaType.Video && usesMaintainedFfmpegKit()) {
                listOf(mergeDownloadedStreams(stagingDirectory, selection))
            } else {
                stagingDirectory.listFiles().orEmpty().toList()
            }
            val completedFiles = stagedFiles
                .orEmpty()
                .filter { it.isFile && !it.name.endsWith(".part") && it.length() > 0L }
                .let { files ->
                    if (selection.mediaType == DownloadMediaType.Video) {
                        val expectedExtension = selection.videoFormat.extension
                        val finalVideos = files.filter { it.extension.equals(expectedExtension, ignoreCase = true) }
                        if (finalVideos.size != 1) {
                            error(
                                if (finalVideos.isEmpty()) {
                                    "動画と音声の結合済みファイルを生成できませんでした"
                                } else {
                                    "結合済み動画が複数生成されたため、安全に保存できませんでした"
                                },
                            )
                        }
                        finalVideos
                    } else {
                        files
                    }
                }
            if (completedFiles.isEmpty()) {
                error("yt-dlpは完了しましたが、保存できるファイルが生成されませんでした")
            }
            onState(DownloadState.Preparing("端末のDownload/Essentialへ保存しています"))
            val savedNames = completedFiles.map { publishFile(it, mimeTypeFor(it.extension)) }
            onState(DownloadState.Completed(savedNames))
            return savedNames
        } finally {
            stagingDirectory.deleteRecursively()
        }
    }

    private fun YoutubeDLRequest.addVideoOptions(selection: DownloaderSelection) {
        val height = selection.videoResolution.maxHeight
        val fps = selection.frameRate.maxFps
        val combinedFallback = "b[height<=?$height][fps<=?$fps]"
        val separateStreams = "bv*[height<=?$height][fps<=?$fps]+ba"
        if (usesMaintainedFfmpegKit()) {
            val videoOnly = if (selection.videoFormat == VideoFormat.Mp4) {
                "bv*[ext=mp4][height<=?$height][fps<=?$fps]"
            } else {
                "bv*[height<=?$height][fps<=?$fps]"
            }
            val audioOnly = if (selection.videoFormat == VideoFormat.Mp4) "ba[ext=m4a]/ba" else "ba"
            addOption("-f", "$videoOnly,$audioOnly")
            addOption("--abort-on-error")
            return
        }
        addOption(
            "-f",
            if (selection.videoFormat == VideoFormat.Mp4) {
                "bv*[ext=mp4][height<=?$height][fps<=?$fps]+ba[ext=m4a]/b[ext=mp4][height<=?$height][fps<=?$fps]/$separateStreams/$combinedFallback"
            } else {
                "$separateStreams/$combinedFallback"
            },
        )
        addOption("--merge-output-format", selection.videoFormat.extension)
        if (selection.videoFormat == VideoFormat.Mp4) {
            addOption("--remux-video", "mp4")
        } else {
            addOption("--recode-video", "mov")
        }
        addOption("--no-keep-video")
        addOption("--no-keep-fragments")
        addOption("--abort-on-error")
    }

    internal fun mergeDownloadedStreams(stagingDirectory: File, selection: DownloaderSelection): File {
        val inputs = stagingDirectory.listFiles().orEmpty().filter { it.isFile && it.length() > 0L }
        val video = inputs.firstOrNull { hasTrack(it, MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) }
            ?: error("映像ストリームを取得できませんでした")
        val audio = inputs.firstOrNull {
            it != video && hasTrack(it, MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
        } ?: error("音声ストリームを取得できませんでした")
        val output = File(stagingDirectory, "Essential-video-${System.currentTimeMillis()}.${selection.videoFormat.extension}")
        val copyArguments = listOf(
            "-i", video.absolutePath,
            "-i", audio.absolutePath,
            "-map", "0:v:0",
            "-map", "1:a:0",
            "-c", "copy",
            "-shortest",
            "-y", output.absolutePath,
        )
        runCatching { FfmpegRunner.execute(context, copyArguments) }
            .getOrElse {
                output.delete()
                FfmpegRunner.execute(
                    context,
                    listOf(
                        "-i", video.absolutePath,
                        "-i", audio.absolutePath,
                        "-map", "0:v:0",
                        "-map", "1:a:0",
                        "-c:v", "mpeg4",
                        "-c:a", "aac",
                        "-shortest",
                        "-y", output.absolutePath,
                    ),
                )
            }
        check(output.exists() && output.length() > 0L) { "動画と音声を結合できませんでした" }
        inputs.filter { it != output }.forEach(File::delete)
        return output
    }

    private fun hasTrack(file: File, metadataKey: Int): Boolean = runCatching {
        jp.essential.app.core.withMetadataRetriever { retriever ->
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(metadataKey) == "yes"
        }
    }.getOrDefault(false)

    private fun usesMaintainedFfmpegKit(): Boolean =
        Build.SUPPORTED_ABIS.firstOrNull() in setOf("arm64-v8a", "x86_64")

    private fun YoutubeDLRequest.addAudioOptions(selection: DownloaderSelection) {
        addOption("-x")
        addOption("--audio-format", selection.audioFormat.extension)
        addOption("--audio-quality", "${selection.audioQuality.bitRate}K")
    }

    private fun downloadImages(
        selection: DownloaderSelection,
        onState: (DownloadState) -> Unit,
    ): List<String> {
        if (selection.selectedImages.isEmpty()) {
            error("保存する画像を1枚以上選択してください")
        }
        val savedNames = mutableListOf<String>()
        selection.selectedImages.forEachIndexed { index, candidate ->
            onState(
                DownloadState.Running(
                    progress = (index.toFloat() / selection.selectedImages.size) * 100f,
                    message = "画像 ${index + 1}/${selection.selectedImages.size} を処理しています",
                ),
            )
            val source = downloadImageToCache(candidate.url, index)
            try {
                val output = transcodeImage(
                    source = source,
                    index = index,
                    quality = selection.imageQuality,
                    format = selection.imageFormat,
                )
                try {
                    savedNames += publishFile(output, mimeTypeFor(output.extension))
                } finally {
                    output.delete()
                }
            } finally {
                source.delete()
            }
        }
        onState(DownloadState.Completed(savedNames))
        return savedNames
    }

    private fun downloadImageToCache(url: String, index: Int): File {
        validatePublicUrl(url)
        val output = File(context.cacheDir, "essential-image-${UUID.randomUUID()}-$index.source")
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 45_000
        connection.instanceFollowRedirects = true
        connection.inputStream.use { input ->
            FileOutputStream(output).use(input::copyTo)
        }
        if (output.length() == 0L) {
            output.delete()
            error("画像データが空でした")
        }
        return output
    }

    private fun transcodeImage(
        source: File,
        index: Int,
        quality: ImageQuality,
        format: ImageFormat,
    ): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            error("画像を読み取れませんでした")
        }
        var sampleSize = 1
        while (
            bounds.outWidth / (sampleSize * 2) >= quality.maxLongEdge ||
            bounds.outHeight / (sampleSize * 2) >= quality.maxLongEdge
        ) {
            sampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeFile(
            source.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: error("画像の変換に必要なデータを読み取れませんでした")
        val longestEdge = maxOf(bitmap.width, bitmap.height)
        val scaled = if (longestEdge > quality.maxLongEdge) {
            val ratio = quality.maxLongEdge.toFloat() / longestEdge
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true,
            ).also { bitmap.recycle() }
        } else {
            bitmap
        }
        val output = File(
            context.cacheDir,
            "Essential-image-${index + 1}-${scaled.width}x${scaled.height}.${format.extension}",
        )
        FileOutputStream(output).use { stream ->
            val compressFormat = if (format == ImageFormat.Png) {
                Bitmap.CompressFormat.PNG
            } else {
                Bitmap.CompressFormat.JPEG
            }
            if (!scaled.compress(compressFormat, 95, stream)) {
                error("画像の書き出しに失敗しました")
            }
        }
        scaled.recycle()
        return output
    }

    private fun publishFile(source: File, mimeType: String): String {
        val safeName = source.name.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(180)
            .ifBlank { "Essential-${System.currentTimeMillis()}.${source.extension}" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Essential")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStoreに保存先を作成できませんでした")
            try {
                context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                } ?: error("保存先を開けませんでした")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            } catch (error: Throwable) {
                context.contentResolver.delete(uri, null, null)
                throw error
            }
        } else {
            @Suppress("DEPRECATION")
            val directory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Essential",
            ).apply { mkdirs() }
            source.copyTo(uniqueFile(directory, safeName), overwrite = false)
        }
        return safeName
    }

    private fun uniqueFile(directory: File, requestedName: String): File {
        var candidate = File(directory, requestedName)
        var suffix = 2
        while (candidate.exists()) {
            val base = requestedName.substringBeforeLast('.', requestedName)
            val extension = requestedName.substringAfterLast('.', "")
            candidate = File(directory, "$base-$suffix${if (extension.isBlank()) "" else ".$extension"}")
            suffix += 1
        }
        return candidate
    }

    private fun validatePublicUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull()
            ?: error("URLの形式が正しくありません")
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            error("httpまたはhttpsの公開URLを入力してください")
        }
        if (uri.userInfo != null) {
            error("認証情報を含むURLは使用できません")
        }
        return trimmed
    }

    private fun extractJsonObject(output: String): JSONObject {
        val jsonLine = output.lineSequence()
            .map(String::trim)
            .lastOrNull { it.startsWith("{") && it.endsWith("}") }
            ?: error("yt-dlpの画像情報を解析できませんでした")
        return JSONObject(jsonLine)
    }

    private fun collectThumbnails(
        thumbnails: JSONArray?,
        prefix: String,
        destination: MutableList<ImageCandidate>,
    ) {
        if (thumbnails == null) return
        for (index in 0 until thumbnails.length()) {
            val item = thumbnails.optJSONObject(index) ?: continue
            val url = item.optString("url")
            if (url.isBlank()) continue
            val width = item.optInt("width").takeIf { it > 0 }
            val height = item.optInt("height").takeIf { it > 0 }
            val size = if (width != null && height != null) "${width}×${height}" else "サイズ不明"
            destination += ImageCandidate(
                id = item.optString("id").ifBlank { "$prefix-$index" },
                url = url,
                width = width,
                height = height,
                label = "$prefix・$size",
            )
        }
    }

    private fun mimeTypeFor(extension: String): String = when (extension.lowercase()) {
        "mp4" -> "video/mp4"
        "mov" -> "video/quicktime"
        "mp3" -> "audio/mpeg"
        "aac", "m4a" -> "audio/aac"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        else -> "application/octet-stream"
    }
}
