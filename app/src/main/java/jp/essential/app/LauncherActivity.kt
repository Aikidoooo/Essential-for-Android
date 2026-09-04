package jp.essential.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** アイコン用の起動口を実画面から分離し、アイコン切り替え時のタスク終了を防ぐ。 */
class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION,
        ))
        finish()
    }
}
