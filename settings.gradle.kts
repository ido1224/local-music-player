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
        // TarsosDSP is published here, not on Maven Central.
        maven { url = uri("https://mvn.0110.be/releases") }
    }
}

rootProject.name = "NERA Music Player"
include(":app")
