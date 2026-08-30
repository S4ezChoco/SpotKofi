package com.spotkofi.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/*
 * Typography.
 *
 * Spotify ships a proprietary face (Circular). We use the platform sans and
 * reproduce the *feel* instead: very heavy display weights, tight negative
 * letter spacing on large text, and a hard drop to a mid-grey 400 weight for
 * metadata. That contrast between 800 and 400 is what reads as "Spotify",
 * more than the letterforms do.
 *
 * To swap in a real font later:
 *   1. Drop the .ttf files into app/src/main/res/font/ (lowercase, e.g.
 *      figtree_bold.ttf).
 *   2. Replace the line below with:
 *        private val AppFont = FontFamily(
 *            Font(R.font.figtree_regular, FontWeight.Normal),
 *            Font(R.font.figtree_medium, FontWeight.Medium),
 *            Font(R.font.figtree_bold, FontWeight.Bold),
 *            Font(R.font.figtree_extrabold, FontWeight.ExtraBold),
 *        )
 *   Nothing else in the app needs to change.
 */
private val AppFont = FontFamily.SansSerif

/** Rounded handwritten display style used only for the café/lofi brand mark. */
val SpotKofiBrandStyle = TextStyle(
    fontFamily = FontFamily.Cursive,
    fontWeight = FontWeight.Bold,
    fontSize = 23.sp,
    lineHeight = 27.sp,
    letterSpacing = 0.2.sp,
)

/** Big screen titles: playlist name on a detail header, "Good evening". */
val DisplayLarge = TextStyle(
    fontFamily = AppFont,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 32.sp,
    lineHeight = 36.sp,
    letterSpacing = (-0.8).sp,
)

val SpotKofiTypography = Typography(
    // ---- Display: hero text only ----
    displayLarge = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        letterSpacing = (-1.2).sp,
    ),
    displayMedium = DisplayLarge,
    displaySmall = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.5).sp,
    ),

    // ---- Headline: section headers ("Made For You") ----
    headlineLarge = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.2).sp,
    ),

    // ---- Title: card titles, track names ----
    titleLarge = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 19.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 17.sp,
    ),

    // ---- Body: metadata, subtitles ----
    bodyLarge = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),

    // ---- Label: buttons, chips, nav ----
    labelLarge = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.3.sp,
        textAlign = TextAlign.Center,
    ),
)
