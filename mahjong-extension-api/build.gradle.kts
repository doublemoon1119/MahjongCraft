plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":mahjong-logic"))
            api(project(":mahjong-flow:mahjong-flow-network-dto"))
            api(project(":mahjong-flow:mahjong-flow-persistence-dto"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
