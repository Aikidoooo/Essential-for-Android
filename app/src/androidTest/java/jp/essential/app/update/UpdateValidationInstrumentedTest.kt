package jp.essential.app.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateValidationInstrumentedTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun 現在と同じAPKは更新として受け付けない() {
        val result = runCatching { GitHubUpdateRepository(context).validateApk(File(context.applicationInfo.sourceDir)) }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("versionCode"))
    }

    @Test fun 別のアプリIDを持つAPKを拒否する() {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val result = runCatching { GitHubUpdateRepository(context).validateApk(File(testContext.applicationInfo.sourceDir)) }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("applicationId"))
    }

    @Test fun 壊れたAPKを拒否する() {
        val file = File(context.cacheDir, "invalid-update-test.apk")
        try {
            file.writeText("APKではありません")
            assertTrue(runCatching { GitHubUpdateRepository(context).validateApk(file) }.isFailure)
        } finally { file.delete() }
    }
}
