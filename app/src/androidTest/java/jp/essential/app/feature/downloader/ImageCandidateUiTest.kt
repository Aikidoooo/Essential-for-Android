package jp.essential.app.feature.downloader

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import jp.essential.app.ui.theme.EssentialTheme
import org.junit.Rule
import org.junit.Test

class ImageCandidateUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun previewsAllowIndependentMultipleSelection() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.CYAN) }
        compose.setContent {
            var chosen by remember { mutableStateOf(setOf<String>()) }
            EssentialTheme(darkTheme = true) {
                Column {
                    listOf("画像 1", "画像 2").forEach { label ->
                        ImageCandidateCard(ImageCandidate(label, label, 100, 100, label), label in chosen, true,
                            loadPreview = { bitmap }, onToggle = { chosen = if (label in chosen) chosen - label else chosen + label })
                    }
                }
            }
        }
        compose.onAllNodes(isToggleable())[0].assertIsOff()
        compose.onAllNodes(isToggleable())[1].assertIsOff()
        compose.onAllNodesWithContentDescription("画像 1")[0].assertIsDisplayed()
        compose.onAllNodes(isToggleable())[0].performClick().assertIsOn()
        compose.onAllNodes(isToggleable())[1].performClick().assertIsOn()
        compose.onAllNodes(isToggleable())[0].performClick().assertIsOff()
        compose.onAllNodes(isToggleable())[1].assertIsOn()
        compose.onAllNodesWithContentDescription("画像 1")[0].performTouchInput { click() }
        compose.onAllNodes(isToggleable())[0].assertIsOn()
        compose.onAllNodesWithContentDescription("画像 1")[0].performTouchInput { longClick() }
        compose.onNodeWithContentDescription("拡大プレビュー").assertIsDisplayed()
        compose.onNodeWithText("閉じる").performClick()
        compose.onAllNodes(isToggleable())[0].assertIsOn()
    }
}
