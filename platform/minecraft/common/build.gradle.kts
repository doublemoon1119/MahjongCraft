plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":mahjong-logic"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
