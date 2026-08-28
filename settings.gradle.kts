pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        // Fabric Loom 外掛只發布在 Fabric 官方 Maven，不在 Gradle Plugin Portal 上
        maven("https://maven.fabricmc.net/")
    }
}

plugins {
    id("mahjongcraft.platform-targets")
}

rootProject.name = "MahjongCraft"

/**
 * 集中化依賴管理與倉庫配置
 */
dependencyResolutionManagement {
    // Fabric Loom 會在套用它的模組上自動注入好幾個它自己需要的 repo（Mojang 官方函式庫、
    // 依專案動態產生路徑的本地重映射 mod jar 快取等），後者的路徑是動態算出來的，本來就無法在這裡
    // 集中宣告。FAIL_ON_PROJECT_REPOS 會直接讓建置失敗，PREFER_SETTINGS 則會把這些 repo 整組忽略
    // （包含無法預先宣告的本地快取 repo），兩者都會讓 Loom 模組壞掉，因此對這個專案只能用預設的
    // PREFER_PROJECT——其他模組仍然只用這裡宣告的 repo，只有 Loom 這種会自行注入必要 repo 的外掛
    // 才會用到專案層級的 repo。
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenCentral()
        gradlePluginPortal()
        // Minecraft/Fabric API/Fabric Language Kotlin 等產物只發布在 Fabric 官方 Maven，不在 Maven Central 上
        maven("https://maven.fabricmc.net/")
    }
}

// 預設始終加載的核心模組
include(":mahjong-logic")
include(":mahjong-ai")

// 透過重新命名 Gradle 專案物件（Project Name），解決不同層級下同名模組（如 :common）
// 導致的 Artifact ID 衝突與循環依賴，同時維持實體目錄結構的整潔。

// testing
include(":testing")
include(":testing:mahjong-logic")
project(":testing:mahjong-logic").name = "testing-mahjong-logic"
include(":testing:mahjong-flow")
project(":testing:mahjong-flow").name = "testing-mahjong-flow"

// application
include(":mahjong-flow")
include(":mahjong-flow:common")
project(":mahjong-flow:common").name = "mahjong-flow-common"
include(":mahjong-flow:network-dto")
project(":mahjong-flow:network-dto").name = "mahjong-flow-network-dto"
include(":mahjong-flow:persistence-dto")
project(":mahjong-flow:persistence-dto").name = "mahjong-flow-persistence-dto"
include(":mahjong-extension-api")
include(":mahjong-flow:server")
project(":mahjong-flow:server").name = "mahjong-flow-server"
include(":mahjong-flow:client")
project(":mahjong-flow:client").name = "mahjong-flow-client"
