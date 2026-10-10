package jp.essential.app.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import jp.essential.app.MainActivity
import jp.essential.app.ui.theme.EssentialTheme
import org.junit.*
import org.junit.Assert.*
import kotlin.math.*
import java.io.File

class RadialMenuTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var original: String? = null
    @Before fun prepare() {
        val prefs = context.getSharedPreferences("radial_shortcuts", Context.MODE_PRIVATE)
        original = prefs.getString("items", null)
        prefs.edit().remove("items").commit()
    }
    @After fun restore() {
        scenario?.close()
        context.getSharedPreferences("radial_shortcuts", Context.MODE_PRIVATE).edit().apply {
            if (original == null) remove("items") else putString("items", original)
        }.commit()
    }
    private fun launch(consumed: Boolean = false, heldWidget: Boolean = false, clickable: Boolean = false) {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        scenario?.onActivity { activity -> activity.setContent {
            EssentialTheme(darkTheme = true) {
                RadialMenuHost {
                    val gate = LocalRadialGestureGate.current
                    Box(Modifier.fillMaxSize().background(Color(0xFF163047)).then(
                        if (heldWidget) Modifier.pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(onDragStart = { gate?.blockWidgetGesture() }) { change, _ -> change.consume() }
                        }
                        else if (clickable) Modifier.clickable { }
                        else if (consumed) Modifier.pointerInput(Unit) { detectDragGestures { change, _ -> change.consume() } }
                        else Modifier))
                }
            }
        } }
        compose.waitForIdle()
    }
    private fun circle(duration: Long = 720, clockwise: Boolean = true, diameterFraction: Float = .5f, holdMillis: Long = 0) {
        compose.onNodeWithTag("radial-gesture-host").performTouchInput {
            val radius = width * diameterFraction / 2f
            down(center + Offset(radius, 0f))
            if (holdMillis > 0) moveTo(center + Offset(radius, 0f), holdMillis)
            for (index in 1..48) {
                val angle = index * 2 * PI / 48 * if (clockwise) 1 else -1
                moveTo(center + Offset(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius), duration / 48)
            }
            up()
        }
    }
    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(80)
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        Thread.sleep(120)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Test fun fastCircleOpensAndSlowCircleDoesNot() {
        launch()
        circle(1440)
        compose.onNodeWithTag("radial-menu").assertDoesNotExist()
        circle(clockwise = false)
        compose.onNodeWithTag("radial-menu").assertIsDisplayed()
        compose.onNodeWithTag("radial-menu").performTouchInput { click(Offset(8f, 8f)) }
        compose.onNodeWithTag("radial-menu").assertDoesNotExist()
        scenario?.close()
        launch(consumed = true)
        circle()
        compose.onNodeWithTag("radial-menu").assertIsDisplayed()
    }
    @Test fun smallCircleWorksOnClickableAndDraggingSurfacesExceptHeldWidgets() {
        launch(clickable = true)
        circle(diameterFraction = .05f)
        compose.onNodeWithTag("radial-menu").assertIsDisplayed()
        scenario?.close()
        launch(consumed = true)
        circle(diameterFraction = .05f)
        compose.onNodeWithTag("radial-menu").assertIsDisplayed()
        scenario?.close()
        launch(heldWidget = true)
        circle(duration = 320, diameterFraction = .05f, holdMillis = 600)
        compose.onNodeWithTag("radial-menu").assertDoesNotExist()
        circle(diameterFraction = .05f)
        compose.onNodeWithTag("radial-menu").assertIsDisplayed()
    }
    @Test fun defaultsEditRemovePersistAndPicker() {
        launch()
        circle()
        capture("radial-default.png")
        val add = compose.onNodeWithTag("radial-add").fetchSemanticsNode().boundsInRoot
        RadialShortcutStore.defaults.forEach { shortcut ->
            val bounds = compose.onNodeWithTag("radial-shortcut-${shortcut.packageName}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("アプリは中央ボタンの外側に配置", (add.center - bounds.center).getDistance() > add.width)
        }
        // 中央ボタンと右側アイコンの間の空白でも閉じられることを確認する。
        compose.onNodeWithTag("radial-gesture-host").performTouchInput {
            click(add.center + Offset(add.width * 1.5f, 0f))
        }
        compose.onNodeWithTag("radial-menu").assertDoesNotExist()
        circle()
        val first = compose.onNodeWithTag("radial-shortcut-com.twitter.android", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        val stationary = compose.onNodeWithTag("radial-shortcut-jp.naver.line.android", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        val second = compose.onNodeWithTag("radial-shortcut-com.facebook.katana", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        compose.onNodeWithTag("radial-gesture-host").performTouchInput {
            down(first)
            moveTo(first, 450)
            moveTo(second, 32)
            moveTo(second, 32)
        }
        compose.waitForIdle()
        val displaced = compose.onNodeWithTag("radial-shortcut-com.facebook.katana", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        assertTrue("ドラッグ中に隣のアイコンが場所を空ける", (displaced - second).getDistance() > 20f)
        val stationaryAfter = compose.onNodeWithTag("radial-shortcut-jp.naver.line.android", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
        assertTrue("無関係なアイコンは動かない", (stationaryAfter - stationary).getDistance() < 1f)
        capture("radial-reorder-preview.png")
        compose.onNodeWithTag("radial-gesture-host").performTouchInput { up() }
        assertEquals("500ms以内の移動で順序を保存", "com.twitter.android", RadialShortcutStore(context).load()[6].packageName)
        compose.onNodeWithTag("radial-shortcut-com.twitter.android", useUnmergedTree = true).performTouchInput { longClick(durationMillis = 650) }
        compose.onNodeWithTag("radial-remove-com.twitter.android").assertIsDisplayed()
        capture("radial-edit.png")
        compose.onNodeWithTag("radial-remove-com.twitter.android").assertIsDisplayed().performClick()
        assertEquals(7, RadialShortcutStore(context).load().size)
        compose.onNodeWithTag("radial-add").performClick()
        compose.onNodeWithTag("radial-add").performClick()
        compose.onNodeWithTag("radial-app-picker").assertIsDisplayed()
        capture("radial-glass-picker.png")
        compose.waitUntil(5000) { compose.onAllNodesWithText("Clock").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Clock").performScrollTo().performClick()
        assertEquals("端末内のアプリを追加", "com.google.android.deskclock", RadialShortcutStore(context).load().last().packageName)
        compose.onNodeWithTag("radial-menu").performTouchInput { click(Offset(8f, 8f)) }
        circle()
        compose.onNodeWithTag("radial-shortcut-com.twitter.android", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("radial-shortcut-com.google.android.deskclock", useUnmergedTree = true).performTouchInput { click() }
        compose.onNodeWithTag("radial-menu").assertDoesNotExist()
    }
    @Test fun storeLimitsDuplicatesAndKeepsAnEmptyList() {
        val store = RadialShortcutStore(context)
        store.save((0..15).map { RadialShortcut("test.package.$it", "アプリ$it") })
        assertEquals(12, store.load().size)
        launch()
        circle()
        capture("radial-twelve.png")
        compose.onNodeWithContentDescription("ショートカットを削除").assertIsDisplayed()
        compose.onNodeWithTag("radial-add").performClick()
        compose.onNodeWithTag("radial-app-picker").assertDoesNotExist()
        compose.onNodeWithTag("radial-remove-test.package.0").assertIsDisplayed()
        store.save(listOf(RadialShortcut("same", "一つ"), RadialShortcut("same", "重複")))
        assertEquals(1, store.load().size)
        store.save(emptyList())
        assertTrue(store.load().isEmpty())
    }
    @Test fun realFeatureScreenAcceptsMouseCircle() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_FEATURE, "downloader"))
        compose.waitUntil(15000) { compose.onAllNodesWithTag("radial-gesture-host").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag("radial-gesture-host").fetchSemanticsNode().boundsInRoot
        capture("radial-real-mouse-before.png")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val started = android.os.SystemClock.uptimeMillis()
        fun mouse(action: Int, position: Offset) {
            val properties = android.view.MotionEvent.PointerProperties().apply {
                id = 0
                toolType = android.view.MotionEvent.TOOL_TYPE_MOUSE
            }
            val coordinates = android.view.MotionEvent.PointerCoords().apply { x = position.x; y = position.y; pressure = 1f }
            val event = android.view.MotionEvent.obtain(started, android.os.SystemClock.uptimeMillis(), action, 1,
                arrayOf(properties), arrayOf(coordinates), 0, if (action == android.view.MotionEvent.ACTION_UP) 0 else android.view.MotionEvent.BUTTON_PRIMARY,
                1f, 1f, 0, 0, android.view.InputDevice.SOURCE_MOUSE, 0)
            instrumentation.uiAutomation.injectInputEvent(event, action == android.view.MotionEvent.ACTION_UP)
            event.recycle()
        }
        val radius = bounds.width * .12f
        mouse(android.view.MotionEvent.ACTION_DOWN, bounds.center + Offset(radius, 0f))
        for (index in 1..16) {
            Thread.sleep(1)
            val angle = index * 2 * PI / 16
            mouse(android.view.MotionEvent.ACTION_MOVE, bounds.center + Offset(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius))
        }
        mouse(android.view.MotionEvent.ACTION_UP, bounds.center + Offset(radius, 0f))
        capture("radial-real-mouse.png")
        compose.onNodeWithTag("radial-menu").assertIsDisplayed()
    }
    @Test fun denseMouseLikeInputDoesNotHitPointLimit() {
        launch()
        compose.onNodeWithTag("radial-gesture-host").performTouchInput {
            val radius = width * .25f
            down(center + Offset(radius, 0f))
            for (index in 1..600) {
                val angle = index * 2 * PI / 600
                moveTo(center + Offset(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius), 1)
            }
            up()
        }
        compose.onNodeWithTag("radial-menu").assertIsDisplayed()
    }
    @Test fun veryFastSmallCircleOpens() {
        launch(consumed = true)
        circle(duration = 96, diameterFraction = .05f)
        compose.onNodeWithTag("radial-menu").assertIsDisplayed()
    }
}
