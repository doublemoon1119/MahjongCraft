plugins {
    alias(libs.plugins.mahjongcraft.kotlin.multiplatform)
}

version = libs.versions.minecraft.mod.version.get()

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":example:example-mahjong-extension"))
            implementation(project(":mahjong-extension-api"))
            implementation(project(":minecraft_common"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        jvmTest.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
