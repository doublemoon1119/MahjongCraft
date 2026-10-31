plugins {
    alias(libs.plugins.mahjongcraft.kotlin.multiplatform)
    alias(libs.plugins.mahjongcraft.public.api.abi)
}

publicApiAbi {
    includedNames.addAll(
        "com.doublemoon1119.mahjongcraft.extension.MahjongExtension",
    )
}

version = libs.versions.extension.api.version.get()

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(project(":mahjong-logic"))
            api(project(":mahjong-flow:mahjong-flow-common"))
            api(project(":mahjong-flow:mahjong-flow-network-dto"))
            api(project(":mahjong-flow:mahjong-flow-persistence-format"))
            api(project(":mahjong-ai"))
            api(project(":mahjong-flow:mahjong-flow-server"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":testing:testing-mahjong-flow"))
        }
    }
}
