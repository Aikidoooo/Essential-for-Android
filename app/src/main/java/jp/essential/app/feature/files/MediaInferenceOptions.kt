package jp.essential.app.feature.files

import ai.onnxruntime.OrtSession

/** 待機時の空回りを抑え、画面処理にCPUの余裕を残す。 */
internal fun mediaInferenceOptions(lowMemory: Boolean = false) = OrtSession.SessionOptions().apply {
    setIntraOpNumThreads(2)
    setInterOpNumThreads(1)
    addConfigEntry("session.intra_op.allow_spinning", "0")
    addConfigEntry("session.inter_op.allow_spinning", "0")
    if (lowMemory) {
        // 大きな定数の展開とアリーナによる最大使用量の保持を避ける。
        setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
        setCPUArenaAllocator(false)
        setMemoryPatternOptimization(false)
    }
}
