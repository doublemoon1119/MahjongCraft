plugins {
    alias(libs.plugins.mahjongcraft.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.mahjongcraft.public.api.abi)
}

publicApiAbi {
    includedNames.addAll(
        "com.doublemoon1119.mahjongcraft.flow.persistence.format.api.registration.**",
    )
}

version = libs.versions.flow.version.get()

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":mahjong-logic"))
            implementation(project(":mahjong-flow:mahjong-flow-common"))
            api(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(project(":testing:testing-mahjong-flow"))
            implementation(project(":testing:testing-mahjong-logic"))
        }
    }
}
