package com.spotkofi.app.ui.components

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotkofi.app.data.model.TrackLyrics
import com.spotkofi.app.ui.motion.clickableScale
import com.spotkofi.app.ui.theme.Motion
import com.spotkofi.app.ui.theme.SpotKofiTheme
import kotlinx.coroutines.delay

/** One line of a timed lyric sheet. */
data class LyricLine(
    val timestampMs: Long,
    val text: String,
)

/**
 * How long after the user stops dragging before the lyrics follow the song again.
 *
 * Without a pause, reading ahead is impossible: the next line boundary yanks the
 * list back. Without a resume, one accidental scroll leaves the panel dead for the
 * rest of the song.
 */
private const val AutoScrollResumeMs = 3_500L

/** Distance in lines past which a line is fully dimmed. */
private const val MaxFalloffDistance = 4f

/**
 * The lyrics reader.
 *
 * Only the current line is fully lit; neighbours fade and, where the platform
 * supports it, blur with distance, so the eye lands on the line being sung without
 * the rest of the sheet disappearing. That depth cue is the whole design - a flat
 * list of equally bright lines gives the reader nothing to track.
 *
 * Tapping a line seeks to it. Timed lyrics are the only kind that scroll or
 * respond to taps; a plain sheet is rendered as static text, because there is
 * nothing to sync it to.
 */
@Composable
fun LyricsView(
    lyrics: TrackLyrics,
    positionMs: Long,
    modifier: Modifier = Modifier,
    onSeekTo: ((Long) -> Unit)? = null,
    /** Font size of a lyric line. The full-screen view uses a larger value. */
    lineFontSize: androidx.compose.ui.unit.TextUnit = 22.sp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    val lines = remember(lyrics.synced) { parseSyncedLyrics(lyrics.synced) }

    if (lyrics.instrumental) {
        LyricsMessage(
            text = "This recording is instrumental.",
            modifier = modifier,
        )
        return
    }

    if (lines.isEmpty()) {
        val plain = lyrics.plain
        if (plain.isNullOrBlank()) {
            LyricsMessage(text = "No lyrics available for this track.", modifier = modifier)
        } else {
            // Unsynced sheets scroll like any other text and are shown at full
            // brightness: with no timings there is no "current" line to favour.
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(dimens.spaceSm),
            ) {
                items(plain.lines().filter { it.isNotBlank() }) { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = lineFontSize,
                            lineHeight = lineFontSize * 1.35f,
                        ),
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = dimens.spaceLg),
                    )
                }
            }
        }
        return
    }

    val listState = rememberLazyListState()
    val activeIndex by remember(lines) {
        derivedStateOf { activeLineIndex(lines, positionMs) }
    }

    // Auto-scroll yields to the user and then takes over again.
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var autoScroll by remember { mutableStateOf(true) }
    LaunchedEffect(isDragged) {
        if (isDragged) {
            autoScroll = false
        } else if (!autoScroll) {
            delay(AutoScrollResumeMs)
            autoScroll = true
        }
    }

    // Centring is measured from the list's own viewport rather than from an outer
    // constraints scope: the LazyColumn is the thing that scrolls, and its
    // layoutInfo already reports the height and the active line's offset within it.
    LaunchedEffect(activeIndex, autoScroll) {
        if (!autoScroll || activeIndex < 0) return@LaunchedEffect
        val info = listState.layoutInfo
        val viewport = info.viewportSize.height
        val item = info.visibleItemsInfo.firstOrNull { it.index == activeIndex }
        if (item == null) {
            // Off-screen after a seek: jump near it first, otherwise the
            // animation would sweep through every line in between.
            listState.scrollToItem(activeIndex.coerceAtLeast(0))
        } else {
            // Centred rather than pinned to the top, so there is as much lyric
            // visible ahead of the line as behind it.
            val delta = item.offset - (viewport - item.size) / 2
            runCatching { listState.animateScrollBy(delta.toFloat(), tween(420)) }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(dimens.spaceMd),
    ) {
        itemsIndexed(lines, key = { index, line -> "$index:${line.timestampMs}" }) { index, line ->
            LyricLineRow(
                text = line.text,
                distance = if (activeIndex < 0) 1f else (index - activeIndex).toFloat(),
                isActive = index == activeIndex,
                fontSize = lineFontSize,
                onClick = onSeekTo?.let { seek -> { seek(line.timestampMs) } },
            )
        }
    }
}

