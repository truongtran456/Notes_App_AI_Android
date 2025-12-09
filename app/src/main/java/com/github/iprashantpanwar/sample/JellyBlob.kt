package com.github.iprashantpanwar.sample

import android.graphics.BlurMaskFilter
import android.graphics.Paint as AndroidPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun JellyBlob(
    modifier: Modifier = Modifier,
    size: Dp,
    color: Color,
    bulges: List<Float>,
    shadowOpacity: Float,
    shadowBlurFactor: Float,
    bulgeAngles: List<Double>,
    bounceFactor: Float,
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val baseR = min(w, h) * 0.45f
        val bulgeValues = bulges.map { it }
        val topBulge = bulgeValues.getOrNull(0) ?: 0f
        val diaBulge = bulgeValues.getOrNull(1) ?: 0f
        val leftBulge = bulgeValues.getOrNull(2) ?: 0f
        val bulgeInfluence = (abs(topBulge) + abs(leftBulge) + abs(diaBulge)) / 3f
        val shadowSpread = 1.1f - 0.08f * bulgeInfluence
        val shadowAlpha = shadowOpacity + 0.1f * bulgeInfluence
        val shadowLift = -topBulge * baseR * 0.1f
        val shadowPaint = AndroidPaint().apply {
            isAntiAlias = true
            style = AndroidPaint.Style.FILL
            this.color = color.copy(alpha = shadowAlpha).toArgb()
            maskFilter = BlurMaskFilter(baseR * shadowBlurFactor * shadowSpread, BlurMaskFilter.Blur.NORMAL)
        }
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawCircle(
                cx,
                cy + baseR * 0.1f + shadowLift,
                baseR * shadowSpread,
                shadowPaint
            )
        }
        val sampleCount = 36
        val pts = MutableList(sampleCount) { i ->
            val a = -Math.PI / 2 + i * (2 * Math.PI / sampleCount)
            Offset((cx + baseR * cos(a)).toFloat(), (cy + baseR * sin(a)).toFloat())
        }
        for ((idx, deg) in bulgeAngles.withIndex()) {
            val v = bulges.getOrNull(idx) ?: 0f
            if (v != 0f) {
                val push = baseR * bounceFactor * v
                val aRad = Math.toRadians(deg)
                for (i in pts.indices) {
                    val p = pts[i]
                    val vec = p - Offset(cx, cy)
                    val pAng = atan2(vec.y, vec.x)
                    val diff = normalizeAngleRad((pAng - aRad).toFloat())
                    val fall = exp(-(diff * diff) / (2 * 0.6f * 0.6f))
                    val len = sqrt(vec.x * vec.x + vec.y * vec.y)
                    val newLen = len + push * fall
                    pts[i] = Offset(cx + cos(pAng) * newLen, cy + sin(pAng) * newLen)
                }
            }
        }
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val prev = pts[i - 1]
                val cur = pts[i]
                val mid = Offset((prev.x + cur.x) / 2, (prev.y + cur.y) / 2)
                quadraticTo(prev.x, prev.y, mid.x, mid.y)
            }
            close()
        }
        drawPath(path, color)
    }
}

