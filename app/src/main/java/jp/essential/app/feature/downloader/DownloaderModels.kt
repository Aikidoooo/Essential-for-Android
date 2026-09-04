package jp.essential.app.feature.downloader

enum class DownloadMediaType(val label: String) {
    Video("動画"),
    Audio("音声"),
    Image("画像"),
}

enum class VideoResolution(val label: String, val maxHeight: Int) {
    P480("480p", 480),
    P720("720p", 720),
    P1080("1080p", 1080),
    P4K("4K", 2160),
}

enum class FrameRate(val label: String, val maxFps: Int) {
    Fps30("30fps", 30),
    Fps60("60fps", 60),
}

enum class VideoFormat(val label: String, val extension: String) {
    Mp4("MP4", "mp4"),
    Mov("MOV", "mov"),
}

enum class AudioQuality(val label: String, val bitRate: Int) {
    Low("64kbps", 64),
    Standard("128kbps", 128),
    High("192kbps", 192),
    VeryHigh("256kbps", 256),
    Maximum("320kbps", 320),
}

enum class AudioFormat(val label: String, val extension: String) {
    Mp3("MP3", "mp3"),
    Aac("AAC", "aac"),
}

enum class ImageQuality(val label: String, val maxLongEdge: Int) {
    Fhd("FHD", 1920),
    Wqhd("WQHD", 2560),
    P4K("4K", 3840),
    P8K("8K", 7680),
}

enum class ImageFormat(val label: String, val extension: String) {
    Png("PNG", "png"),
    Jpeg("JPEG", "jpg"),
}

data class ImageCandidate(
    val id: String,
    val url: String,
    val width: Int?,
    val height: Int?,
    val label: String,
)

data class DownloaderSelection(
    val url: String,
    val mediaType: DownloadMediaType,
    val videoResolution: VideoResolution = VideoResolution.P1080,
    val frameRate: FrameRate = FrameRate.Fps60,
    val videoFormat: VideoFormat = VideoFormat.Mp4,
    val audioQuality: AudioQuality = AudioQuality.High,
    val audioFormat: AudioFormat = AudioFormat.Mp3,
    val imageQuality: ImageQuality = ImageQuality.P4K,
    val imageFormat: ImageFormat = ImageFormat.Png,
    val selectedImages: List<ImageCandidate> = emptyList(),
)

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Preparing(val message: String) : DownloadState
    data class Running(val progress: Float?, val message: String) : DownloadState
    data class Completed(val savedNames: List<String>) : DownloadState
    data class Failed(val message: String) : DownloadState
}
