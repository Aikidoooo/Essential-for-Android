package jp.essential.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AppIconManagerTest {
    @Test
    fun launcherClassNameUsesManifestNamespace() {
        assertEquals(
            "jp.essential.app.LightLauncher",
            AppIconManager.launcherClassName(".LightLauncher"),
        )
    }

    @Test
    fun launcherClassNameRejectsRelativeNameWithoutDot() {
        assertThrows(IllegalArgumentException::class.java) {
            AppIconManager.launcherClassName("LightLauncher")
        }
    }
}
