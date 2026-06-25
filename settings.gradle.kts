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
        // Linphone SDK (liblinphone) Maven repository.
        maven("https://download.linphone.org/maven_repository")
    }
}

rootProject.name = "Softphone"
include(":app")
