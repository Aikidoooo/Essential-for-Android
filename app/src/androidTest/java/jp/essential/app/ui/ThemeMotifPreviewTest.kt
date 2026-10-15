package jp.essential.app.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.captureToImage
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import jp.essential.app.AppIconManager
import jp.essential.app.MainActivity
import jp.essential.app.R
import jp.essential.app.ui.theme.EssentialTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

/** 実際のCompose描画で全9模様のライト／ダーク比較画像を生成する。 */
class ThemeMotifPreviewTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun captureSpecialArtworkInBothModes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dark = mutableStateOf(false)
        val theme = mutableStateOf("sky")
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            scenario.onActivity { activity -> activity.setContent {
                EssentialTheme(darkTheme = dark.value) {
                    Box(Modifier.fillMaxSize().testTag("lunar-companion-preview")) {
                        AnimatedBackdrop(AppIconManager.optionForId(theme.value))
                        ThemeMotifBackground(theme.value, dark.value)
                    }
                }
            } }
            for (themeId in listOf("sky", "ruby", "rose", "ocean", "forest", "violet", "sunset", "mint")) for (mode in listOf(false, true)) {
                compose.runOnIdle { theme.value = themeId; dark.value = mode }
                compose.mainClock.advanceTimeBy(1500)
                compose.waitForIdle()
                val bitmap = compose.onNodeWithTag("lunar-companion-preview").captureToImage().asAndroidBitmap()
                val name = when (themeId) { "sky" -> "lunar-companion"; "ruby" -> "pyro-motorcycle"; "ocean" -> "hydro-water"; "forest" -> "dendro-companion"; "violet" -> "electro-companion"; "sunset" -> "geo-companion"; "mint" -> "anemo-circle"; else -> "cryo-attendants" }
                java.io.File(context.getExternalFilesDir(null), "$name-${if (mode) "dark" else "light"}.png")
                    .outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        } finally { scenario.close() }
    }

    /** 採用した原本の各人物が空白にならず、下部の脚・裾まで描かれていることを確認する。 */
    @Test fun originalFullBodyCellsContainLowerDetails() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.theme_character_original_v4)
        try {
            val cellWidth = bitmap.width / 4
            val cellHeight = bitmap.height / 2
            for (index in 0..7) {
                var inkCount = 0
                var lowerInkCount = 0
                for (y in 0 until cellHeight step 2) for (x in 0 until cellWidth step 2) {
                    val pixel = bitmap.getPixel(index % 4 * cellWidth + x, index / 4 * cellHeight + y)
                    val opacity = android.graphics.Color.alpha(pixel)
                    val brightness = (android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) + android.graphics.Color.blue(pixel)) / 3
                    if (opacity > 32 && brightness < 200) {
                        inkCount++
                        if (y > cellHeight * .75f) lowerInkCount++
                    }
                }
                assertTrue("全身セル $index に線が存在する", inkCount > 100)
                assertTrue("全身セル $index の下部に脚・裾の線が存在する", lowerInkCount > 50)
            }
        } finally { bitmap.recycle() }
    }

    @Test fun captureBothModeSheets() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dark = mutableStateOf(false)
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            scenario.onActivity { activity -> activity.setContent {
                EssentialTheme(darkTheme = dark.value) {
                    Column(Modifier.fillMaxSize().testTag("theme-motif-sheet").background(if (dark.value) Color.Black else Color.White)
                        .windowInsetsPadding(WindowInsets.safeDrawing).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppIconManager.options.drop(2).chunked(3).forEach { row ->
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { option ->
                                    Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                                        AnimatedBackdrop(option, motionEnabled = false)
                                        ThemeMotifBackground(option.id, dark.value)
                                        Text(option.label.substringBefore(" Essential"), Modifier.padding(10.dp), color = if (dark.value) Color.White else Color(0xFF182328))
                                    }
                                }
                            }
                        }
                    }
                }
            } }
            for (mode in listOf(false, true)) {
                compose.runOnIdle { dark.value = mode }
                compose.mainClock.advanceTimeBy(1500)
                compose.waitForIdle()
                val bitmap = compose.onNodeWithTag("theme-motif-sheet").captureToImage().asAndroidBitmap()
                java.io.File(context.getExternalFilesDir(null), "theme-motifs-${if (mode) "dark" else "light"}.png")
                    .outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        } finally { scenario.close() }
    }
}
