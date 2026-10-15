package jp.essential.app.feature.textscan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Path
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** 写真の向き補正後の座標を0〜1に正規化し、表示倍率に依存させない。 */
internal data class ScanPoint(val x: Float, val y: Float)
internal data class ScanRegion(val points: List<ScanPoint>) {
    fun isValid(): Boolean {
        if (points.size < 4 || points.any { !it.x.isFinite() || !it.y.isFinite() || it.x !in 0f..1f || it.y !in 0f..1f }) return false
        val area = points.indices.sumOf { index ->
            val next = points[(index + 1) % points.size]
            (points[index].x * next.y - next.x * points[index].y).toDouble()
        }
        return abs(area) / 2 > .0002
    }
}

internal const val SCAN_MAX_EDGE = 3072
internal const val SCAN_MAX_PIXELS = 6_000_000

internal fun scanDecodeSize(width: Int, height: Int): Pair<Int, Int> {
    require(width > 0 && height > 0)
    val ratio = min(1.0, min(SCAN_MAX_EDGE.toDouble() / max(width, height),
        sqrt(SCAN_MAX_PIXELS.toDouble() / (width.toDouble() * height))))
    return max(1, (width * ratio).toInt()) to max(1, (height * ratio).toInt())
}

/** 大きな画像のメモリを制限し、EXIFの回転・反転を表示とOCRで共通にする。IOで呼ぶ。 */
internal fun decodeScanPhoto(context: Context, uri: Uri): Bitmap {
    if (Build.VERSION.SDK_INT >= 28) return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        val target = scanDecodeSize(info.size.width, info.size.height)
        decoder.setTargetSize(target.first, target.second)
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    val target = scanDecodeSize(options.outWidth, options.outHeight)
    var sample = 1
    while (options.outWidth / sample > target.first || options.outHeight / sample > target.second) sample *= 2
    options.inJustDecodeBounds = false
    options.inSampleSize = sample
    options.inPreferredConfig = Bitmap.Config.ARGB_8888
    val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: error("画像を開けませんでした")
    val orientation = context.contentResolver.openInputStream(uri)?.use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
    val matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
        }
    }
    if (matrix.isIdentity) return bitmap
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
        if (it !== bitmap) bitmap.recycle()
    }
}

/** 囲みの外側を白くし、重なった範囲も一枚へ統合して重複認識を避ける。 */
internal fun maskScanRegions(photo: Bitmap, regions: List<ScanRegion>): Bitmap {
    require(regions.isNotEmpty() && regions.all { it.isValid() })
    val output = Bitmap.createBitmap(photo.width, photo.height, Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        regions.forEach { region ->
            val path = Path().apply {
                region.points.forEachIndexed { index, point ->
                    if (index == 0) moveTo(point.x * photo.width, point.y * photo.height)
                    else lineTo(point.x * photo.width, point.y * photo.height)
                }
                close()
            }
            val checkpoint = canvas.save()
            canvas.clipPath(path)
            canvas.drawBitmap(photo, 0f, 0f, null)
            canvas.restoreToCount(checkpoint)
        }
        return output
    } catch (failure: Throwable) { output.recycle(); throw failure }
}
