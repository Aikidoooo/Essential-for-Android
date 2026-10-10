package jp.essential.app.feature.minigame

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import jp.essential.app.profile.AppProgressStore
import kotlinx.coroutines.delay

/** 前面で実際に遊んだ時間だけを積算し、30秒未満の端数も次回へ持ち越す。 */
@Composable
internal fun GamePlayXp(game: String, active: Boolean, points: Int) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(game, active, points, owner) {
        if (!active) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val preferences = context.getSharedPreferences("game_play_xp", 0)
            var remaining = preferences.getLong(game, 0L).coerceIn(0L, 29999L)
            var previous = SystemClock.elapsedRealtime()
            fun account() {
                val now = SystemClock.elapsedRealtime()
                remaining += (now - previous).coerceAtLeast(0L)
                previous = now
                val intervals = remaining / 30000L
                if (intervals > 0) AppProgressStore(context).addXp((intervals * points).toInt())
                remaining %= 30000L
                preferences.edit().putLong(game, remaining).apply()
            }
            try { while (true) { delay(1000); account() } }
            finally { account() }
        }
    }
}