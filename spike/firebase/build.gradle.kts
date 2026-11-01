import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

// The MON-24 Firebase spike: every criterion of the phase 2 brief as one test in commonTest, run
// through GitLive's firebase-kotlin-sdk on an Android emulator (connectedDebugAndroidTest) and on
// an iOS simulator (iosSimulatorArm64Test), both against the Firebase emulators. The toolchain is
// the app's: Kotlin 2.3.21, AGP 8.10.1, minSdk 26.
plugins {
    id("org.jetbrains.kotlin.multiplatform") version "2.3.21"
    id("org.jetbrains.kotlin.native.cocoapods") version "2.3.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21"
    id("com.android.library") version "8.10.1"
}

version = "1.0"

// GitLive's newest stable release. 3.0.0-alpha02 is built with Kotlin 2.4, which this project's
// 2.3.21 compiler cannot read.
val gitlive = "2.7.0"

// The Firebase iOS SDK GitLive 2.7.0's cinterop bindings were generated against.
val firebaseIos = "11.8.0"

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        // commonTest runs as the instrumented tests; the JVM unit tests get none of it.
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        unitTestVariant.sourceSetTree.set(KotlinSourceSetTree.unitTest)
    }

    iosSimulatorArm64 {
        // Firebase Auth keeps the signed-in user in the keychain, which a test executable may use
        // only when it carries a keychain access group.
        testRuns.configureEach {
            executionSource.binary.linkerOpts(
                "-sectcreate",
                "__TEXT",
                "__entitlements",
                file("entitlements.plist").absolutePath,
            )
        }
    }

    cocoapods {
        ios.deploymentTarget = "15.0"
        noPodspec()
        framework {
            baseName = "FirebaseSpike"
            isStatic = false
        }
        // GitLive publishes its own cinterop bindings, so the pods are linked only: a second
        // binding of the same headers here would clash with them.
        listOf(
            "FirebaseCore",
            "FirebaseFirestore",
            "FirebaseFirestoreInternal",
            "FirebaseAuth",
            "FirebaseFunctions",
            "FirebaseStorage",
            "FirebaseMessaging",
        ).forEach { name ->
            pod(name) {
                version = firebaseIos
                linkOnly = true
            }
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("dev.gitlive:firebase-app:$gitlive")
            implementation("dev.gitlive:firebase-firestore:$gitlive")
            implementation("dev.gitlive:firebase-auth:$gitlive")
            implementation("dev.gitlive:firebase-functions:$gitlive")
            implementation("dev.gitlive:firebase-storage:$gitlive")
            implementation("dev.gitlive:firebase-messaging:$gitlive")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
        }
        getByName("androidInstrumentedTest").dependencies {
            implementation(kotlin("test-junit"))
            implementation("androidx.test:runner:1.7.0")
            implementation("androidx.test.ext:junit:1.3.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
        }
    }
}

android {
    namespace = "app.coplanly.spike.firebase"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += listOf("META-INF/AL2.0", "META-INF/LGPL2.1")
    }
}

// The simulator the workflow booted, so the keychain service is running.
tasks.withType<KotlinNativeSimulatorTest>().configureEach {
    standalone.set(false)
    device.set("booted")
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
