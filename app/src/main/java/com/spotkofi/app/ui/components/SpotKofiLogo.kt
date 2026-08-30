package com.spotkofi.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spotkofi.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val LOGO_ASSET = "SpotKofi-Logo.png"

/** Loads the user-provided transparent logo from app/src/main/assets. */
@Composable
fun SpotKofiLogo(
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, context.applicationContext) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.applicationContext.assets.open(LOGO_ASSET).use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    val accessibilityLabel = if (onClick != null) {
        contentDescription ?: stringResource(R.string.cd_open_profile)
    } else {
        contentDescription
    }

    Box(
        modifier = modifier
            .size(size)
            .then(
                onClick?.let { callback ->
                    Modifier.clickable(onClick = callback)
                } ?: Modifier,
            )
            .then(
                accessibilityLabel?.let { label ->
                    Modifier.semantics { this.contentDescription = label }
                } ?: Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let { logo ->
            Image(
                bitmap = logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
