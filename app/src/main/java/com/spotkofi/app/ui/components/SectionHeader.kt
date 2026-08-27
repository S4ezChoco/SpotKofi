package com.spotkofi.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.spotkofi.app.ui.theme.SpotKofiTheme

/**
 * Title above a shelf, with an optional trailing action.
 *
 * The action is a plain text button rather than an icon so the tap target reads
 * clearly to screen readers without needing a separate description.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.screenGutter, vertical = dimens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (actionLabel != null && onActionClick != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                modifier = Modifier
                    .clickable(onClick = onActionClick)
                    .padding(
                        horizontal = dimens.spaceSm,
                        vertical = dimens.spaceSm,
                    ),
            )
        }
    }
}

@Preview(backgroundColor = 0xFF121212, showBackground = true)
@Composable
private fun SectionHeaderPreview() {
    SpotKofiTheme {
        SectionHeader(title = "Made for you", actionLabel = "Show all", onActionClick = {})
    }
}
