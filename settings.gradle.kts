pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        exclusiveContent {
            forRepository {
                maven("https://gitlab.com/api/v4/projects/18497829/packages/maven")
            }
            filter {
                includeGroup("com.auroraoss")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "apkup"
include(":app")
