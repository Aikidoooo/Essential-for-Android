package jp.essential.app.feature.files

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class MediaProgressTransitionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun downloadCompletionCanSwitchToIndeterminateRepeatedly() {
        val progress = mutableStateOf<Float?>(1f)
        compose.setContent { MaterialTheme { Column { MediaProcessingProgress("モデル", progress.value) } } }
        repeat(30) {
            compose.runOnIdle { progress.value = null }
            compose.onNodeWithText("モデル").assertExists()
            compose.runOnIdle { progress.value = 1f }
            compose.onNodeWithText("モデル 100%").assertExists()
        }
    }
}
