package com.doublemoon1119.mahjongcraft.buildlogic

/**
 * 單一正式平台建置目標的通用設定。
 *
 * @property id 供 CLI、環境變數與 CI 使用的穩定完整 ID。
 * @property platform 平台種類；核心載入器不假設任何特定平台。
 * @property javaToolchain 編譯此 target 使用的 JDK toolchain。
 * @property javaRelease 此 target 產出的最低 JVM bytecode 版本。
 * @property modules Settings 階段必須載入的模組目錄。
 * @property attributes 由各平台自行定義並解讀的額外屬性。
 */
data class PlatformTarget(
    val id: String,
    val platform: String,
    val javaToolchain: Int,
    val javaRelease: Int,
    val modules: List<String>,
    val attributes: Map<String, String>,
)
