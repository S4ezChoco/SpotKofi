package com.spotkofi.app.core

import com.spotkofi.app.BuildConfig

/**
 * Single source for the app's own identity strings.
 *
 * Anything that names the product, its version, its author or its links belongs
 * here rather than being typed into a screen. The footer, the About rows and the
 * lyrics-provider user agent all read from this object, so changing the version
 * or the credit is a one-line edit instead of a hunt through the UI.
 *
 * [VERSION_NAME] deliberately comes from `BuildConfig` rather than being a
 * literal: the version the app reports and the version Gradle stamped into the
 * APK must be the same string, and duplicating it is how those two drift apart.
 */
object AppConstants {

    const val APP_NAME = "SpotKofi"

    /** Mirrors `versionName` in app/build.gradle.kts. */
    val VERSION_NAME: String = BuildConfig.VERSION_NAME

    val VERSION_CODE: Int = BuildConfig.VERSION_CODE

    const val COPYRIGHT_YEAR = "2026"

    const val DEVELOPER = "S4EZCHOCO"

    const val DEVELOPER_ROLE = "DEV"

    /** "SpotKofi v1.0.0", used wherever a version needs naming inline. */
    val VERSION_LABEL: String get() = "$APP_NAME v$VERSION_NAME"

    /** First footer line. */
    val COPYRIGHT_LINE: String get() = "@$COPYRIGHT_YEAR $VERSION_LABEL"

    /** Second footer line. */
    val CREDIT_LINE: String get() = "$DEVELOPER - $DEVELOPER_ROLE"

    /**
     * Contact string sent to third-party providers.
     *
     * Providers such as the lyrics catalog ask clients to identify themselves, so
     * this is a real product name and version rather than a browser disguise.
     */
    val USER_AGENT: String get() = "$APP_NAME/$VERSION_NAME (Android)"

    // ---- Links shown in Settings > About ----

    const val DEVELOPER_URL = "https://github.com/S4EZCHOCO"

    const val LYRICS_PROVIDER_NAME = "LRCLIB"

    const val LYRICS_PROVIDER_URL = "https://lrclib.net"
}
