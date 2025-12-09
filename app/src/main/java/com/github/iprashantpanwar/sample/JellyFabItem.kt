package com.github.iprashantpanwar.sample

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.vector.ImageVector

@Immutable
@Stable
data class JellyFabItem(
    val icon: ImageVector,
    val onClick: () -> Unit = {},
)

