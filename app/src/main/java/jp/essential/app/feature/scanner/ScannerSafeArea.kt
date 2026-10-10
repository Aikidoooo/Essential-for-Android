package jp.essential.app.feature.scanner

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity

/** 親でInsetsが消費されていても、カメラ操作には実際の安全余白を確保する。 */
@Composable
internal fun scannerSafePadding(): PaddingValues {
    val padding = WindowInsets.safeDrawing.asPaddingValues()
    val context = LocalContext.current
    val density = LocalDensity.current
    val resource = context.resources.getIdentifier("status_bar_height", "dimen", "android")
    val fallback = with(density) { (if (resource != 0) context.resources.getDimensionPixelSize(resource) else 0).toDp().coerceAtLeast(androidx.compose.ui.unit.Dp(32f)) }
    val safePadding = PaddingValues(
        start = padding.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
        top = maxOf(padding.calculateTopPadding(), fallback),
        end = padding.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
        bottom = padding.calculateBottomPadding(),
    )
    return safePadding
}


/** 操作UIだけに安全域を適用し、映像と全画面の影には余白を付けない。 */
@Composable
internal fun Modifier.scannerSafeArea(): Modifier {
    val padding = scannerSafePadding()
    return this.padding(padding).consumeWindowInsets(padding)
}
