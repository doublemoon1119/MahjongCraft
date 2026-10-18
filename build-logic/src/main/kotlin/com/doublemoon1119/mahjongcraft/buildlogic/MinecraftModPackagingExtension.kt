package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

/** Minecraft mod 的身分、圖示與產物名稱；未指定時沿用正式 mod 的設定。 */
abstract class MinecraftModPackagingExtension {
    /** metadata 與圖示資源路徑使用的 mod ID。 */
    abstract val modId: Property<String>

    /** mod 清單顯示的名稱。 */
    abstract val displayName: Property<String>

    /** metadata 使用的 mod 說明。 */
    abstract val description: Property<String>

    /** 複製至 mod 自己的 assets 目錄、命名為 icon.png 的來源圖示。 */
    abstract val icon: RegularFileProperty

    /** JAR 名稱中版本號之前的部分。 */
    abstract val archiveBaseName: Property<String>
}
