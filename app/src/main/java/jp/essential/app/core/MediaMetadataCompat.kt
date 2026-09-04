package jp.essential.app.core

import android.media.MediaMetadataRetriever

/** API 26でもAutoCloseableへのキャストなしで確実に解放する。 */
internal inline fun <T> withMetadataRetriever(block: (MediaMetadataRetriever) -> T): T {
    val retriever = MediaMetadataRetriever()
    try { return block(retriever) } finally { retriever.release() }
}
