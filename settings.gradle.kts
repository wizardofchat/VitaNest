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

rootProject.name = "VitaNest"
include(":app")

// Future modules — uncomment as you create them
// include(":core:ui")
// include(":core:data")
// include(":core:common")
// include(":features:sicksense")
// include(":features:flow")
// include(":features:soul")
// include(":features:sky")
// include(":features:playnest")
// include(":features:council")
include(":app")
