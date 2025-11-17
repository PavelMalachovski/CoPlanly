// Top-level build file where you can add configuration options common to all sub-projects/modules.
// Updated to latest stable versions for Day 8 - Final Polish & Testing
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.dagger.hilt.android") version "2.52" apply false
    id("com.android.library") version "8.7.3" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.7" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

