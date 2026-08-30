import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // Kotlin compilation comes from AGP 9's built-in Kotlin support, so
    // `org.jetbrains.kotlin.android` is intentionally absent.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Supabase credentials are read from `local.properties`, which is git-ignored.
 *
 * Only the *publishable* (anon) key belongs here. It is safe to ship in a client
 * app because every table is guarded by Row Level Security. The `service_role`
 * key and the database password must never appear in this module: anything
 * compiled into the APK is recoverable from the APK.
 */
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun localProp(key: String, fallback: String = ""): String =
    (localProps.getProperty(key) ?: System.getenv(key) ?: fallback)

android {
    namespace = "com.spotkofi.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.spotkofi.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        // Read back through AppConstants.VERSION_NAME, which is what the footer and
        // the About rows display. Keep the two in step by only editing this line.
        versionName = "1.0.0"

        vectorDrawables.useSupportLibrary = true

        buildConfigField("String", "SUPABASE_URL", "\"${localProp("SUPABASE_URL")}\"")
        buildConfigField(
            "String",
            "SUPABASE_PUBLISHABLE_KEY",
            "\"${localProp("SUPABASE_PUBLISHABLE_KEY")}\"",
        )
        // Optional short-lived Spotify Web API access token for local development.
        // Never put a Spotify client secret here: anything compiled into an APK is
        // recoverable. Production should use PKCE or a server-side token broker.
        buildConfigField(
            "String",
            "SPOTIFY_ACCESS_TOKEN",
            "\"${localProp("SPOTIFY_ACCESS_TOKEN")}\"",
        )
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Debug signing so `assembleRelease` stays runnable before a real
            // keystore exists. Replace with a proper signingConfig before shipping.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.splashscreen)

    // Compose: the BOM aligns every compose artifact to one release train.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.okhttp)
    implementation(libs.pipepipe.extractor)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.database)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.session)

    // `ui-tooling` powers the @Preview renderer, debug-only so it is stripped
    // from release builds.
    debugImplementation(libs.androidx.compose.ui.tooling)
}