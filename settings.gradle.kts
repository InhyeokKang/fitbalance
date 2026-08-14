pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 카카오 지도 SDK는 카카오 자체 저장소에만 올라온다.
        maven("https://devrepo.kakao.com/nexus/content/groups/public/")
    }
}

rootProject.name = "FitBalance"
include(":app")
