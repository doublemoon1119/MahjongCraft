package com.doublemoon1119.mahjongcraft.platform.minecraft.environment

/**
 * 版本無關的 Minecraft loader／執行環境查詢介面，讓共用程式碼（`platform/minecraft/common` 與其上層）
 * 能查詢跟目前執行環境有關的資訊，不需要直接依賴任何一個 loader 專屬的 API——`platform/minecraft/`
 * 底下每個 loader 各自的模組提供各自的實作。
 */
interface MinecraftEnvironment {
    /**
     * 目前是否透過 loader 自己的開發環境機制啟動（例如 Gradle `runServer`／`runClient`）——正式打包
     * 發布的產物一律回傳 `false`。用途例如 op 限定的 debug 指令群組，只在開發環境才註冊。
     *
     * 各 loader 各自的等價機制：
     * - Fabric：`FabricLoader.getInstance().isDevelopmentEnvironment()`
     * - Forge：`FMLLoader.isProduction()`（語意相反，回傳「是否為正式環境」，實作時記得取反）
     * - NeoForge：`FMLEnvironment.production`（語意同上，取反）
     * - 完全沒有等價機制的 loader：可以 fallback 成讀取一個只在開發用 run configuration 才會設定的
     *   系統屬性（例如 `-Dmahjongcraft.debugCommands=true`），這個 fallback 本身跨 loader 通用。
     */
    val isDevelopment: Boolean
}
