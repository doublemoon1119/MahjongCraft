plugins {
    alias(libs.plugins.mahjongcraft.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.mahjongcraft.public.api.abi)
}

publicApiAbi {
    includedNames.addAll(
        "com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.**",
        "com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.**",
        "com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.**",
        "com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.**",
        "com.doublemoon1119.mahjongcraft.platform.minecraft.api.MahjongCraftInternalApi",
        "com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.**",
        "com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtension",
    )
}

kotlin {
    jvm()

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":mahjong-logic"))
                implementation(project(":mahjong-ai"))
                implementation(project(":mahjong-flow:mahjong-flow-common"))
                implementation(project(":mahjong-flow:mahjong-flow-network-dto"))
                implementation(project(":mahjong-flow:mahjong-flow-client"))
                implementation(project(":mahjong-flow:mahjong-flow-server"))
                implementation(project(":mahjong-flow:mahjong-flow-persistence-format"))
                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.ktoml.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines)
            }
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(project(":testing:testing-mahjong-logic"))
            implementation(project(":testing:testing-mahjong-flow"))
        }

        jvmTest {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}

// 從標準牌面與牌身貼圖產生透明牌的牌面、平面預覽、模型與牌身。改動標準牌面或牌身後手動執行，檢查並視需要修圖；
// 產生的檔案與其他資源一起保存在 src 底下，一般建置不會執行這個任務。
tasks.register<com.doublemoon1119.mahjongcraft.buildlogic.GenerateTransparentTileTexturesTask>("generateTransparentTileTextures") {
    group = "mahjongcraft"
    description = "Regenerates the transparent tile textures and models from the standard tile textures."
    textureDirectory.set(layout.projectDirectory.dir("src/jvmMain/resources/assets/mahjongcraft/textures/item/mahjong_tile"))
    modelDirectory.set(layout.projectDirectory.dir("src/jvmMain/resources/assets/mahjongcraft/models/item/mahjong_tile"))
}
