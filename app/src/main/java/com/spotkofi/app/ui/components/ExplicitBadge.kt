package com.spotkofi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotkofi.app.R
import com.spotkofi.app.ui.theme.SpotKofiTheme

/**
 * The small "E" marker shown next to explicit tracks.
 *
 * The glyph is decorative, so the real meaning is attached as a semantics
 * content description on the container instead of being read out as the
 * letter "E".
 */
@Composable
fun ExplicitBadge(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.cd_explicit)

    Box(
        modifier = modifier
            .size(16.dp)
            .background(
                color = SpotKofiTheme.colors.explicit,
                shape = RoundedCornerShape(2.dp),
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "E",
            color = SpotKofiTheme.colors.base,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
