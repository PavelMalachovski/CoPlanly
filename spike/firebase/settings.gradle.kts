// A throwaway, standalone build for the MON-24 Firebase spike (iPhone phase 2, task 1). It is not
// part of the root build: run it with `./gradlew -p spike/firebase …`. It is deleted once the
// decision is recorded in docs/ROADMAP.md.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "firebase-spike"
