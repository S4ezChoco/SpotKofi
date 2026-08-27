package com.spotkofi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.spotkofi.app.ui.theme.ArtworkSeeds
import com.spotkofi.app.ui.theme.SpotKofiTheme
import kotlin.math.absoluteValue

/**
 * Builds a stable two-stop gradient for an item id.
 *
 * Deterministic on purpose: the same playlist always gets the same colours
 * across launches and inside `@Preview`, so the UI looks designed rather than
 * random. The second stop is the seed pushed towards black, which mimics how
 * real cover art tends to sit dark at the bottom.
 */
fun artworkBrush(id: String): Brush {
    val top = artworkSeedColor(id)
    val bottom = lerp(top, Color.Black, 0.55f)
    return Brush.linearGradient(listOf(top, bottom))
}

/**
 * The dominant colour for an item id.
 *
 * Detail screens tint their header with this so the scrim reads as if it were
 * sampled from the cover art, which is what Spotify does with real artwork.
 */
fun artworkSeedColor(id: String): Color {
    val hash = id.hashCode().absoluteValue
    return ArtworkSeeds[hash % ArtworkSeeds.size]
}

/**
 * Cover art for any media item.
 *
 * Falls back to [artworkBrush] whenever [url] is null, which is the whole of
 * Phase 1. That keeps every screen renderable with no network and no bundled
 * image assets.
 */
@Composable
fun Artwork(
    id: String,
    modifier: Modifier = Modifier,
    url: String? = null,
    shape: Shape = SpotKofiTheme.shapes.artwork,
    contentDescription: String? = null,
) {
    val brush = remember(id) { artworkBrush(id) }

    // A null contentDescription marks the artwork as decorative, which is
    // correct in lists: the adjacent title text already carries the meaning for
    // screen readers, and announcing both would be redundant.
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = 0.22f),
                modifier = Modifier.fillMaxSize(0.34f),
            )
        }
    }
}

/** Square convenience overload for the common fixed-size case. */
@Composable
fun Artwork(
    id: String,
    size: Dp,
    modifier: Modifier = Modifier,
    url: String? = null,
    shape: Shape = SpotKofiTheme.shapes.artwork,
    contentDescription: String? = null,
) {
    Artwork(
        id = id,
        url = url,
        shape = shape,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
    )
}
