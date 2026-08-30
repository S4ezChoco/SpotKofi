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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spotkofi.app.core.AppConstants
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.model.TrackCredits
import com.spotkofi.app.data.model.asTrackDuration
import com.spotkofi.app.ui.theme.Motion
import com.spotkofi.app.ui.theme.SpotKofiTheme

/**
 * Song credits.
 *
 * Every row is a fact one of the providers actually returned for this recording.
 * There is no songwriter, producer or label row because nothing in this app knows
 * them: the streaming provider publishes an uploading channel, a play count and a
 * date, and the metadata catalog publishes an album and a release year. Printing
 * anything more would mean attributing a song to people who were never named.
 */
@Composable
fun TrackCreditsSheet(
    visible: Boolean,
    track: Track?,
    credits: TrackCredits?,
    genre: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    val scrimAlpha by animateFloatAsState(
        targetValue = if (visible) 0.62f else 0f,
        animationSpec = Motion.fast(),
        label = "creditsScrim",
    )
    if ((!visible && scrimAlpha == 0f) || track == null) return

    BackHandler(enabled = visible, onBack = onDismiss)

    val rows = remember(track, credits, genre) {
        buildList {
            track.artistName.takeIf { it.isNotBlank() }?.let { add("Artist" to it) }
            track.albumTitle.takeIf { it.isNotBlank() }?.let { add("Album" to it) }
            genre?.takeIf { it.isNotBlank() }?.let { add("Genre" to it) }
            track.durationMs.takeIf { it > 0L }?.let { add("Length" to it.asTrackDuration()) }
            if (track.isExplicit) add("Advisory" to "Explicit")
            credits?.channelName?.let { add("Published by" to it) }
            credits?.publishedOn?.let { add("Released" to it) }
            credits?.plays?.let { add("Plays" to it.formatCount()) }
            add("Source" to "YouTube Music")
        }
    }

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
                    .clip(SpotKofiTheme.shapes.sheet)
                    .background(colors.elevated)
                    .padding(vertical = dimens.spaceLg),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimens.spaceLg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(id = track.id, size = 52.dp, url = track.artworkUrl)
                    Spacer(Modifier.width(dimens.spaceMd))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Credits",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }

                Spacer(Modifier.padding(vertical = dimens.spaceXs))

                LazyColumn(
                    // Capped so a long description cannot push the sheet past the
                    // top of the screen; the list scrolls inside the sheet instead.
                    modifier = Modifier.heightIn(max = 420.dp),
                    contentPadding = PaddingValues(vertical = dimens.spaceSm),
                ) {
                    items(rows.size) { index ->
                        val (label, value) = rows[index]
                        CreditRow(label = label, value = value)
                    }

                    credits?.description?.takeIf { it.isNotBlank() }?.let { description ->
                        item {
                            Column(
                                modifier = Modifier.padding(
                                    horizontal = dimens.spaceLg,
                                    vertical = dimens.spaceSm,
                                ),
                            ) {
                                Text(
                                    text = "Description",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.textSecondary,
                                )
                                Spacer(Modifier.padding(vertical = dimens.spaceXxs))
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textPrimary,
                                )
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Lyrics, when available, come from " +
                                "${AppConstants.LYRICS_PROVIDER_NAME}.",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textTertiary,
                            modifier = Modifier.padding(
                                horizontal = dimens.spaceLg,
                                vertical = dimens.spaceSm,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreditRow(label: String, value: String) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceSm),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.width(104.dp),
        )
        Spacer(Modifier.width(dimens.spaceSm))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Play counts as the provider's own surfaces write them.
 *
 * A raw nine-digit number is unreadable at a glance, and rounding is what makes it
 * a fact the eye can take in rather than a string to decode.
 */
private fun Long.formatCount(): String = when {
    this >= 1_000_000_000L -> "%.1fB".format(this / 1_000_000_000.0)
    this >= 1_000_000L -> "%.1fM".format(this / 1_000_000.0)
    this >= 1_000L -> "%.1fK".format(this / 1_000.0)
    else -> toString()
}
