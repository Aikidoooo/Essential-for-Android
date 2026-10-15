package jp.essential.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/** 鑑賞中だけシステムバーを隠し、終了時には元の表示状態へ戻す。 */
@Composable
internal fun BackgroundPreviewSystemBars(hidden: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(hidden, view) {
        var context = view.context
        while (context is android.content.ContextWrapper && context !is android.app.Activity) {
            context = context.baseContext
        }
        val activity = context as? android.app.Activity
        val controller = activity?.let { androidx.core.view.WindowCompat.getInsetsController(it.window, view) }
        val insets = androidx.core.view.ViewCompat.getRootWindowInsets(view)
        val status = androidx.core.view.WindowInsetsCompat.Type.statusBars()
        val navigation = androidx.core.view.WindowInsetsCompat.Type.navigationBars()
        val restoreTypes = (if (insets?.isVisible(status) != false) status else 0) or
            (if (insets?.isVisible(navigation) != false) navigation else 0)
        val previousBehavior = controller?.systemBarsBehavior
        if (hidden) {
            controller?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(status or navigation)
        }
        onDispose {
            if (hidden) {
                controller?.show(restoreTypes)
                previousBehavior?.let { controller?.systemBarsBehavior = it }
            }
        }
    }
}
