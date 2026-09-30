package com.doublemoon1119.mahjongcraft.platform.minecraft.config

/**
 * 保存目前 server runtime 最後一份通過驗證的 [MinecraftServerConfig]。
 *
 * 初始化及指令重載由 server thread 發布；背景服務只讀取不可變且具可見性的設定快照。
 */
class MinecraftServerConfigState(
    initialConfig: MinecraftServerConfig = MinecraftServerConfig(),
) {
    /** 目前實際生效的不可變設定 snapshot。 */
    @Volatile var current: MinecraftServerConfig = initialConfig
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
