/*
 * Root build file.
 *
 * AGP 9 provides Kotlin compilation itself (built-in Kotlin), so there is
 * deliberately no `org.jetbrains.kotlin.android` plugin anywhere in this build.
 * Applying it is a hard failure.
 *
 * AGP 9.2.1 pins KGP 2.2.10, which is too old to read the kotlin-stdlib 2.4.10
 * that the AndroidX and Coil versions in the catalog drag in. The documented fix
 * for raising KGP above AGP's own pin is a buildscript classpath entry, which is
 * what the block below does.
 */
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
