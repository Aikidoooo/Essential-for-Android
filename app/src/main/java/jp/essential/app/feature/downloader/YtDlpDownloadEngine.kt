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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject

class YtDlpDownloadEngine(private val context: Context) {
    private val previewSlots = Semaphore(3)
    private val previewCache = object : android.util.LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    suspend fun preview(candidate: ImageCandidate): Bitmap? = withContext(Dispatchers.IO) {
        val url = candidate.url
        previewCache.get(url)?.let { return@withContext it }
        previewSlots.withPermit {
            previewCache.get(url)?.let { return@withPermit it }
            // 一覧では軽量版を読み、保存時だけオリジナル画像を取得する。
            val previewUrl = if (runCatching { URI(url).host }.getOrNull() == "pbs.twimg.com") url.replace("name=orig", "name=small") else url
            val source = downloadCandidateToCache(candidate.copy(url = previewUrl), 0)
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(source.absolutePath, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 640) sample *= 2
                BitmapFactory.decodeFile(source.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
                    ?.also { previewCache.put(url, it) }
            } finally { source.delete() }
        }
    }
    suspend fun analyzeImages(url: String): Result<List<ImageCandidate>> = withContext(Dispatchers.IO) {
        runCatching {
            val safeUrl = validatePublicUrl(url)
            val discovered = ImageDiscovery().discover(safeUrl)
            if (discovered.isNotEmpty()) return@runCatching discovered
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
                .ifEmpty { error("このURLから選択可能な画像を取得できませんでした") }
        }.onFailure { if (it is CancellationException) throw it }
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
        addOption("-f", VideoDownloadPolicy.format(selection, usesMaintainedFfmpegKit()))
        // 欠落した断片を飛ばして成功扱いにしない。
        addOption("--abort-on-unavailable-fragments")
        addOption("--fragment-retries", "10")
        if (usesMaintainedFfmpegKit()) {
            addOption("--abort-on-error")
            return
        }
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
        val inputs = stagingDirectory.listFiles().orEmpty().filter { it.isFile && it.length() > 0L && it.extension !in setOf("part", "ytdl", "json") }
        val video = inputs.filter { hasTrack(it, MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) }.maxByOrNull(::durationMillis)
            ?: error("映像ストリームを取得できませんでした")
        val embeddedAudio = hasTrack(video, MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
        val audio = if (embeddedAudio) null else inputs.firstOrNull {
            it != video && hasTrack(it, MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) &&
                !hasTrack(it, MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
        }
        if (embeddedAudio && video.extension.equals(selection.videoFormat.extension, ignoreCase = true)) return video
        val output = File(stagingDirectory, "Essential-video-${System.currentTimeMillis()}.${selection.videoFormat.extension}")
        val inputArguments = buildList {
            addAll(listOf("-fflags", "+genpts", "-i", video.absolutePath))
            if (audio != null) addAll(listOf("-i", audio.absolutePath))
            addAll(listOf("-map", "0:v:0"))
            addAll(listOf("-map", if (audio != null) "1:a:0" else "0:a:0?"))
        }
        val copyArguments = inputArguments + listOf("-c", "copy", "-movflags", "+faststart", "-y", output.absolutePath)
        runCatching { FfmpegRunner.execute(context, copyArguments) }
            .getOrElse {
                output.delete()
                FfmpegRunner.execute(
                    context,
                    inputArguments + listOf(
                        "-c:v", "mpeg4",
                        "-c:a", "aac",
                        "-movflags", "+faststart",
                        "-y", output.absolutePath,
                    ),
                )
            }
        check(output.exists() && output.length() > 0L) { "動画と音声を結合できませんでした" }
        check(hasTrack(output, MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)) { "出力に映像がありません" }
        check(durationMillis(output) + 500 >= durationMillis(video)) { "映像が途中で切れたため保存を中止しました" }
        if (embeddedAudio || audio != null) check(hasTrack(output, MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)) { "出力に音声がありません" }
        inputs.filter { it != output }.forEach(File::delete)
        return output
    }

    private fun hasTrack(file: File, metadataKey: Int): Boolean = runCatching {
        jp.essential.app.core.withMetadataRetriever { retriever ->
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(metadataKey) == "yes"
        }
    }.getOrDefault(false)

    private fun durationMillis(file: File): Long = runCatching {
        jp.essential.app.core.withMetadataRetriever { retriever ->
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        }
    }.getOrDefault(0L)

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
            val source = downloadCandidateToCache(candidate, index)
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

    private fun downloadCandidateToCache(candidate: ImageCandidate, index: Int): File {
        var failure: Exception? = null
        for (url in (listOf(candidate.url) + candidate.alternateUrls).distinct()) {
            try { return downloadImageToCache(url, index) }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                failure = error
            }
        }
        throw failure ?: IllegalStateException("画像URLがありません")
    }

    private fun downloadImageToCache(url: String, index: Int): File {
        validatePublicUrl(url)
        val output = File(context.cacheDir, "essential-image-${UUID.randomUUID()}-$index.source")
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 45_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Essential/1.0")
        val host = URI(url).host.orEmpty().lowercase()
        if (host == "tiktokcdn.com" || host.endsWith(".tiktokcdn.com")) {
            connection.setRequestProperty("Referer", "https://www.tiktok.com/")
        }
        if (host == "cdninstagram.com" || host.endsWith(".cdninstagram.com")) {
            connection.setRequestProperty("Referer", "https://www.instagram.com/")
        }
        try {
            check(connection.responseCode == 200) { "画像を取得できません（HTTP ${connection.responseCode}）" }
            connection.inputStream.use { input ->
                FileOutputStream(output).use { stream ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= 64L * 1024 * 1024) { "画像が64MBを超えています" }
                        stream.write(buffer, 0, count)
                    }
                }
            }
            check(output.length() > 0L) { "画像データが空でした" }
            return output
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally { connection.disconnect() }
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

    internal fun publishFile(source: File, mimeType: String): String {
        val savedAt = System.currentTimeMillis()
        val datedSource = DownloadTimestamp.stamp(context, source, savedAt)
        try {
            return publishDatedFile(datedSource, source.name, mimeType, savedAt)
        } finally {
            if (datedSource != source) datedSource.delete()
        }
    }

    private fun publishDatedFile(source: File, requestedName: String, mimeType: String, savedAt: Long): String {
        val safeName = requestedName.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(180)
            .ifBlank { "Essential-${System.currentTimeMillis()}.${source.extension}" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Essential")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                if (mimeType.startsWith("image/") || mimeType.startsWith("video/")) put(MediaStore.MediaColumns.DATE_TAKEN, savedAt)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStoreに保存先を作成できませんでした")
            try {
                context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                    source.inputStream().use { input -> input.copyTo(output) }
                } ?: error("保存先を開けませんでした")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                // DATE_ADDED/DATE_MODIFIEDはシステム管理。撮影日時は埋め込み情報とも一致させる。
                if (mimeType.startsWith("image/") || mimeType.startsWith("video/")) values.put(MediaStore.MediaColumns.DATE_TAKEN, savedAt)
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
            val destination = source.copyTo(uniqueFile(directory, safeName), overwrite = false)
            check(destination.setLastModified(savedAt)) { "保存したファイルの日時を設定できませんでした" }
            android.media.MediaScannerConnection.scanFile(context, arrayOf(destination.absolutePath), arrayOf(mimeType), null)
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
                id = url,
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
