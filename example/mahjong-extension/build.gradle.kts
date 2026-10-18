plugins {
    alias(libs.plugins.mahjongcraft.kotlin.multiplatform)
}

version = libs.versions.extension.api.version.get()

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":mahjong-extension-api"))
        }
    }
}
