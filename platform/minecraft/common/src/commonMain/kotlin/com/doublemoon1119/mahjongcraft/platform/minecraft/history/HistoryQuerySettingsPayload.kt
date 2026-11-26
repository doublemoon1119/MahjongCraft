package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import kotlinx.serialization.Serializable
import kotlin.time.Duration

/**
 * 伺服器發布給客戶端的歷史查詢操作間隔，不包含管理員專用設定。
 *
 * @property minimumInterval 目前有效的最短查詢間隔。
 */
@Serializable
data class HistoryQuerySettingsPayload(val minimumInterval: Duration)
