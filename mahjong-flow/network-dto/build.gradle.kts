plugins {
    alias(libs.plugins.mahjongcraft.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.mahjongcraft.public.api.abi)
}

publicApiAbi {
    includedNames.addAll(
        "com.doublemoon1119.mahjongcraft.flow.network.dto.api.registration.**",
    )
}

version = libs.versions.flow.version.get()

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":mahjong-logic"))
            implementation(project(":mahjong-flow:mahjong-flow-common"))
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":testing:testing-mahjong-logic"))
            implementation(project(":testing:testing-mahjong-flow"))
        }
    }
}
