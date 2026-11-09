import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The UI both apps run (MON-24 phase 2; docs/superpowers/plans/
// 2026-11-01-iphone-phase-2-multiplatform-app.md, Task 9 onwards), on Compose Multiplatform. It
// starts with the strings: every `<string>`, `<plurals>` and `<string-array>` the app had in
// `app/src/main/res/values*/` lives in `src/commonMain/composeResources/values*/` (same file names,
// the five locales), where Compose Multiplatform's resource plugin generates `Res` (package
// `com.coparently.app.resources`) from them. The app keeps `R.string` and Android's own reading of
// the same files through `:app`'s `stringsToAndroidRes` task, which copies them unchanged into
// the app's generated resources. Then the theme (`presentation/theme/`, slice 9b), with the Onest
// font files in `composeResources/font/`, and the common components (`presentation/common/`) that
// need neither a ViewModel nor an Android API. Then (Task 10) the shared state the ViewModels read
// and the ViewModels themselves, group by group, with their Koin modules (`uiModules`); the screens
// follow (Task 11), packages unchanged.
//
// Built like `:shared-data`: an `androidTarget()` on the `com.android.library` plugin and the two
// iPhone targets. It depends on both other shared modules, and its framework is the one the iPhone
// app links: `Shared`, static, exporting the domain and the data layer.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    // kotlinx.serialization's compiler plugin, at the Kotlin version (the root build file): the
    // event form's draft (`EventDraft`, Task 10) is a `@Serializable` model `StoredJson` writes.
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.gitlab.arturbosch.detekt")
    id("org.jetbrains.kotlinx.kover")
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

    // `expect object` (`ReaderDateText`, slice 11a) is a Beta feature that warns on every build
    // without this flag; an object is the shape the date seam needs, so the warning is acknowledged
    // here once, as `:shared` and `:shared-data` do.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
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
            // The theme and the common components (slice 9b). `api`, because their public
            // signatures carry Material 3's `ColorScheme`, `Typography` and `WindowSizeClass` and
            // Compose's `Modifier`, `Color`, `ImageVector` and transitions. On Android each of
            // these is the androidx artifact the app's BOM 2025.10.00 already names: material3 and
            // its window-size-class 1.4.0, animation, foundation and ui at the 1.9.4 above.
            api(compose.animation)
            api(compose.foundation)
            api(compose.ui)
            api(compose.material3)
            // `CoPlanlyTheme(windowSizeClass = …)` and the adaptive dimensions. Not among the
            // plugin's `compose.*` accessors, so named here, at the material3 version Compose
            // Multiplatform 1.9.3 itself uses (`composeMaterial3Version`).
            api("org.jetbrains.compose.material3:material3-window-size-class:1.9.0")
            // The common components' glyphs (`Icons.Default.…`). 1.7.3 is the last Compose
            // Multiplatform release of the extended set; on Android it is androidx's
            // material-icons-extended, which the app's BOM puts at 1.7.8, so the classes the app
            // already draws.
            implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
            // The ViewModels (Task 10): the multiplatform `ViewModel` and `viewModelScope`. `api`,
            // because the app's screens hold them and call their API. 2.9.6 is the release Compose
            // Multiplatform 1.9.3 pairs with; on Android (and, through its redirect, on the iPhone)
            // it is androidx.lifecycle:lifecycle-viewmodel 2.9.4, so the app's lifecycle artifacts
            // (2.8.7 declared, 2.9.3 through Koin) resolve to 2.9.4, as they move in lockstep. Not
            // 2.10 or 2.11, which lift Compose, activity and lifecycle past the app's BOM 2025.10.00,
            // the reason Koin waits at 4.1.1 (see the app's build file).
            api("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.9.6")
            // `SavedStateHandle`, which the custody editors take for the parenting-plan question they
            // were opened from (slice 10c). The same release; on every target it is
            // androidx.lifecycle:lifecycle-viewmodel-savedstate 2.9.4, which the app already resolves
            // through Koin, with org.jetbrains.androidx.savedstate 1.3.6, which is androidx.savedstate
            // 1.3.3. `api`, because the constructors that take it are public.
            api("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-savedstate:2.9.6")
            // `viewModelOf` in `uiModules`, which the app's Koin graph includes. 4.1.1, the version
            // of the app's Koin BOM; it asks for lifecycle-viewmodel 2.9.3, which the line above
            // lifts.
            implementation("io.insert-koin:koin-core-viewmodel:4.1.1")
            // The screens (Task 11). Each of the three is already in the app's Android graph at the
            // version it resolves to here, so none moves an Android version:
            // - `koinViewModel()` in common code. 4.1.1, the Koin BOM's; the app's
            //   koin-androidx-compose brings the same artifact, which asks for activity-compose
            //   1.10.1 and lifecycle-viewmodel-compose 2.9.3 (2.9.4 through the line above).
            implementation("io.insert-koin:koin-compose-viewmodel:4.1.1")
            // - `collectAsStateWithLifecycle` and `LocalLifecycleOwner`. The release Compose
            //   Multiplatform 1.9.3 pairs with, which on Android is androidx's 2.9.4, the line the
            //   ViewModels already resolve.
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.6")
            // - `BackHandler` and `PredictiveBackHandler` in common code (`androidx.compose.ui.
            //   backhandler`, experimental). On Android its 1.9.3 calls androidx.activity's own
            //   handlers and asks for activity-compose 1.8.0, below the app's; on the iPhone it is
            //   Compose Multiplatform's back gesture dispatcher.
            implementation("org.jetbrains.compose.ui:ui-backhandler:1.9.3")
            // - The month grid's `HorizontalCalendar` (`MonthView`, slice 11c): kizitonwose's
            //   multiplatform artifact, whose months and days are kotlinx-datetime's. 2.9.1 asks for
            //   Compose Multiplatform 1.9.0 (androidx Compose 1.9.0 on Android, below the BOM's),
            //   kotlinx-datetime 0.7.1, kotlinx-serialization 1.9.0 and stdlib 2.2.20, all below what
            //   the graph resolves, so it moves no Android version; 2.10 asks for Compose
            //   Multiplatform 1.10, past the BOM. It replaced the app's Android-only
            //   `com.kizitonwose.calendar:compose:2.6.1`, whose packages are the same: the two cannot
            //   both be on the classpath.
            implementation("com.kizitonwose.calendar:compose-multiplatform:2.9.1")
            // - The navigation graph (`presentation/navigation/`, Task 12): JetBrains' multiplatform
            //   Navigation, the release that pairs with Compose Multiplatform 1.9.3, lifecycle 2.9.6
            //   and savedstate 1.3.6 above. On Android it is androidx's navigation-compose 2.9.7, a
            //   lift from the 2.9.3 the app declared before the graph moved. `api`, because the
            //   graph's public signatures carry `NavHostController` and `NavGraphBuilder`, and the
            //   app's tests build a `NavHost` of their own (`ViewModelScopeTest`).
            api("org.jetbrains.androidx.navigation:navigation-compose:2.9.2")
        }
        androidMain.dependencies {
            // `WindowCompat`, for the system bars (`Theme.android.kt`). The app's version.
            implementation("androidx.core:core-ktx:1.13.1")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            // The ViewModels' and the shared state's tests (Task 10), at the app's versions:
            // `runTest` and `Dispatchers.setMain`, and Turbine for their flows. They run on the JVM;
            // CI compiles them for iOS, and nothing runs them on the simulator yet (the test
            // executable would need shared-data's Firebase pods).
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
            implementation("app.cash.turbine:turbine:1.2.0")
        }
        androidUnitTest.dependencies {
            // The theme's tests (moved with it from the app, slice 9b) run here as the Android unit
            // tests, on JUnit 4 like the app's.
            implementation("junit:junit:4.13.2")
            // The moved ViewModel tests that mock their collaborators (Task 10). The app's version;
            // MockK runs on the JVM only, so a test that mocks lives here rather than in commonTest.
            implementation("io.mockk:mockk:1.13.13")
            // The oracle a moved test compares `StoredJson` with, as `:shared`'s parity tests do: the
            // Gson the app used until MON-24, at the version it used (`EventViewModelTest` opens a
            // draft Gson wrote). Tests only.
            implementation("com.google.code.gson:gson:2.11.0")
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

    // As in the app, where the theme's tests ran until slice 9b: an Android framework method is a
    // stub in JVM unit tests, and answers its default rather than throwing "not mocked".
    testOptions {
        unitTests.isReturnDefaultValues = true
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
