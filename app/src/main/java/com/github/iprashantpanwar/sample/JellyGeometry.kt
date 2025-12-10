package com.github.iprashantpanwar.sample

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

internal fun evenArcAngles(
    startDeg: Double = 270.0,
    endDeg: Double = 180.0,
    count: Int,
): List<Double> {
    if (count <= 0) return emptyList()
    if (count == 1) return listOf(startDeg)
    val step = (endDeg - startDeg) / (count - 1)
    return List(count) { index -> startDeg + index * step }
}

internal fun arcOffset(angleDeg: Double, radius: Float): Offset {
    val rad = Math.toRadians(angleDeg)
    return Offset((cos(rad) * radius).toFloat(), (-sin(rad) * radius).toFloat())
}

internal fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)

internal fun lerpOffset(a: Offset, b: Offset, t: Float): Offset =
    Offset(lerp(a.x, b.x, t), lerp(a.y, b.y, t))

internal fun normalizeAngleRad(a: Float): Float {
    var x = a % (2 * Math.PI).toFloat()
    if (x > Math.PI) x -= (2 * Math.PI).toFloat()
    if (x < -Math.PI) x += (2 * Math.PI).toFloat()
    return x
}
