package jp.essential.app.core

object EssentialCore {
    private val isLoaded = runCatching {
        System.loadLibrary("essential_core")
        true
    }.getOrDefault(false)

    fun version(): Int = if (isLoaded) {
        runCatching { nativeCoreVersion() }.getOrDefault(0)
    } else {
        0
    }

    fun pulse(seed: Long): Long? = if (isLoaded) {
        runCatching { nativePulse(seed) }.getOrNull()
    } else {
        null
    }

    private external fun nativeCoreVersion(): Int

    private external fun nativePulse(seed: Long): Long
}
