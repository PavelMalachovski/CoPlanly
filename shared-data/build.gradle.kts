import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The data layer both apps run (MON-24 phase 2; docs/superpowers/plans/
// 2026-11-01-iphone-phase-2-multiplatform-app.md, Task 5 onwards). It starts with the app's two
// HTTP clients, the Bakaláři school import and Google Calendar's `events.list`, on Ktor; Room,
// Firebase, the repositories and sync follow it here task by task. A file moved here keeps its
// package (`com.coparently.app.data.…`), as phase 1's moves into `:shared` did, so no importer in
// the app changes.
//
// It depends on `:shared` (the domain) and is built like it: AGP 9's Android KMP library target
// (`com.android.kotlin.multiplatform.library`, `kotlin { android { } }`, MON-24 phase 2, Task 13)
// and the two iPhone targets. It makes no iOS framework of its own:
// the one framework the iPhone app links comes later, from `:shared-ui`, and exports this module.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    // The Firebase iOS SDK for the iPhone targets (Task 8a): GitLive's bindings name its pods, and
    // this plugin links them. See `cocoapods { }` below.
    id("org.jetbrains.kotlin.native.cocoapods")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.android.kotlin.multiplatform.library")
    id("io.gitlab.arturbosch.detekt")
    id("org.jetbrains.kotlinx.kover")
    // Room's processor, for Android and both iPhone targets (Task 7; the app ran it until then).
    id("com.google.devtools.ksp")
}

/**
 * Ktor 3.6.1, the newest release (MON-24 phase 2, Task 13; 3.5.1 before). Its artifacts are built
 * with Kotlin 2.3.21 (a stdlib dependency of 2.3.21), which this project's 2.4.21 compiler reads, and
 * it asks for kotlinx-coroutines 1.11.0, the project's, and kotlinx-io 0.9.1 (below). Never take a
 * Ktor built with a newer Kotlin than the project's.
 *
 * Its OkHttp engine brings OkHttp 5.5.1, whose `okhttp-android` AAR requires compileSdk 37 (5.4
 * requires 36, 5.3.2 and older 1). AGP 8.10 could not compile against 37, which is what held Ktor at
 * 3.5.1 (and OkHttp at 5.3.2); AGP 9.3 can, so the app and the three shared modules compile against
 * 37 and Ktor moved with AGP 9. Don't force an older OkHttp under a newer Ktor: no test here runs the
 * real OkHttp engine, so a binary mismatch would show only on a phone.
 */
val ktorVersion = "3.6.1"

/**
 * Room 2.7.2, the version the app compiled against before the database moved here, so nothing about
 * the generated code or the exported schema changes with the move. It is Room for Kotlin
 * Multiplatform: the same artifacts carry the Android, iOS and JVM targets. Its SQLite driver
 * artifacts are androidx.sqlite 2.5.2, released with it (see `sqlite-bundled` below).
 */
val roomVersion = "2.7.2"

/**
 * GitLive's firebase-kotlin-sdk 2.7.0 (MON-24 Task 1 chose it; ROADMAP MON-24, "Firebase on two
 * platforms"): Firestore from common code, over the Android SDK on Android and the iOS SDK on the
 * iPhone. Its newest stable release, built with Kotlin 2.2.21 (metadata this compiler reads) and on
 * kotlinx-coroutines 1.10.2 and kotlinx-serialization 1.9.0, which the project's 1.11.0 and 1.12.0
 * replace. 3.0's alphas are built with Kotlin 2.4, which this project now uses (Task 13), and stay
 * out while they are alphas. On Android it imports the Firebase BoM [firebaseBom], which the app
 * declares too.
 */
val gitliveVersion = "2.7.0"

/** The Firebase BoM GitLive 2.7.0 imports; the app's build file names the same one. */
val firebaseBom = "com.google.firebase:firebase-bom:34.18.0"

