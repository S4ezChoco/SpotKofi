package com.spotkofi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spotkofi.app.data.model.Station
import com.spotkofi.app.ui.motion.clickableScale
import com.spotkofi.app.ui.theme.SpotKofiTheme

/**
 * A radio station tile.
 *
 * Pale saturated background, brand dot top-left, RADIO label top-right,
 * overlapping circular seed-artist portraits, and the station name set large
 * along the bottom. Foreground colour is chosen from the background's luminance
 * rather than hardcoded, because the palette runs from pale lilac to deep teal
 * and a fixed white would disappear on the light end.
 */
@Composable
fun StationCard(
    station: Station,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = SpotKofiTheme.dimens.artworkCard,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    // Lifted towards white so the tile reads as a pastel panel, not album art.
    val panel = remember(station.id) {
        lerp(artworkSeedColor(station.id), Color.White, 0.42f)
    }
    val onPanel = remember(panel) {
        if (panel.luminance() > 0.45f) Color(0xFF121212) else Color.White
    }

    Column(modifier = modifier.width(width)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(SpotKofiTheme.shapes.tile)
                .background(panel)
                .clickableScale(onClick = onClick),
        ) {
            // Brand dot, standing in for the service logo.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(dimens.spaceSm)
                    .size(16.dp)
                    .background(onPanel, CircleShape),
            )

            Text(
                text = "RADIO",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = onPanel,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(dimens.spaceSm),
            )

            // Two overlapping portraits, the trailing one tucked behind.
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = dimens.spaceMd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The tucked-behind portrait stays a gradient on purpose: the
                // catalog gives one image per station, and repeating it twice
                // would read as a rendering glitch rather than two artists.
                Artwork(
                    id = station.id + "_b",
                    size = 46.dp,
                    shape = CircleShape,
                )
                Artwork(
                    id = station.id,
                    size = 62.dp,
                    url = station.artworkUrl,
                    shape = CircleShape,
                    modifier = Modifier.offset(x = (-14).dp),
                )
            }

            Text(
                text = station.name,
                style = MaterialTheme.typography.headlineMedium,
                color = onPanel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = dimens.spaceSm,
                        end = dimens.spaceSm,
                        bottom = dimens.spaceSm,
                    ),
            )
        }

        Spacer(Modifier.height(dimens.spaceSm))

        Text(
            text = station.seedArtists,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(backgroundColor = 0xFF121212, showBackground = true)
@Composable
private fun StationCardPreview() {
    SpotKofiTheme {
        Row {
            StationCard(
                station = Station("st_mira", "Mira Solano", "Alon, Tres Marias, Halohalo"),
                onClick = {},
            )
            Spacer(Modifier.width(12.dp))
            StationCard(
                station = Station("st_bagyo", "Bagyo", "Kalye Kolektib, Neon Manila"),
                onClick = {},
            )
        }
    }
}
