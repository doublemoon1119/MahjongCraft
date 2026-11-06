package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 從某個遊戲時間開始生效的本人手牌排列。
 *
 * @property effectiveAt 開始生效的遊戲時間。
 * @property order 生效後的本人手牌排列。
 */
data class PrivateHandOrderEntry(
    val effectiveAt: Long,
    val order: PrivateHandOrder,
)
