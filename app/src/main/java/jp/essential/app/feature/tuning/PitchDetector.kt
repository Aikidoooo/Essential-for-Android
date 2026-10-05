package jp.essential.app.feature.tuning

import kotlin.math.*

internal data class TuningPreset(val name: String, val midiNotes: List<Int>)
internal val tuningPresets = listOf(
    TuningPreset("ギター・標準", listOf(40, 45, 50, 55, 59, 64)),
    TuningPreset("ギター・Drop D", listOf(38, 45, 50, 55, 59, 64)),
    TuningPreset("ギター・半音下げ", listOf(39, 44, 49, 54, 58, 63)),
    TuningPreset("ウクレレ・High G", listOf(67, 60, 64, 69)),
    TuningPreset("ウクレレ・Low G", listOf(55, 60, 64, 69)),
    TuningPreset("ベース・4弦", listOf(28, 33, 38, 43)),
    TuningPreset("バイオリン", listOf(55, 62, 69, 76)),
)
internal fun noteLabel(midi: Int): String = listOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")[Math.floorMod(midi, 12)] + (midi / 12 - 1)
internal fun noteFrequency(midi: Int, reference: Int = 440): Double = reference * 2.0.pow((midi - 69) / 12.0)
internal fun centsFrom(frequency: Double, target: Double): Double = 1200 * ln(frequency / target) / ln(2.0)

/** YINの正規化差分で基本周波数を推定し、無音と周期性の低い入力を除く。 */
internal fun detectPitch(samples: ShortArray, sampleRate: Int): Double? {
    if (samples.size < 2048) return null
    val mean = samples.map { it.toDouble() }.average()
    val rms = sqrt(samples.sumOf { (it - mean).pow(2) } / samples.size)
    if (rms < 120) return null
    val minLag = (sampleRate / 1400).coerceAtLeast(2)
    val maxLag = minOf(sampleRate / 35, samples.size / 2 - 1)
    val window = samples.size - maxLag
    val normalized = DoubleArray(maxLag + 1)
    var cumulative = 0.0
    for (lag in 1..maxLag) {
        var difference = 0.0
        for (index in 0 until window) {
            val delta = samples[index].toDouble() - samples[index + lag]
            difference += delta * delta
        }
        cumulative += difference
        normalized[lag] = if (cumulative > 0) difference * lag / cumulative else 1.0
    }
    var candidate = minLag
    while (candidate < maxLag) {
        if (normalized[candidate] < .15) {
            while (candidate < maxLag && normalized[candidate + 1] < normalized[candidate]) candidate++
            break
        }
        candidate++
    }
    if (candidate == maxLag || normalized[candidate] > .25) return null
    val left = normalized[candidate - 1]
    val middle = normalized[candidate]
    val right = normalized.getOrElse(candidate + 1) { middle }
    val divisor = 2 * (2 * middle - right - left)
    val adjusted = candidate + if (abs(divisor) > 1e-12) (right - left) / divisor else 0.0
    return (sampleRate / adjusted).takeIf { it.isFinite() && it in 35.0..1400.0 }
}
