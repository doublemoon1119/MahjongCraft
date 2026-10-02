package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftHistoryConfig
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** 歷史查詢准入目前使用的動態限制快照。
 *
 * @property minimumInterval 同一玩家接受兩次查詢之間的最短間隔。
 * @property maximumOutstanding 全伺服器同時執行中的查詢工作上限。
 * @property rejectionResponseInterval 同一玩家收到拒絕回覆之間的最短間隔。
 */
internal data class HistoryQueryAdmissionLimits(
    val minimumInterval: Duration = MinecraftHistoryConfig.DEFAULT_QUERY_MINIMUM_INTERVAL_MILLISECONDS.milliseconds,
    val maximumOutstanding: Int = MinecraftHistoryConfig.DEFAULT_QUERY_MAX_OUTSTANDING,
    val rejectionResponseInterval: Duration =
        MinecraftHistoryConfig.DEFAULT_QUERY_REJECTION_REPLY_INTERVAL_MILLISECONDS.milliseconds,
)
