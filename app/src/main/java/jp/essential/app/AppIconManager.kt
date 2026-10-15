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
    val backgroundStart: Int,
    val backgroundEnd: Int,
    val unlockLevel: Int,
    val aliasName: String,
) {
    /** 各アイコンの色相を共有し、表示モードに合わせた背景の明るさを返す。 */
    fun backgroundColors(dark: Boolean): IntArray {
        val base = if (dark) 0xFF0D1018.toInt() else 0xFFFFFCF7.toInt()
        val intensity = if (dark) 0.32f else 0.22f
        val fullGradient = id in setOf("mint", "sunset", "violet", "forest", "ocean", "ruby", "sky", "rose")
        // 八つの人物テーマは全画面で濃淡が分かるよう、始点と終点の強度を分ける。
        val startIntensity = if (fullGradient) { if (dark) .48f else .54f } else intensity
        val endIntensity = if (fullGradient) { if (dark) .10f else .30f } else intensity
        val highlight = if (fullGradient && dark) androidx.core.graphics.ColorUtils.setAlphaComponent(androidx.core.graphics.ColorUtils.blendARGB(backgroundStart, android.graphics.Color.WHITE, .04f), 255) else backgroundStart
        var adjustedIntensity = startIntensity
        var startColor = androidx.core.graphics.ColorUtils.setAlphaComponent(androidx.core.graphics.ColorUtils.blendARGB(base, highlight, adjustedIntensity), 255)
        // 明るいテーマでも白文字の読みやすさを確保し、濃淡は深い終点色で強調する。
        if (fullGradient && dark) {
            while (adjustedIntensity > .10f && androidx.core.graphics.ColorUtils.calculateContrast(android.graphics.Color.WHITE, startColor) < 4.8) {
                adjustedIntensity -= .01f
                startColor = androidx.core.graphics.ColorUtils.setAlphaComponent(androidx.core.graphics.ColorUtils.blendARGB(base, highlight, adjustedIntensity), 255)
            }
        }
        return intArrayOf(
            startColor,
            androidx.core.graphics.ColorUtils.setAlphaComponent(androidx.core.graphics.ColorUtils.blendARGB(base, backgroundEnd, endIntensity), 255),
        )
    }
}

internal object AppIconManager {
    private const val PREFERENCES = "appearance"
    private const val SELECTED_ICON_KEY = "app_icon_style"
    private const val LAUNCHER_COMPONENT_NAMESPACE = "jp.essential.app"

    val options: List<AppIconOption> = listOf(
        AppIconOption(
            id = "default_light",
            label = "Essential Light",
            description = "標準ライトアイコン",
            iconRes = R.drawable.essential_icon,
            backgroundStart = 0xFFB5D52A.toInt(),
            backgroundEnd = 0xFFFF6508.toInt(),
            unlockLevel = 0,
            aliasName = ".LightLauncher",
        ),
        AppIconOption(
            id = "default_dark",
            label = "Essential Dark",
            description = "標準ダークアイコン",
            iconRes = R.drawable.essential_icon_dark,
            backgroundStart = 0xFF53545C.toInt(),
            backgroundEnd = 0xFF090A0E.toInt(),
            unlockLevel = 0,
            aliasName = ".DarkLauncher",
        ),
        rewardOption("mint", "Anemo Essential", "LV16報酬・アネモ", R.drawable.routine_reward_lv16, 16, ".RewardLv16Launcher", 0xFFDDFCF5.toInt(), 0xFF00AF91.toInt()),
        rewardOption("sunset", "Geo Essential", "LV20報酬・ジオ", R.drawable.routine_reward_lv20, 20, ".RewardLv20Launcher", 0xFFFFE83C.toInt(), 0xFFFF6507.toInt()),
        rewardOption("violet", "Electro Essential", "LV25報酬・エレクトロ", R.drawable.routine_reward_lv25, 25, ".RewardLv25Launcher", 0xFFC45AFF.toInt(), 0xFF3000B9.toInt()),
        rewardOption("forest", "Dendro Essential", "LV30報酬・デンドロ", R.drawable.routine_reward_lv30, 30, ".RewardLv30Launcher", 0xFFB2F42B.toInt(), 0xFF00732E.toInt()),
        rewardOption("ocean", "Hydro Essential", "LV35報酬・ハイドロ", R.drawable.routine_reward_lv35, 35, ".RewardLv35Launcher", 0xFF2ABEFF.toInt(), 0xFF0814BC.toInt()),
        rewardOption("ruby", "Pyro Essential", "LV40報酬・パイロ", R.drawable.routine_reward_lv40, 40, ".RewardLv40Launcher", 0xFFECE9F0.toInt(), 0xFFD00021.toInt()),
        rewardOption("sky", "Lunar Essential", "LV50報酬・ルナ", R.drawable.routine_reward_lv50, 50, ".RewardLv50Launcher", 0xFF858DDD.toInt(), 0xFFDCE6FC.toInt()),
        rewardOption("rose", "Cryo Essential", "LV55報酬・クライオ", R.drawable.routine_reward_lv55, 55, ".RewardLv55Launcher", 0xFFE3F8FF.toInt(), 0xFF009DFF.toInt()),
        rewardOption("reference", "Ruler of Death", "LV60報酬・死の執政", R.drawable.routine_reward_lv60, 60, ".RewardLv60Launcher", 0xFFFFF0F7.toInt(), 0xFFFF28AB.toInt()),
    )

