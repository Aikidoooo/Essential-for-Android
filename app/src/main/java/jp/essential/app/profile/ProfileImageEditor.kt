package jp.essential.app.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** 元画像を変更せず、メモリー使用量を制限して編集用の画像を読み込む。 */
internal fun loadProfileEditorBitmap(context: Context, uri: Uri): Bitmap? = runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0)
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
    val bitmap = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val orientation = resolver.openInputStream(uri)?.use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
    val matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
        }
    }
    if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { bitmap.recycle() }
}.getOrNull()

/** 枠から画像がはみ出しても、空白が枠内に入らない位置へ制限する。 */
internal fun clampCropOffset(offset: Offset, image: Size, frame: Size): Offset = Offset(
    offset.x.coerceIn(-max(0f, (image.width - frame.width) / 2), max(0f, (image.width - frame.width) / 2)),
    offset.y.coerceIn(-max(0f, (image.height - frame.height) / 2), max(0f, (image.height - frame.height) / 2)),
)

@Composable
internal fun ProfileImageEditor(uri: Uri, banner: Boolean, context: Context, onCancel: () -> Unit, onSave: (Bitmap) -> Unit, bannerAspect: Float = 2.5f) {
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }
    var zoom by remember(uri) { mutableFloatStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(Size.Zero) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) { loadProfileEditorBitmap(context, uri) }
        failed = bitmap == null
    }
    val source = bitmap
    val aspect = if (banner) bannerAspect.coerceAtLeast(.1f) else 1f
    val frameWidth = minOf(viewport.width * .92f, viewport.height * .85f * aspect)
    val frame = Size(frameWidth, frameWidth / aspect)
    val baseScale = source?.let { max(frame.width / it.width, frame.height / it.height) } ?: 1f
    fun constrain(value: Offset, scale: Float) = source?.let {
        clampCropOffset(value, Size(it.width * baseScale * scale, it.height * baseScale * scale), frame)
    } ?: Offset.Zero
    LaunchedEffect(viewport, source) { offset = constrain(offset, zoom) }
    Dialog(onDismissRequest = { if (!saving) onCancel() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .10f)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onCancel, enabled = !saving) { Text("キャンセル", color = Color.White) }
                    Text(if (banner) "背景を編集" else "アイコンを編集", color = Color.White, modifier = Modifier.padding(top = 12.dp))
                    TextButton(enabled = source != null && viewport.width > 0 && !saving, onClick = {
                        source ?: return@TextButton
                        saving = true
                        val scale = baseScale * zoom
                        val width = (frame.width / scale).roundToInt().coerceIn(1, source.width)
                        val height = (frame.height / scale).roundToInt().coerceIn(1, source.height)
                        val left = ((source.width - width) / 2f - offset.x / scale).roundToInt().coerceIn(0, source.width - width)
                        val top = ((source.height - height) / 2f - offset.y / scale).roundToInt().coerceIn(0, source.height - height)
                        onSave(Bitmap.createBitmap(source, left, top, width, height))
                    }) { Text("保存", color = Color.White) }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Canvas(Modifier.fillMaxSize().pointerInput(source, frame) {
                        detectTransformGestures { _, pan, scale, _ ->
                            val next = (zoom * scale).coerceIn(1f, 5f)
                            offset = constrain(offset * (next / zoom) + pan, next)
                            zoom = next
                        }
                    }) {
                        if (viewport != size) viewport = size
                        source?.let {
                            val imageSize = IntSize((it.width * baseScale * zoom).roundToInt(), (it.height * baseScale * zoom).roundToInt())
                            drawImage(it.asImageBitmap(), dstOffset = IntOffset(((size.width - imageSize.width) / 2 + offset.x).roundToInt(), ((size.height - imageSize.height) / 2 + offset.y).roundToInt()), dstSize = imageSize)
                        }
                        val left = (size.width - frame.width) / 2
                        val top = (size.height - frame.height) / 2
                        val shade = Color.Black.copy(alpha = .60f)
                        drawRect(shade, size = Size(size.width, top))
                        drawRect(shade, Offset(0f, top + frame.height), Size(size.width, top))
                        drawRect(shade, Offset(0f, top), Size(left, frame.height))
                        drawRect(shade, Offset(left + frame.width, top), Size(left, frame.height))
                        drawRect(Color.White, Offset(left, top), frame, style = Stroke(2.dp.toPx()))
                        for (division in 1..2) {
                            val x = left + frame.width * division / 3
                            val y = top + frame.height * division / 3
                            drawLine(Color.White.copy(alpha = .45f), Offset(x, top), Offset(x, top + frame.height))
                            drawLine(Color.White.copy(alpha = .45f), Offset(left, y), Offset(left + frame.width, y))
                        }
                    }
                }
                Surface(color = Color.White.copy(alpha = .12f), shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
                    Column(Modifier.padding(24.dp)) {
                        Text(if (failed) "画像を読み込めませんでした" else if (source == null) "画像を読み込み中…" else "ドラッグで移動・指を広げて拡大", color = Color.White)
                        Text("${(zoom * 100).roundToInt()}%", color = Color.White, modifier = Modifier.padding(top = 12.dp))
                        Slider(value = zoom, onValueChange = { zoom = it; offset = constrain(offset, it) }, valueRange = 1f..5f, enabled = source != null && !saving)
                        TextButton(onClick = { zoom = 1f; offset = Offset.Zero }, enabled = !saving) { Text("位置と大きさをリセット", color = Color.White) }
                        Text("保存すると選択範囲の静止画像になります", color = Color.White.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
