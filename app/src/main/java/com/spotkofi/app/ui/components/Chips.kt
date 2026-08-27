package com.spotkofi.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spotkofi.app.ui.theme.SpotKofiTheme

/** How long the chip colour and shape transitions take. */
private const val CHIP_ANIM_MS = 220

/**
 * Single selection pill.
 *
 * Uses [selectable] rather than `clickable` so assistive tech announces the
 * selected state instead of only reading the label. Colours cross-fade rather
 * than snapping, which is what makes the filter row feel responsive instead of
 * flickering between states.
 */
@Composable
fun SpotKofiChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = SpotKofiTheme.shapes.chip,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    val background by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.chip,
        animationSpec = tween(CHIP_ANIM_MS),
        label = "chipBackground",
    )
    val content by animateColorAsState(
        targetValue = if (selected) colors.onAccent else colors.textPrimary,
        animationSpec = tween(CHIP_ANIM_MS),
        label = "chipContent",
    )

    Box(
        modifier = modifier
            .height(dimens.chipHeight)
            .clip(shape)
            .background(background)
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = dimens.spaceLg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
        )
    }
}

/**
 * Two chips that fuse into one pill when both are active.
 *
 * This is how the Music / Following filter behaves: Following is a sub-filter of
 * Music, and once both are lit they read as a single segmented control rather
 * than two independent options. Corner radii, the gap and the divider all
 * animate, so the merge and split are continuous.
 */
@Composable
fun SegmentedChipPair(
    leadingLabel: String,
    trailingLabel: String,
    leadingSelected: Boolean,
    trailingSelected: Boolean,
    trailingVisible: Boolean,
    onLeadingClick: () -> Unit,
    onTrailingClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    val joined = trailingVisible && leadingSelected && trailingSelected
    val fullRadius = dimens.chipHeight / 2

    // Collapsing to 0 turns the two pills into one continuous shape.
    val innerRadius by animateDpAsState(
        targetValue = if (joined) 0.dp else fullRadius,
        animationSpec = tween(CHIP_ANIM_MS),
        label = "segmentInnerRadius",
    )
    val gap by animateDpAsState(
        targetValue = if (joined) 0.dp else dimens.spaceSm,
        animationSpec = tween(CHIP_ANIM_MS),
        label = "segmentGap",
    )
    val dividerAlpha by animateFloatAsState(
        targetValue = if (joined) 1f else 0f,
        animationSpec = tween(CHIP_ANIM_MS),
        label = "segmentDivider",
    )

    Row(
        modifier = modifier.height(dimens.chipHeight),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpotKofiChip(
            label = leadingLabel,
            selected = leadingSelected,
            onClick = onLeadingClick,
            shape = RoundedCornerShape(
                topStart = fullRadius,
                bottomStart = fullRadius,
                topEnd = if (trailingVisible) innerRadius else fullRadius,
                bottomEnd = if (trailingVisible) innerRadius else fullRadius,
            ),
        )

        if (trailingVisible) {
            // Hairline seam so the fused pill still reads as two segments.
            Box(
                modifier = Modifier
                    .width(if (joined) 1.dp else 0.dp)
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.22f * dividerAlpha)),
            )

            SpotKofiChip(
                label = trailingLabel,
                selected = trailingSelected,
                onClick = onTrailingClick,
                shape = RoundedCornerShape(
                    topStart = innerRadius,
                    bottomStart = innerRadius,
                    topEnd = fullRadius,
                    bottomEnd = fullRadius,
                ),
            )
        }
    }
}

/** Horizontally scrolling row of [SpotKofiChip]. */
@Composable
fun ChipRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = SpotKofiTheme.dimens

    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = dimens.screenGutter),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
    ) {
        items(options, key = { it }) { option ->
            SpotKofiChip(
                label = option,
                selected = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

@Preview(name = "Chips / split", backgroundColor = 0xFF121212, showBackground = true)
@Composable
private fun SegmentedSplitPreview() {
    SpotKofiTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpotKofiChip(label = "All", selected = false, onClick = {})
            SegmentedChipPair(
                leadingLabel = "Music",
                trailingLabel = "Following",
                leadingSelected = true,
                trailingSelected = false,
                trailingVisible = true,
                onLeadingClick = {},
                onTrailingClick = {},
            )
            SpotKofiChip(label = "Podcasts", selected = false, onClick = {})
        }
    }
}

@Preview(name = "Chips / joined", backgroundColor = 0xFF121212, showBackground = true)
@Composable
private fun SegmentedJoinedPreview() {
    SpotKofiTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpotKofiChip(label = "All", selected = false, onClick = {})
            SegmentedChipPair(
                leadingLabel = "Music",
                trailingLabel = "Following",
                leadingSelected = true,
                trailingSelected = true,
                trailingVisible = true,
                onLeadingClick = {},
                onTrailingClick = {},
            )
            SpotKofiChip(label = "Podcasts", selected = false, onClick = {})
        }
    }
}
