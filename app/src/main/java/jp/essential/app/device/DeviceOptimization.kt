package jp.essential.app.device

import android.os.Build
import android.util.Size
import java.util.Locale

enum class OptimizationTier {
    Highest,
    Priority,
    General,
}

data class DeviceOptimization(
    val tier: OptimizationTier,
    val label: String,
    val qrAnalysisSize: Size,
)

object DeviceOptimizer {
    fun current(): DeviceOptimization {
        val identity = "${Build.MANUFACTURER} ${Build.BRAND} ${Build.MODEL} ${Build.DEVICE}"
            .lowercase(Locale.ROOT)

        val highestPriorityNames = listOf(
            "xiaomi 17 ultra",
            "xiaomi 15 ultra",
            "xiaomi 15t pro",
            "leitz phone",
        )
        val priorityNames = listOf(
            "xiaomi 17t pro",
            "xiaomi 17t",
            "xiaomi 15t",
            "find x9 ultra",
            "find x9",
            "sm-s93",
            "sm-s94",
            "pixel",
        )

        return when {
            highestPriorityNames.any(identity::contains) -> DeviceOptimization(
                tier = OptimizationTier.Highest,
                label = "最優先端末プロファイル",
                qrAnalysisSize = Size(1280, 720),
            )
            priorityNames.any(identity::contains) -> DeviceOptimization(
                tier = OptimizationTier.Priority,
                label = "優先端末プロファイル",
                qrAnalysisSize = Size(1280, 720),
            )
            else -> DeviceOptimization(
                tier = OptimizationTier.General,
                label = "Android標準プロファイル",
                qrAnalysisSize = Size(1280, 720),
            )
        }
    }
}
