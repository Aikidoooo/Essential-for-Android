package jp.essential.app.feature.qr

import jp.essential.app.ui.elasticListEdges

import android.content.ClipData
import android.content.Intent
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import jp.essential.app.R
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import jp.essential.app.ui.FeatureHeader
import jp.essential.app.ui.fixedHeader
import jp.essential.app.feature.widgets.WidgetGlass
import jp.essential.app.feature.widgets.WidgetButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.io.File

internal fun validQrUrl(value: String): Boolean = runCatching {
    val uri = java.net.URI(value.trim())
    uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() && value.toByteArray(Charsets.UTF_8).size <= 2000
}.getOrDefault(false)

internal fun createQrBitmap(value: String): Bitmap {
    require(validQrUrl(value))
    val matrix = QRCodeWriter().encode(value.trim(), BarcodeFormat.QR_CODE, 1024, 1024,
        mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 4))
    val pixels = IntArray(matrix.width * matrix.height) { index ->
        if (matrix[index % matrix.width, index / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    val source = Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    val rounded = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(rounded)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    // 外周だけを角丸にし、QRの模様と読み取り用の余白は維持する。
    val radius = matrix.width * 0.045f
    canvas.drawRoundRect(0f, 0f, matrix.width.toFloat(), matrix.height.toFloat(), radius, radius, paint)
    paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(source, 0f, 0f, paint)
    source.recycle()
    return rounded
}

@Composable
fun QrCreateScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by rememberSaveable { mutableStateOf("") }
    var generatedUrl by rememberSaveable { mutableStateOf("") }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingSaveUrl by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(generatedUrl) {
        if (generatedUrl.isNotBlank()) {
            busy = true
            try { bitmap = withContext(Dispatchers.Default) { createQrBitmap(generatedUrl) }; message = null }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = "QRコードを作成できませんでした" }
            finally { busy = false }
        }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val selectedUrl = pendingSaveUrl
        pendingSaveUrl = ""
        if (uri != null && selectedUrl.isNotBlank()) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    val selected = createQrBitmap(selectedUrl)
                    context.contentResolver.openOutputStream(uri)?.use { check(selected.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                        ?: error("保存先を開けません")
                }
                message = "QRコードを保存しました"
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message = "保存できませんでした。保存先を確認してください" }
            finally { busy = false }
        }
    }
    LazyColumn(overscrollEffect = null, modifier = Modifier.elasticListEdges().fillMaxSize().imePadding(), contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        fixedHeader { FeatureHeader("QR Create", onBack) }
        item { WidgetGlass(0) {
            Text("URLをQRコードに", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(input, { input = it; message = null }, label = { Text("https://example.com") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                maxLines = 4, isError = input.isNotBlank() && !validQrUrl(input),
                trailingIcon = {
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        input = clipboard.primaryClip?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                        message = null
                    }) {
                        Icon(painterResource(R.drawable.ic_content_paste), contentDescription = "クリップボードから貼り付け",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                })
            WidgetButton("QRコードを作成", enabled = validQrUrl(input) && !busy) {
                if (generatedUrl != input.trim()) { bitmap = null; generatedUrl = input.trim() }
            }
        } }
        bitmap?.let { image -> item { WidgetGlass(1) {
            Image(image.asImageBitmap(), contentDescription = "${generatedUrl} のQRコード",
                modifier = Modifier.fillMaxWidth().aspectRatio(1f), filterQuality = FilterQuality.None)
            Text(generatedUrl)
            if (input.trim() != generatedUrl) Text("入力を変更しました。作成し直すと画像へ反映されます。")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WidgetButton("画像を保存", enabled = !busy) { pendingSaveUrl = generatedUrl; save.launch("Essential-QR-${System.currentTimeMillis()}.png") }
                WidgetButton("共有", enabled = !busy) { scope.launch {
                    busy = true
                    try {
                        val uri = withContext(Dispatchers.IO) {
                            val directory = File(context.cacheDir, "qr-images").apply { mkdirs() }
                            // 古い共有画像だけを期限付きで整理し、共有中の画像は保持する。
                            directory.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 86_400_000 }?.forEach { it.delete() }
                            val file = File(directory, "qr-${System.currentTimeMillis()}.png")
                            file.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                            FileProvider.getUriForFile(context, "${context.packageName}.qr-images", file)
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"; putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newRawUri("QRコード", uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "QRコードを共有"))
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { message = "共有できませんでした" }
                    finally { busy = false }
                } }
            }
        } } }
        item { if (busy) CircularProgressIndicator(); message?.let { Text(it) } }
    }
}
