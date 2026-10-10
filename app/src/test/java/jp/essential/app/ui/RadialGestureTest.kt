package jp.essential.app.ui

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class RadialGestureTest {
    @Test fun acceptsFastSparseAndUnevenCircles() {
        val sparse = (0..8).map { val angle = it * 2 * PI / 8; CirclePoint(200f + cos(angle).toFloat() * 90f, 200f + sin(angle).toFloat() * 90f) }
        assertTrue(isRadialCircle(sparse, 80, 54f))
        assertTrue(isRadialCircle(circle().mapIndexed { index, point -> CirclePoint(point.x + if (index % 2 == 0) 5 else -5, point.y * .75f) }, 180, 54f))
    }
    private fun circle(clockwise: Boolean = true) = (0..48).map {
        val angle = it * 2 * PI / 48 * if (clockwise) 1 else -1
        CirclePoint(200f + cos(angle).toFloat() * 90f, 200f + sin(angle).toFloat() * 90f)
    }
    @Test fun acceptsBothDirectionsAndDeadline() {
        assertTrue(isRadialCircle(circle(), 1100, 64f))
        assertTrue(isRadialCircle(circle(false), 650, 64f))
        assertFalse(isRadialCircle(circle(), 1101, 64f))
    }
    @Test fun rejectsArcLineAndBacktracking() {
        assertFalse(isRadialCircle(circle().take(30), 600, 64f))
        assertFalse(isRadialCircle((0..48).map { CirclePoint(it * 5f, 100f) }, 600, 64f))
        assertFalse(isRadialCircle(circle().take(25) + circle().take(25).reversed(), 600, 64f))
        assertFalse(isRadialCircle(circle().map { CirclePoint(it.x / 5, it.y / 5) }, 600, 64f))
    }
    @Test fun acceptsScreenTwentiethWithoutAcceptingTouchNoise() {
        val smallCircle = circle().map { CirclePoint(it.x * .3f, it.y * .3f) }
        assertTrue(isRadialCircle(smallCircle, 720, 1080f / 20))
        assertFalse(isRadialCircle(circle().map { CirclePoint(it.x * .01f, it.y * .01f) }, 720, 1080f / 20))
    }
}
