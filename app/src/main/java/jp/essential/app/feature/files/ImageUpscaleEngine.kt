package jp.essential.app.feature.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** 入力の長辺を2048px以内へ収め、重なり付きタイルでメモリ負荷と継ぎ目を抑える。 */
internal class ImageUpscaleEngine(private val context: Context) {
    suspend fun upscale(file: ReferencedFile, mode: UpscaleMode, progress: (String, Float?) -> Unit): Bitmap {
        val model = MediaAiModels.obtain(context, mode.model) { progress("モデルをダウンロード", it) }
        return withContext(Dispatchers.Default) {
            progress("画像を準備", null)
            val source = loadMediaImage(context, file, 2048)
            val output = Bitmap.createBitmap(source.width * mode.scale, source.height * mode.scale, Bitmap.Config.ARGB_8888)
            val environment = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
            try {
                environment.createSession(model.absolutePath, options).use { session ->
                    val tile = 64
                    val pad = 12
                    val edge = tile + pad * 2
                    val pixels = IntArray(edge * edge)
                    val values = FloatArray(3 * edge * edge)
                    val tileOutput = IntArray(tile * mode.scale * tile * mode.scale)
                    val total = ((source.width + tile - 1) / tile) * ((source.height + tile - 1) / tile)
                    var completed = 0
                    for (top in 0 until source.height step tile) for (left in 0 until source.width step tile) {
                        currentCoroutineContext().ensureActive()
                        for (y in 0 until edge) for (x in 0 until edge) pixels[y * edge + x] = source.getPixel((left + x - pad).coerceIn(0, source.width - 1), (top + y - pad).coerceIn(0, source.height - 1))
                        for (i in pixels.indices) {
                            values[i] = Color.red(pixels[i]) / 255f
                            values[edge * edge + i] = Color.green(pixels[i]) / 255f
                            values[edge * edge * 2 + i] = Color.blue(pixels[i]) / 255f
                        }
                        OnnxTensor.createTensor(environment, FloatBuffer.wrap(values), longArrayOf(1, 3, edge.toLong(), edge.toLong())).use { input ->
                            session.run(mapOf(session.inputNames.first() to input)).use { result ->
                                val tensor = result[0] as OnnxTensor
                                val shape = tensor.info.shape
                                val side = edge * mode.scale
                                check(shape.contentEquals(longArrayOf(1, 3, side.toLong(), side.toLong()))) { "モデルの出力寸法が一致しません" }
                                val buffer = tensor.floatBuffer
                                val width = minOf(tile, source.width - left) * mode.scale
                                val height = minOf(tile, source.height - top) * mode.scale
                                for (y in 0 until height) for (x in 0 until width) {
                                    val i = (y + pad * mode.scale) * side + x + pad * mode.scale
                                    fun channel(offset: Int) = (buffer.get(i + offset).coerceIn(0f, 1f) * 255).toInt()
                                    val alpha = Color.alpha(source.getPixel(left + x / mode.scale, top + y / mode.scale))
                                    tileOutput[y * width + x] = Color.argb(alpha, channel(0), channel(side * side), channel(side * side * 2))
                                }
                                output.setPixels(tileOutput, 0, width, left * mode.scale, top * mode.scale, width, height)
                            }
                        }
                        completed++
                        progress("高画質化", completed.toFloat() / total)
                    }
                }
                output
            } catch (error: Throwable) { output.recycle(); throw error }
            finally { source.recycle(); options.close() }
        }
    }
}

internal fun loadMediaImage(context: Context, file: ReferencedFile, maxEdge: Int): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(file.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    check(bounds.outWidth > 0 && bounds.outHeight > 0) { "画像を読み込めませんでした" }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge * 2) sample *= 2
    var decoded = context.contentResolver.openInputStream(file.uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("画像を読み込めませんでした")
    val orientation = context.contentResolver.openInputStream(file.uri)?.use {
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
    if (!matrix.isIdentity) {
        val corrected = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (corrected !== decoded) decoded.recycle()
        decoded = corrected
    }
    val ratio = minOf(1f, maxEdge.toFloat() / maxOf(decoded.width, decoded.height))
    if (ratio == 1f) return decoded
    return Bitmap.createScaledBitmap(decoded, (decoded.width * ratio).toInt().coerceAtLeast(1), (decoded.height * ratio).toInt().coerceAtLeast(1), true).also { decoded.recycle() }
}
