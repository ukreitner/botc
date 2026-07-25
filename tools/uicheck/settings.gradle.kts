// Standalone verification build: type-checks the Compose UI sources against
// JetBrains' Compose Multiplatform desktop artifacts (identical common API
// surface), using thin stubs for Android-only framework classes. This lets
// CI environments without access to Google's Maven repository catch UI
// compilation errors. It is NOT part of the shipping app build.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}
rootProject.name = "uicheck"
