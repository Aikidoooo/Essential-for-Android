package jp.essential.app.feature.qr

/** 新しいコードは即反映し、一時的な検出抜けだけを吸収する。メインスレッドで使用する。 */
internal class QrDetectionTracker {
    var value: String? = null
        private set
    private var lastSeenAt = 0L

    fun update(detected: String?, nowMillis: Long): Boolean {
        if (detected != null) lastSeenAt = nowMillis
        val next = detected ?: value.takeIf { nowMillis - lastSeenAt < 350L }
        if (next == value) return false
        value = next
        return true
    }
}
