pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        maven("https://android-sdk.is.com")
        maven("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea")
        maven("https://jitpack.io")
        mavenCentral()
        google {
            google()
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://android-sdk.is.com")
        maven("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea")
        google()
        maven("https://jitpack.io")
        mavenCentral()
    }
}
rootProject.name = "Gold V2ray"
include ("app")