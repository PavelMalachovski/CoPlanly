import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The shared Kotlin Multiplatform module (MON-24, phase 1 of the iPhone app;
// docs/superpowers/specs/2026-10-27-iphone-app-design.md §5.1). It holds the domain both apps
// run: the Android app depends on it (`implementation(project(":shared"))`) and the iPhone app
// reaches it through `:shared-ui`'s `Shared` framework, which exports it. A file moved here keeps
// its package (`com.coparently.app.domain.…`), so no importer in the app changes.
//
// Android is AGP 9's `com.android.kotlin.multiplatform.library` target (MON-24 phase 2, Task 13;
// it replaced `androidTarget()` on `com.android.library`, which AGP 9 no longer allows beside the
// multiplatform plugin): one variant, no build types, configured in `kotlin { android { } }`. Its
// JVM tests are the `androidHostTest` source set (`src/androidHostTest/`, which was
// `src/androidUnitTest/`), run by `testAndroidHostTest`; commonTest runs there too.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.android.kotlin.multiplatform.library")
    id("io.gitlab.arturbosch.detekt")
    id("org.jetbrains.kotlinx.kover")
}

kotlin {
    android {
        namespace = "com.coparently.app.shared"
        // API 37, as the app and the other shared modules (Ktor 3.6's OkHttp asks for it).
        compileSdk = 37
        // The app's minSdk. Nothing in this module may need a higher API level.
        minSdk = 26
        // Java and Kotlin bytecode alike, as `compileOptions` and `jvmTarget` set them before.
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        // The JVM tests: `androidHostTest`, with commonTest in the same compilation. The new plugin
        // creates them only when asked.
        withHostTest {}
    }

    // The iPhone app's targets: a device and the Apple-silicon simulator. Since Kotlin 2.2.20
    // a Linux host compiles their klibs (`compileKotlinIosSimulatorArm64`) without any flag, so
    // the Linux `shared` job checks that the code compiles for iOS; running the tests on a
    // simulator needs Xcode, which is the hosted macOS `shared-ios` job. This module makes no
    // framework of its own since MON-24 phase 2 (Task 9): `:shared-ui`'s `Shared` framework, the one
    // the iPhone app links, exports it.
    iosArm64()
    iosSimulatorArm64()

    // `expect object` (Sha256, Lzma1Raw) is a Beta feature that warns on every build without this
    // flag; an object is the shape the brief names, so the warning is acknowledged here once.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            // The same version as the app's kotlinx-coroutines-android: one coroutines release
            // in the process. `api`, because the domain's repository interfaces expose `Flow` in
            // their signatures, so a consumer of this module compiles against it too.
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            // 0.8.0 (May 2026) requires Kotlin stdlib 2.1.20 or later and uses the standard
            // library's `kotlin.time.Instant` (0.7 moved it there). On Android it is java.time
            // underneath, which minSdk 26 has. `api`, because the domain's signatures take its
            // types (`EventTimestamp.ofWallClock`, `IsoText`); its jar carries its own R8 rules.
            api("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
            // `StoredJson` (MON-24), which replaced Gson for every stored and sent JSON text. 1.12.0
            // is the newest release built with a Kotlin no newer than this project's: Kotlin 2.4.10
            // (its stdlib dependency), which the 2.4.21 compiler reads. It moves with Kotlin (Task 13
            // took it from 1.11.0 with the move to 2.4); `GsonParityTest`, `StoredJsonVectorsTest`
            // and the R8 probe hold its output to the bytes Gson wrote. `api`, because `StoredJson`'s
            // signatures take its `KSerializer` and `JsonElement`. Its jar carries its own R8 rules
            // for the generated serializers.
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
        }
        androidMain.dependencies {
            // PAY by square's raw LZMA1 encoder (`Lzma1Raw`), the version the app uses: one
            // library in the process, and the encoder `PaymentQrTest` pins byte for byte.
            implementation("org.tukaani:xz:1.10")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
        // Parity tests that compare a moved function with the JVM API it replaces run here,
        // where java.* exists. commonTest runs here too, as the Android host (unit) tests.
        getByName("androidHostTest").dependencies {
            implementation("junit:junit:4.13.2")
            // The oracle `GsonParityTest` and `StoredLocalDateParityTest` compare `StoredJson` with:
            // the Gson the app used until MON-24, at the version it used. Tests only.
            implementation("com.google.code.gson:gson:2.11.0")
        }
    }
}

// Unit-test coverage for the app's merged Kover report (`:app:koverXmlReportUnit`): the JVM tests,
// which AGP 9's Android KMP library is to Kover. The app merges a variant of this name only.
// Kover 0.9.x does not recognise AGP 9.3's target yet, so this is empty for now (CLAUDE.md, CI).
kover {
    currentProject {
        createVariant("unit") {
            // Optional: should Kover stop finding the target, coverage shrinks; configuring never fails.
            add("jvm", optional = true)
        }
    }
}

// The app's detekt configuration and the app's baseline file, one for both modules: a file moved
// here keeps the baseline entries it had (an entry names the file and the signature, never the
// directory). The plugin's default sources are the JVM layout, so the multiplatform source sets
// are named here.
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$rootDir/app/config/detekt/detekt.yml")
    baseline = file("$rootDir/app/config/detekt/baseline.xml")
    source.setFrom(
        "src/commonMain/kotlin",
        "src/commonTest/kotlin",
        "src/androidMain/kotlin",
        "src/androidHostTest/kotlin",
        "src/iosMain/kotlin",
        "src/iosTest/kotlin",
    )
}

dependencies {
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.7")
}

// Never written from here: detekt's baseline task replaces the file's findings with its own run's,
// so this task would erase every entry of the app's. `:app:detektBaseline` reads `shared/src` too
// and writes the one file for both (app/build.gradle.kts).
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
