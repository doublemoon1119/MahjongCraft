plugins {
    alias(libs.plugins.mahjongcraft.kotlin.multiplatform)
}

version = libs.versions.bundled.extensions.version.get()

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":mahjong-extension-api"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
