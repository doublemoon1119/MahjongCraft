package com.doublemoon1119.mahjongcraft.platform.minecraft.environment

/**
 * 提供目前 Minecraft 執行環境資訊的介面，供共用程式碼在不依賴 loader API 的情況下查詢。
 */
interface MinecraftEnvironment {
    /**
     * 目前執行環境是否為開發環境。正式發布的模組應回傳 `false`。
     */
    val isDevelopment: Boolean
}
