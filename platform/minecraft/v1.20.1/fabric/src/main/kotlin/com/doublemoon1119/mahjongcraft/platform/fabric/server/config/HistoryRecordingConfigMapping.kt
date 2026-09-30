package com.doublemoon1119.mahjongcraft.platform.fabric.server.config

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryRecordingPolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig

/**
 * 將有效伺服器設定轉為不依賴平台的記錄資格政策。
 *
 * @return 新對局的總開關與 AI 篩選政策。
 */
internal fun MinecraftServerConfig.historyRecordingPolicy(): HistoryRecordingPolicy = HistoryRecordingPolicy(
    enabled = history.enabled,
    includeAiMatches = history.includeAiMatches,
)
