package com.spotkofi.app.feature.analytics

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spotkofi.app.R
import com.spotkofi.app.core.LocalAppContainer
import com.spotkofi.app.data.local.LocalMusicStore
import com.spotkofi.app.data.model.Track
import com.spotkofi.app.ui.components.Artwork
import com.spotkofi.app.ui.components.SpotKofiChip
import com.spotkofi.app.ui.motion.staggeredEntry
import com.spotkofi.app.ui.theme.SpotKofiTheme
import java.util.Calendar

private enum class AnalyticsPeriod(
    val label: String,
    val title: String,
) {
    Week("7D", "Last 7 days"),
    Month("30D", "Last 30 days"),
    All("All", "All time"),
}

@Composable
fun ListeningAnalyticsScreen(
    onBack: () -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val container = LocalAppContainer.current
    val history by container.localStore.historyStats.collectAsStateWithLifecycle()
    val playbackEvents by container.localStore.playbackEvents.collectAsStateWithLifecycle()
    var selectedPeriod by remember { mutableStateOf(AnalyticsPeriod.Month) }
    val snapshot = remember(history, playbackEvents, selectedPeriod) {
        buildAnalyticsSnapshot(
            history = history,
            playbackEvents = playbackEvents,
            period = selectedPeriod,
            now = System.currentTimeMillis(),
        )
    }
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.base),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = contentPadding.calculateTopPadding())
                .padding(horizontal = dimens.spaceXs, vertical = dimens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = colors.textPrimary,
                )
            }
            Text(
                text = "Listening Analytics",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimary,
            )
        }

        if (history.isEmpty()) {
            AnalyticsEmptyState(
                title = "No listening data yet",
                message = "Play a few songs and your listening patterns will appear here.",
                icon = Icons.Filled.ShowChart,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = dimens.screenGutter),
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                PeriodSelector(
                    selected = selectedPeriod,
                    onSelect = { selectedPeriod = it },
                )
                if (snapshot.totalPlays == 0) {
                    AnalyticsEmptyState(
                        title = "Nothing in ${selectedPeriod.title.lowercase()}",
                        message = "Try another period or keep listening to build your snapshot.",
                        icon = Icons.Filled.CalendarToday,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = dimens.screenGutter),
                    )
                } else {
                    AnalyticsContent(
                        snapshot = snapshot,
                        hasUntrackedHistory = selectedPeriod == AnalyticsPeriod.All &&
                            snapshot.totalPlays > playbackEvents.size,
                        onTrackClick = onTrackClick,
                        contentPadding = contentPadding,
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodSelector(
    selected: AnalyticsPeriod,
    onSelect: (AnalyticsPeriod) -> Unit,
) {
    val dimens = SpotKofiTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = dimens.screenGutter, vertical = dimens.spaceXs),
        horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
    ) {
        AnalyticsPeriod.entries.forEach { period ->
            SpotKofiChip(
                label = period.label,
                selected = period == selected,
                onClick = { onSelect(period) },
            )
        }
    }
}

