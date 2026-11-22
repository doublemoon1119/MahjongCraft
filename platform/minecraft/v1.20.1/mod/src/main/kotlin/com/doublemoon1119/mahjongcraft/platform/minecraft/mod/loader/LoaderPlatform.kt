package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader

import java.nio.file.Path

/** 載入器提供的執行環境資訊。 */
interface LoaderPlatform {
    /** 載入器的名稱，例如 `Fabric`；用在記錄等給人看的地方。 */
    val name: String

    /** 是否在用戶端執行；專用伺服器為 false，單人遊戲的內建伺服器與用戶端同在一邊，為 true。 */
    val isClient: Boolean

    /** 是否在開發環境中執行。 */
    val isDevelopment: Boolean

    /** 遊戲共用的設定檔資料夾。 */
    val configDirectory: Path

    /** MahjongCraft 本身的版本。 */
    val modVersion: String

    /**
     * 指定的 mod 是否已安裝並載入。
     *
     * @param modId mod 的 ID。
     * @return 已載入時為 true。
     */
    fun isModLoaded(modId: String): Boolean
}
