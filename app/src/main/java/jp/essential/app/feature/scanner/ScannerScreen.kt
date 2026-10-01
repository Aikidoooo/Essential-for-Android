package jp.essential.app.feature.scanner

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import jp.essential.app.feature.qr.QrScannerScreen
import jp.essential.app.feature.textscan.TextScanScreen

/** モード切替時は前のカメラを破棄し、最後に使用したモードを次回も開く。 */
@Composable
fun ScannerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("scanner", Context.MODE_PRIVATE) }
    var textMode by rememberSaveable { mutableStateOf(preferences.getBoolean("text_mode", false)) }
    var transitionFrame by remember { mutableStateOf<Bitmap?>(null) }
    val modePosition by animateFloatAsState(if (textMode) 1f else 0f,
        spring(dampingRatio = 0.76f, stiffness = 420f), label = "スキャナーの選択位置")
    fun selectText(enabled: Boolean) {
        textMode = enabled
        preferences.edit().putBoolean("text_mode", enabled).apply()
    }
    if (textMode) TextScanScreen(modePosition = modePosition, transitionFrame = transitionFrame,
        onTransitionFrame = { transitionFrame = it }, onTransitionFinished = { transitionFrame = null }, onModeChange = { selectText(false) }, onBack = onBack)
    else QrScannerScreen(modePosition = modePosition, transitionFrame = transitionFrame,
        onTransitionFrame = { transitionFrame = it }, onTransitionFinished = { transitionFrame = null }, onModeChange = { selectText(true) }, onBack = onBack)
}
