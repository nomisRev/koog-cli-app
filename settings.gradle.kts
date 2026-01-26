plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "koog-cli-app"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("ktorLibs") {
            from("io.ktor:ktor-version-catalog:3.4.0")
        }
    }
    repositories {
        maven("https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public") {
            mavenContent {
                includeGroup("ai.koog")
            }
        }
        mavenCentral()
    }
}