@Composable
private fun LyricLineRow(
    text: String,
    distance: Float,
    isActive: Boolean,
    fontSize: androidx.compose.ui.unit.TextUnit,
    onClick: (() -> Unit)?,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    // Lines already sung recede slightly faster than lines still to come, which is
    // what makes the sheet read as moving forwards.
    val weightedDistance = if (distance < 0f) -distance + 0.6f else distance

    val alpha by animateFloatAsState(
        targetValue = if (isActive) {
            1f
        } else {
            (1f - weightedDistance / MaxFalloffDistance).coerceIn(0.25f, 0.85f)
        },
        animationSpec = tween(320),
        label = "lyricAlpha",
    )
    val blurRadius by animateDpAsState(
        targetValue = if (isActive || !BlurSupported) {
            0.dp
        } else {
            (weightedDistance * 1.1f).coerceAtMost(5f).dp
        },
        animationSpec = tween(320),
        label = "lyricBlur",
    )
    val color by animateColorAsState(
        targetValue = if (isActive) colors.textPrimary else colors.textSecondary,
        animationSpec = tween(320),
        label = "lyricColor",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickableScale(pressedScale = 0.98f, onClick = onClick)
                } else {
                    Modifier
                },
            )
            // Alpha before blur: blurring an already-translucent layer smears it
            // instead of softening it.
            .graphicsLayer { this.alpha = alpha }
            .then(
                if (blurRadius > 0.dp) {
                    Modifier.blur(blurRadius, BlurredEdgeTreatment.Unbounded)
                } else {
                    Modifier
                },
            )
            // Padding inside the blurred box, so the blur is not sliced flat at the
            // screen edge.
            .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceXxs),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = fontSize,
                lineHeight = fontSize * 1.3f,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
            ),
            color = color,
        )
    }
}

@Composable
private fun LyricsMessage(text: String, modifier: Modifier = Modifier) {
    val dimens = SpotKofiTheme.dimens
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(dimens.spaceLg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = SpotKofiTheme.colors.textSecondary,
        )
    }
}

/**
 * `Modifier.blur` is documented as a no-op below API 31, so the depth cue falls
 * back to opacity alone there rather than pretending to blur.
 */
private val BlurSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Index of the line currently being sung, or -1 before the first one.
 *
 * -1 is a real state: an intro has no active line, and forcing the first line to
 * light up during it makes the sheet look out of sync from the very first second.
 * After the last line the index sticks, so the closing line stays lit.
 */
fun activeLineIndex(lines: List<LyricLine>, positionMs: Long): Int {
    if (lines.isEmpty() || positionMs <= 0L) return -1
    var low = 0
    var high = lines.lastIndex
    var found = -1
    while (low <= high) {
        val mid = (low + high) / 2
        if (lines[mid].timestampMs <= positionMs) {
            found = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return found
}

/**
 * Parses the `[mm:ss.xx]` form used by lyric sheets.
 *
 * Fractions are read at their real precision: truncating to whole seconds first,
 * which an earlier helper did, landed every line up to a second early or late and
 * made a correctly timed sheet look broken.
 */
fun parseSyncedLyrics(raw: String?): List<LyricLine> {
    if (raw.isNullOrBlank()) return emptyList()
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
        LyricLine(
            timestampMs = minutes * 60_000L + seconds * 1_000L + fractionMs,
            text = text,
        )
    }.sortedBy { it.timestampMs }
}

/** True when the sheet carries timings, which is what enables sync and seeking. */
fun TrackLyrics.isSynced(): Boolean = !synced.isNullOrBlank()

/** Short label naming the kind of sheet, shown in the player's lyrics card. */
fun TrackLyrics.syncLabel(): String = when {
    instrumental -> "Instrumental"
    isSynced() -> "Synced"
    hasText -> "Unsynced"
    else -> "Unavailable"
}

/** Kept out of the composable so the colour is defined once. */
internal val LyricsScrimColor: Color = Color.Black.copy(alpha = 0.35f)
