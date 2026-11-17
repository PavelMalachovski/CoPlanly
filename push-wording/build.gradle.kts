import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Every notification's wording (iPhone phase 4 part C; docs/superpowers/specs/
// 2026-11-17-iphone-phase-4c-pushes-design.md §7.1): the pushes', the chat cards' push text, the
// event reminder's, the child support reminder's and the request reminder's. The Android app depends
// on it, and on the iPhone it is a framework of its own, `PushWording`, which only the Notification
// Service Extension links: the extension has a small memory budget and carries neither Compose nor
// the data layer, so this module depends on no other module of ours and on no library.
//
// Its strings reach it through `WordingStrings`: on Android the app's own resources (the copy the
// app's `stringsToAndroidRes` generates from `:shared-ui`'s XML), on the iPhone the `.strings` and
// `.stringsdict` files `tools/ios/generate-push-strings.js` writes from the same XML for the keys
// `push-keys.txt` lists. The XML stays the one source.
//
// Built like `:shared`: AGP 9's Android KMP library target (`kotlin { android { } }`) and the two
// iPhone targets.
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("io.gitlab.arturbosch.detekt")
    id("org.jetbrains.kotlinx.kover")
}

kotlin {
    android {
        namespace = "com.coparently.app.pushwording"
        // As the app and the other shared modules.
        compileSdk = 37
        // The app's minSdk. Nothing in this module may need a higher API level.
        minSdk = 26
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        // The JVM tests: `androidHostTest`, with commonTest in the same compilation.
        withHostTest {}
    }

    // The extension's framework: static, exporting nothing, so it carries this module alone.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "PushWording"
            isStatic = true
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        getByName("androidHostTest").dependencies {
            implementation("junit:junit:4.13.2")
        }
    }
}

// Unit-test coverage for the app's merged Kover report, as in the other shared modules.
kover {
    currentProject {
        createVariant("unit") {
            add("jvm", optional = true)
        }
    }
}

// The app's detekt configuration and the app's one baseline file, as in the other shared modules.
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

// Never written from here: `:app:detektBaseline` reads `push-wording/src` too and writes the one
// file for every module (app/build.gradle.kts).
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
