package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.module.PublicPlayerIndicator
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlin.uuid.Uuid

/**
 * 單一固定座位的完整公開顯示快照；[playerName] 為 `null` 代表目前解析不出這個玩家的真實名稱。
 *
 * @property playerId 玩家 UUID。
 * @property playerName 目前解析出的玩家名稱；無法解析時為 `null`。
 * @property isAi 是否由 AI 操控。
 * @property seatIndex 固定座位索引。
 * @property seatWind 該座位的風位。
 * @property score 目前分數。
 * @property indicators 規則提供的公開玩家標示。
 */
data class MahjongPlayerInfoEntry(
    val playerId: Uuid,
    val playerName: String?,
    val isAi: Boolean,
    val seatIndex: Int,
    val seatWind: Wind,
    val score: Int,
    val indicators: List<PublicPlayerIndicator>,
)
