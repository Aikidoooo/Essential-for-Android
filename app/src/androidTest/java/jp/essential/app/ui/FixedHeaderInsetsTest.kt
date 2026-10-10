package jp.essential.app.ui

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 固定ヘッダーだけをカメラ下に残し、通常の行はステータスバー背面まで流せることを確認する。 */
class FixedHeaderInsetsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun headerStaysSafeWhileRowsReachTop() {
        compose.activity.runOnUiThread { compose.activity.enableEdgeToEdge() }
        var inset = 0f
        lateinit var scroll: () -> Unit
        compose.setContent {
            inset = WindowInsets.safeDrawing.getTop(LocalDensity.current).toFloat()
            val state = rememberLazyListState()
            val scope = rememberCoroutineScope()
            scroll = { scope.launch { state.scrollToItem(5, 12) }; Unit }
            MaterialTheme {
                LazyColumn(Modifier.fillMaxSize(), state = state) {
                    fixedHeader { Box(Modifier.height(48.dp)) { Text("固定ヘッダー") } }
                    items(20) { index -> Box(Modifier.fillMaxWidth().height(90.dp).testTag("row-$index")) { Text("項目$index") } }
                }
            }
        }
        compose.runOnIdle { scroll() }
        compose.waitForIdle()
        val headerTop = compose.onNodeWithText("固定ヘッダー").fetchSemanticsNode().boundsInRoot.top
        val rowTop = compose.onNodeWithTag("row-4").fetchSemanticsNode().boundsInRoot.top
        assertTrue("安全余白がある端末で検証する", inset > 0f)
        assertTrue("固定操作はカメラ下に留まる", headerTop >= inset)
        assertTrue("通常のUIは画面上端まで届く", rowTop < inset)
    }
}
