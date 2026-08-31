package com.spotkofi.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spotkofi.app.ui.theme.Motion
import com.spotkofi.app.ui.theme.SpotKofiTheme
import kotlin.math.max

/**
 * Shared loading surface for the app.
 *
 * A shimmer preserves the shape of the content that is about to arrive, so the
 * screen does not jump from a spinner into a completely different layout. Every
 * screen uses the same neutral ramp and timing to keep loading states cohesive.
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = SpotKofiTheme.shapes.card,
    baseColor: Color = SpotKofiTheme.colors.elevated,
    highlightColor: Color = SpotKofiTheme.colors.highlight,
) {
    val transition = rememberInfiniteTransition(label = "skeletonShimmer")
    val offset by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skeletonOffset",
    )
    val brush = Brush.horizontalGradient(
        colors = listOf(
            baseColor,
            highlightColor,
            baseColor,
        ),
        startX = offset * 420f,
        endX = offset * 420f + 220f,
    )

    Box(modifier = modifier.clip(shape).background(brush))
}

@Composable
fun SkeletonLine(
    modifier: Modifier = Modifier,
    fraction: Float = 1f,
    height: Dp = 14.dp,
) {
    SkeletonBox(
        modifier = modifier
            .fillMaxWidth(fraction.coerceIn(0.05f, 1f))
            .height(height),
        shape = RoundedCornerShape(height / 2),
    )
}

@Composable
fun LoadingDots(
    color: Color = SpotKofiTheme.colors.textPrimary,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "loadingDots")
    val first by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(Motion.Medium, delayMillis = 0),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "loadingDotOne",
    )
    val second by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(Motion.Medium, delayMillis = 110),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "loadingDotTwo",
    )
    val third by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(Motion.Medium, delayMillis = 220),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "loadingDotThree",
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Dot(color = color, alpha = first)
        Dot(color = color, alpha = second)
        Dot(color = color, alpha = third)
    }
}

@Composable
private fun Dot(color: Color, alpha: Float) {
    Box(
        modifier = Modifier
            .size(6.dp)
            .alpha(alpha)
            .background(color, CircleShape),
    )
}

@Composable
fun LyricsSkeleton(modifier: Modifier = Modifier) {
    val dimens = SpotKofiTheme.dimens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SpotKofiTheme.shapes.card)
            .background(SpotKofiTheme.colors.card)
            .padding(dimens.spaceLg),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceSm),
    ) {
        SkeletonLine(fraction = 0.28f, height = 16.dp)
        repeat(5) { index ->
            SkeletonLine(
                fraction = if (index % 2 == 0) 0.88f else 0.64f,
                height = 13.dp,
            )
        }
    }
}

@Composable
fun SearchSkeleton(modifier: Modifier = Modifier) {
    val dimens = SpotKofiTheme.dimens
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceSm),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.screenGutter, vertical = dimens.spaceSm),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
        ) {
            repeat(4) { SkeletonBox(Modifier.size(width = 72.dp, height = 32.dp), SpotKofiTheme.shapes.chip) }
        }
        repeat(6) { SkeletonTrackRow() }
    }
}

@Composable
fun HomeSkeleton(
    gutter: Dp,
    gridColumns: Int,
    shelfWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val dimens = SpotKofiTheme.dimens
    val cols = max(2, gridColumns)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = dimens.spaceXl),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
    ) {
        // Quick-pick grid: 4 rows of compact cards matching the real QuickPickCard
        // layout (artworkSmall thumbnail + text bar inside a card-coloured slab).
        repeat(4) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = gutter),
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
            ) {
                repeat(cols) {
                    SkeletonBox(
                        Modifier
                            .weight(1f)
                            .height(dimens.artworkSmall),
                        SpotKofiTheme.shapes.quickPick,
                    )
                }
            }
        }
        Spacer(Modifier.height(dimens.spaceMd))
        SkeletonShelf(titleWidth = 148.dp, cardWidth = shelfWidth, gutter = gutter)
        Spacer(Modifier.height(dimens.spaceSm))
        SkeletonShelf(titleWidth = 112.dp, cardWidth = shelfWidth, gutter = gutter)
    }
}

@Composable
fun SkeletonShelf(
    titleWidth: Dp = 128.dp,
    cardWidth: Dp = 148.dp,
    gutter: Dp = SpotKofiTheme.dimens.screenGutter,
    modifier: Modifier = Modifier,
) {
    val dimens = SpotKofiTheme.dimens
    Column(modifier = modifier.fillMaxWidth()) {
        SkeletonLine(
            modifier = Modifier.padding(horizontal = gutter),
            fraction = 0.34f,
            height = 18.dp,
        )
        Spacer(Modifier.height(dimens.spaceSm))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = gutter),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
        ) {
            repeat(4) {
                Column(modifier = Modifier.width(cardWidth)) {
                    SkeletonBox(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        SpotKofiTheme.shapes.artwork,
                    )
                    Spacer(Modifier.height(dimens.spaceSm))
                    SkeletonLine(fraction = 0.82f, height = 14.dp)
                    Spacer(Modifier.height(4.dp))
                    SkeletonLine(fraction = 0.58f, height = 12.dp)
                }
            }
        }
    }
}

@Composable
fun SkeletonTrackRow(
    modifier: Modifier = Modifier,
) {
    val dimens = SpotKofiTheme.dimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.screenGutter, vertical = dimens.spaceSm),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
    ) {
        SkeletonBox(
            modifier = Modifier.size(dimens.artworkRow),
            shape = SpotKofiTheme.shapes.artwork,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SkeletonLine(fraction = 0.72f, height = 15.dp)
            SkeletonLine(fraction = 0.48f, height = 12.dp)
        }
        SkeletonBox(Modifier.size(22.dp), CircleShape)
    }
}

@Composable
fun CollectionSkeleton(
    modifier: Modifier = Modifier,
) {
    val dimens = SpotKofiTheme.dimens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = dimens.screenGutter, vertical = dimens.spaceXl),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.spaceMd),
    ) {
        SkeletonBox(
            modifier = Modifier.size(220.dp),
            shape = SpotKofiTheme.shapes.artwork,
        )
        SkeletonLine(fraction = 0.58f, height = 26.dp)
        SkeletonLine(fraction = 0.38f, height = 14.dp)
        Spacer(Modifier.height(dimens.spaceSm))
        repeat(5) { SkeletonTrackRow(modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
fun ExploreSkeleton(modifier: Modifier = Modifier) {
    val dimens = SpotKofiTheme.dimens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = dimens.spaceXl),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceMd),
    ) {
        SkeletonBox(
            Modifier
                .width(180.dp)
                .height(40.dp),
            SpotKofiTheme.shapes.chip,
        )
        SkeletonShelf(cardWidth = 148.dp)
        repeat(5) { SkeletonTrackRow() }
        SkeletonShelf(cardWidth = 148.dp)
    }
}