@Composable
private fun AnalyticsContent(
    snapshot: AnalyticsSnapshot,
    hasUntrackedHistory: Boolean,
    onTrackClick: (Track, List<Track>) -> Unit,
    contentPadding: PaddingValues,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = dimens.spaceSm,
            bottom = contentPadding.calculateBottomPadding() + dimens.spaceHuge,
        ),
    ) {
        item(key = "summary") {
            Column(
                modifier = Modifier
                    .staggeredEntry(0)
                    .padding(horizontal = dimens.screenGutter)
                    .clip(SpotKofiTheme.shapes.group)
                    .background(colors.card)
                    .padding(dimens.spaceLg),
            ) {
                Text(
                    text = snapshot.period.title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textTertiary,
                )
                Spacer(Modifier.height(dimens.spaceXs))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = snapshot.totalPlays.toString(),
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Black,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.width(dimens.spaceSm))
                    Text(
                        text = if (snapshot.totalPlays == 1) "play" else "plays",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                Spacer(Modifier.height(dimens.spaceLg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
                ) {
                    AnalyticsMetric(
                        icon = Icons.Filled.Timer,
                        label = "Listening time",
                        value = formatListeningTime(snapshot.listenedMs),
                        modifier = Modifier.weight(1f),
                    )
                    AnalyticsMetric(
                        icon = Icons.Filled.MusicNote,
                        label = "Songs",
                        value = snapshot.uniqueSongs.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    AnalyticsMetric(
                        icon = Icons.Filled.People,
                        label = "Artists",
                        value = snapshot.uniqueArtists.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item(key = "activity_heading") {
            AnalyticsSectionTitle(
                title = "Listening activity",
                trailing = "${snapshot.activeDays} active days",
            )
        }
        item(key = "activity") {
            ActivityChart(
                buckets = snapshot.activity,
                modifier = Modifier
                    .staggeredEntry(1)
                    .padding(horizontal = dimens.screenGutter),
            )
        }

        item(key = "tracks_heading") {
            AnalyticsSectionTitle(
                title = "Top tracks",
                trailing = "Tap to play",
            )
        }
        itemsIndexed(
            items = snapshot.topTracks,
            key = { _, entry -> "top_${entry.track.id}" },
        ) { index, entry ->
            TopTrackRow(
                rank = index + 1,
                entry = entry,
                barFraction = entry.playCount.toFloat() / snapshot.maxTrackPlays,
                onClick = {
                    onTrackClick(entry.track, snapshot.topTracks.map { it.track })
                },
            )
        }

        item(key = "artists_heading") {
            AnalyticsSectionTitle(title = "Top artists")
        }
        item(key = "artists") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = dimens.screenGutter),
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceMd),
            ) {
                itemsIndexed(
                    items = snapshot.topArtists,
                    key = { _, artist -> artist.name },
                ) { _, artist ->
                    TopArtist(artist = artist)
                }
            }
        }

        item(key = "footer") {
            Text(
                text = buildString {
                    append("Stats update as you listen")
                    snapshot.lastPlayedAt.takeIf { it > 0L }?.let {
                        append(" · last played ")
                        append(DateUtils.getRelativeTimeSpanString(it))
                    }
                    if (hasUntrackedHistory) {
                        append(" · older plays have no time data")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.screenGutter, vertical = dimens.spaceLg),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AnalyticsMetric(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    Column(modifier = modifier) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(dimens.iconSm),
        )
        Spacer(Modifier.height(dimens.spaceXs))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActivityChart(
    buckets: List<ActivityBucket>,
    modifier: Modifier = Modifier,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    val maxPlays = buckets.maxOfOrNull { it.plays }?.coerceAtLeast(1) ?: 1
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SpotKofiTheme.shapes.group)
            .background(colors.card)
            .padding(dimens.spaceLg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
            verticalAlignment = Alignment.Bottom,
        ) {
            buckets.forEach { bucket ->
                val fraction = bucket.plays.toFloat() / maxPlays
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        text = bucket.plays.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (bucket.plays == maxPlays) {
                            colors.accent
                        } else {
                            colors.textTertiary
                        },
                    )
                    Spacer(Modifier.height(dimens.spaceXs))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((80.dp * fraction).coerceAtLeast(4.dp))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                                if (bucket.plays == maxPlays) {
                                    colors.accent
                                } else {
                                    colors.brown.copy(alpha = 0.36f)
                                },
                            ),
                    )
                    Spacer(Modifier.height(dimens.spaceSm))
                    Text(
                        text = bucket.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun TopArtist(artist: ArtistStat) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    Column(
        modifier = Modifier.width(80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Artwork(
            id = artist.artworkTrack.id,
            url = artist.artworkTrack.artworkUrl,
            size = 72.dp,
            shape = SpotKofiTheme.shapes.avatar,
            contentDescription = "${artist.name} artwork",
        )
        Spacer(Modifier.height(dimens.spaceXs))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "${artist.plays} plays",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
        )
    }
}

@Composable
private fun AnalyticsSectionTitle(
    title: String,
    trailing: String? = null,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = dimens.screenGutter,
                end = dimens.screenGutter,
                top = dimens.spaceLg,
                bottom = dimens.spaceSm,
            )
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
        )
        Spacer(Modifier.weight(1f))
        trailing?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (title == "Top tracks") {
                        Icons.Filled.PlayArrow
                    } else {
                        Icons.Filled.TrendingUp
                    },
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.accent,
                )
            }
        }
    }
}

@Composable
private fun TopTrackRow(
    rank: Int,
    entry: LocalMusicStore.HistoryEntry,
    barFraction: Float,
    onClick: () -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.screenGutter, vertical = dimens.spaceXs)
            .semantics(mergeDescendants = true) {
                contentDescription = "Play ${entry.track.title} by ${entry.track.artistName}"
            },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.brown,
                modifier = Modifier.width(24.dp).padding(top = 2.dp),
            )
            Artwork(
                id = entry.track.id,
                url = entry.track.artworkUrl,
                size = 44.dp,
                contentDescription = "${entry.track.title} artwork",
            )
            Spacer(Modifier.width(dimens.spaceSm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.track.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(dimens.spaceSm))
            Text(
                text = entry.playCount.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = colors.brown,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .padding(start = 24.dp + dimens.spaceSm + 44.dp + dimens.spaceSm)
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(colors.divider),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(barFraction.coerceIn(0.05f, 1f))
                    .fillMaxHeight()
                    .background(colors.accent.copy(alpha = 0.55f)),
            )
        }
    }
}

@Composable
private fun AnalyticsEmptyState(
    title: String,
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(dimens.spaceMd))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(dimens.spaceXs))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

private data class AnalyticsSnapshot(
    val period: AnalyticsPeriod,
    val totalPlays: Int,
    val listenedMs: Long,
    val uniqueSongs: Int,
    val uniqueArtists: Int,
    val activeDays: Int,
    val topTracks: List<LocalMusicStore.HistoryEntry>,
    val topArtists: List<ArtistStat>,
    val maxTrackPlays: Int,
    val activity: List<ActivityBucket>,
    val lastPlayedAt: Long,
)

