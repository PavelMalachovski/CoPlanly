plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.gitlab.arturbosch.detekt")
    id("io.github.takahirom.roborazzi")
    id("org.jetbrains.kotlinx.kover")
}

// The Google Services and Firebase Crashlytics Gradle plugins both require a
// google-services.json. It is gitignored, so on CI (and any fresh clone without it)
// applying them would fail — the Crashlytics plugin's release mapping-upload task
// errors with "Google-Services plugin not configured properly".
// Apply both plugins only when the file is present. Runtime Crashlytics still works
// everywhere via the firebase-crashlytics SDK dependency; only build-time mapping
// upload is skipped when the config is absent.
val hasGoogleServicesJson =
    file("google-services.json").exists() ||
    file("src/google-services.json").exists() ||
    file("src/debug/google-services.json").exists() ||
    file("src/release/google-services.json").exists()

if (hasGoogleServicesJson) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

/**
 * A build secret, from `~/.gradle/gradle.properties` or the environment — never from a tracked
 * file. Same rule the OAuth client secret followed before SEC-1 moved it server-side, and the
 * reason is the same: anything committed here ships to everyone who clones the repository.
 *
 * A blank value counts as absent. CI sets some of these to empty strings rather than leaving them
 * unset, and a blank password produces a keystore error that reads nothing like "not configured".
 */
fun buildSecret(name: String): String? =
    (findProperty(name) as String?)?.takeIf { it.isNotBlank() }
        ?: System.getenv(name)?.takeIf { it.isNotBlank() }

