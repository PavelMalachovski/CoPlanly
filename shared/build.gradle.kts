import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The shared Kotlin Multiplatform module (MON-24, phase 1 of the iPhone app;
// docs/superpowers/specs/2026-10-27-iphone-app-design.md §5.1). It holds the domain both apps
// run: the Android app depends on it (`implementation(project(":shared"))`) and the iPhone app
// links the `Shared` framework below. A file moved here keeps its package
// (`com.coparently.app.domain.…`), so no importer in the app changes.
//
// Android is an ordinary `androidTarget()` on the `com.android.library` plugin the root declares
// (AGP 8.10). The AGP 9 `com.android.kotlin.multiplatform.library` plugin is a later move, with
// AGP 9 itself.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.android.library")
    id("io.gitlab.arturbosch.detekt")
    id("org.jetbrains.kotlinx.kover")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // The iPhone app's targets: a device and the Apple-silicon simulator. Since Kotlin 2.2.20
    // a Linux host compiles their klibs (`compileKotlinIosSimulatorArm64`) without any flag, so
    // the Linux `shared` job checks that the code compiles for iOS; linking the framework and
    // running the tests on a simulator need Xcode, which is the Mac's `shared-ios` job.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

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
            // `StoredJson` (MON-24), which replaced Gson for every stored and sent JSON text. 1.11.0
            // (April 2026) is the newest release built with a Kotlin no newer than this project's:
            // Kotlin 2.3.20, which the 2.3.21 compiler reads; 1.12.0 is built with Kotlin 2.4.
            // `api`, because `StoredJson`'s signatures take its `KSerializer` and `JsonElement`. Its
            // jar carries its own R8 rules for the generated serializers.
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
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
        // where java.* exists. commonTest runs here too, as the Android unit tests.
        androidUnitTest.dependencies {
            implementation("junit:junit:4.13.2")
            // The oracle `GsonParityTest` and `StoredLocalDateParityTest` compare `StoredJson` with:
            // the Gson the app used until MON-24, at the version it used. Tests only.
            implementation("com.google.code.gson:gson:2.11.0")
        }
    }
}

android {
    namespace = "com.coparently.app.shared"
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
        "src/androidUnitTest/kotlin",
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
