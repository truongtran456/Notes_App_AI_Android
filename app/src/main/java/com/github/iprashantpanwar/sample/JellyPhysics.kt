package com.github.iprashantpanwar.sample

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal suspend fun playDoubleBounce(
    target: Animatable<Float, AnimationVector1D>,
    outward: Boolean,
    soft: Boolean = false,
) {
    val dir = if (outward) 1f else -1f
    val seq =
        if (soft) listOf(0f, 0.7f * dir, -0.3f * dir, 0.1f * dir, 0f)
        else listOf(0f, 1f * dir, -0.6f * dir, 0.3f * dir, 0f)

    for (i in 1 until seq.size) {
        target.animateTo(seq[i], tween(90, easing = LinearOutSlowInEasing))
    }
}

internal suspend fun expandPrimaryLayer(
    primaryProgress: List<Animatable<Float, AnimationVector1D>>,
    bulges: List<Animatable<Float, AnimationVector1D>>,
    config: JellyFabConfig,
    bounceLastBulge: suspend (bulge: Animatable<Float, AnimationVector1D>) -> Unit,
) = coroutineScope {
    primaryProgress.indices.forEach { i ->
        launch { primaryProgress[i].animateTo(1f, tween(400, easing = FastOutSlowInEasing)) }

        launch {
            bulges[i].snapTo(0f)
            bulges[i].animateTo(1f, tween(140, easing = LinearOutSlowInEasing))
            bulges[i].animateTo(0f, spring(dampingRatio = Spring.DampingRatioHighBouncy))
        }

        delay(config.expandStagger)

        if (i == primaryProgress.lastIndex) {
            bounceLastBulge(bulges[i])
        }
    }
}

internal suspend fun collapsePrimaryLayer(
    primaryProgress: List<Animatable<Float, AnimationVector1D>>,
    bulges: List<Animatable<Float, AnimationVector1D>>,
    config: JellyFabConfig,
    bounceTopBulge: suspend (bulge: Animatable<Float, AnimationVector1D>) -> Unit,
    bounceSideBulge: suspend (bulge: Animatable<Float, AnimationVector1D>) -> Unit,
) = coroutineScope {
    for (i in primaryProgress.indices) {
        launch {
            bulges[i].snapTo(0f)
            bulges[i].animateTo(0.3f, tween(100))
            bulges[i].animateTo(0f, tween(160))
        }
        launch { primaryProgress[i].animateTo(0f, tween(300, easing = FastOutSlowInEasing)) }
        delay(config.collapseStagger)
    }

    launch { bounceTopBulge(bulges.first()) }
    launch {
        delay(50)
        bounceSideBulge(bulges.last())
    }
}

internal suspend fun expandSecondaryLayer(
    secondaryProgress: List<Animatable<Float, AnimationVector1D>>,
    config: JellyFabConfig,
) = coroutineScope {
    secondaryProgress.indices.forEach { i ->
        launch {
            delay(i * config.secondaryExpandStagger)
            secondaryProgress[i].animateTo(1.06f, tween(320, easing = FastOutSlowInEasing))
            secondaryProgress[i].animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            )
        }
    }
}

internal suspend fun collapseSecondaryLayer(
    secondaryProgress: List<Animatable<Float, AnimationVector1D>>,
    config: JellyFabConfig,
) = coroutineScope {
    for (i in secondaryProgress.indices.reversed()) {
        launch {
            delay((secondaryProgress.size - 1 - i) * config.secondaryCollapseStagger)
            secondaryProgress[i].animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        }
    }
}

internal suspend fun collapseSecondaryThenPrimary(
    secondaryProgress: List<Animatable<Float, AnimationVector1D>>,
    primaryProgress: List<Animatable<Float, AnimationVector1D>>,
    bulges: List<Animatable<Float, AnimationVector1D>>,
    config: JellyFabConfig,
    bounceTop: suspend (Animatable<Float, AnimationVector1D>) -> Unit,
    bounceSide: suspend (Animatable<Float, AnimationVector1D>) -> Unit,
) = coroutineScope {
    collapseSecondaryLayer(secondaryProgress, config)
    delay(secondaryCollapseDuration(secondaryProgress.size, config))
    collapsePrimaryLayer(primaryProgress, bulges, config, bounceTop, bounceSide)
}

internal fun secondaryCollapseDuration(secondaryCount: Int, config: JellyFabConfig): Long {
    if (secondaryCount <= 0) return 0L
    val tailStagger = (secondaryCount - 1) * config.secondaryCollapseStagger
    return tailStagger + 200L
}
