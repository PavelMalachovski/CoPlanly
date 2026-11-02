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
    // Room's processor, for Android and both iPhone targets (Task 7; the app ran it until then).
    id("com.google.devtools.ksp")
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

/**
 * Room 2.7.2, the version the app compiled against before the database moved here, so nothing about
 * the generated code or the exported schema changes with the move. It is Room for Kotlin
 * Multiplatform: the same artifacts carry the Android, iOS and JVM targets. Its SQLite driver
 * artifacts are androidx.sqlite 2.5.2, released with it (see `sqlite-bundled` below).
 */
val roomVersion = "2.7.2"

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

    // `expect object CoPlanlyDatabaseConstructor`, whose `actual` Room's processor writes per
    // target (`@ConstructedBy`), is a Beta feature that warns on every build without this flag, as
    // in `:shared`.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

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
            // `api`: `CoPlanlyDatabase` extends `RoomDatabase` and the app reaches its DAOs.
            api("androidx.room:room-runtime:$roomVersion")
        }
        androidMain.dependencies {
            // OkHttp, which the school import already talked to schools with before the move.
            implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
            // SQLCipher, Room's open helper on Android (SEC-2; `EncryptedDatabase`). The same
            // declaration as the app's: `@aar`, as the library's own instructions specify, which
            // drops its transitive dependencies.
            implementation("net.zetetic:sqlcipher-android:4.6.1@aar")
            // The `SupportSQLite…` interfaces SQLCipher's open helper and the legacy migrations
            // are written against, named because the `@aar` above drops SQLCipher's own. Room
            // brings it too; 2.5.2 is the release that shipped with Room 2.7.2 (18 June 2025).
            implementation("androidx.sqlite:sqlite:2.5.2")
            // `CrashlyticsManager`, which `EncryptedDatabase` reports a failed conversion through
            // and whose constructor the `upgrade` job's seed calls. The app's BoM version.
            implementation(project.dependencies.platform("com.google.firebase:firebase-bom:34.4.0"))
            implementation("com.google.firebase:firebase-crashlytics")
        }
        iosMain.dependencies {
            // NSURLSession.
            implementation("io.ktor:ktor-client-darwin:$ktorVersion")
            // Room's bundled SQLite driver on the iPhone (`IosDatabase.kt`): SQLite compiled into
            // the app rather than the system library, so both platforms' SQLite behave alike.
            // androidx.sqlite 2.5.2 is the release Room 2.7.2 depends on (both 18 June 2025).
            // iOS only, so no Android build resolves it.
            implementation("androidx.sqlite:sqlite-bundled:2.5.2")
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
    // Room's processor runs on the platform compilations, each of which includes `commonMain`; it
    // writes the DAOs' implementations and the `actual` of `CoPlanlyDatabaseConstructor` for each.
    add("kspAndroid", "androidx.room:room-compiler:$roomVersion")
    add("kspIosArm64", "androidx.room:room-compiler:$roomVersion")
    add("kspIosSimulatorArm64", "androidx.room:room-compiler:$roomVersion")
}

// Room writes the exported schema, `<schemaLocation>/com.coparently.app.data.local.CoPlanlyDatabase/
// <version>.json`, where it has always been: `app/schemas/`, which the app's instrumented tests
// read as assets (`MigrationTestHelper`), the `upgrade` job reads, and `ci.yml` checks with
// `git status --porcelain -- app/schemas` after the build. Every target's processor writes the same
// file (the schema is common code), so the Linux `shared` job's iOS compile checks it too.
//
// A KSP argument, as the app passed it, not the Room Gradle plugin's `room { schemaDirectory }`:
// the plugin finds the KSP tasks it configures by KSP's task classes, and KSP 2.3 removed the KSP1
// ones, which no session here could check against the plugin (Google's Maven repository is not
// reachable from the cloud sessions this project is written in). The cost is the one the app
// already had: the directory is not a declared output, so a KSP task restored from the build cache
// writes nothing there, which is why the Regenerate workflow runs the task with `--rerun`.
ksp {
    arg("room.schemaLocation", "$rootDir/app/schemas")
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
