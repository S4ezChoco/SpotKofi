package com.spotkofi.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.ui.theme.Motion
import com.spotkofi.app.ui.theme.SpotKofiTheme

/** Queue editor inspired by the reference app's modal queue panel. */
@Composable
fun QueueSheet(
    visible: Boolean,
    queue: List<Track>,
    currentTrackId: String?,
    onDismiss: () -> Unit,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    val scrimAlpha by animateFloatAsState(
        targetValue = if (visible) 0.62f else 0f,
        animationSpec = Motion.fast(),
        label = "queueScrim",
    )

    if (!visible && queue.isEmpty() || !visible && scrimAlpha == 0f) return
    BackHandler(enabled = visible, onBack = onDismiss)

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(Motion.gentle()) { it } + fadeIn(Motion.fast()),
            exit = slideOutVertically(Motion.snappy()) { it } + fadeOut(Motion.fast()),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(dimens.spaceSm)
                    .background(colors.elevated, RoundedCornerShape(dimens.floatingBarRadius))
                    .padding(vertical = dimens.spaceMd),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.spaceLg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.QueueMusic, contentDescription = null, tint = colors.accent)
                    Spacer(Modifier.width(dimens.spaceMd))
                    Text("Queue", color = colors.textPrimary, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClear, enabled = queue.isNotEmpty()) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Clear queue", tint = colors.textSecondary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close queue", tint = colors.textSecondary)
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(queue, key = { index, track -> "${track.id}_$index" }) { index, track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceXs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Artwork(id = track.id, url = track.artworkUrl, size = 44.dp)
                            Spacer(Modifier.width(dimens.spaceMd))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    color = if (track.id == currentTrackId) colors.accent else colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = track.artistName,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = { if (index > 0) onMove(index, index - 1) },
                                enabled = index > 0,
                            ) {
                                Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up", tint = colors.textSecondary)
                            }
                            IconButton(
                                onClick = { if (index < queue.lastIndex) onMove(index, index + 1) },
                                enabled = index < queue.lastIndex,
                            ) {
                                Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down", tint = colors.textSecondary)
                            }
                            IconButton(onClick = { onRemove(track.id) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove from queue", tint = colors.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}
