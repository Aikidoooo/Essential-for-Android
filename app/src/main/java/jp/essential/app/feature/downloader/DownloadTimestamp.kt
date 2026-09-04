package jp.essential.app.feature.downloader

import android.content.Context
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import jp.essential.app.feature.files.FfmpegRunner

internal object DownloadTimestamp {
    /** 撮影日時と作成日時を保存時刻へそろえる。動画・音声は再エンコードしない。 */
    fun stamp(context: Context, source: File, timeMillis: Long): File {
        val instant = Instant.ofEpochMilli(timeMillis)
        val extension = source.extension.lowercase(Locale.ROOT)
        if (extension in setOf("jpg", "jpeg", "png")) {
            val date = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ROOT).withZone(ZoneOffset.UTC).format(instant)
            val subseconds = (timeMillis % 1000).toString().padStart(3, '0')
            ExifInterface(source).apply {
                for (tag in listOf(ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED)) setAttribute(tag, date)
                for (tag in listOf(ExifInterface.TAG_OFFSET_TIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_OFFSET_TIME_DIGITIZED)) setAttribute(tag, "+00:00")
                for (tag in listOf(ExifInterface.TAG_SUBSEC_TIME, ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, ExifInterface.TAG_SUBSEC_TIME_DIGITIZED)) setAttribute(tag, subseconds)
                saveAttributes()
            }
        } else if (extension in setOf("mp4", "mov", "mp3", "m4a")) {
            val output = File(context.cacheDir, "essential-dated-${UUID.randomUUID()}.$extension")
            try {
                val date = instant.toString()
                val arguments = mutableListOf("-i", source.absolutePath, "-map", "0", "-c", "copy",
                    "-metadata", "creation_time=$date", "-metadata:s", "creation_time=$date",
                    "-metadata", "date=$date", "-metadata", "year=${instant.atOffset(ZoneOffset.UTC).year}")
                if (extension in setOf("mp4", "mov", "m4a")) {
                    arguments += listOf("-metadata", "com.apple.quicktime.creationdate=$date", "-movflags", "+faststart")
                }
                arguments += listOf("-y", output.absolutePath)
                FfmpegRunner.execute(context, arguments)
                check(output.length() > 0) { "保存日時を書き込めませんでした" }
                check(output.setLastModified(timeMillis)) { "ファイル日時を設定できませんでした" }
                return output
            } catch (error: Exception) {
                output.delete()
                throw error
            }
        }
        // 生AACなど標準の撮影日時タグを持たない形式も、ファイル更新日時は統一する。
        check(source.setLastModified(timeMillis)) { "ファイル日時を設定できませんでした" }
        return source
    }
}
