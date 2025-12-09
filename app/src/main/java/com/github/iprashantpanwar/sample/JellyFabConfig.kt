package com.github.iprashantpanwar.sample

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
@Stable
data class JellyFabConfig(
    val expandStagger: Long = 150L,
    val collapseStagger: Long = 100L,
    val secondaryExpandStagger: Long = 150L,
    val secondaryCollapseStagger: Long = 80L,
    val shadowOpacity: Float = 0.55f,
    val shadowBlurFactor: Float = 0.2f,
    val bounceFactor: Float = 0.18f,
    val secondaryLayerSpacingMultiplier: Float = 1.8f,
    val layerDistance: Dp = 80.dp,
)

