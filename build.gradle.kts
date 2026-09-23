// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("com.google.dagger.hilt.android") version "2.56.2" apply false
    id("com.android.library") version "8.10.1" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.7" apply false
    // JVM screenshot tests (Robolectric, no emulator). 1.60.0 is the last release built with
    // Kotlin 2.0.x; from 1.61.0 the library is compiled with Kotlin 2.3, and the Kotlin 2.1
    // compiler this project uses reads metadata at most one minor version ahead. Upgrade the two
    // together. The library versions live in app/build.gradle.kts beside the other test deps.
    id("io.github.takahirom.roborazzi") version "1.60.0" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.9" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
