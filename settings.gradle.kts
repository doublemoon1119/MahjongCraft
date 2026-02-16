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

// 預設始終加載的模組
include(":core")
include(":di")

/**
 * 動態路由配置：根據參數加載特定 Minecraft 適配層
 * 優先序：指令行參數 (-P) > local.dev.properties > 預設值 (null)
 */
val devProps = java.util.Properties().apply {
    val propFile = file("local.dev.properties")
    if (propFile.exists()) {
        propFile.inputStream().use { load(it) }
    }
}

// 獲取目標版本與加載器: (可以透過指令參數 -PtargetVersion=xxx -PtargetLoader=xxx，或者是 local.dev.properties 設定)
val targetVersion: String? = providers.gradleProperty("targetVersion").orNull
    ?: devProps.getProperty("targetVersion")
val targetLoader: String? = providers.gradleProperty("targetLoader").orNull
    ?: devProps.getProperty("targetLoader")

if (targetVersion != null && targetLoader != null) {
    val versionPath = findVersionPath(targetVersion, targetLoader)

    if (versionPath != null && versionPath.exists()) {
        // 建立一個具備唯一性的模組名稱，例如 :v1_20_1_fabric
        val sanitizedVersion = targetVersion.replace(".", "_")
        val moduleName = "${sanitizedVersion}_$targetLoader"

        // 動態包含該模組並指定路徑
        include(":$moduleName")
        project(":$moduleName").projectDir = versionPath

        logger.lifecycle("BUILD LOG: Environment active -> :$moduleName")
    } else {
        logger.warn("BUILD LOG: Specified path does not exist -> $targetVersion/$targetLoader")
    }
} else {
    logger.lifecycle("BUILD LOG: No target specified. Running in core-only mode.")
}

/**
 * 尋找指定版本與加載器的實體路徑
 * @param version 版本資料夾名稱
 * @param loader 加載器資料夾名稱
 * @return 對應的目錄 File 物件，若不存在則返回 null
 */
fun findVersionPath(version: String, loader: String): File? {
    val versionsRoot = file("versions")
    return versionsRoot.walkTopDown()
        .filter { it.isDirectory && it.name == version }
        .map { it.resolve(loader) }
        .firstOrNull { it.exists() && it.isDirectory }
}