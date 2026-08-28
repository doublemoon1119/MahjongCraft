plugins {
    alias(libs.plugins.mahjongcraft.kotlin.multiplatform)
}

version = libs.versions.extension.api.version.get()

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":mahjong-logic"))
            api(project(":mahjong-flow:mahjong-flow-common"))
            api(project(":mahjong-flow:mahjong-flow-network-dto"))
            api(project(":mahjong-flow:mahjong-flow-persistence-dto"))
            api(project(":mahjong-ai"))
            api(project(":mahjong-flow:mahjong-flow-server"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
