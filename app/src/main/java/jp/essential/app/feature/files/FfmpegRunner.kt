package jp.essential.app.feature.files

import android.content.Context
import android.os.Build
import android.system.Os
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File

object FfmpegRunner {
    fun execute(context: Context, arguments: List<String>) {
        if (Build.SUPPORTED_ABIS.firstOrNull() in setOf("arm64-v8a", "x86_64")) {
            val session = FFmpegKit.executeWithArguments(arguments.toTypedArray())
            if (!ReturnCode.isSuccess(session.returnCode)) {
                error("FFmpeg処理に失敗しました: ${session.allLogsAsString.takeLast(600)}")
            }
            return
        }
        executeBundled(context, arguments)
    }

    private fun executeBundled(context: Context, arguments: List<String>) {
        YoutubeDL.getInstance().init(context.applicationContext)
        FFmpeg.getInstance().init(context.applicationContext)
        val nativeDirectory = context.applicationInfo.nativeLibraryDir
        val packagesDirectory = File(context.noBackupFilesDir, "youtubedl-android/packages")
        val pythonLibraryDirectory = File(packagesDirectory, "python/usr/lib")
        val ffmpegLibraryDirectory = File(packagesDirectory, "ffmpeg/usr/lib")
        val quickJsLibraryDirectory = File(packagesDirectory, "quickjs/usr/lib")
        val executable = File(nativeDirectory, "libffmpeg.so")
        check(executable.exists()) { "FFmpeg実行ファイルが見つかりません" }
        ensureExpatCompatibilityLink(ffmpegLibraryDirectory)
        val process = ProcessBuilder(listOf(executable.absolutePath) + arguments)
            .redirectErrorStream(true)
            .apply {
                environment()["LD_LIBRARY_PATH"] = listOf(
                    nativeDirectory,
                    pythonLibraryDirectory.absolutePath,
                    ffmpegLibraryDirectory.absolutePath,
                    quickJsLibraryDirectory.absolutePath,
                ).joinToString(":")
            }
            .start()
        val log = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode != 0) error("FFmpeg処理に失敗しました: ${log.takeLast(600)}")
    }

    private fun ensureExpatCompatibilityLink(libraryDirectory: File) {
        val compatibilityName = File(libraryDirectory, "libexpat.so.1")
        if (compatibilityName.exists()) return
        val systemLibrary = listOf(File("/system/lib64/libexpat.so"), File("/system/lib/libexpat.so"))
            .firstOrNull(File::exists)
            ?: error("AndroidのXML互換ライブラリが見つかりません")
        runCatching { Os.symlink(systemLibrary.absolutePath, compatibilityName.absolutePath) }
            .getOrElse { error -> throw IllegalStateException("FFmpeg互換リンクを準備できませんでした", error) }
    }
}
