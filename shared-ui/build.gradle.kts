import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The UI both apps run (MON-24 phase 2; docs/superpowers/plans/
// 2026-11-01-iphone-phase-2-multiplatform-app.md, Task 9 onwards), on Compose Multiplatform. It
// starts with the strings: every `<string>`, `<plurals>` and `<string-array>` the app had in
// `app/src/main/res/values*/` lives in `src/commonMain/composeResources/values*/` (same file names,
// the five locales), where Compose Multiplatform's resource plugin generates `Res` (package
// `com.coparently.app.resources`) from them. The app keeps `R.string` and Android's own reading of
// the same files through `tools/strings-to-android-res.gradle.kts`, which copies them unchanged into
// the app's generated resources. The theme, the common components, the ViewModels and the screens
// follow here task by task, packages unchanged.
//
// Built like `:shared-data`: an `androidTarget()` on the `com.android.library` plugin and the two
// iPhone targets. It depends on both other shared modules, and its framework is the one the iPhone
// app links: `Shared`, static, exporting the domain and the data layer.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("io.gitlab.arturbosch.detekt")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // The one framework the iPhone app links (it replaced `:shared`'s, Task 9). `export` puts the
    // domain's and the data layer's public API in its header, so Swift sees one module. Static, as
    // `:shared`'s was: the Firebase pods `:shared-data` reaches are linked by the app that links
    // this, not into it.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            export(project(":shared"))
            export(project(":shared-data"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `api`, because the framework exports both (an exported module must be an `api`
            // dependency), and the app reaches the data layer through this module from Task 10.
            api(project(":shared"))
            api(project(":shared-data"))
            // `api`: `Res`'s accessors are `StringResource`s and `PluralStringResource`s, which the
            // app's parity tests (and, from Task 10, its code) read.
            //
            // Compose Multiplatform 1.9.3's Android artifacts depend on androidx.compose.runtime and
            // androidx.compose.foundation 1.9.4, one patch above the app's BOM 2025.10.00 (1.9.3), so
            // the app's Compose resolves to 1.9.4 (the androidx Compose libraries move in lockstep);
            // material3 stays the BOM's 1.4.0, which CMP material3 1.9.0 asks for. Not 1.10, which
            // would lift Compose to 1.10 (see the root build file).
            api(compose.runtime)
            api(compose.components.resources)
        }
    }
}

// `Res`, generated from `src/commonMain/composeResources/`. Public, because the app reads it (its
// parity tests now, its code from Task 10), in a package of its own, and generated whether or not a
// module depends on the resources library directly.
compose.resources {
    publicResClass = true
    packageOfResClass = "com.coparently.app.resources"
    generateResClass = always
}

android {
    namespace = "com.coparently.app.sharedui"
    compileSdk = 36

    defaultConfig {
        // The app's minSdk. Nothing in this module may need a higher API level.
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// The app's detekt configuration and the app's one baseline file, as the other shared modules read
// them (see shared/build.gradle.kts). The generated `Res` is under `build/` and is not checked.
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$rootDir/app/config/detekt/detekt.yml")
    baseline = file("$rootDir/app/config/detekt/baseline.xml")
    source.setFrom(
        "src/commonMain/kotlin",
        "src/commonTest/kotlin",
        "src/androidMain/kotlin",
        "src/androidUnitTest/kotlin",
        "src/iosMain/kotlin",
        "src/iosTest/kotlin",
    )
}

dependencies {
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.7")
}

// Never written from here, for `:shared`'s reason: `:app:detektBaseline` reads `shared-ui/src` too
// and writes the one file for every module (app/build.gradle.kts).
tasks.named("detektBaseline") {
    enabled = false
}

// Failures print their message and stack in the CI log, as the app's unit tests do.
tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStackTraces = true
        showCauses = true
    }
}
