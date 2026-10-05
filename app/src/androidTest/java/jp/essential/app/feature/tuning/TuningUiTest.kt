package jp.essential.app.feature.tuning

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.Lifecycle
import jp.essential.app.MainActivity
import org.junit.Rule
import org.junit.Test

class TuningUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun presetsManualStringsAndReferenceCanBeChanged() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("tuning", Context.MODE_PRIVATE)
        val original = preferences.all
        preferences.edit().putInt("preset", 0).putInt("reference", 440).commit()
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_FEATURE, "tuning"))
        try {
            compose.onNodeWithText("ウクレレ  g C E A").performClick()
            compose.onNodeWithContentDescription("4弦 G4").performClick().assertIsSelected()
            compose.onNodeWithText("ギター  E A D G B E").performClick()
            compose.onNodeWithContentDescription("6弦 E2").assertIsSelected()
            compose.onNodeWithContentDescription("1弦 E4").performClick().assertIsSelected()
            compose.onNodeWithContentDescription("6弦 E2").assertIsNotSelected()
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("詳細設定・その他のプリセット"))
            compose.onNodeWithText("詳細設定・その他のプリセット").performClick()
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("基準音 A4（400〜480 Hz）"))
            compose.onNodeWithText("基準音 A4（400〜480 Hz）").performScrollTo().performTextReplacement("442")
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasContentDescription("チューニングを開始"))
            compose.onNodeWithContentDescription("チューニングを開始").assertIsEnabled()
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithContentDescription("チューニングを開始").assertExists()
        } finally {
            scenario.close()
            preferences.edit().apply {
                if (original["preset"] is Int) putInt("preset", original["preset"] as Int) else remove("preset")
                if (original["reference"] is Int) putInt("reference", original["reference"] as Int) else remove("reference")
            }.commit()
        }
    }
}
