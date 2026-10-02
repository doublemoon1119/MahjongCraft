package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftHistoryConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryQuerySettingsPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** 保存目前伺服器公布的歷史查詢間隔；斷線後不沿用上一個伺服器的設定。 */
@Single
class ClientHistoryQuerySettings {
    /** 主執行緒更新的有效查詢間隔。 */
    private val mutableMinimumInterval = MutableStateFlow(MinecraftHistoryConfig.DEFAULT_QUERY_MINIMUM_INTERVAL_MILLISECONDS.milliseconds)

    /** 供排程及查詢控制項觀察的唯讀間隔。 */
    val minimumInterval: StateFlow<Duration> = mutableMinimumInterval.asStateFlow()

    /**
     * 套用有效的伺服器設定，忽略超出共用合法範圍的封包。
     *
     * @param payload 目前連線伺服器公布的查詢設定。
     */
    fun apply(payload: HistoryQuerySettingsPayload) {
        if (payload.minimumIntervalMilliseconds !in
            MinecraftHistoryConfig.MIN_QUERY_MINIMUM_INTERVAL_MILLISECONDS..MinecraftHistoryConfig.MAX_QUERY_MINIMUM_INTERVAL_MILLISECONDS
        ) {
            return
        }
        mutableMinimumInterval.value = payload.minimumIntervalMilliseconds.milliseconds
    }

    /** 連線或世界切換時恢復預設值，等待新伺服器公布設定。 */
    fun reset() {
        mutableMinimumInterval.value = MinecraftHistoryConfig.DEFAULT_QUERY_MINIMUM_INTERVAL_MILLISECONDS.milliseconds
    }
}
