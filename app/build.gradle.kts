plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("io.gitlab.arturbosch.detekt")
    kotlin("kapt")
}

// The Google Services and Firebase Crashlytics Gradle plugins both require a
// google-services.json. It is gitignored, so on CI (and any fresh clone without it)
// applying them would fail — the Crashlytics plugin's release mapping-upload task
// errors with "Google-Services plugin not configured properly".
// Apply both plugins only when the file is present. Runtime Crashlytics still works
// everywhere via the firebase-crashlytics-ktx SDK dependency; only build-time mapping
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

android {
    namespace = "com.coparently.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.coparently.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "com.coparently.app.HiltTestRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            buildConfigField("Boolean", "ENABLE_CRASHLYTICS", "false")
            buildConfigField("Boolean", "ENABLE_ANALYTICS", "false")
            // Gemini API key from gradle.properties or environment variable
            buildConfigField("String", "GEMINI_API_KEY", "\"${project.findProperty("GEMINI_API_KEY") ?: System.getenv("GEMINI_API_KEY") ?: ""}\"")
            // Google OAuth client secret — never committed; supplied via ~/.gradle/gradle.properties or env
            buildConfigField("String", "GOOGLE_CLIENT_SECRET", "\"${project.findProperty("GOOGLE_CLIENT_SECRET") ?: System.getenv("GOOGLE_CLIENT_SECRET") ?: ""}\"")
        }

        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("Boolean", "ENABLE_CRASHLYTICS", "true")
            buildConfigField("Boolean", "ENABLE_ANALYTICS", "true")
            // Gemini API key from gradle.properties or environment variable
            buildConfigField("String", "GEMINI_API_KEY", "\"${project.findProperty("GEMINI_API_KEY") ?: System.getenv("GEMINI_API_KEY") ?: ""}\"")
            // Google OAuth client secret — never committed; supplied via ~/.gradle/gradle.properties or env
            buildConfigField("String", "GOOGLE_CLIENT_SECRET", "\"${project.findProperty("GOOGLE_CLIENT_SECRET") ?: System.getenv("GOOGLE_CLIENT_SECRET") ?: ""}\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"

        // Compose Compiler Metrics for performance analysis
        // After build, check build/compose_metrics/ for unstable composables
        // and build/compose_reports/ for detailed reports
        freeCompilerArgs += listOf(
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:metricsDestination=${project.layout.buildDirectory.get().asFile.absolutePath}/compose_metrics",
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=${project.layout.buildDirectory.get().asFile.absolutePath}/compose_reports"
        )
    }

    buildFeatures {
        compose = true
        // Keep BuildConfig enabled for feature flags
        buildConfig = true
    }

    lint {
        // Localization is intentionally partial: strings missing from a locale fall back
        // to the base (English) resources at runtime. Don't fail the build over it — this
        // stays a warning. Completing translations is tracked separately.
        disable += "MissingTranslation"
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

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
}

dependencies {
    // Core Android - Updated to latest stable versions
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose - Updated to latest BOM (2024.11.00)
    val composeBom = platform("androidx.compose:compose-bom:2024.11.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation - Updated to latest stable
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Splash Screen API for Android 12+
    implementation("androidx.core:core-splashscreen:1.0.1")

    // ViewModel - Updated to match lifecycle version
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Hilt - Updated to latest stable
    implementation("com.google.dagger:hilt-android:2.52")
    kapt("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    kapt("androidx.hilt:hilt-compiler:1.2.0")

    // Room - Already at latest stable
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    // Coroutines - Updated to latest stable
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // WorkManager for background tasks
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Calendar - Check for updates at https://github.com/kizitonwose/Calendar
    implementation("com.kizitonwose.calendar:compose:2.6.1")

    // Google Sign-In - Migrating to Credential Manager API
    // Old deprecated API (will be removed)
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // New Credential Manager API
    implementation("androidx.credentials:credentials:1.2.2")
    implementation("androidx.credentials:credentials-play-services-auth:1.2.2")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Google Calendar API
    implementation("com.google.api-client:google-api-client-android:2.2.0")
    implementation("com.google.apis:google-api-services-calendar:v3-rev20220715-2.0.0")

    // Encrypted SharedPreferences - Updated to stable
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Gson for JSON - Updated to latest
    implementation("com.google.code.gson:gson:2.11.0")

    // Lottie for Compose - Animations library
    implementation("com.airbnb.android:lottie-compose:6.5.2")

    // Jetpack Glance for Widgets
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    // ZXing for QR code generation
    implementation("com.google.zxing:core:3.5.3")

    // ML Kit for QR code scanning
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Generative AI - Gemini API
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")

    // Retrofit for AI API calls
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // OkHttp for HTTP client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Firebase - Updated to latest BOM
    val firebaseBom = platform("com.google.firebase:firebase-bom:33.7.0")
    implementation(firebaseBom)
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-config-ktx")

    // Testing - Updated to latest stable
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.11.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // MockK for mocking - Latest stable
    testImplementation("io.mockk:mockk:1.13.13")
    testImplementation("io.mockk:mockk-android:1.13.13")
    androidTestImplementation("io.mockk:mockk-android:1.13.13")

    // Coroutines Test - Latest stable
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    // Turbine for Flow testing
    testImplementation("app.cash.turbine:turbine:1.2.0")

    // ArchCore Testing for LiveData and ViewModel
    testImplementation("androidx.arch.core:core-testing:2.2.0")

    // Hilt testing - Updated to match Hilt version
    testImplementation("com.google.dagger:hilt-android-testing:2.52")
    kaptTest("com.google.dagger:hilt-compiler:2.52")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.52")
    kaptAndroidTest("com.google.dagger:hilt-compiler:2.52")

    // Navigation Testing
    androidTestImplementation("androidx.navigation:navigation-testing:2.8.5")
}

kapt {
    correctErrorTypes = true
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

// Detekt configuration for static code analysis
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$projectDir/config/detekt/detekt.yml")
    baseline = file("$projectDir/config/detekt/baseline.xml")
}

dependencies {
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.7")
}

