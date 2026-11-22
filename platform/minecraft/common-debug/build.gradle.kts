// 開發專用 debug 工具中不需要 Minecraft 的部分：debug 規則、情境資料、壓力測試的統計與歷史生成的設定。
// 由各 Minecraft 版本的 debug 模組依賴；它的資源（debug 翻譯與材質）由載入器的 debug 模組一起打包。不發布。
plugins {
    alias(libs.plugins.mahjongcraft.kotlin.multiplatform)
    // debug 情境與規則的資料類型以 kotlinx.serialization 編碼。
    alias(libs.plugins.kotlin.serialization)
}

version = libs.versions.minecraft.mod.version.get()

kotlin {
    jvm()

    sourceSets {
        jvmMain {
            dependencies {
                implementation(project(":minecraft_common"))
                implementation(project(":mahjong-logic"))
                implementation(project(":mahjong-ai"))
                implementation(project(":mahjong-flow:mahjong-flow-common"))
                implementation(project(":mahjong-flow:mahjong-flow-server"))
                implementation(project(":mahjong-flow:mahjong-flow-network-dto"))
                implementation(project(":mahjong-flow:mahjong-flow-persistence-format"))
                implementation(project(":mahjong-extension-api"))
                implementation(libs.kotlinx.serialization.json)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":testing:testing-mahjong-logic"))
            }
        }
    }
}
