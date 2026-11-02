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
        // MSAL이 끌어오는 display-mask 0.3.0은 jcenter 종료 후 이 피드에만 남아 있다.
        maven {
            url = uri("https://pkgs.dev.azure.com/MicrosoftDeviceSDK/DuoSDK-Public/_packaging/Duo-SDK-Feed/maven/v1")
            content { includeGroup("com.microsoft.device.display") }
        }
    }
}

rootProject.name = "onesync"
include(":app")
