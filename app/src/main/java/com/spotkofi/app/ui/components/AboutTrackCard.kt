package com.spotkofi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.data.model.TrackCredits
import com.spotkofi.app.data.model.asTrackDuration
import com.spotkofi.app.ui.motion.clickableScale
import com.spotkofi.app.ui.theme.SpotKofiTheme

/**
 * "About this song".
 *
 * Every row is a field the catalog actually returned for this recording. There is
 * no writer, label or release-date row because the catalog does not expose them,
 * and a card that fills those gaps with plausible-looking text is worse than a
 * shorter card.
 */
@Composable
fun AboutTrackCard(
    track: Track,
    genre: String?,
    modifier: Modifier = Modifier,
    credits: TrackCredits? = null,
    /** Opens the full credits sheet. Null makes the card read-only. */
    onExpand: (() -> Unit)? = null,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    val rows = buildList {
        track.artistName.takeIf { it.isNotBlank() }?.let { add("Artist" to it) }
        track.albumTitle.takeIf { it.isNotBlank() }?.let { add("Album" to it) }
        genre?.takeIf { it.isNotBlank() }?.let { add("Genre" to it) }
        track.durationMs.takeIf { it > 0L }?.let { add("Length" to it.asTrackDuration()) }
        if (track.isExplicit) add("Advisory" to "Explicit")
        credits?.channelName?.let { add("Published by" to it) }
        credits?.publishedOn?.let { add("Published" to it) }
        credits?.plays?.let { add("Views" to it.compactCount()) }
        track.externalUrl?.takeIf { it.isNotBlank() }?.let { add("Source URL" to it) }
    }
    if (rows.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SpotKofiTheme.shapes.card)
            .background(colors.card)
            .then(
                if (onExpand != null) {
                    Modifier.clickableScale(pressedScale = 0.99f, onClick = onExpand)
                } else {
                    Modifier
                },
            )
            .padding(vertical = dimens.spaceMd),
    ) {
        Text(
            text = "About this song",
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = dimens.spaceLg),
        )
        Spacer(Modifier.height(dimens.spaceSm))
        rows.forEach { (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceXs),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.width(84.dp),
                )
                Spacer(Modifier.width(dimens.spaceSm))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Start,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        credits?.description?.takeIf { it.isNotBlank() }?.let { description ->
            Spacer(Modifier.height(dimens.spaceSm))
            Text(
                text = "Description",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = dimens.spaceLg),
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textPrimary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(
                    horizontal = dimens.spaceLg,
                    vertical = dimens.spaceXs,
                ),
            )
        }

        if (onExpand != null) {
            Spacer(Modifier.height(dimens.spaceXs))
            Text(
                text = "Tap for full credits",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = dimens.spaceLg),
            )
        }
    }
}

private fun Long.compactCount(): String = when {
    this >= 1_000_000_000L -> "%.1fB".format(this / 1_000_000_000.0)
    this >= 1_000_000L -> "%.1fM".format(this / 1_000_000.0)
    this >= 1_000L -> "%.1fK".format(this / 1_000.0)
    else -> toString()
}

@Composable
private fun AboutTrackCardPreview() {
    SpotKofiTheme {
        AboutTrackCard(
            track = Track(
                id = "tr_01",
                title = "Umaga",
                artistName = "Mira Solano",
                albumTitle = "Umaga",
                durationMs = 214_000,
                isExplicit = true,
            ),
            genre = "OPM",
            modifier = Modifier.padding(16.dp),
        )
    }
}
