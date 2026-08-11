package com.doublemoon1119.mahjongcraft.platform.minecraft.config

/**
 * 保存目前 server runtime 最後一份通過驗證的 [MinecraftServerConfig]。
 *
 * 初始化、指令重載與政策讀取皆由 Minecraft server thread 執行。
 */
class MinecraftServerConfigState(
    initialConfig: MinecraftServerConfig = MinecraftServerConfig(),
) {
    /** 目前實際生效的不可變設定 snapshot。 */
    var current: MinecraftServerConfig = initialConfig
        private set

    /** 以完整且已驗證的 [config] 原子取代目前設定。 */
    fun replace(config: MinecraftServerConfig) {
        current = config
    }

    /** 將有效設定還原為程式內建預設值。 */
    fun reset() {
        current = MinecraftServerConfig()
    }
}
