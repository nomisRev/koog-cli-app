import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalMainFunctionArgumentsDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

group = "io.github.nomisrev"
version = "1.0-SNAPSHOT"

kotlin {
    jvmToolchain(21)

    js {
        nodejs {
            @OptIn(ExperimentalMainFunctionArgumentsDsl::class)
            passCliArgumentsToMainFunction()
        }

        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs {
            @OptIn(ExperimentalMainFunctionArgumentsDsl::class)
            passCliArgumentsToMainFunction()
        }

        binaries.executable()
    }

    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    jvm {
        binaries {
            executable {
                mainClass = "io.github.nomisrev.MainKt"
            }
        }
        mainRun {
            mainClass = "io.github.nomisrev.MainKt"
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation("com.github.ajalt.clikt:clikt:5.0.3")
                implementation("ai.koog:koog-agents:0.4.3-feat-101-01")
                implementation("com.xemantic.ai:xemantic-ai-tool-schema:1.1.2")
                implementation(ktorLibs.client.cio)
                implementation(ktorLibs.serialization.kotlinx.json)
                implementation(ktorLibs.client.contentNegotiation)
            }
        }
    }
}
