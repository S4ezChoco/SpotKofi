package com.spotkofi.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.spotkofi.app.core.AppConstants
import com.spotkofi.app.ui.theme.SpotKofiTheme

/**
 * The end-of-page credit block.
 *
 * Placed as the last item of every scrollable tab so reaching the bottom of a
 * list has a definite end instead of stopping at an arbitrary row. Both lines
 * come from [AppConstants], so the version and the credit are edited in one
 * place rather than in each screen.
 */
@Composable
fun AppFooter(modifier: Modifier = Modifier) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.screenGutter, vertical = dimens.spaceXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.spaceXs),
    ) {
        Text(
            text = AppConstants.COPYRIGHT_LINE,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = AppConstants.CREDIT_LINE,
            style = MaterialTheme.typography.labelSmall,
            // Lowest emphasis in the palette: it is an attribution, not content.
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(backgroundColor = 0xFF121212, showBackground = true)
@Composable
private fun AppFooterPreview() {
    SpotKofiTheme {
        AppFooter()
    }
}
