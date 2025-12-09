package com.philkes.notallyx.presentation.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Link
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.iprashantpanwar.sample.JellyFab
import com.github.iprashantpanwar.sample.JellyFabItem
import com.github.iprashantpanwar.sample.rememberJellyFabState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun JellyFabMenu(
    onDrawClick: () -> Unit,
    onTextFormatClick: () -> Unit,
    onMoreClick: () -> Unit,
    onAddFilesClick: () -> Unit,
    onAddImagesClick: () -> Unit,
    onRecordAudioClick: () -> Unit,
    onLinkNoteClick: (() -> Unit)? = null,
    showLinkNote: Boolean = false,
    onMainFabClick: () -> Unit = {}
) {
    val jellyState = rememberJellyFabState()

    // primary items: direct actions (JellyFab fixed to call onClick)
    // Order matters: the last primary toggles secondary in JellyFab
    // Primary: draw, text format, more (more toggles secondary via JellyFab internal logic)
    val primaryItems = listOf(
        JellyFabItem(Icons.Default.Create) { onDrawClick() },            // Draw pen
        JellyFabItem(Icons.Default.FormatSize) { onTextFormatClick() },  // Text format
        JellyFabItem(Icons.Default.MoreHoriz) { /* handled by JellyFab toggle for secondary */ }
    )

    // secondary items: AddFiles, AddImages, Mic, Link
    val secondaryItems = mutableListOf(
        JellyFabItem(Icons.Default.InsertDriveFile) { onAddFilesClick() },
        JellyFabItem(Icons.Default.Image) { onAddImagesClick() },
        JellyFabItem(Icons.Default.Mic) { onRecordAudioClick() }
    )
    if (showLinkNote && onLinkNoteClick != null) {
        secondaryItems.add(
            JellyFabItem(Icons.Default.Link) { onLinkNoteClick() }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(end = 0.dp, bottom = 0.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        JellyFab(
            state = jellyState,
            primaryItems = primaryItems,
            secondaryItems = secondaryItems
        )
    }
}
