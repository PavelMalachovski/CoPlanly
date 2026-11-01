// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.10.1" apply false
    // Kotlin 2.3.21 (MON-24). Kotlin 2.4 used to wait for two things, AGP 9 and Dagger 2.60 (the
    // first Dagger to read metadata 2.4.0, and it needs AGP 9 too). Dagger left with Hilt in MON-24
    // phase 2 (Koin has no processor), so only AGP 9 is left (plan Task 13). The Compose compiler
    // plugin always takes the Kotlin version.
    id("org.jetbrains.kotlin.android") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    // The shared module (MON-24). Same artifact as the Android plugin above, so the same version.
    id("org.jetbrains.kotlin.multiplatform") version "2.3.21" apply false
    // Annotation processing for Room (Hilt's processor left with Hilt, MON-24 phase 2). Since 2.3.0
    // KSP is versioned on its own and is KSP2 only (no Kotlin prefix). 2.3.11 is built against
    // Kotlin 2.3.20, analyses with a 2.4.20 development build, and requires AGP 8.10 or later;
    // 2.3.12 raised that to AGP 8.12.
    id("com.google.devtools.ksp") version "2.3.11" apply false
    id("com.android.library") version "8.10.1" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.7" apply false
    // JVM screenshot tests (Robolectric, no emulator). From 1.61.0 the library and the plugin are
    // compiled with Kotlin 2.3 (metadata 2.3.0), so the project's Kotlin must be 2.3 or later:
    // never let Roborazzi be built with a newer Kotlin than the one above. Upgrade the two
    // together. The library versions live in app/build.gradle.kts beside the other test deps.
    id("io.github.takahirom.roborazzi") version "1.76.0" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.9" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