// REL-2. The four halves of a release signing key. All of them or none: a config with three of
// the four fails at signing time with a message about the missing one, which is a worse failure
// than not signing at all.
val releaseStoreFile = buildSecret("COPLANLY_RELEASE_STORE_FILE")
val releaseStorePassword = buildSecret("COPLANLY_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = buildSecret("COPLANLY_RELEASE_KEY_ALIAS")
val releaseKeyPassword = buildSecret("COPLANLY_RELEASE_KEY_PASSWORD")

/**
 * Whether this machine can sign a release build.
 *
 * **False must stay a working build, not a failure.** CI runs `assembleRelease` on every pull
 * request — it is the only place R8 runs, and an R8 rewrite of a Gson model shipped to devices
 * once already (audit §2.8) — and CI has no keystore and should never have one. So an
 * unconfigured machine produces an *unsigned* release APK, which still proves the build survives
 * shrinking, and only a machine holding the key produces an installable one.
 *
 * The file existing is part of the answer: a path pointing at nothing is a typo in
 * `gradle.properties`, and catching it here says so instead of failing inside apksigner.
 */
val canSignRelease = run {
    // Bound to a local first: a top-level `val` in a Gradle script is a property of the generated
    // script class, and Kotlin does not smart-cast those, so `file(releaseStoreFile)` on the
    // nullable would not compile.
    val store = releaseStoreFile
    store != null &&
        releaseStorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null &&
        file(store).exists()
}

/**
 * The published privacy policy (REL-4), `web/privacy/` — see `web/README.md`. Set in October 2026
 * when hosting went live, on the owner's decision, while the page is still the draft with its
 * banner: the closed test needs the link in the app, and the page is regenerated in place when
 * counsel's pass lands, so the URL does not change. Empty hides the Settings row and the consent
 * screen's button.
 */
val publishedPrivacyPolicyUrl = "https://coparently-a39c9.web.app/privacy/"

/**
 * The published terms of service, `web/terms/` — set with the privacy policy, and a draft in the
 * same way. Empty hides the sign-in screen's and Settings' links to it.
 */
val publishedTermsUrl = "https://coparently-a39c9.web.app/terms/"

/**
 * The export verification page (MON-16), `web/verify/` — printed on every exported file beside its
 * record id. Hosted since October 2026 (`firebase deploy --only hosting`); before that it was empty,
 * and a registered file printed its record id without an address rather than one that did not
 * resolve.
 */
val publishedExportVerifyUrl = "https://coparently-a39c9.web.app/verify/"

/**
 * Whether an invitation is shared as `https://coparently-a39c9.web.app/pair/CODE` (`web/pair/`, a
 * link messengers make clickable, which opens the app or says how to get it) rather than
 * `coplanly://pair?code=CODE`, which they leave as text. On since `web/pair/` was hosted (October
 * 2026); it was false until then, because a link to a page that is not there is worse than one
 * that is not clickable. The app reads both forms either way. -PCOPLANLY_PAIRING_WEB_LINK=false
 * turns it off for one build.
 */
val publishedPairingWebLink = true

/**
 * Where a parent reports a chat message (Play's user-generated content policy; play-final audit
 * F-10): the support or privacy mailbox somebody reads. Empty until the owner names one, and empty
 * hides the chat's "Report message" action, since a report with nowhere to go is the affordance
 * design item 8 forbids. Pass -PCOPLANLY_SUPPORT_EMAIL to try one without committing it.
 */
val publishedSupportEmail = ""

/**
 * The AI assist (reply suggestions, the month-in-review summary): on in debug, off in `release`
 * and `r8Test` until billing (MON-11) exists to gate it. `-PCOPLANLY_AI_ASSIST_ENABLED=true|false`
 * overrides every build type, to try it in a release build or switch it off in a debug one. While
 * it is false no AI affordance renders at all (design item 8); the server has its own switch
 * (`ai-disabled`) on top of this one.
 */
val aiAssistOverride: String? = (findProperty("COPLANLY_AI_ASSIST_ENABLED") as String?)
    ?.trim()
    ?.lowercase()
    ?.takeIf { it == "true" || it == "false" }

/** The `AI_ASSIST_ENABLED` value for a build type whose own default is [default]. */
fun aiAssistFlag(default: Boolean): String = aiAssistOverride ?: default.toString()

android {
    // The Kotlin package, and therefore where `R` and `BuildConfig` are generated. Deliberately
    // *not* the same as `applicationId` below: renaming the package would touch every file in
    // the tree for no user-visible gain, while the applicationId is the identity Play and
    // Firebase key on. The two are allowed to differ and this is exactly the case for it.
    namespace = "com.coparently.app"
    compileSdk = 36

    defaultConfig {
        // The app's permanent identity. Changed from `com.coparently.app` (REL-1) while it still
        // could be: after the first Play upload an applicationId can never change — a different
        // one is a different app, with no upgrade path for anyone who installed the first. The
        // product is CoPlanly, the deep-link scheme is `coplanly://`, and the old id said
        // "coparently".
        //
        // **This breaks a local build until Firebase is updated**, and it is meant to: the
        // Google Services plugin matches `google-services.json` on the package name and will
        // fail with "No matching client found for package name 'app.coplanly'". Register the new
        // app in the Firebase console, download a fresh `google-services.json`, and update the
        // OAuth client's package name and SHA-1. CI is unaffected — `google-services.json` is
        // gitignored, so the plugin is not applied there at all.
        applicationId = "app.coplanly"
        minSdk = 26
        targetSdk = 36
        // Overridden per upload by the release workflow (-PCOPLANLY_VERSION_CODE), because Play
        // refuses a second bundle with the same versionCode.
        versionCode = (project.findProperty("COPLANLY_VERSION_CODE") as String?)?.toInt() ?: 2
        versionName = "1.1.0"

        testInstrumentationRunner = "com.coparently.app.KoinTestRunner"

        // REL-4. Where the hosted privacy policy lives, read by the Settings row and the
        // telemetry consent screen. Blank until the policy is published, and blank hides both
        // links: a link to a page that does not resolve is the affordance-promising-nothing
        // design rule #8 forbids. Set `publishedPrivacyPolicyUrl` above once
        // `firebase deploy --only hosting` has run (it is a public URL, not a secret), or pass
        // -PCOPLANLY_PRIVACY_POLICY_URL to try one without committing it.
        val privacyPolicyUrl = (project.findProperty("COPLANLY_PRIVACY_POLICY_URL") as String?)
            ?.trim()
            ?: publishedPrivacyPolicyUrl
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"$privacyPolicyUrl\"")

        // The terms of service, same rule: blank until hosted, and blank hides the links.
        val termsUrl = (project.findProperty("COPLANLY_TERMS_URL") as String?)
            ?.trim()
            ?: publishedTermsUrl
        buildConfigField("String", "TERMS_URL", "\"$termsUrl\"")

        // MON-16. Same rule as the policy: blank until hosted, and blank omits the line.
        val exportVerifyUrl = (project.findProperty("COPLANLY_EXPORT_VERIFY_URL") as String?)
            ?.trim()
            ?: publishedExportVerifyUrl
        buildConfigField("String", "EXPORT_VERIFY_URL", "\"$exportVerifyUrl\"")

        // Which pairing link the share sheet and the QR carry (see publishedPairingWebLink).
        val pairingWebLink = (project.findProperty("COPLANLY_PAIRING_WEB_LINK") as String?)
            ?.trim()
            ?.toBoolean()
            ?: publishedPairingWebLink
        buildConfigField("boolean", "PAIRING_WEB_LINK", "$pairingWebLink")

        // F-10. The chat's report address; blank hides the action (see publishedSupportEmail).
        val supportEmail = (project.findProperty("COPLANLY_SUPPORT_EMAIL") as String?)
            ?.trim()
            ?: publishedSupportEmail
        buildConfigField("String", "SUPPORT_EMAIL", "\"$supportEmail\"")

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        // Created only when every part is present, so an unconfigured checkout has no `release`
        // config to resolve rather than a broken one.
        if (canSignRelease) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            buildConfigField("Boolean", "ENABLE_CRASHLYTICS", "false")
            buildConfigField("Boolean", "ENABLE_ANALYTICS", "false")
            buildConfigField("Boolean", "AI_ASSIST_ENABLED", aiAssistFlag(default = true))
        }

        release {
            // Null on a machine without the key: the APK comes out unsigned and the build still
            // succeeds. See `canSignRelease`.
            signingConfig = if (canSignRelease) signingConfigs.getByName("release") else null
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("Boolean", "ENABLE_CRASHLYTICS", "true")
            buildConfigField("Boolean", "ENABLE_ANALYTICS", "true")
            // Premium, and gated until billing (MON-11) exists: no AI affordance in a release.
            buildConfigField("Boolean", "AI_ASSIST_ENABLED", aiAssistFlag(default = false))
        }

        // REL-7's runtime proof: `release` as R8 builds it, plus the probe in `src/r8Test/`, run on
        // an emulator by the `r8-runtime` CI job (`tools/run-r8-probe.sh`). `check-r8-mapping.js`
        // shows R8 kept the field names a keep rule names (none since MON-24 phase 2); this shows
        // the minified app still *writes and reads* its stored JSON — generated serializers,
        // constructors and all. Nothing here changes `debug` or `release`.
        create("r8Test") {
            initWith(getByName("release"))
            // Installable without the release key, which CI must never hold. Signing is applied
            // after R8 and does not change what it produces.
            signingConfig = signingConfigs.getByName("debug")
            // Stays non-debuggable, as `release` is: for a debuggable build AGP runs R8 in its debug
            // mode, which skips optimisations the shipped build gets — the probe would pass on code
            // that never ships.
            isDebuggable = false
            // Keeps the probe's entry point, and nothing of the app (see the file).
            proguardFile("proguard-r8test.pro")
            matchingFallbacks += listOf("release")
            // Nothing reports anywhere from a test build. The probe never starts the telemetry
            // applier anyway (it skips Application.onCreate); these only close the build-flag half.
            buildConfigField("Boolean", "ENABLE_CRASHLYTICS", "false")
            buildConfigField("Boolean", "ENABLE_ANALYTICS", "false")
            buildConfigField("Boolean", "AI_ASSIST_ENABLED", aiAssistFlag(default = false))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    bundle {
        // Keep every locale in the base APK. Play otherwise splits the AAB by language and
        // installs only the device's languages, so picking another one in Settings → Language
        // (AppCompat per-app locales) would silently fall back to English.
        language {
            enableSplit = false
        }
    }

    buildFeatures {
        compose = true
        // Keep BuildConfig enabled for feature flags
        buildConfig = true
    }

    lint {
        // Localization is intentionally partial: strings missing from a locale fall back
        // to the base (English) resources at runtime. This turns the check off entirely
        // (not merely down to a warning) so an incomplete locale never fails the build.
        // Completing translations is tracked separately; verify locale completeness with
        // a direct grep across values*/ instead of relying on lint for it.
        disable += "MissingTranslation"

        // CredManMissingDal fires because CredentialAuthenticator offers password sign-in
        // through Credential Manager (GetPasswordOption / CreatePasswordRequest) without an
        // `asset_statements` <meta-data> in the manifest. That meta-data exists to associate
        // the app with a *website* via Digital Asset Links, so a password saved on one is
        // offered on the other. CoPlanly owns no domain — the same reason the pairing deep
        // link in AndroidManifest.xml is a custom scheme rather than an App Link — so there
        // is no assetlinks.json to point at, and inventing one would fail verification
        // rather than satisfy anything. Password sign-in itself works without it: the
        // credential is then scoped to this package and signing certificate alone.
        // Re-enable this check together with the App Links work, when a domain exists
        // (docs/ROADMAP.md, CQ-16).
        disable += "CredManMissingDal"
    }

    // Compose compiler is applied via the org.jetbrains.kotlin.plugin.compose plugin
    // (its version follows the Kotlin version; composeOptions is no longer needed).

    // Test optimizations
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
        // Disable animations for faster UI tests
        animationsDisabled = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1,DEPENDENCIES}"
        }
    }

    // Exported Room schemas (`room.schemaLocation` in shared-data/build.gradle.kts) are the fixtures
    // MigrationTestHelper reads to create a database at a past version and to validate the
    // rebuilt schema after a migration — without this, CoPlanlyDatabaseMigrationTest cannot
    // find 11.json/12.json at instrumentation runtime.
    sourceSets {
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
        // The wire contracts' harness lives in `:shared-data`'s commonTest (MON-24 phase 2, Task
        // 8a), where the contracts move as their writers do. The contracts whose writers are
        // still here run from this module, so its unit tests compile the same harness from the
        // same files, beside this module's `WireContracts` (the list of every contract) and its
        // `WireFixtureLocation`. Only these two directories are shared this way.
        getByName("test") {
            java.srcDir("$rootDir/shared-data/src/commonTest/kotlin/com/coparently/app/wire")
            // The recording Firestore and callable fakes the moved contracts and the app's own
            // data-layer tests drive the data sources with.
            java.srcDir("$rootDir/shared-data/src/commonTest/kotlin/com/coparently/app/data/remote/firebase/testing")
        }
    }
}

