package jp.essential.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

/** 端末内とインストール済みアプリの両方からメディアを読み込むIntent契約。 */
class EssentialMediaPickerContract : ActivityResultContract<Array<String>, Uri?>() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        return Intent(Intent.ACTION_GET_CONTENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, input)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
            // クラウドを含む外部DocumentProviderも候補に含める。
            .putExtra(Intent.EXTRA_LOCAL_ONLY, false)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return intent?.data?.takeIf { resultCode == Activity.RESULT_OK }
    }
}
