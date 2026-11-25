package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

/** Minecraft mod 的身分、圖示、產物名稱與執行依賴需求。 */
abstract class MinecraftModPackagingExtension {
    /** metadata 與圖示資源路徑使用的 mod ID。 */
    abstract val modId: Property<String>

    /** mod 清單顯示的名稱。 */
    abstract val displayName: Property<String>

    /** metadata 使用的 mod 說明。 */
    abstract val description: Property<String>

    /** 複製至 mod 自己的 assets 目錄、命名為 icon.png 的來源圖示。 */
    abstract val icon: RegularFileProperty

    /**
     * mod 執行時所在的載入器，寫進 metadata 的版本，例如 `fabric`。預設為模組資料夾的名稱。
     *
     * 開發用的附屬模組放在另一個資料夾，例如 `forge-debug`，須設成它實際所在的載入器，版本才會與本體相同。
     */
    abstract val loader: Property<String>

    /** JAR 名稱中版本號之前的部分。 */
    abstract val archiveBaseName: Property<String>

    /** 由版本與 loader 模組指定的執行依賴範圍。metadata 以 dependencyRequirements 取得。 */
    abstract val dependencyRequirements: MapProperty<String, String>

    /**
     * 產出的模組是否為交付給玩家的成品。預設為是。只在開發時使用的模組與範例模組設為否，CI 不收集它們的產出。
     */
    abstract val distributable: Property<Boolean>
}
