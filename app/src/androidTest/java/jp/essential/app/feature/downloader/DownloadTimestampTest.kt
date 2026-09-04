package jp.essential.app.feature.downloader

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import jp.essential.app.core.withMetadataRetriever
import jp.essential.app.feature.files.FfmpegRunner
import java.io.File
import java.time.Instant
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class DownloadTimestampTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val timestamp = Instant.parse("2026-09-04T13:00:00.123Z").toEpochMilli()

    private fun image(file: File, png: Boolean) {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, it) }
        } finally { bitmap.recycle() }
    }

    @Test fun jpegAndPngReceiveDownloadTimeWithTimezone() {
        for (extension in listOf("jpg", "png")) {
            val file = File(context.cacheDir, "timestamp-${System.nanoTime()}.$extension")
            try {
                image(file, extension == "png")
                ExifInterface(file).apply {
                    setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2001:01:01 00:00:00")
                    saveAttributes()
                }
                DownloadTimestamp.stamp(context, file, timestamp)
                val exif = ExifInterface(file)
                assertEquals("2026:09:04 13:00:00", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
                assertEquals("+00:00", exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL))
                assertEquals("123", exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL))
                assertTrue(kotlin.math.abs(file.lastModified() - timestamp) < 1000)
            } finally { file.delete() }
        }
    }

    @Test fun videoCreationTimeChangesWithoutLosingTracksOrDuration() {
        for (extension in listOf("mp4", "mov")) {
            val source = File(context.cacheDir, "timestamp-video-${System.nanoTime()}.$extension")
            var output: File? = null
            try {
                FfmpegRunner.execute(context, listOf("-f", "lavfi", "-i", "testsrc2=s=160x120:r=15", "-f", "lavfi", "-i", "sine=frequency=440", "-t", "3", "-c:v", "mpeg4", "-c:a", "aac", "-metadata", "creation_time=2001-01-01T00:00:00Z", "-y", source.absolutePath))
                output = DownloadTimestamp.stamp(context, source, timestamp)
                withMetadataRetriever { retriever ->
                    retriever.setDataSource(output.absolutePath)
                    assertTrue(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE).orEmpty().startsWith("20260904T130000"))
                    assertEquals("yes", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                    assertEquals("yes", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
                    assertTrue(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() >= 2900)
                }
            } finally { source.delete(); output?.delete() }
        }
    }

    @Test fun publishedImageIsIndexedAtDownloadTime() {
        assumeTrue(Build.VERSION.SDK_INT >= 29)
        val name = "essential-timestamp-test-${System.nanoTime()}.jpg"
        val source = File(context.cacheDir, name)
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val before = System.currentTimeMillis()
        try {
            image(source, false)
            YtDlpDownloadEngine(context).publishFile(source, "image/jpeg")
            context.contentResolver.query(collection, arrayOf(MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.DATE_MODIFIED), "${MediaStore.MediaColumns.DISPLAY_NAME} = ?", arrayOf(name), null)!!.use { cursor ->
                assertTrue(cursor.moveToFirst())
                val after = System.currentTimeMillis()
                assertTrue(cursor.getLong(0) in (before - 1000)..after)
                assertTrue(cursor.getLong(1) in (before / 1000 - 1)..(after / 1000))
                assertTrue(cursor.getLong(2) in (before / 1000 - 1)..(after / 1000))
            }
        } finally {
            source.delete()
            // 今回作成した一意名の検証用画像だけを削除する。
            context.contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), "${MediaStore.MediaColumns.DISPLAY_NAME} = ?", arrayOf(name), null)?.use { cursor ->
                while (cursor.moveToNext()) context.contentResolver.delete(ContentUris.withAppendedId(collection, cursor.getLong(0)), null, null)
            }
        }
    }
}
