pluginManagement {
    // A Microcord checkout next to this one wins over the published Gradle plugin.
    file("../manticordrebuild/plugin-gradle").takeIf { it.exists() }?.let(::includeBuild)
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://raw.git.hoodnet.work/microcord/microcord/maven")
    }
    plugins {
        id("dev.mantikafasi.manticord.plugin") version "1.0.0"
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://raw.git.hoodnet.work/microcord/microcord/maven")
    }
}

rootProject.name = "V2ComponentFix"
