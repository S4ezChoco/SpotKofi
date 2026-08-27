package com.spotkofi.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spotkofi.app.R
import com.spotkofi.app.ui.theme.SpotKofiTheme

/**
 * The user's avatar, used as the affordance that opens the profile drawer.
 *
 * Falls back to a coloured monogram until Phase 3 supplies a real avatar from
 * Supabase Storage. The colour is derived from the name so it stays stable.
 */
@Composable
fun ProfileAvatar(
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
) {
    val label = stringResource(R.string.cd_open_profile)
    val background = lerp(artworkSeedColor(name), Color.Black, 0.25f)
    val initial = name.trim().firstOrNull()?.uppercase() ?: "?"

    Box(
        modifier = modifier
            .size(size)
            .background(background, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
        )
    }
}
