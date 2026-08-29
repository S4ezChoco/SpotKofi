package com.spotkofi.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spotkofi.app.data.model.TrackLyrics
import com.spotkofi.app.ui.theme.SpotKofiTheme

/**
 * The lyrics panel.
 *
 * Everything shown here comes from the lyrics provider for the track that is
 * playing; nothing is stored in the app and nothing is generated when a lookup
 * comes back empty. When the provider has a timed sheet the current line is
 * highlighted and the list follows the playhead, which is the only reason to
 * prefer the timed form over plain text.
 */
@Composable
fun LyricsCard(
    lyrics: TrackLyrics,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    val timedLines = remember(lyrics.synced) {
        lyrics.synced?.let(::parseTimedLyrics).orEmpty()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SpotKofiTheme.shapes.card)
            .background(colors.card)
            .padding(vertical = dimens.spaceMd),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.spaceLg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Lyrics,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(dimens.iconSm),
            )
            Spacer(Modifier.size(dimens.spaceSm))
            Text(
                text = "Lyrics",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
        }

        Spacer(Modifier.height(dimens.spaceSm))

        when {
            lyrics.instrumental -> Text(
                text = "This recording is instrumental.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = dimens.spaceLg),
            )

            timedLines.isNotEmpty() -> TimedLyrics(
                lines = timedLines,
                positionMs = positionMs,
            )

            !lyrics.plain.isNullOrBlank() -> Text(
                text = lyrics.plain,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .padding(horizontal = dimens.spaceLg),
            )

            else -> Text(
                text = "No lyrics available for this track.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = dimens.spaceLg),
            )
        }
    }
}

@Composable
private fun TimedLyrics(
    lines: List<TimedLyricLine>,
    positionMs: Long,
) {
    val dimens = SpotKofiTheme.dimens
    val listState = rememberLazyListState()

    // The last line whose stamp has passed. A binary search would be overkill: a
    // lyric sheet is a few dozen lines.
    val activeIndex = remember(lines, positionMs) {
        lines.indexOfLast { it.timestampMs <= positionMs }.coerceAtLeast(0)
    }

    LaunchedEffect(activeIndex) {
        if (lines.isNotEmpty()) {
            // Kept a couple of lines from the top edge so the user can read ahead
            // instead of the current line sitting on the boundary.
            runCatching { listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0)) }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.heightIn(max = 320.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = dimens.spaceLg,
            vertical = dimens.spaceXs,
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.spaceSm),
    ) {
        itemsIndexed(lines) { index, line ->
            LyricLine(text = line.text, active = index == activeIndex)
        }
    }
}

@Composable
private fun LyricLine(text: String, active: Boolean) {
    val colors = SpotKofiTheme.colors
    val color by animateColorAsState(
        targetValue = if (active) colors.textPrimary else colors.textTertiary,
        label = "lyricLineColor",
    )

    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        ),
        color = color,
    )
}

/** One line of a timed lyric sheet. */
private data class TimedLyricLine(val timestampMs: Long, val text: String)

/**
 * Parses the `[mm:ss.xx]` form used by lyric sheets.
 *
 * Written here rather than reusing the older helper in the data layer because
 * that one truncated to whole seconds before converting to milliseconds, so
 * every line landed up to a second early or late.
 */
private fun parseTimedLyrics(raw: String): List<TimedLyricLine> {
    val stamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    return raw.lines().mapNotNull { line ->
        val matches = stamp.findAll(line).toList()
        if (matches.isEmpty()) return@mapNotNull null
        val text = line.substring(matches.last().range.last + 1).trim()
        if (text.isEmpty()) return@mapNotNull null

        val match = matches.first()
        val minutes = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
        val seconds = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
        val fraction = match.groupValues[3]
        val fractionMs = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100L
            2 -> fraction.toLong() * 10L
            else -> fraction.take(3).toLong()
        }
        TimedLyricLine(
            timestampMs = minutes * 60_000L + seconds * 1_000L + fractionMs,
            text = text,
        )
    }
}