private data class ActivityBucket(
    val label: String,
    val plays: Int,
)

private data class ArtistStat(
    val name: String,
    val plays: Int,
    val artworkTrack: Track,
)

private fun buildAnalyticsSnapshot(
    history: List<LocalMusicStore.HistoryEntry>,
    playbackEvents: List<LocalMusicStore.PlaybackEvent>,
    period: AnalyticsPeriod,
    now: Long,
): AnalyticsSnapshot {
    val start = periodStart(period, now)
    val periodEvents = playbackEvents.filter { it.startedAt >= start }
    val periodEntries = if (period == AnalyticsPeriod.All) {
        history
    } else {
        periodEvents
            .groupBy { it.track.id }
            .map { (_, entries) ->
                LocalMusicStore.HistoryEntry(
                    track = entries.maxByOrNull { it.startedAt }?.track ?: entries.first().track,
                    playCount = entries.size,
                    playedAt = entries.maxOf { it.startedAt },
                )
            }
    }
    val totalPlays = if (period == AnalyticsPeriod.All) {
        history.sumOf { it.playCount }
    } else {
        periodEvents.size
    }
    val topTracks = periodEntries
        .sortedWith(
            compareByDescending<LocalMusicStore.HistoryEntry> { it.playCount }
                .thenByDescending { it.playedAt },
        )
        .take(10)
    val topArtists = periodEntries
        .groupBy { it.track.artistName }
        .map { (name, entries) ->
            ArtistStat(
                name = name,
                plays = entries.sumOf { it.playCount },
                artworkTrack = entries.maxByOrNull { it.playedAt }?.track
                    ?: entries.first().track,
            )
        }
        .sortedWith(compareByDescending<ArtistStat> { it.plays }.thenBy { it.name })
        .take(6)
    return AnalyticsSnapshot(
        period = period,
        totalPlays = totalPlays,
        listenedMs = periodEvents.sumOf { it.listenedMs },
        uniqueSongs = periodEntries.size,
        uniqueArtists = periodEntries.map { it.track.artistName }.distinct().size,
        activeDays = periodEvents.map { startOfDay(it.startedAt) }.distinct().size,
        topTracks = topTracks,
        topArtists = topArtists,
        maxTrackPlays = topTracks.firstOrNull()?.playCount?.coerceAtLeast(1) ?: 1,
        activity = buildActivityBuckets(period, periodEvents, now),
        lastPlayedAt = periodEntries.maxOfOrNull { it.playedAt } ?: 0L,
    )
}

private fun periodStart(period: AnalyticsPeriod, now: Long): Long {
    return when (period) {
        AnalyticsPeriod.Week -> startOfDay(now) - DAY_MS * 6
        AnalyticsPeriod.Month -> startOfDay(now) - DAY_MS * 29
        AnalyticsPeriod.All -> Long.MIN_VALUE
    }
}

private fun buildActivityBuckets(
    period: AnalyticsPeriod,
    events: List<LocalMusicStore.PlaybackEvent>,
    now: Long,
): List<ActivityBucket> {
    return when (period) {
        AnalyticsPeriod.Week -> {
            val firstDay = startOfDay(now) - DAY_MS * 6
            (0..6).map { offset ->
                val start = firstDay + DAY_MS * offset
                ActivityBucket(
                    label = dayLabel(start),
                    plays = events.count { it.startedAt in start until start + DAY_MS },
                )
            }
        }

        AnalyticsPeriod.Month -> {
            val firstDay = startOfDay(now) - DAY_MS * 29
            (0..4).map { index ->
                val start = firstDay + DAY_MS * index * 7
                val end = if (index == 4) startOfDay(now) + DAY_MS else start + DAY_MS * 7
                ActivityBucket(
                    label = "W${index + 1}",
                    plays = events.count { it.startedAt in start until end },
                )
            }
        }

        AnalyticsPeriod.All -> {
            (5 downTo 0).map { monthsAgo ->
                val start = monthStart(now, monthsAgo)
                val end = monthStart(now, monthsAgo - 1)
                ActivityBucket(
                    label = monthLabel(start),
                    plays = events.count { it.startedAt in start until end },
                )
            }
        }
    }
}

private fun formatListeningTime(listenedMs: Long): String {
    val minutes = listenedMs / 60_000L
    return when {
        minutes <= 0L -> "<1 min"
        minutes < 60L -> "$minutes min"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}

private fun startOfDay(timestamp: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun monthStart(timestamp: Long, monthsAgo: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.MONTH, -monthsAgo)
    }.timeInMillis

private fun dayLabel(timestamp: Long): String {
    val labels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    return labels[Calendar.getInstance().apply { timeInMillis = timestamp }
        .get(Calendar.DAY_OF_WEEK) - 1]
}

private fun monthLabel(timestamp: Long): String {
    val labels = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )
    return labels[Calendar.getInstance().apply { timeInMillis = timestamp }
        .get(Calendar.MONTH)]
}

private const val DAY_MS = 86_400_000L