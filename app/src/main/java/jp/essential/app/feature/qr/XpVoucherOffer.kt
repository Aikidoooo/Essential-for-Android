package jp.essential.app.feature.qr

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import jp.essential.app.feature.widgets.WidgetGlass
import jp.essential.app.feature.widgets.WidgetButton
import jp.essential.app.profile.AppProgressStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 署名を検証して内容を提示し、明示的な操作でだけXPを受け取る。 */
@Composable
internal fun XpVoucherOffer(value: String) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var grant by remember(value) { mutableStateOf<XpVoucher.Grant?>(null) }
    var message by remember(value) { mutableStateOf("署名を確認しています…") }
    var busy by remember(value) { mutableStateOf(true) }
    var finished by remember(value) { mutableStateOf(false) }
    LaunchedEffect(value) {
        val result = withContext(Dispatchers.IO) { runCatching {
            val modulus = context.assets.open("xp-issuer-public.txt").bufferedReader().use { it.readText() }
            XpVoucher.verify(value, modulus)
        } }
        grant = result.getOrNull()
        message = result.fold({ "正規の配布署名を確認しました。${it.points} XP" }, { it.message ?: "署名を確認できませんでした" })
        busy = false
    }
    WidgetGlass(index = 0) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Essential XP配布")
            Text(message)
            if (grant != null && !finished) {
                WidgetButton(text = if (busy) "受取中…" else "XPを受け取る", onClick = {
                    if (!busy) {
                        busy = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { runCatching {
                                // ボタン押下時にも期限と署名を再確認する。
                                AppProgressStore(context).redeemQrXp(value)
                            } }
                            message = result.fold({ if (it) "${grant?.points} XPを受け取りました" else "このQRは受取済みです" },
                                { it.message ?: "受取に失敗しました" })
                            finished = result.isSuccess
                            busy = false
                        }
                    }
                }, enabled = !busy)
            }
        }
    }
}
