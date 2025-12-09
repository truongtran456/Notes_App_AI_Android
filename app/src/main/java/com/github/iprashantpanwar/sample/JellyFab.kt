package com.github.iprashantpanwar.sample

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

@Composable
fun JellyFab(
    modifier: Modifier = Modifier,
    state: JellyFabState = rememberJellyFabState(),
    fabSize: Dp = 72.dp,
    miniFabSize: Dp = 48.dp,
    fabColor: Color = Color(0xFFFF7F86),
    secondLayerFabColor: Color = Color(0xFF6BA5FF),
    primaryItems: List<JellyFabItem>,
    secondaryItems: List<JellyFabItem> = emptyList(),
    showScrim: Boolean = true,
    config: JellyFabConfig = JellyFabConfig(),
) {
    val scope = rememberCoroutineScope()

    val primaryProgress = remember { primaryItems.map { Animatable(0f) } }
    val secondaryProgress = remember { secondaryItems.map { Animatable(0f) } }
    val bulges = remember { primaryItems.map { Animatable(0f) } }

    var primaryAnimJob by remember { mutableStateOf<Job?>(null) }
    var secondaryAnimJob by remember { mutableStateOf<Job?>(null) }

    var didRunOnce by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        primaryProgress.forEach { it.snapTo(if (state.expanded) 1f else 0f) }
        bulges.forEach { it.snapTo(0f) }
        secondaryProgress.forEach { it.snapTo(if (state.secondaryExpanded) 1f else 0f) }
    }

    LaunchedEffect(state.expanded) {
        primaryAnimJob?.cancel()
        if (!didRunOnce) {
            didRunOnce = true
            return@LaunchedEffect
        }
        primaryAnimJob = scope.launch {
            if (state.expanded) {
                expandPrimaryLayer(primaryProgress, bulges, config) { bulge ->
                    playDoubleBounce(bulge, outward = true, soft = true)
                }
            } else {
                collapsePrimaryLayer(
                    primaryProgress,
                    bulges,
                    config,
                    bounceTopBulge = { playDoubleBounce(it, outward = false) },
                    bounceSideBulge = { playDoubleBounce(it, outward = false) }
                )
            }
        }
    }

    LaunchedEffect(state.secondaryExpanded) {
        secondaryAnimJob?.cancel()
        secondaryAnimJob = scope.launch {
            if (state.secondaryExpanded) {
                expandSecondaryLayer(secondaryProgress, config)
            } else {
                collapseSecondaryLayer(secondaryProgress, config)
            }
        }
    }

    val centerCorrect = (miniFabSize - fabSize) / 2f

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        val scrimVisible = state.expanded || state.secondaryExpanded
        val scrimAlpha by animateFloatAsState(
            targetValue = if (scrimVisible) 1f else 0f,
            animationSpec = tween(250)
        )

        if (showScrim && scrimAlpha > 0.01f) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = scrimAlpha }
                    .background(Color.White.copy(alpha = 0.55f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        scope.launch {
                            state.secondaryExpanded = false
                            delay(secondaryCollapseDuration(secondaryItems.size, config))
                            state.expanded = false
                        }
                    }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            val primaryAngles = remember(primaryItems.size) { evenArcAngles(startDeg = 180.0, endDeg = 270.0, count = primaryItems.size) }
            val primaryPositions = remember { MutableList(primaryItems.size) { Offset.Zero } }

            // PRIMARY LAYER — executes item.onClick(); last item toggles secondary if present
            primaryItems.forEachIndexed { index, item ->
                val angleRad = Math.toRadians(primaryAngles[index])
                val p = primaryProgress[index].value
                val dx = (cos(angleRad) * config.layerDistance.value * p)
                val dy = (sin(angleRad) * config.layerDistance.value * p)
                primaryPositions[index] = Offset(dx.toFloat(), dy.toFloat())

                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            val isMorePrimary = (index == primaryItems.lastIndex) && secondaryItems.isNotEmpty()
                            if (isMorePrimary) {
                                // toggle secondary layer from the "more" primary (last item)
                                state.secondaryExpanded = !state.secondaryExpanded
                                state.expanded = true
                            } else {
                                // execute action and close everything
                                item.onClick()
                                state.secondaryExpanded = false
                                delay(secondaryCollapseDuration(secondaryItems.size, config))
                                state.expanded = false
                            }
                        }
                    },
                    modifier = Modifier
                        .offset(dx.dp + centerCorrect, dy.dp + centerCorrect)
                        .graphicsLayer {
                            scaleX = 0.8f + 0.2f * p
                            scaleY = 0.8f + 0.2f * p
                        }
                        .size(miniFabSize),
                    shape = CircleShape,
                    containerColor = if ((index == primaryItems.lastIndex) && secondaryItems.isNotEmpty()) {
                        secondLayerFabColor
                    } else {
                        fabColor
                    },
                    elevation = FloatingActionButtonDefaults.elevation(0.dp)
                ) {
                    val icon: ImageVector =
                        if ((index == primaryItems.lastIndex) && secondaryItems.isNotEmpty())
                            animatedSecondaryToggleIcon(
                                secondaryExpanded = state.secondaryExpanded,
                                delayOnCollapse = secondaryCollapseDuration(secondaryItems.size, config)
                            )
                        else item.icon

                    Icon(icon, null, tint = Color.White)
                }
            }

            // SECONDARY LAYER
            if (state.secondaryExpanded || secondaryProgress.any { it.value > 0f }) {
                val radius = config.layerDistance.value * config.secondaryLayerSpacingMultiplier
                val anchor = primaryPositions.firstOrNull() ?: Offset.Zero
                var prevOrigin = anchor
                var prevTarget = anchor

                secondaryItems.forEachIndexed { i, item ->
                    val p = secondaryProgress[i].value
                    val angle = 90 + (90.0 / (secondaryItems.size - 1).coerceAtLeast(1)) * i
                    val target = arcOffset(angle, radius)

                    val prevProg = if (i == 0) 1f else secondaryProgress[i - 1].value
                    val prevCurrent = lerpOffset(prevOrigin, prevTarget, prevProg)
                    val current = lerpOffset(prevCurrent, target, p)

                    prevOrigin = prevCurrent
                    prevTarget = target

                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                state.secondaryExpanded = false
                                delay(secondaryCollapseDuration(secondaryItems.size, config))
                                state.expanded = false
                                item.onClick()
                            }
                        },
                        modifier = Modifier
                            .offset(current.x.dp + centerCorrect, current.y.dp + centerCorrect)
                            .graphicsLayer {
                                alpha = p
                                scaleX = 0.8f + 0.2f * p
                                scaleY = 0.8f + 0.2f * p
                            }
                            .size(miniFabSize),
                        shape = CircleShape,
                        containerColor = secondLayerFabColor,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp)
                    ) {
                        Icon(item.icon, null, tint = Color.White)
                    }
                }
            }

            // MAIN FAB
            Box(modifier = Modifier.size(fabSize), contentAlignment = Alignment.Center) {
                JellyBlob(
                    size = fabSize,
                    color = fabColor,
                    bulges = bulges.map { it.value },
                    shadowOpacity = config.shadowOpacity,
                    shadowBlurFactor = config.shadowBlurFactor,
                    bulgeAngles = primaryAngles,
                    bounceFactor = config.bounceFactor
                )

                val rotation by animateFloatAsState(if (state.expanded) 0f else 45f, tween(260))

                Box(
                    Modifier
                        .size(fabSize)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            when {
                                !state.expanded -> state.expanded = true
                                state.secondaryExpanded -> {
                                    scope.launch {
                                        state.secondaryExpanded = false
                                        delay(
                                            secondaryCollapseDuration(
                                                secondaryItems.size,
                                                config
                                            )
                                        )
                                        state.expanded = false
                                    }
                                }
                                else -> state.expanded = false
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        CloseIcon,
                        null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(32.dp)
                            .rotate(rotation)
                    )
                }
            }
        }
    }
}

@Composable
internal fun animatedSecondaryToggleIcon(
    secondaryExpanded: Boolean,
    delayOnCollapse: Long = 360L
): ImageVector {
    var icon by remember { mutableStateOf(MoreHorizontal) }

    LaunchedEffect(secondaryExpanded) {
        if (secondaryExpanded) {
            icon = CloseIcon
        } else {
            delay(delayOnCollapse)
            icon = MoreHorizontal
        }
    }

    return icon
}

