import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The data layer both apps run (MON-24 phase 2; docs/superpowers/plans/
// 2026-11-01-iphone-phase-2-multiplatform-app.md, Task 5 onwards). It starts with the app's two
// HTTP clients, the Bakaláři school import and Google Calendar's `events.list`, on Ktor; Room,
// Firebase, the repositories and sync follow it here task by task. A file moved here keeps its
// package (`com.coparently.app.data.…`), as phase 1's moves into `:shared` did, so no importer in
// the app changes.
//
// It depends on `:shared` (the domain) and is built like it: an `androidTarget()` on the
// `com.android.library` plugin, and the two iPhone targets. It makes no iOS framework of its own:
// the one framework the iPhone app links comes later, from `:shared-ui`, and exports this module.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.android.library")
    id("io.gitlab.arturbosch.detekt")
    id("org.jetbrains.kotlinx.kover")
}

/**
 * Ktor 3.5.1. Its artifacts are built with Kotlin 2.3.21 (class metadata 2.3.0, a stdlib dependency
 * of 2.3.21), which this project's compiler reads, and it asks for kotlinx-coroutines and
 * kotlinx-serialization 1.11.0, the versions the project already uses. Its OkHttp engine brings
 * OkHttp 5.3.2 (see the app's build file).
 *
 * Not 3.6: Ktor 3.6.0 brings OkHttp 5.5.0 and 3.6.1 brings 5.5.1, and from OkHttp 5.5 the
 * `okhttp-android` AAR requires compileSdk 37 (5.4 requires 36, 5.3.2 and older 1), which AGP 8.10
 * cannot compile against — `checkDebugAarMetadata` fails. So Ktor 3.6 and later wait for AGP 9, as
 * Kotlin 2.4 does. Don't force an older OkHttp under a newer Ktor: no test here runs the real OkHttp
 * engine, so a binary mismatch would show only on a phone.
 */
val ktorVersion = "3.5.1"

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    // The iPhone app's targets, as in `:shared`. A Linux host compiles their klibs; running the
    // tests on a simulator needs Xcode, which is the hosted macOS `shared-ios` job.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // `api`: this module's signatures take the domain's types and its date-time and JSON
            // types (kotlinx-datetime and kotlinx-serialization are `api` there).
            api(project(":shared"))
            // `api`, because the clients' public constructors take an `HttpClient`.
            api("io.ktor:ktor-client-core:$ktorVersion")
            // `kotlinx.io.IOException`, which a network failure arrives as on every platform (on
            // the JVM it is `java.io.IOException` itself). The version Ktor 3.5.1 is built on.
            api("org.jetbrains.kotlinx:kotlinx-io-core:0.9.0")
        }
        androidMain.dependencies {
            // OkHttp, which the school import already talked to schools with before the move.
            implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
        }
        iosMain.dependencies {
            // NSURLSession.
            implementation("io.ktor:ktor-client-darwin:$ktorVersion")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
            // Answers the clients' requests in memory and records them, on the JVM and on the
            // simulator alike: it replaced OkHttp's MockWebServer, which runs on the JVM only.
            implementation("io.ktor:ktor-client-mock:$ktorVersion")
        }
        androidUnitTest.dependencies {
            // commonTest runs here as the Android unit tests, on JUnit 4 like the app's.
            implementation("junit:junit:4.13.2")
        }
    }
}

android {
    namespace = "com.coparently.app.shareddata"
    compileSdk = 36

    defaultConfig {
        // The app's minSdk. Nothing in this module may need a higher API level.
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // As in the app: `android.util.Log`, which the common code's `logger` reaches on Android, is a
    // stub in JVM unit tests and would throw "not mocked" from a failure path a test drives.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// The app's detekt configuration and the app's one baseline file, as `:shared` reads them (see
// shared/build.gradle.kts): a file moved here keeps the baseline entries it had.
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

// Never written from here, for `:shared`'s reason: `:app:detektBaseline` reads `shared-data/src`
// too and writes the one file for every module (app/build.gradle.kts).
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