    val defaultLight: AppIconOption = options.first()
    val defaultDark: AppIconOption = options[1]

    fun optionForId(id: String?): AppIconOption = options.firstOrNull { it.id == id } ?: defaultLight

    fun permanentlyAvailable(context: Context, option: AppIconOption): Boolean = option.unlockLevel == 0 ||
        jp.essential.app.profile.calculateAppLevel(jp.essential.app.profile.AppProgressStore(context).loadXp()).level >= option.unlockLevel

    fun available(context: Context, option: AppIconOption): Boolean = permanentlyAvailable(context, option) ||
        jp.essential.app.design.AppDesignAccessStore(context).activeUntil(option.id) != null

    fun load(context: Context): AppIconOption {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val saved = preferences.getString(SELECTED_ICON_KEY, null)
        val selected = optionForId(saved)
        if (saved != null && available(context, selected)) return selected
        val fallback = optionForId(preferences.getString("app_design_normal", null))
        if (preferences.contains("app_design_normal") && permanentlyAvailable(context, fallback)) return fallback
        return if (preferences.getBoolean("dark_theme", false)) defaultDark else defaultLight
    }

    fun save(context: Context, option: AppIconOption) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val editor = preferences.edit()
        if (permanentlyAvailable(context, option)) editor.putString("app_design_normal", option.id)
        else if (!preferences.contains("app_design_normal")) {
            // 最初の期間限定選択でも、直前の通常デザインを失わない。
            editor.putString("app_design_normal", load(context).id)
        }
        editor.putString(SELECTED_ICON_KEY, option.id).apply()
    }

    /** 選択した別名を有効化し、同時に他のランチャー入口を無効化する。 */
    fun apply(context: Context, requested: AppIconOption) {
        val option = if (available(context, requested)) requested else load(context)
        val manager = context.packageManager
        // R8でこのクラス名が変更されても、Manifestのactivity-alias名は変わらない。
        val components = options.map { ComponentName(context, launcherClassName(it.aliasName)) }
        val target = ComponentName(context, launcherClassName(option.aliasName))
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

    /** Manifestへ登録したランチャー別名の完全修飾名を返す。 */
    internal fun launcherClassName(aliasName: String): String {
        require(aliasName.startsWith('.')) { "ランチャー別名はピリオドから始める必要があります" }
        return LAUNCHER_COMPONENT_NAMESPACE + aliasName
    }

    private fun rewardOption(
        id: String,
        label: String,
        description: String,
        @DrawableRes iconRes: Int,
        unlockLevel: Int,
        aliasName: String,
        backgroundStart: Int,
        backgroundEnd: Int,
    ) = AppIconOption(id, label, description, iconRes, backgroundStart, backgroundEnd, unlockLevel, aliasName)
}
