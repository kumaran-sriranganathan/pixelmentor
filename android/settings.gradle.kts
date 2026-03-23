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
        // MSAL is hosted on GitHub Packages
        maven { url = uri("https://pkgs.dev.azure.com/MicrosoftDeviceSDKData/DeviceSDK/_packaging/androidsdk/maven/v1") }
    }
}

rootProject.name = "PixelMentor"
include(":app")
