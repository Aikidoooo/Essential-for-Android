package jp.essential.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes

/** ランチャーアイコンと、アイコンに合わせたアプリ背景を管理する。 */
internal data class AppIconOption(
    val id: String,
    val label: String,
    val description: String,
    @DrawableRes val iconRes: Int,
    val prefersDarkTheme: Boolean,
    val backgroundStart: Int,
    val backgroundEnd: Int,
    val unlockLevel: Int,
    val aliasName: String,
)

internal object AppIconManager {
    private const val PREFERENCES = "appearance"
    private const val SELECTED_ICON_KEY = "app_icon_style"

    val options: List<AppIconOption> = listOf(
        AppIconOption(
            id = "default_light",
            label = "Essential Light",
            description = "標準ライトアイコン",
            iconRes = R.drawable.essential_icon,
            prefersDarkTheme = false,
            backgroundStart = 0xFFFFF9EB.toInt(),
            backgroundEnd = 0xFFD9F894.toInt(),
            unlockLevel = 0,
            aliasName = ".LightLauncher",
        ),
        AppIconOption(
            id = "default_dark",
            label = "Essential Dark",
            description = "標準ダークアイコン",
            iconRes = R.drawable.essential_icon_dark,
            prefersDarkTheme = true,
            backgroundStart = 0xFF101018.toInt(),
            backgroundEnd = 0xFF363C52.toInt(),
            unlockLevel = 0,
            aliasName = ".DarkLauncher",
        ),
        rewardOption("mint", "Anemo Essential", "LV16報酬・アネモ", R.drawable.routine_reward_lv16, false, 16, ".RewardLv16Launcher", 0xFFE9FFF7.toInt(), 0xFF9BE9D1.toInt()),
        rewardOption("sunset", "Geo Essential", "LV20報酬・ジオ", R.drawable.routine_reward_lv20, false, 20, ".RewardLv20Launcher", 0xFFFFF5D2.toInt(), 0xFFFFB77A.toInt()),
        rewardOption("violet", "Electro Essential", "LV25報酬・エレクトロ", R.drawable.routine_reward_lv25, true, 25, ".RewardLv25Launcher", 0xFFEBDFFF.toInt(), 0xFF8A59E8.toInt()),
        rewardOption("forest", "Dendro Essential", "LV30報酬・デンドロ", R.drawable.routine_reward_lv30, false, 30, ".RewardLv30Launcher", 0xFFE8FFD8.toInt(), 0xFF67D97A.toInt()),
        rewardOption("ocean", "Hydro Essential", "LV35報酬・ハイドロ", R.drawable.routine_reward_lv35, true, 35, ".RewardLv35Launcher", 0xFFDCEBFF.toInt(), 0xFF4D78E8.toInt()),
        rewardOption("ruby", "Pyro Essential", "LV40報酬・パイロ", R.drawable.routine_reward_lv40, true, 40, ".RewardLv40Launcher", 0xFFFFDFE8.toInt(), 0xFFE65B6E.toInt()),
        rewardOption("sky", "Lunar Essential", "LV50報酬・ルナ", R.drawable.routine_reward_lv50, false, 50, ".RewardLv50Launcher", 0xFFE3F8FF.toInt(), 0xFF6BCBFF.toInt()),
        rewardOption("rose", "Cryo Essential", "LV55報酬・クライオ", R.drawable.routine_reward_lv55, false, 55, ".RewardLv55Launcher", 0xFFE3F8FF.toInt(), 0xFF6BCBFF.toInt()),
        rewardOption("reference", "Rose Essential", "LV60報酬・ローズ／アイコン変更権", R.drawable.routine_reward_lv60, true, 60, ".RewardLv60Launcher", 0xFFFFE4F5.toInt(), 0xFFFF65B7.toInt()),
    )

    val defaultLight: AppIconOption = options.first()
    val defaultDark: AppIconOption = options[1]

    fun optionForId(id: String?): AppIconOption = options.firstOrNull { it.id == id } ?: defaultLight

    fun load(context: Context): AppIconOption {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val saved = preferences.getString(SELECTED_ICON_KEY, null)
        if (saved != null) return optionForId(saved)
        val dark = preferences.getBoolean("dark_theme", false)
        return if (dark) defaultDark else defaultLight
    }

    fun save(context: Context, option: AppIconOption) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(SELECTED_ICON_KEY, option.id).apply()
    }

    /** 選択した別名を有効化し、同時に他のランチャー入口を無効化する。 */
    fun apply(context: Context, option: AppIconOption) {
        val manager = context.packageManager
        // Application IDへ接尾辞が付くDebug版でも、別名クラスはソースのnamespace内に存在する。
        val namespace = AppIconManager::class.java.name.substringBeforeLast('.')
        val components = options.map { ComponentName(context, namespace + it.aliasName) }
        val target = ComponentName(context, namespace + option.aliasName)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            manager.setComponentEnabledSettings(
                components.map { component ->
                    PackageManager.ComponentEnabledSetting(
                        component,
                        if (component == target) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                },
            )
        } else {
            manager.setComponentEnabledSetting(target, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            components.filterNot { it == target }.forEach { component ->
                manager.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        }
        save(context, option)
    }

    private fun rewardOption(
        id: String,
        label: String,
        description: String,
        @DrawableRes iconRes: Int,
        prefersDarkTheme: Boolean,
        unlockLevel: Int,
        aliasName: String,
        backgroundStart: Int,
        backgroundEnd: Int,
    ) = AppIconOption(id, label, description, iconRes, prefersDarkTheme, backgroundStart, backgroundEnd, unlockLevel, aliasName)
}
