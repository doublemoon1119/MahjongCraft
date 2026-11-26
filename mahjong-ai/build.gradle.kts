plugins {
    alias(libs.plugins.mahjongcraft.kotlin.multiplatform)
    alias(libs.plugins.mahjongcraft.public.api.abi)
}

publicApiAbi {
    includedNames.addAll(
        "com.doublemoon1119.mahjongcraft.ai.api.registration.**",
    )
}

version = libs.versions.ai.version.get()

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":mahjong-logic"))
            implementation(project(":mahjong-flow:mahjong-flow-common"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(project(":testing:testing-mahjong-logic"))
            implementation(project(":testing:testing-mahjong-flow"))
        }
    }
}
