plugins {
    alias(libs.plugins.mahjongcraft.kotlin.multiplatform)
}

version = "0.0.0-dev"

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines)
            implementation(libs.kotlinx.coroutines.test)
            implementation(project(":mahjong-logic"))
            implementation(project(":mahjong-flow:mahjong-flow-common"))
        }
    }
}
