package jp.essential.app.feature.scanner

import android.graphics.Bitmap
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.Observer
import kotlinx.coroutines.delay

/** 次のカメラ映像が届くまで直前の画像を表示し、カメラの二重起動なしで切り替える。 */
@Composable
internal fun ScannerPreviewTransition(frame: Bitmap?, preview: PreviewView?, onFinished: () -> Unit) {
    var streaming by remember(preview) { mutableStateOf(false) }
    val currentFinished by rememberUpdatedState(onFinished)
    DisposableEffect(preview) {
        val observer = Observer<PreviewView.StreamState> { streaming = it == PreviewView.StreamState.STREAMING }
        preview?.previewStreamState?.observeForever(observer)
        onDispose { preview?.previewStreamState?.removeObserver(observer) }
    }
    LaunchedEffect(frame, streaming) {
        if (frame != null) {
            // 接続失敗時も古い静止画像を残さず、エラー表示へ戻す。
            if (!streaming) delay(2000)
            currentFinished()
        }
    }
    if (frame != null) Image(frame.asImageBitmap(), contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier.fillMaxSize())
}
