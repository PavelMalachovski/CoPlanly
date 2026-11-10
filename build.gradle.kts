// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    // AGP 9.3.3 (MON-24 phase 2, Task 13). 9.3 is the newest minor Kotlin 2.4.21 supports (its
    // Gradle plugin is tested against AGP 8.5.2–9.3.1, kotlinlang.org's compatibility table), and
    // 9.3.3 its newest patch: 9.3.2 fixed lint crashing on JDK 17 (`List.removeLast()`), the JDK
    // every CI job builds on. AGP 9.3 needs Gradle 9.5.0 or later (the wrapper) and compiles
    // against API 37, which Ktor 3.6's OkHttp 5.5 asks for (see shared-data/build.gradle.kts).
    // AGP 9 compiles Kotlin itself ("built-in Kotlin"), so the app no longer applies
    // `org.jetbrains.kotlin.android`, and the shared modules' Android target is AGP's
    // `com.android.kotlin.multiplatform.library` below rather than `com.android.library`.
    id("com.android.application") version "9.3.3" apply false
    id("com.android.kotlin.multiplatform.library") version "9.3.3" apply false
    // Kotlin 2.4.21 (MON-24 phase 2, Task 13), the newest stable 2.4. It waited for AGP 9 alone
    // once Dagger left with Hilt (Koin has no processor). AGP 9 depends on a Kotlin Gradle plugin of
    // its own (2.2.10), and these declarations raise it to this version: one classloader holds both,
    // and the higher version wins. Kotlin 2.4.21 supports Gradle 7.6.3–9.7.0, AGP 8.5.2–9.3.1 and
    // Xcode up to 26.4. The Compose compiler plugin always takes the Kotlin version.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    // The shared modules (MON-24).
    id("org.jetbrains.kotlin.multiplatform") version "2.4.21" apply false
    id("org.jetbrains.kotlin.native.cocoapods") version "2.4.21" apply false
    // kotlinx.serialization's compiler plugin, which replaced Gson's reflection (MON-24). It ships
    // with the compiler, so it always takes the Kotlin version.
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false
    // Annotation processing for Room (Hilt's processor left with Hilt, MON-24 phase 2). Since 2.3.0
    // KSP is versioned on its own and is KSP2 only (no Kotlin prefix). 2.3.13 is the newest release:
    // its API is built with Kotlin 2.4.0, it requires AGP 8.12 or later (from 2.3.12), and its Gradle
    // plugin knows AGP 9's built-in Kotlin and the `com.android.kotlin.multiplatform.library` target.
    id("com.google.devtools.ksp") version "2.3.13" apply false
    // Applied only where a google-services.json exists, which CI never has (app/build.gradle.kts),
    // so no CI job runs these two on AGP 9; check them with a local build that has the file.
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
    // Compose Multiplatform (MON-24 phase 2, Task 9): `:shared-ui`'s resources, theme, components,
    // ViewModels and screens. 1.9.3, not 1.10: 1.10 pulls Compose 1.10 past the app's BOM
    // (2025.10.00, Compose 1.9.3 and Material 3 1.4.0), which would move every screenshot baseline
    // inside a build change, the reason Koin waits at 4.1.1 too. 1.9.3 supports AGP 9 (Compose
    // Multiplatform's 1.10 release notes name 1.9.3 and 1.10.0 as the releases that do), and its
    // resources reach an Android KMP library's assets. 1.9.3's Android artifacts ask for androidx
    // Compose 1.9.4, one patch above the BOM (see shared-ui/build.gradle.kts). The Compose compiler
    // stays the `org.jetbrains.kotlin.plugin.compose` above, at the Kotlin version.
    id("org.jetbrains.compose") version "1.9.3" apply false
    // 1.23.7 on Gradle 9 and AGP 9: its plain `detekt` task, the one CI runs, needs nothing from
    // AGP; its per-variant Android tasks look for AGP's old `BaseExtension`, which AGP 9 no longer
    // registers, so they are simply not created. 2.x changes the baseline format (only the
    // Regenerate workflow writes the baseline), so it is a separate step.
    id("io.gitlab.arturbosch.detekt") version "1.23.7" apply false
    // JVM screenshot tests (Robolectric, no emulator). From 1.61.0 the library and the plugin are
    // compiled with Kotlin 2.3 (metadata 2.3.0), so the project's Kotlin must be 2.3 or later:
    // never let Roborazzi be built with a newer Kotlin than the one above. Its Gradle plugin uses
    // only AGP's public variant API, which AGP 9 keeps, so 1.76.0 stays: a newer release would move
    // the screenshot harness inside a build change. The library versions live in
    // app/build.gradle.kts beside the other test deps.
    id("io.github.takahirom.roborazzi") version "1.76.0" apply false
    // 0.9.9 knows AGP 9's built-in Kotlin and the Android KMP library target (it locates both).
    id("org.jetbrains.kotlinx.kover") version "0.9.9" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
