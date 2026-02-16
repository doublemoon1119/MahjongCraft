// settings.gradle.kts
rootProject.name = "MahjongCraft"

// 集中管理依賴倉庫與 Version Catalog
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

// 預設始終加載的模組
include(":core")
include(":di")

// 獲取指令行參數: -PtargetVersion=xxx -PtargetLoader=xxx
val targetVersion: String? = providers.gradleProperty("targetVersion").orNull
val targetLoader: String? = providers.gradleProperty("targetLoader").orNull

if (targetVersion != null && targetLoader != null) {
    val versionPath = findVersionPath(targetVersion, targetLoader)
    if (versionPath != null) {
        include(":target")
        project(":target").projectDir = versionPath
        logger.lifecycle("Development target locked: $targetVersion ($targetLoader)")
    } else {
        logger.warn("Target path not found for version: $targetVersion, loader: $targetLoader")
    }
} else {
    logger.lifecycle("No target version specified. Only loading core and di modules.")
}

/**
 * 根據版本號與載入器名稱尋找對應的資料夾路徑
 */
fun findVersionPath(version: String, loader: String): File? {
    val versionsRoot = file("versions")
    return versionsRoot.walkTopDown()
        .filter { it.isDirectory && it.name == version }
        .map { it.resolve(loader) }
        .firstOrNull { it.exists() && it.isDirectory }
}