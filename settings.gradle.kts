rootProject.name = "MahjongCraft"

/**
 * 集中化依賴管理與倉庫配置
 */
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

// 預設始終加載的核心模組
include(":domain")
include(":application")
include(":infrastructure")
include(":testing")

/**
 * 動態路由配置：根據參數加載特定平台適配層
 * 優先序：指令行參數 (-P) > local.dev.properties > 預設值 (null)
 */
val devProps = java.util.Properties().apply {
    val propFile = file("local.dev.properties")
    if (propFile.exists()) {
        propFile.inputStream().use { load(it) }
    }
}

// 獲取目標平台
val targetPlatform: String? = providers.gradleProperty("targetPlatform").orNull
    ?: devProps.getProperty("targetPlatform")

// 獲取 Minecraft 特定的版本與加載器
val targetMinecraftVersion: String? = providers.gradleProperty("targetMinecraftVersion").orNull
    ?: devProps.getProperty("targetMinecraftVersion")
val targetMinecraftLoader: String? = providers.gradleProperty("targetMinecraftLoader").orNull
    ?: devProps.getProperty("targetMinecraftLoader")

// 標記是否有任何平台模組被成功載入
var anyModuleLoaded = false

// 輔助函數，用於註冊平台模組
fun includePlatformModule(path: String) {
    val moduleDir = file(path)
    if (moduleDir.exists() && moduleDir.isDirectory) {
        // 將路徑轉換為模組名稱，移除 'platform/' 前綴以保持簡潔
        val moduleName = ":" + path.removePrefix("platform/").replace('/', '_')
        include(moduleName)
        project(moduleName).projectDir = moduleDir
        anyModuleLoaded = true // 標記成功載入
    } else {
        logger.warn("BUILD LOG: Module path does not exist, skipping -> $path")
    }
}

// 根據目標平台載入模組
if (targetPlatform != null) {
    when (targetPlatform) {
        "minecraft" -> {
            if (targetMinecraftVersion != null && targetMinecraftLoader != null) {
                // 嘗試載入所有相關的 Minecraft 模組
                includePlatformModule("platform/minecraft/common")
                includePlatformModule("platform/minecraft/$targetMinecraftVersion/common")
                includePlatformModule("platform/minecraft/$targetMinecraftVersion/$targetMinecraftLoader")

                // 只有在至少一個模組成功載入後，才顯示活動環境日誌
                if (anyModuleLoaded) {
                    logger.lifecycle("BUILD LOG: Environment active -> Platform: $targetPlatform, Version: $targetMinecraftVersion, Loader: $targetMinecraftLoader")
                } else {
                    logger.lifecycle("BUILD LOG: All specified Minecraft module paths were missing. Running in domain/application-only mode.")
                }
            } else {
                logger.lifecycle("BUILD LOG: Minecraft platform specified, but targetMinecraftVersion or targetMinecraftLoader is missing. Running in domain/application-only mode.")
            }
        }
        // 未來可以在這裡添加其他平台的處理邏輯
        else -> {
            logger.lifecycle("BUILD LOG: Unknown targetPlatform '$targetPlatform'. Running in domain/application-only mode.")
        }
    }
} else {
    logger.lifecycle("BUILD LOG: No targetPlatform specified. Running in domain/application-only mode.")
}
