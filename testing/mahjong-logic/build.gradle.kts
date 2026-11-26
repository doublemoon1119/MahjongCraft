plugins {
    alias(libs.plugins.mahjongcraft.kotlin.multiplatform)
}

version = "0.0.0-dev"

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":mahjong-logic"))
        }
    }
}
