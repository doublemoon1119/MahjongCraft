package com.doublemoon1119.mahjongcraft.platform.minecraft.config

/** Server config 初始化或熱重載的結果。 */
sealed interface MinecraftServerConfigUpdateResult {
    /**
     * 已載入並套用完整有效設定。
     *
     * @property config 新的有效設定。
     * @property createdDefaultFile 本次初始化是否建立了預設設定檔。
     */
    data class Success(
        val config: MinecraftServerConfig,
        val createdDefaultFile: Boolean = false,
    ) : MinecraftServerConfigUpdateResult

    /**
     * 檔案讀取、TOML 解碼或驗證失敗，runtime 仍保留先前設定。
     *
     * @property message 可提供給管理員的診斷訊息。
     * @property cause 原始例外；只供 log 保留詳細資訊。
     */
    data class Failure(
        val message: String,
        val cause: Throwable? = null,
    ) : MinecraftServerConfigUpdateResult
}
