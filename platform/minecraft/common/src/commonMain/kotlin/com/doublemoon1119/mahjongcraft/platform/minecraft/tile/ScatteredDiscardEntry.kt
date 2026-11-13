package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import kotlin.uuid.Uuid

/**
 * 散放牌河中一張打出的牌，依全桌打牌順序排列。
 *
 * @property tileId 牌。
 * @property seatIndex 打牌者的座位 index。
 * @property isTaken 是否已被鳴走；被鳴走的牌讓出位置給之後打出的牌。
 */
data class ScatteredDiscardEntry(
    val tileId: Uuid,
    val seatIndex: Int,
    val isTaken: Boolean,
)
