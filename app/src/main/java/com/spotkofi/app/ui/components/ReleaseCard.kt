package com.spotkofi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spotkofi.app.R
import com.spotkofi.app.data.model.ReleaseItem
import com.spotkofi.app.ui.theme.SpotKofiTheme

/**
 * A full-width card in the Following tab's release feed.
 *
 * Like the mini player, the surface is tinted from the artwork, which is what
 * makes the feed read as a stack of records rather than a list of rows.
 */
@Composable
fun ReleaseCard(
    release: ReleaseItem,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onAdd: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    val tint = remember(release.id) {
        lerp(artworkSeedColor(release.id), Color.Black, 0.62f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SpotKofiTheme.shapes.card)
            .background(tint)
            .clickable(onClick = onClick)
            .padding(dimens.spaceMd),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Artwork(
                id = release.id,
                size = 68.dp,
                url = release.artworkUrl,
                shape = SpotKofiTheme.shapes.artwork,
            )

            Spacer(Modifier.width(dimens.spaceMd))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = release.artistName,
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    // The release year is often missing from the catalog, so the
                    // separator is only drawn when there is something to separate.
                    text = listOf(release.title, release.releasedLabel)
                        .filter { it.isNotBlank() }
                        .joinToString(" \u2022 "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(
                onClick = onMore,
                modifier = Modifier.size(dimens.iconLg),
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.cd_more_options),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(dimens.iconMd),
                )
            }
        }

        Spacer(Modifier.height(dimens.spaceMd))

        Text(
            text = buildString {
                append(release.songCount)
                append(if (release.songCount == 1) " song" else " songs")
                append(" \u2022 ")
                append(release.title)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(dimens.spaceMd))

        Row(verticalAlignment = Alignment.CenterVertically) {
            PreviewPill(onClick = onPlay)

            Spacer(Modifier.weight(1f))

            IconButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Filled.AddCircleOutline,
                    contentDescription = stringResource(R.string.cd_add_to_library),
                    tint = colors.textPrimary,
                    modifier = Modifier.size(dimens.iconLg),
                )
            }

            Spacer(Modifier.width(dimens.spaceXs))

            // Solid white circle, unlike the green button used elsewhere. On a
            // tinted card white separates from every seed colour; green does not.
            Box(
                modifier = Modifier
                    .size(dimens.playButtonSm)
                    .background(Color.White, CircleShape)
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.cd_play),
                    tint = Color.Black,
                    modifier = Modifier.size(dimens.iconMd),
                )
            }
        }
    }
}

/** The muted "Preview single" affordance. */
@Composable
private fun PreviewPill(onClick: () -> Unit) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = Modifier
            .clip(SpotKofiTheme.shapes.chip)
            .background(Color.White.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.spaceMd, vertical = dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceXs),
    ) {
        Icon(
            imageVector = Icons.Filled.VolumeOff,
            contentDescription = null,
            tint = colors.textPrimary,
            modifier = Modifier.size(dimens.iconSm),
        )
        Text(
            text = "Preview single",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textPrimary,
        )
    }
}

@Preview(backgroundColor = 0xFF121212, showBackground = true, widthDp = 400)
@Composable
private fun ReleaseCardPreview() {
    SpotKofiTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            ReleaseCard(
                release = ReleaseItem(
                    id = "preview_release_1",
                    artistName = "Preview Artist",
                    title = "Preview Album",
                    releasedLabel = "2026",
                    songCount = 10,
                ),
                onClick = {},
                onPlay = {},
                onAdd = {},
                onMore = {},
            )
        }
    }
}
