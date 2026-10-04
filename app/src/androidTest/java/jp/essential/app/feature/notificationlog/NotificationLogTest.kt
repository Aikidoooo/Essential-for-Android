package jp.essential.app.feature.notificationlog

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import jp.essential.app.MainActivity
import jp.essential.app.ui.theme.EssentialTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class NotificationLogTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun retentionBoundaryKeepPersistenceAndDeletion() {
        val name = "notification-log-retention-test.db"
        val file = File(context.noBackupFilesDir, name)
        val now = System.currentTimeMillis()
        val store = NotificationLogStore(context, name)
        try {
            val kept = store.record("test", "テスト", "残す通知", "本文", now - LOG_RETENTION_MILLIS)
            store.keep(kept, true, now - LOG_RETENTION_MILLIS)
            val expired = store.record("test", "テスト", "期限切れ", "本文", now - LOG_RETENTION_MILLIS)
            val fresh = store.record("test", "テスト", "期限直前", "本文", now - LOG_RETENTION_MILLIS + 1)
            assertEquals(listOf(fresh, kept), store.list(false, 100, now).map { it.id })
            assertFalse(store.list(false, 100, now).any { it.id == expired })
            store.close()
            NotificationLogStore(context, name).use { reopened ->
                assertEquals(kept, reopened.list(true, 100, now).single().id)
                reopened.keep(kept, false, now)
                assertTrue(reopened.list(true, 100, now).isEmpty())
                reopened.delete(fresh)
                assertTrue(reopened.list(false, 100, now).isEmpty())
            }
        } finally { store.close(); SQLiteFiles.delete(file) }
    }

    @Test fun uiShowsSecondsKeepFilterAndConfirmedDelete() {
        val name = "notification-log-ui-test.db"
        val file = File(context.noBackupFilesDir, name)
        val store = NotificationLogStore(context, name)
        val now = System.currentTimeMillis()
        val id = store.record("notification.test", "通知テスト", "到着テスト", "通知ログのテスト本文", now)
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            scenario.onActivity { activity -> activity.setContent {
                EssentialTheme(darkTheme = false) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) { NotificationLogScreen(store) {} }
                }
            } }
            compose.waitUntil(10000) { compose.onAllNodesWithTag("notification-entry-$id").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("notification-entry-$id").performScrollTo()
            val expected = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss").format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()))
            compose.onNodeWithText(expected).assertExists()
            compose.onNodeWithContentDescription("keepする").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("keep中").fetchSemanticsNodes().isNotEmpty() }
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            compose.mainClock.advanceTimeBy(600)
            compose.waitForIdle()
            automation.takeScreenshot().let { bitmap ->
                File(context.getExternalFilesDir(null), "notification-log-ui.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            compose.onNodeWithText("keep", substring = false).performScrollTo().performClick()
            compose.onNodeWithTag("notification-entry-$id").performScrollTo()
            compose.onNodeWithText("削除", substring = false).performClick()
            compose.onNodeWithText("キャンセル").performClick()
            compose.onNodeWithText("通知ログのテスト本文").assertExists()
            compose.onNodeWithText("削除", substring = false).performClick()
            compose.onNodeWithText("削除する").performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("keepしたログはありません").fetchSemanticsNodes().isNotEmpty() }
        } finally { scenario.close(); store.close(); SQLiteFiles.delete(file) }
    }

    @Test fun accessCardTracksPermissionChangesAndShowsLogo() {
        val originallyAllowed = notificationAccessEnabled(context)
        val component = android.content.ComponentName(context, NotificationLogListener::class.java).flattenToString()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun setAccess(enabled: Boolean) {
            val action = if (enabled) "allow_listener" else "disallow_listener"
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                automation.executeShellCommand("cmd notification $action $component")).use { it.readBytes() }
        }
        val scenario = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_FEATURE, "notification_log"))
        try {
            compose.waitUntil(10000) { compose.onAllNodesWithTag("notification-log-logo").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("通知ログのロゴ").assertExists()
            setAccess(false)
            compose.waitUntil(10000) { compose.onAllNodesWithText("通知へのアクセスを許可").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("許可する").assertExists()
            setAccess(true)
            compose.waitUntil(10000) { compose.onAllNodesWithText("通知へのアクセスを許可").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("許可する").assertDoesNotExist()
            setAccess(false)
            compose.waitUntil(10000) { compose.onAllNodesWithText("通知へのアクセスを許可").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("許可する").assertExists()
        } finally {
            setAccess(originallyAllowed)
            // OSの許可変更が反映されるまで待ち、次の検証へ元の状態を引き継ぐ。
            compose.waitUntil(10000) { notificationAccessEnabled(context) == originallyAllowed }
            scenario.close()
        }
    }

    @Test fun realNotificationIsCapturedAndContentUpdatesAreLogged() {
        // 検証AVDで通知アクセスと通知送信の許可を付与してから実行する。
        assertTrue("検証AVDの通知アクセスが必要", notificationAccessEnabled(context))
        // instrumentationの停止でOSが再接続を抑制するため、検証用許可を付け直す。
        val component = android.content.ComponentName(context, NotificationLogListener::class.java).flattenToString()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        for (action in listOf("disallow_listener", "allow_listener")) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(
                "cmd notification $action $component")).use { it.readBytes() }
        }
        compose.waitUntil(20000) { NotificationLogConnection.connected.value }
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = "notification-log-test"
        val token = "通知記録テスト-${System.nanoTime()}"
        val store = NotificationLogStore.get(context)
        manager.createNotificationChannel(NotificationChannel(channel, "通知ログ検証", NotificationManager.IMPORTANCE_DEFAULT))
        val postedAt = System.currentTimeMillis()
        fun post(body: String, sentAt: Long = postedAt) = manager.notify(74032, Notification.Builder(context, channel)
            .setWhen(sentAt).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(token).setContentText(body).build())
        fun rows() = store.list(false, 1000).filter { it.title == token }
        try {
            val before = System.currentTimeMillis()
            post("最初の本文")
            compose.waitUntil(15000) { rows().size == 1 }
            assertEquals("最初の本文", rows().single().body)
            assertTrue(rows().single().receivedAt >= before)
            post("最初の本文")
            Thread.sleep(600)
            assertEquals(1, rows().size)
            post("更新された本文")
            compose.waitUntil(15000) { rows().size == 2 }
            post("更新された本文", postedAt + 1000)
            compose.waitUntil(15000) { rows().size == 3 }
            manager.cancel(74032)
            assertEquals(3, rows().size)
        } finally {
            manager.cancel(74032)
            manager.deleteNotificationChannel(channel)
            rows().forEach { store.delete(it.id) }
        }
    }
}

private object SQLiteFiles {
    fun delete(file: File) {
        // テスト専用DBと補助ファイルだけを削除する。
        listOf(file, File(file.path + "-journal"), File(file.path + "-wal"), File(file.path + "-shm")).forEach { it.delete() }
    }
}
