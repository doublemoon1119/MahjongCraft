package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import kotlin.uuid.Uuid

/**
 * 一桌一個 entity 所需的規則公開資料，不含世界位置與方塊朝向。
 *
 * @property tableId 麻將桌 UUID。
 * @property dealerPlayerId 莊家玩家 UUID。
 * @property players 依座位順序排列的公開玩家資料。
 */
data class MahjongPlayerInfoPresentation(
    val tableId: Uuid,
    val dealerPlayerId: Uuid,
    val players: List<MahjongPlayerInfoEntry>,
)