/**
 * The Firebase iOS SDK GitLive 2.7.0's bindings were generated against. Its pods are linked only
 * (`linkOnly`): GitLive publishes the bindings, and a second binding of the same headers here would
 * clash with them.
 */
val firebaseIosVersion = "11.8.0"

/**
 * `WireFixtureLocation`, the wire contracts' one input from the build (CLAUDE.md item 5): the
 * fixture directory, which stays at `app/src/test/resources/wire/` because the `upgrade` job copies
 * that path, as an absolute path — the iOS simulator's tests run in a directory of their own and
 * read the host's files by it — and whether `UPDATE_WIRE_FIXTURES` asks `CurrentWireFixturesTest`
 * to rewrite `wire/current/`. Read through kotlinx-io on both targets.
 */
val generateWireFixtureLocation = tasks.register("generateWireFixtureLocation") {
    val fixtureRoot = rootDir.resolve("app/src/test/resources/wire").absolutePath
    val update = providers.environmentVariable("UPDATE_WIRE_FIXTURES").orElse("")
    val output = layout.buildDirectory.dir("generated/wireFixtures/commonTest/kotlin")
    inputs.property("fixtureRoot", fixtureRoot)
    inputs.property("update", update)
    outputs.dir(output)
    doLast {
        val rewrite = update.get().let { it == "1" || it == "true" }
        val escaped = fixtureRoot.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\${'$'}")
        val file = output.get().file("com/coparently/app/wire/WireFixtureLocation.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package com.coparently.app.wire
            |
            |/** Written by `:shared-data:generateWireFixtureLocation`; see `shared-data/build.gradle.kts`. */
            |internal object WireFixtureLocation {
            |    /** `app/src/test/resources/wire`, absolute. */
            |    const val ROOT: String = "$escaped"
            |
            |    /** Whether `UPDATE_WIRE_FIXTURES` was set when the tests were built. */
            |    const val UPDATE: Boolean = $rewrite
            |}
            |
            """.trimMargin()
        )
    }
}

kotlin {
    android {
        namespace = "com.coparently.app.shareddata"
        // API 37: Ktor 3.6's OkHttp 5.5 AAR asks for it (see [ktorVersion]).
        compileSdk = 37
        // The app's minSdk. Nothing in this module may need a higher API level.
        minSdk = 26
        // Java and Kotlin bytecode alike, as `compileOptions` and `jvmTarget` set them before.
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        // The JVM tests: `androidHostTest`, with commonTest in the same compilation (the new plugin
        // creates them only when asked). As in the app: `android.util.Log`, which the common code's
        // `logger` reaches on Android, is a stub in JVM tests and would throw "not mocked" from a
        // failure path a test drives.
        withHostTest {
            isReturnDefaultValues = true
        }
    }

    // The iPhone app's targets, as in `:shared`. A Linux host compiles their klibs; running the
    // tests on a simulator needs Xcode, which is the hosted macOS `shared-ios` job.
    iosArm64()
    iosSimulatorArm64()

    // The Firebase pods GitLive's iOS bindings call, built by CocoaPods and linked into what links
    // this module: the iOS test executable here, the app's framework later. Only the products the
    // moved code uses. A Linux host never runs CocoaPods: the plugin configures its pod and link
    // steps on macOS only, and a klib compile needs none of them (the bindings come with GitLive).
    // The dynamic framework below is the plugin's own requirement for `linkOnly` pods; nothing
    // builds it — the iPhone app's one framework comes from `:shared-ui`. CI's `shared-ios` keys its
    // pod cache on three lines here, `firebaseIosVersion`, `ios.deploymentTarget` and the pod list;
    // keep each on one line, as its grep reads them.
    cocoapods {
        version = "1.0"
        summary = "CoPlanly's shared data layer"
        homepage = "https://github.com/PavelMalachovski/CoPlanly"
        ios.deploymentTarget = "17.0"
        noPodspec()
        framework {
            baseName = "SharedData"
            isStatic = false
        }
        listOf("FirebaseCore", "FirebaseFirestore", "FirebaseFirestoreInternal", "FirebaseFunctions").forEach { name ->
            pod(name) {
                version = firebaseIosVersion
                linkOnly = true
            }
        }
    }

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
            // the JVM it is `java.io.IOException` itself). The version Ktor 3.6.1 is built on.
            api("org.jetbrains.kotlinx:kotlinx-io-core:0.9.1")
            // `api`: `CoPlanlyDatabase` extends `RoomDatabase` and the app reaches its DAOs.
            api("androidx.room:room-runtime:$roomVersion")
            // Firestore from common code (`data/remote/firebase/GitLiveFirestoreClient`). Only
            // Firestore: Functions goes through GitLive on the iPhone alone (see `iosMain`).
            // `api`: `GitLiveFirestoreClient` takes GitLive's `FirebaseFirestore`, and the app's R8
            // probe encodes a write through GitLive's encoder.
            api("dev.gitlive:firebase-firestore:$gitliveVersion")
            // `dataModules` and `platformDataModule`, which the app includes in `appModules`.
            // `api`: they are `Module`s in this module's public API. The app's BoM names 4.1.1.
            api("io.insert-koin:koin-core:4.1.1")
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
            implementation(project.dependencies.platform(firebaseBom))
            implementation("com.google.firebase:firebase-crashlytics")
            // The Android SDK the platform halves call (`data/remote/firebase/` in androidMain):
            // the Firestore instance Koin builds is wrapped, not replaced, and callables go through
            // the SDK here because GitLive cannot wrap an existing `FirebaseFunctions` (its region
            // and an emulator's host are set on that instance). Same BoM as above.
            implementation("com.google.firebase:firebase-firestore")
            implementation("com.google.firebase:firebase-functions")
            // `Task.await()`, which the Android halves call; the app's version.
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
        }
        iosMain.dependencies {
            // NSURLSession.
            implementation("io.ktor:ktor-client-darwin:$ktorVersion")
            // Room's bundled SQLite driver on the iPhone (`IosDatabase.kt`): SQLite compiled into
            // the app rather than the system library, so both platforms' SQLite behave alike.
            // androidx.sqlite 2.5.2 is the release Room 2.7.2 depends on (both 18 June 2025).
            // iOS only, so no Android build resolves it.
            implementation("androidx.sqlite:sqlite-bundled:2.5.2")
            // Callables on the iPhone (`IosCallableFunctions`), in the functions' region.
            implementation("dev.gitlive:firebase-functions:$gitliveVersion")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
            // Answers the clients' requests in memory and records them, on the JVM and on the
            // simulator alike: it replaced OkHttp's MockWebServer, which runs on the JVM only.
            implementation("io.ktor:ktor-client-mock:$ktorVersion")
        }
        // The wire fixtures' location, generated by Gradle (see `generateWireFixtureLocation`).
        commonTest {
            kotlin.srcDir(generateWireFixtureLocation)
        }
        getByName("androidHostTest").dependencies {
            // commonTest runs here as the Android host (unit) tests, on JUnit 4 like the app's.
            implementation("junit:junit:4.13.2")
            // `GitLiveFirestoreClientAndroidTest` drives the GitLive client over a mocked Android
            // Firestore and checks each SDK call it makes. The app's version.
            implementation("io.mockk:mockk:1.13.13")
            // The chat repository's tests read its flows with it (Task 8b). The app's version.
            implementation("app.cash.turbine:turbine:1.2.0")
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
        "src/androidHostTest/kotlin",
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
// the plugin was not tried in this task (Google's Maven repository, where it is published, is not
// reachable from the cloud sessions this project is written in), and this argument is what the app
// already used with KSP 2.3. The cost is the one the app already had: the directory is not a
// declared output, so a KSP task restored from the build cache writes nothing there, which is why
// the Regenerate workflow runs the task with `--rerun` and CI deletes `46.json` before its build.
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
