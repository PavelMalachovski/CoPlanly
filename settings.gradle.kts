pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "CoPlanly"
include(":app")
// The Kotlin Multiplatform module the Android and iPhone apps share (MON-24).
include(":shared")
// The data layer both apps share (MON-24 phase 2): the HTTP clients first, the rest task by task.
include(":shared-data")

// The UI both apps share (MON-24 phase 2, Task 9): the strings as Compose Multiplatform resources
// first, then the theme, the common components, the ViewModels and the screens. Its iOS framework,
// `Shared`, is the one the iPhone app links.
include(":shared-ui")

// Every notification's wording, the pushes', the reminders' and the chat cards' (iPhone phase 4 part
// C): the Android app depends on it, and its iOS framework, `PushWording`, is the one the iPhone's
// notification extension links, without Compose.
include(":push-wording")
