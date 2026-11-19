import io.github.androidpoet.weekview.Configuration

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(17)

    androidTarget()
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(project(":weekview-compose"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

android {
    namespace = "io.github.androidpoet.weekview.sample"
    compileSdk = Configuration.COMPILE_SDK

    defaultConfig {
        applicationId = "io.github.androidpoet.weekview.sample"
        minSdk = Configuration.MIN_SDK
        targetSdk = Configuration.COMPILE_SDK
        versionCode = 1
        versionName = Configuration.VERSION
    }
}

compose.desktop {
    application {
        mainClass = "io.github.androidpoet.weekview.sample.MainKt"
    }
}
