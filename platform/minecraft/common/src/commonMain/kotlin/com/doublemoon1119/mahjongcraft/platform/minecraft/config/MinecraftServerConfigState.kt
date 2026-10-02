package com.doublemoon1119.mahjongcraft.platform.minecraft.config

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 保存目前 server runtime 最後一份通過驗證的 [MinecraftServerConfig]。
 *
 * 初始化及指令重載由 server thread 發布；背景服務只讀取不可變且具可見性的設定快照。
 *
 * @param initialConfig 啟動時已驗證的設定快照。
 */
class MinecraftServerConfigState(
    initialConfig: MinecraftServerConfig = MinecraftServerConfig(),
) {
    /** 完整設定發布的反應式來源，只在有效設定變更後更新。 */
    private val mutableUpdates = MutableStateFlow(initialConfig)

    /** 可供平台同步客戶端有效設定的唯讀更新來源。 */
    val updates: StateFlow<MinecraftServerConfig> = mutableUpdates.asStateFlow()

    /** 目前實際生效的不可變設定 snapshot。 */
    @Volatile var current: MinecraftServerConfig = initialConfig
        private set

    /**
     * 以完整且已驗證的設定取代目前設定，並發布變更。
     *
     * @param config 欲發布的完整設定快照。
     */
    fun replace(config: MinecraftServerConfig) {
        current = config
        mutableUpdates.value = config
    }

    /** 將有效設定還原為程式內建預設值。 */
    fun reset() {
        replace(MinecraftServerConfig())
    }
}
