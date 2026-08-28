package com.spotkofi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spotkofi.app.ui.motion.clickableScale
import com.spotkofi.app.ui.theme.SpotKofiTheme

/**
 * Shown when a catalog request fails.
 *
 * Every feed in the app is a live network call now, so failure is a state the UI
 * has to render rather than an impossibility. A bare spinner that never resolves
 * is the worst version of this: the user cannot tell a slow connection from a
 * dead one, and has nothing to tap.
 *
 * Retry is deliberately the only affordance. Offline caching would be the better
 * answer, but filling the screen with invented content would not.
 */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Can't load this right now",
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.screenGutter, vertical = dimens.spaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.spaceMd),
    ) {
        Icon(
            imageVector = Icons.Filled.CloudOff,
            contentDescription = null,
            tint = colors.textTertiary,
            modifier = Modifier.size(44.dp),
        )

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )

        // The underlying message is shown rather than swallowed: "Unable to
        // resolve host" and "HTTP 503" mean different things to whoever is
        // looking at the screen, and one generic string would hide both.
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )

        Row(
            modifier = Modifier
                .clip(SpotKofiTheme.shapes.button)
                .background(colors.accent)
                .clickableScale(pressedScale = 0.95f, onClick = onRetry)
                .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Try again",
                style = MaterialTheme.typography.labelLarge,
                color = colors.onAccent,
            )
        }
    }
}

@Preview(backgroundColor = 0xFF121212, showBackground = true)
@Composable
private fun ErrorStatePreview() {
    SpotKofiTheme {
        ErrorState(message = "Unable to resolve host \"api.audius.co\"", onRetry = {})
    }
}
