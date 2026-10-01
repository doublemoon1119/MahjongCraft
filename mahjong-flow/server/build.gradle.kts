plugins {
    alias(libs.plugins.mahjongcraft.kotlin.multiplatform)
    alias(libs.plugins.koin.compiler)
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
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.annotations)
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
