package jp.essential.app.feature.minigame

import androidx.compose.ui.graphics.Color
import jp.essential.app.ui.LiquidGlassStyle
import kotlin.math.roundToInt

/** Web側もホームの値から生成し、透明度や色を二重管理しない。 */
internal fun homeGlassWebScript(dark: Boolean): String {
    fun css(color: Color) = "rgba(${(color.red * 255).roundToInt()},${(color.green * 255).roundToInt()},${(color.blue * 255).roundToInt()},${color.alpha})"
    val tint = if (dark) LiquidGlassStyle.darkTint else LiquidGlassStyle.tint
    return "(function(){var r=document.documentElement;if(!r)return;" +
        "r.dataset.essentialGlass='${if (dark) "dark" else "light"}';" +
        "r.style.setProperty('--essential-glass-start','${css(tint.first())}');" +
        "r.style.setProperty('--essential-glass-end','${css(tint.last())}');" +
        "r.style.setProperty('--essential-glass-edge','${css(LiquidGlassStyle.edge.first())}');" +
        "r.style.setProperty('--essential-glass-edge-end','${css(LiquidGlassStyle.edge.last())}');" +
        "r.style.setProperty('--essential-glass-blur','${LiquidGlassStyle.blurRadius.value}px');})();"
}
