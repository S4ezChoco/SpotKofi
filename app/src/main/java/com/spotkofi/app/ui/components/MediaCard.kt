package com.spotkofi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spotkofi.app.data.model.Album
import com.spotkofi.app.data.model.Artist
import com.spotkofi.app.data.model.HomeQuickPick
import com.spotkofi.app.data.model.MediaCollection
import com.spotkofi.app.data.model.Playlist
import com.spotkofi.app.ui.motion.clickableScale
import com.spotkofi.app.ui.theme.SpotKofiTheme

/**
 * A card in a horizontal shelf.
 *
 * Artists get a circular avatar and centred text; albums and playlists get
 * square art and left-aligned text. That single difference is what lets the eye
 * tell a person apart from a record at a glance while scrolling, so it is
 * handled here rather than left to each screen.
 */
@Composable
fun MediaCard(
    item: MediaCollection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = SpotKofiTheme.dimens.artworkCard,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    val shapes = SpotKofiTheme.shapes

    val isArtist = item is Artist
    val alignment = if (isArtist) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (isArtist) TextAlign.Center else TextAlign.Start

    Column(
        modifier = modifier
            .width(width)
            .clickableScale(onClick = onClick)
            .padding(vertical = dimens.spaceXs),
        horizontalAlignment = alignment,
    ) {
        Artwork(
            id = item.id,
            url = item.artworkUrl,
            shape = if (isArtist) shapes.avatar else shapes.artwork,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )

        Spacer(Modifier.height(dimens.spaceSm))

        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(2.dp))

        Text(
            text = item.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Compact two-column card pinned to the top of Home.
 *
 * Sits on its own raised surface with the artwork flush into the leading edge,
 * so the card reads as a single tappable slab. Title only: at this width a
 * subtitle truncates to noise.
 */
@Composable
fun QuickPickCard(
    item: MediaCollection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    val shapes = SpotKofiTheme.shapes

    Row(
        modifier = modifier
            .height(dimens.artworkSmall)
            .clip(shapes.quickPick)
            .background(colors.card)
            .clickableScale(pressedScale = 0.97f, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            id = item.id,
            size = dimens.artworkSmall,
            url = item.artworkUrl,
            // Square-cornered here: the card's own rounding already frames it,
            // and doubling the radius looks soft at this size.
            shape = if (item is Artist) shapes.avatar else RectangleShape,
        )
        Spacer(Modifier.width(dimens.spaceMd))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = dimens.spaceSm),
        )
    }
}


/** Compact Home tile for either a recently played track or a remote collection. */
@Composable
fun QuickPickCard(
    item: HomeQuickPick,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = modifier
            .height(dimens.artworkSmall)
            .clip(SpotKofiTheme.shapes.quickPick)
            .background(colors.card)
            .clickableScale(pressedScale = 0.97f, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            id = item.id,
            size = dimens.artworkSmall,
            url = item.artworkUrl,
            shape = androidx.compose.ui.graphics.RectangleShape,
        )
        Spacer(Modifier.width(dimens.spaceMd))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = dimens.spaceSm),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A single large highlighted item, used by the pre-save promo on Home.
 *
 * Wider than a shelf card and left-aligned rather than centred, so it reads as
 * one featured record instead of the first item of a row the user should scroll.
 */
@Composable
fun SpotlightCard(
    item: MediaCollection,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 184.dp,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Column(
        modifier = modifier
            .width(width)
            .clickableScale(onClick = onClick),
    ) {
        Artwork(
            id = item.id,
            url = item.artworkUrl,
            shape = SpotKofiTheme.shapes.artwork,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )

        Spacer(Modifier.height(dimens.spaceMd))

        Text(
            text = item.title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(name = "MediaCard", backgroundColor = 0xFF121212, showBackground = true)
@Composable
private fun MediaCardPreview() {
    SpotKofiTheme {
        Row {
            MediaCard(
                item = Playlist(
                    id = "pl_daily1",
                    title = "Daily Mix 1",
                    description = "Mira Solano, Alon, Tres Marias and more",
                    ownerName = "SpotKofi",
                ),
                onClick = {},
            )
            MediaCard(
                item = Artist(id = "ar_bagyo", name = "Bagyo"),
                onClick = {},
            )
        }
    }
}
