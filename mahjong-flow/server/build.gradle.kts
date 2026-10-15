plugins {
    alias(libs.plugins.mahjongcraft.kotlin.multiplatform)
}

version = libs.versions.flow.version.get()

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines)
            implementation(project(":mahjong-logic"))
            implementation(project(":mahjong-ai"))
            implementation(project(":mahjong-flow:mahjong-flow-common"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotlinx.serialization.json)
            implementation(project(":mahjong-flow:mahjong-flow-persistence-format"))
            implementation(project(":testing:testing-mahjong-logic"))
            implementation(project(":testing:testing-mahjong-flow"))
        }
    }
}
