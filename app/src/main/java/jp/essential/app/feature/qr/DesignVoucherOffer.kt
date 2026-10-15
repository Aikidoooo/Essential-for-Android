package jp.essential.app.feature.qr

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import jp.essential.app.AppIconManager
import jp.essential.app.design.AppDesignAccessStore
import jp.essential.app.design.AppDesignExpiry
import jp.essential.app.feature.widgets.WidgetButton
import jp.essential.app.feature.widgets.WidgetGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 読み取っただけでは登録せず、署名と期間を提示して受け取り操作を待つ。 */
@Composable
internal fun DesignVoucherOffer(value: String, storeOverride: AppDesignAccessStore? = null, modulusOverride: String? = null) {
    val context = LocalContext.current
    val store = remember(context, storeOverride) { storeOverride ?: AppDesignAccessStore(context) }
    val scope = rememberCoroutineScope()
    var grant by remember(value) { mutableStateOf<DesignVoucher.Grant?>(null) }
    var message by remember(value) { mutableStateOf("署名を確認しています…") }
    var busy by remember(value) { mutableStateOf(true) }
    var finished by remember(value) { mutableStateOf(false) }
    val format = remember { DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm") }
    fun date(time: Long) = format.format(Instant.ofEpochSecond(time).atZone(ZoneId.systemDefault()))
    LaunchedEffect(value) {
        val result = withContext(Dispatchers.IO) { runCatching {
            val key = modulusOverride ?: context.assets.open("design-issuer-public.txt").bufferedReader().use { it.readText() }
            DesignVoucher.verify(value, key, AppIconManager.options.map { it.id }.toSet(), store.effectiveNow())
        } }
        grant = result.getOrNull()
        message = result.fold({ "正規のデザイン配布署名を確認しました" }, { it.message ?: "署名を確認できませんでした" })
        busy = false
    }
    WidgetGlass(index = 0) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Essential アプリデザイン配布")
            Text(message)
            grant?.let { item ->
                Text(AppIconManager.optionForId(item.designId).label)
                Text("${date(item.startsAt)} 〜 ${date(item.endsAt)}")
                Text("受け取り後、プロフィールの設定 → アプリデザインから選べます。")
                if (!finished) WidgetButton(text = if (busy) "受取中…" else "デザインを受け取る", enabled = !busy, onClick = {
                    if (!busy) {
                        busy = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { runCatching {
                                val accepted = store.redeem(value)
                                if (storeOverride == null) AppDesignExpiry.reconcile(context.applicationContext)
                                accepted
                            } }
                            message = result.fold({ if (it) "期間限定デザインを受け取りました" else "このQRは受取済みです" },
                                { it.message ?: "受取に失敗しました" })
                            finished = result.isSuccess
                            busy = false
                        }
                    }
                })
            }
        }
    }
}
