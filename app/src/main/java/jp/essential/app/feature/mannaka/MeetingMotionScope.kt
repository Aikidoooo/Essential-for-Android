package jp.essential.app.feature.mannaka

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import jp.essential.app.ui.LocalProgressiveMotionCompleted
import jp.essential.app.ui.LocalProgressiveMotionCycle
import jp.essential.app.ui.LocalProgressiveMotionDirection
import jp.essential.app.ui.LocalProgressiveMotionEager

/** 画面の段階が変わったときに、共通の順次表示を独立して再生する。 */
@Composable
internal fun MeetingMotionScope(stage: String, content: @Composable () -> Unit) {
    val cycle = remember(stage) { stage.hashCode().toLong().let { if (it == 0L) 1L else it } }
    CompositionLocalProvider(
        LocalProgressiveMotionCycle provides cycle,
        LocalProgressiveMotionDirection provides 0,
        LocalProgressiveMotionCompleted provides false,
        LocalProgressiveMotionEager provides false,
        content = content,
    )
}
