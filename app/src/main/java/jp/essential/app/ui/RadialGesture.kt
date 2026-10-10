package jp.essential.app.ui

import kotlin.math.*

internal data class CirclePoint(val x: Float, val y: Float)

/** 一筆の閉じた円だけを認識し、往復や弧、長い操作は除外する。 */
internal fun isRadialCircle(points: List<CirclePoint>, durationMillis: Long, minimumDiameterPixels: Float): Boolean {
    if (durationMillis !in 1..1100 || points.size < 6) return false
    val width = points.maxOf { it.x } - points.minOf { it.x }
    val height = points.maxOf { it.y } - points.minOf { it.y }
    if (min(width, height) < minimumDiameterPixels.coerceAtLeast(2f) * .95f || width / height !in .5f..2f) return false
    val cx = (points.maxOf { it.x } + points.minOf { it.x }) / 2f
    val cy = (points.maxOf { it.y } + points.minOf { it.y }) / 2f
    val radius = (width + height) / 4f
    if (hypot(points.last().x - points.first().x, points.last().y - points.first().y) > radius) return false
    // 等間隔の経路へ補間し、高速入力の細かな揺れを角度の逆走と誤認しない。
    val distances = FloatArray(points.size)
    for (index in 1 until points.size) distances[index] = distances[index - 1] + hypot(points[index].x - points[index - 1].x, points[index].y - points[index - 1].y)
    val sampled = ArrayList<CirclePoint>(33)
    var segment = 1
    for (index in 0..32) {
        val target = distances.last() * index / 32f
        while (segment < points.lastIndex && distances[segment] < target) segment++
        val fraction = ((target - distances[segment - 1]) / (distances[segment] - distances[segment - 1]).coerceAtLeast(.001f)).coerceIn(0f, 1f)
        val a = points[segment - 1]
        val b = points[segment]
        sampled.add(CirclePoint(a.x + (b.x - a.x) * fraction, a.y + (b.y - a.y) * fraction))
    }
    var turn = 0.0
    var travel = 0.0
    var length = 0f
    var previousAngle = atan2(sampled.first().y - cy, sampled.first().x - cx).toDouble()
    for (index in 1 until sampled.size) {
        val point = sampled[index]
        if (hypot(point.x - cx, point.y - cy) !in radius * .3f..radius * 1.7f) return false
        val angle = atan2(point.y - cy, point.x - cx).toDouble()
        val delta = atan2(sin(angle - previousAngle), cos(angle - previousAngle))
        turn += delta
        travel += abs(delta)
        length += hypot(point.x - sampled[index - 1].x, point.y - sampled[index - 1].y)
        previousAngle = angle
    }
    return abs(turn) in 5.0..7.4 && travel < abs(turn) * 1.5 && length in radius * 4.5f..radius * 9f
}