dependencies {
    // The shared Kotlin Multiplatform module (MON-24): the domain the iPhone app runs too. Its
    // classes keep their `com.coparently.app.domain` packages, so nothing here imports
    // differently, and R8 shrinks them with the app's own.
    implementation(project(":shared"))
    // Its Android unit tests' coverage joins this module's Kover report (`koverXmlReportDebug`
    // runs `:shared`'s debug tests too), so the one figure in the PR comment still covers the
    // domain after it moves.
    kover(project(":shared"))
    // The data layer both apps share (MON-24 phase 2): so far the school import's and Google
    // Calendar's HTTP clients, on Ktor. Packages unchanged, as for `:shared`; its tests' coverage
    // joins the same report.
    implementation(project(":shared-data"))
    kover(project(":shared-data"))
    // The UI both apps share (MON-24 phase 2, Task 9): so far the strings, as Compose
    // Multiplatform resources (`Res`, which the parity tests read). The app reads the same files
    // as Android resources through `tools/strings-to-android-res.gradle.kts`, applied below.
    implementation(project(":shared-ui"))

    // Core Android - Updated to latest stable versions
    implementation("androidx.core:core-ktx:1.13.1")
    // AppCompat is required for per-app language preferences
    // (AppCompatDelegate.setApplicationLocales with autoStoreLocales persistence)
    implementation("androidx.appcompat:appcompat:1.7.0")
    // Play Install Referrer: an invitation's Play link carries the pairing code through the install,
    // read once on the phone (domain/pairing/InstallReferrer.kt). Nothing is sent anywhere.
    implementation("com.android.installreferrer:installreferrer:2.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    // Installs the baseline profile (src/main/baseline-prof.txt) on a device the app did not
    // reach through Play, which is every closed-test and sideloaded install. Activity already
    // brings it in; declared so the profile does not depend on a transitive dependency.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    // Compose - BOM 2025.10 (Compose 1.9.x, Material 3 1.4.x / M3 Expressive)
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation - 2.9.x (predictive-back aware transitions)
    implementation("androidx.navigation:navigation-compose:2.9.3")

    // Splash Screen API for Android 12+
    implementation("androidx.core:core-splashscreen:1.0.1")

    // ViewModel - Updated to match lifecycle version
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Koin (MON-24 phase 2): dependency injection resolved at runtime, with no processor, so the
    // graph is checked by `KoinGraphTest` (`verify()` over every module) instead of at compile time.
    // 4.1.1, not the newer 4.2.x: 4.2's Compose artifacts depend on Compose Multiplatform 1.10,
    // activity 1.12 and lifecycle 2.10, which would lift this app's Compose past its BOM above and
    // move every screenshot baseline inside a DI change. 4.1.1 asks for Compose 1.8.2 (the BOM's
    // 1.9 wins), lifecycle 2.9.3 and activity 1.10.1 (above the 2.8.7 and 1.9.3 declared here, so
    // those two resolve up), and is built with Kotlin 2.1.21, which this project's 2.3.21 compiler
    // reads. `koin-androidx-compose` brings `koinViewModel()` (koin-compose-viewmodel) with it.
    val koinBom = platform("io.insert-koin:koin-bom:4.1.1")
    implementation(koinBom)
    implementation("io.insert-koin:koin-android")
    implementation("io.insert-koin:koin-androidx-compose")
    implementation("io.insert-koin:koin-androidx-workmanager")

    // Room - 2.7.x is required for Kotlin 2.x metadata (2.6.x fails on it). The database, its
    // entities and DAOs live in `:shared-data` since MON-24 phase 2 (Room for Kotlin
    // Multiplatform), and Room's processor runs there; the app keeps the runtime it calls
    // (`withTransaction`, `Room.inMemoryDatabaseBuilder` in the tests) and runs no processor.
    val roomVersion = "2.7.2"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    // MigrationTestHelper — instrumented tests that run a real migration against a real
    // SQLite database and validate the result against the exported schema JSON.
    androidTestImplementation("androidx.room:room-testing:$roomVersion")

    // SQLCipher — Room's open helper, so the database file is encrypted at rest (SEC-2).
    // `@aar` is what the library's own instructions specify, and it drops the transitive
    // dependencies, which is why androidx.sqlite is named here rather than inherited.
    implementation("net.zetetic:sqlcipher-android:4.6.1@aar")
    implementation("androidx.sqlite:sqlite:2.4.0")

    // Coroutines — 1.11.0, the companion release of Kotlin 2.2.20 and the newest stable; every
    // kotlinx-coroutines artifact below takes the same version.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // WorkManager for background tasks. 2.10.x (CQ-17) for its Doze and foreground-service
    // fixes, which is what the 15-minute periodic sync runs into; 2.11 raised minSdk and is a
    // separate step.
    implementation("androidx.work:work-runtime-ktx:2.10.5")

    // The "Today" home-screen widget (presentation/widget). Glance 1.1.1, the stable line: it
    // renders Compose-style code to RemoteViews, and glance-material3 fills its theme from the
    // app's own colour schemes. Its sessions run on WorkManager, above.
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    // Calendar - Check for updates at https://github.com/kizitonwose/Calendar
    implementation("com.kizitonwose.calendar:compose:2.6.1")

    // Google Sign-In - Migrating to Credential Manager API
    // Old deprecated API (will be removed)
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // New Credential Manager API
    implementation("androidx.credentials:credentials:1.2.2")
    implementation("androidx.credentials:credentials-play-services-auth:1.2.2")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Google Calendar's one call, `events.list`, is made on Ktor from `:shared-data` since MON-24
    // phase 2; google-api-client-android and google-api-services-calendar left with it, and so did
    // the Gson they brought and the keep rules their `@Key` models needed.

    // Read-only since SEC-5: EncryptedPreferences now seals its own file with the Keystore key
    // EncryptionManager holds, and uses this library once, to read the pre-SEC-5 store on the first
    // launch of that build. Remove it together with EncryptedPreferences.readLegacyStore once no
    // older install can remain — never bump it to write with it again.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // JSON for everything the app stores or sends as text goes through `StoredJson` (`:shared`),
    // on kotlinx.serialization, which replaced Gson in MON-24 and writes the bytes Gson wrote. The
    // same version as the shared module's, declared here because the app's own models
    // (`EventDraft`) and the school import's tree reads use it directly.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    // Not a dependency: a library on the runtime classpath still brings Gson, asking for 2.10.1,
    // after Google's calendar client left (MON-24 phase 2). This keeps it at 2.11.0, the version the
    // app declared and ran until MON-24, so the library runs the Gson it ran before. It is also the
    // version `GsonParityTest` and `GsonDeviceParityTest` compare with: Android's consistent
    // resolution pins the test classpaths to the app's runtime version, so without this they
    // cannot resolve 2.11.0 at all.
    constraints {
        implementation("com.google.code.gson:gson:2.11.0")
    }

    // ZXing for QR code generation
    implementation("com.google.zxing:core:3.5.3")

    // Raw LZMA1 for the Slovak "PAY by square" payment code (domain/payment/PaymentQr.kt), the one
    // QR standard that compresses its text. Pure Java, no reflection; 0BSD.
    implementation("org.tukaani:xz:1.10")

    // ML Kit for QR code scanning
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // CameraX — live preview for the pairing QR scanner. ML Kit only analyses
    // frames; something has to produce them.
    val cameraxVersion = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ML Kit text recognition for receipts — bundled Latin model, so OCR works offline
    // and from first launch (~4 MB) instead of waiting on a Play Services download.
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // Removed with the AI subsystem (MON-7): `generativeai`, `retrofit`, `converter-gson`,
    // `okhttp` and `logging-interceptor`. All five were declared for it and, after it went,
    // had no consumer left in `app/src` at all — retrofit already had none before.
    // OkHttp was declared again for the school import (MON-8) and left the app's own dependencies
    // in MON-24 phase 2, when the import moved to Ktor in `:shared-data`. Its OkHttp engine brings
    // OkHttp 5.3.2, which Coil 2.7 (built against 4.12) now runs on too: Coil uses only OkHttp's
    // public API (calls, requests, responses, headers, cache control), which 5.x keeps
    // binary-compatible, and nothing in the app calls OkHttp itself any more.

    // Firebase BoM 34 (audit L-2). 34.0.0 removed the `-ktx` modules; their Kotlin extensions
    // (`Firebase`, `logEvent`, `storageMetadata`, …) have lived in the main modules under the
    // product's own package since BoM 32.5, which is where every import in `app/src` already
    // points. Don't add a `-ktx` artifact back: it no longer resolves.
    //
    // 34.18.0 since MON-24 phase 2 (Task 8a): GitLive's firebase-kotlin-sdk 2.7.0, which
    // `:shared-data` reaches Firestore through, imports this BoM, and Gradle resolves one BoM for
    // the whole graph — the higher one. Declaring the same version here keeps the app's own
    // Firebase artifacts on the version they actually run at, rather than one this line no longer
    // decides. GitLive's Android side is this same SDK, so the data layer's calls are the SDK's
    // own. Move this and `:shared-data`'s together, and only to a BoM a GitLive release names.
    val firebaseBom = platform("com.google.firebase:firebase-bom:34.18.0")
    implementation(firebaseBom)
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-storage")

    // Callable Cloud Functions — pairing accept/unpair write both parents'
    // user documents, which is only safe server-side.
    implementation("com.google.firebase:firebase-functions")

    // `Task.await()` (kotlinx.coroutines.tasks), which the data layer calls throughout. It used
    // to arrive only transitively through the Firebase modules; declared so a BoM move cannot
    // take it away. Same version as kotlinx-coroutines-android above.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")

    // The R8 probe's `FirestoreWriteProbe` encodes a write through GitLive's own encoder
    // (`dev.gitlive.firebase.internal.encodeAsObject`) in the minified build. GitLive's Android
    // metadata puts this artifact in its runtime variant only, so the app runs it but cannot
    // compile against it; the probe alone needs it on the compile path. Same version as
    // `:shared-data`'s GitLive (2.7.0, built with Kotlin 2.2.21, `minCompileSdk=1`).
    "r8TestImplementation"("dev.gitlive:firebase-common-internal:2.7.0")

    // Coil for async image loading in Compose (receipt photos)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Testing - Updated to latest stable
    testImplementation("junit:junit:4.13.2")
    // The oracle `GsonParityTest` compares `StoredJson` with, and the wire contracts' fixture
    // printer: the Gson the app shipped until MON-24, at the version it shipped. The app's own code
    // has no Gson since then; a library still brings it, held at this version by the constraint
    // above.
    testImplementation("com.google.code.gson:gson:2.11.0")
    testImplementation(kotlin("test"))
    // kotlin-reflect at the compiler's own version for the JVM tests. MockK asks for 2.0.0, which
    // reads Kotlin metadata only up to 2.1, and this module is compiled with Kotlin 2.3; a
    // reflection library older than the compiler is unsupported. Unversioned, it follows the
    // Kotlin plugin. Not for androidTest: the test APK carries kotlin-reflect but not the stdlib,
    // and the `upgrade` job runs it inside the *base* build's process, on the base's stdlib. A
    // kotlin-reflect newer than that stdlib fails there (2.3's needs
    // `kotlin.jvm.internal.KotlinGenericDeclaration`, which stdlib 2.1 lacks), so androidTest keeps
    // MockK's 2.0.0 until main itself ships stdlib 2.3.
    testImplementation(kotlin("reflect"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    // `GsonDeviceParityTest`'s oracle: the Gson the app shipped until MON-24, on the device's ART,
    // whose field order the stored bytes follow. Test APK only.
    androidTestImplementation("com.google.code.gson:gson:2.11.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.10.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // MockK for mocking - Latest stable
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("io.mockk:mockk-android:1.13.13")
    // 1.14.0 is the first release whose inline-mocking agent (libmockkjvmtiagent.so) is 16 KB
    // page-aligned; 1.13.x fails to dlopen on the API 35 16 KB emulator leg, before any test
    // runs. The JVM unit tests above do not load the agent and stay on 1.13.13.
    androidTestImplementation("io.mockk:mockk-android:1.14.0")

    // Coroutines Test - Latest stable
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")

    // Turbine for Flow testing
    testImplementation("app.cash.turbine:turbine:1.2.0")

    // Ktor's in-memory server for the school import's tests (MON-8): token rotation and the
    // refresh-and-retry on a 401, through the real client. It replaced OkHttp's MockWebServer when
    // the client moved to Ktor (MON-24); the version is `:shared-data`'s Ktor.
    testImplementation("io.ktor:ktor-client-mock:3.5.1")

    // ArchCore Testing for LiveData and ViewModel
    testImplementation("androidx.arch.core:core-testing:2.2.0")

    // Koin's graph check for the JVM tests (`KoinGraphTest`). Not for androidTest: koin-test
    // carries kotlin-reflect, which the test APK must not (see kotlin-reflect above); the
    // instrumented tests start and stop the graph with koin-android alone (`KoinTestRule`).
    testImplementation(koinBom)
    testImplementation("io.insert-koin:koin-test")
    testImplementation("io.insert-koin:koin-test-junit4")
    // OneParentOnScreenTest: the app's pairing path asks WorkManager for a sync, and
    // KoinTestApplication is not the Configuration.Provider the manifest expects.
    androidTestImplementation("androidx.work:work-testing:2.10.5")

    // Navigation Testing
    androidTestImplementation("androidx.navigation:navigation-testing:2.9.3")

    // JVM screenshot tests — Roborazzi on Robolectric's native graphics, no emulator
    // (`app/src/test/java/com/coparently/app/screenshots`, recorded by `recordRoborazziDebug`).
    // Robolectric 4.16.1 knows SDK 36, but runs SDK 36 only on JDK 21 and CI builds on 17, so
    // the tests pin `@Config(sdk = [34])` — see `ScreenshotMatrix`. `ui-test-junit4` provides
    // `createComposeRule`; its activity comes from the `ui-test-manifest` debug dependency above.
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.76.0")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    // The widget's layout, composed and asserted on the JVM (TodayWidgetContentTest).
    testImplementation("androidx.glance:glance-appwidget-testing:1.1.1")
}

// androidx.room:room-testing-android pulls in JUnit 5 (junit-jupiter/junit-platform)
// transitively for its own Kotlin-multiplatform test fixtures, which this project never uses
// (everything here is JUnit 4). Left in, six of those jars all ship META-INF/LICENSE.md and
// fail androidTest resource merging. An exclude on the room-testing dependency declaration
// itself does not reach this: room-testing is a Kotlin Multiplatform umbrella artifact that
// Gradle resolves to room-testing-android via variant metadata ("available-at"), and per-
// dependency exclude rules do not propagate across that redirect. A configuration-level
// exclude does, since it is enforced against every resolved module regardless of how it
// entered the graph.
configurations.named("androidTestImplementation") {
    exclude(group = "org.junit.jupiter")
    exclude(group = "org.junit.platform")
}

// The JVM bytecode target, in the compilerOptions DSL: `android { kotlinOptions { … } }` is an
// error since Kotlin 2.2.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Room ran on KSP here until MON-24 phase 2 moved the database to `:shared-data`, which now runs
// the processor for Android and the iPhone and writes the exported schema to this module's
// `schemas/` as before (`room.schemaLocation` in shared-data/build.gradle.kts). Nothing in this
// module needs KSP any more, so the plugin is not applied. Room generates Kotlin there, the only
// output Room for Kotlin Multiplatform has; the Kotlin output throws where the Java returned null
// into a non-null type, on a single-row query that finds no row, so every such query is declared
// nullable (`DaoEmptyResultTest`). The schema JSON does not depend on it.

// Detekt configuration for static code analysis
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$projectDir/config/detekt/detekt.yml")
    baseline = file("$projectDir/config/detekt/baseline.xml")
    // The plugin's defaults are src/{main,test}/{java,kotlin}; the R8 probe (the `r8Test` build
    // type's own source set) is held to the same rules.
    source.from("src/r8Test/java")
}

dependencies {
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.7")
}

// One baseline for every module (MON-24): `:shared:detekt`, `:shared-data:detekt` and
// `:shared-ui:detekt` read this module's baseline file, and detekt's `detektBaseline` *replaces* a
// file's findings with the ones it found in its own run. So the other modules' baseline tasks are
// switched off (their build files) and this one also reads their sources, which makes
// `./gradlew detektBaseline` (the Regenerate workflow) write one file holding every module's
// accepted findings. An entry names a
// file and a signature, never a directory, so a file moved into `shared/`, `shared-data/` or
// `shared-ui/` keeps the entry it had.
tasks.named<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>("detektBaseline") {
    source(rootProject.layout.projectDirectory.dir("shared/src"))
    source(rootProject.layout.projectDirectory.dir("shared-data/src"))
    source(rootProject.layout.projectDirectory.dir("shared-ui/src"))
}

// Screenshots are recorded into, and verified against, the committed baselines in
// `src/test/screenshots` — one directory for both, so `recordRoborazziDebug` writes exactly what
// `verifyRoborazziDebug` reads. Test code names each file relative to it (see
// `roborazzi.record.filePathStrategy` in gradle.properties). The baselines are recorded only by
// the Regenerate workflow, on the same runner image CI verifies on: Robolectric's native renderer
// is deterministic per platform and font set, not across them, so a baseline recorded on a laptop
// fails in CI on pixels nobody changed. A verify run writes `<variant>_compare.png` (baseline,
// diff, new image) and `<variant>_actual.png` into the compare directory, under a folder per
// component that `ScreenshotMatrix` adds — Roborazzi itself keeps only the file name there.
roborazzi {
    outputDir.set(file("src/test/screenshots"))
    compare {
        outputDir.set(layout.buildDirectory.dir("outputs/roborazzi"))
    }
}

/**
 * Whether this build was asked for a Roborazzi task (`recordRoborazziDebug`, `verify…`,
 * `compare…`). Screenshot tests run only then: they are slow (each one boots a Robolectric
 * sandbox and renders natively), their first run downloads an SDK jar, and a rendering problem in
 * one of them must not turn the ordinary unit-test job red. Conversely a Roborazzi run runs only
 * them, so the `screenshots` CI job does not repeat the unit tests `build-test` already ran.
 */
val roborazziRequested = gradle.startParameter.taskNames.any { it.contains("Roborazzi") }

tasks.withType<Test>().configureEach {
    if (roborazziRequested) {
        filter.includeTestsMatching("com.coparently.app.screenshots.*")
    } else {
        exclude("com/coparently/app/screenshots/**")
    }
}

// A failing unit test on CI printed only its exception class and a line number — enough to
// know something broke, not enough to know what. `FULL` prints the assertion message and the
// stack, so a red build can be diagnosed from its log instead of by downloading the XML report
// and guessing in the meantime. Only failures are logged; a green run stays quiet.
tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStackTraces = true
        showCauses = true
    }
}

// Unit-test coverage, for visibility only: CI runs `koverXmlReportDebug koverHtmlReportDebug`
// after the unit tests and prints the line percentage in the PR summary comment. There is no
// verification rule, deliberately — a gate on a number nobody has yet watched move would be
// tuned to pass rather than to mean something.
//
// The exclusions are code nobody here writes: Room's `_Impl` DAOs and database, Compose's lambda
// singletons and previews, and BuildConfig. Counting them would make the percentage mostly a
// measure of how much the annotation processors emit.
// In Kover's class filters `*` matches any characters, dots and `$` included.
kover {
    reports {
        filters {
            excludes {
                classes(
                    "*_Impl",
                    "*_Impl$*",
                    "*_AutoMigration_*",
                    "*ComposableSingletons*",
                    "*.BuildConfig",
                )
                annotatedBy(
                    "androidx.compose.ui.tooling.preview.Preview",
                    "javax.annotation.processing.Generated",
                )
            }
        }
    }
}
