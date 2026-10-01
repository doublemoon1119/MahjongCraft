plugins {
    alias(libs.plugins.mahjongcraft.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.koin.compiler)
}

version = libs.versions.flow.version.get()

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":mahjong-logic"))
            implementation(project(":mahjong-flow:mahjong-flow-common"))
            implementation(libs.kotlinx.serialization.json)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.annotations)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
