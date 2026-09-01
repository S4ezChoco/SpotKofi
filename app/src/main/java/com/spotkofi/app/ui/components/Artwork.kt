package com.spotkofi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.BitmapImage
import coil3.DrawableImage
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.spotkofi.app.ui.theme.ArtworkSeeds
import com.spotkofi.app.ui.theme.SpotKofiTheme
import kotlin.math.absoluteValue

/** Builds a stable two-stop gradient for an item id. */
fun artworkBrush(id: String): Brush {
    val top = artworkSeedColor(id)
    val bottom = lerp(top, Color.Black, 0.55f)
    return Brush.linearGradient(listOf(top, bottom))
}

/** The dominant colour for an item id. */
fun artworkSeedColor(id: String): Color {
    val hash = id.hashCode().absoluteValue
    return ArtworkSeeds[hash % ArtworkSeeds.size]
}

@Composable
fun rememberArtworkColor(id: String, url: String?): Color {
    val displayUrl = remember(url) {
        url?.takeIf { it.isNotBlank() }?.let(::optimizedArtworkUrl)
    }
    val painter = rememberAsyncImagePainter(model = displayUrl)
    val sampledColor = (painter.state as? AsyncImagePainter.State.Success)
        ?.result
        ?.image
        ?.let { image ->
            when (image) {
                is BitmapImage -> image.bitmap
                is DrawableImage -> image.drawable.toArtworkBitmap()
                else -> null
            }
        }
        ?.let(::sampleArtworkColor)
    return sampledColor ?: artworkSeedColor(id)
}

private fun android.graphics.drawable.Drawable.toArtworkBitmap(): android.graphics.Bitmap {
    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    return android.graphics.Bitmap.createBitmap(
        width,
        height,
        android.graphics.Bitmap.Config.ARGB_8888,
    ).also { bitmap ->
        android.graphics.Canvas(bitmap).also { canvas ->
            setBounds(0, 0, canvas.width, canvas.height)
            draw(canvas)
        }
    }
}

private fun sampleArtworkColor(bitmap: android.graphics.Bitmap): Color {
    val stepX = (bitmap.width / 24).coerceAtLeast(1)
    val stepY = (bitmap.height / 24).coerceAtLeast(1)
    var red = 0f
    var green = 0f
    var blue = 0f
    var weightTotal = 0f

    for (y in 0 until bitmap.height step stepY) {
        for (x in 0 until bitmap.width step stepX) {
            val pixel = bitmap.getPixel(x, y)
            val alpha = android.graphics.Color.alpha(pixel) / 255f
            val pixelRed = android.graphics.Color.red(pixel) / 255f
            val pixelGreen = android.graphics.Color.green(pixel) / 255f
            val pixelBlue = android.graphics.Color.blue(pixel) / 255f
            val brightness = (pixelRed + pixelGreen + pixelBlue) / 3f
            if (alpha < 0.5f || brightness < 0.06f || brightness > 0.94f) continue
            val saturation = maxOf(pixelRed, pixelGreen, pixelBlue) -
                minOf(pixelRed, pixelGreen, pixelBlue)
            val weight = alpha * (0.35f + saturation)
            red += pixelRed * weight
            green += pixelGreen * weight
            blue += pixelBlue * weight
            weightTotal += weight
        }
    }

    if (weightTotal == 0f) {
        return Color(bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
    }
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (red / weightTotal * 255f).toInt(),
        (green / weightTotal * 255f).toInt(),
        (blue / weightTotal * 255f).toInt(),
        hsv,
    )
    return Color.hsv(
        hue = hsv[0],
        saturation = (hsv[1] * 1.35f).coerceIn(0.16f, 1f),
        value = hsv[2].coerceIn(0.2f, 0.9f),
    )
}

/**
 * Requests the largest useful variant from the common provider URL formats.
 * iTunes and YouTube Music often return tiny card thumbnails even when a larger
 * variant is available at the same URL; using that tiny bitmap in the player is
 * what made otherwise valid covers look pixelated.
 */
private fun optimizedArtworkUrl(url: String): String = url.trim()
    .replace(Regex("\\d+x\\d+(?=bb)"), "1200x1200")
    .replace(Regex("w\\d+-h\\d+"), "w1200-h1200")
    .replace(Regex("=w\\d+(?=-|$)"), "=w1200")
    .replace(Regex("=s\\d+(?![0-9])"), "=s1200")

/** Cover art with a deterministic, non-letter fallback for missing or failed URLs. */
@Composable
fun Artwork(
    id: String,
    modifier: Modifier = Modifier,
    url: String? = null,
    shape: Shape = SpotKofiTheme.shapes.artwork,
    contentDescription: String? = null,
) {
    val brush = remember(id) { artworkBrush(id) }
    val displayUrl = remember(url) {
        url?.takeIf { it.isNotBlank() }?.let(::optimizedArtworkUrl)
    }
    var imageFailed by remember(displayUrl) { mutableStateOf(false) }
    var imageLoading by remember(displayUrl) { mutableStateOf(displayUrl != null) }

    Box(
        modifier = modifier
            .clip(shape)
            .background(brush),
        contentAlignment = Alignment.Center,
    ) {
        if (displayUrl != null && !imageFailed) {
            AsyncImage(
                model = displayUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                onLoading = { imageLoading = true },
                onSuccess = {
                    imageLoading = false
                    imageFailed = false
                },
                onError = {
                    imageLoading = false
                    imageFailed = true
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (imageLoading) {
                SkeletonBox(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.32f),
                    shape = shape,
                )
            }
        } else {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = 0.34f),
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
        modifier = modifier.then(Modifier.size(size)),
    )
}

/**
 * A 2x2 grid of artwork thumbnails for community playlists.
 *
 * When a playlist has multiple thumbnail URLs (up to 4), this renders them in a
 * compact grid instead of a single blurred image.
 */
@Composable
fun CommunityArtwork(
    id: String,
    urls: List<String>,
    modifier: Modifier = Modifier,
    shape: Shape = SpotKofiTheme.shapes.artwork,
) {
    val displayUrls = urls.take(4)
    Box(
        modifier = modifier.clip(shape),
    ) {
        if (displayUrls.size >= 4) {
            // 2x2 grid
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f)) {
                    Artwork(id = "$id-0", url = displayUrls[0], modifier = Modifier.weight(1f).aspectRatio(1f), shape = RectangleShape)
                    Artwork(id = "$id-1", url = displayUrls[1], modifier = Modifier.weight(1f).aspectRatio(1f), shape = RectangleShape)
                }
                Row(modifier = Modifier.weight(1f)) {
                    Artwork(id = "$id-2", url = displayUrls[2], modifier = Modifier.weight(1f).aspectRatio(1f), shape = RectangleShape)
                    Artwork(id = "$id-3", url = displayUrls[3], modifier = Modifier.weight(1f).aspectRatio(1f), shape = RectangleShape)
                }
            }
        } else if (displayUrls.isNotEmpty()) {
            // Fallback to single artwork if less than 4 URLs
            Artwork(id = id, url = displayUrls.first(), modifier = Modifier.fillMaxSize(), shape = shape)
        } else {
            Artwork(id = id, modifier = Modifier.fillMaxSize(), shape = shape)
        }
    }
}