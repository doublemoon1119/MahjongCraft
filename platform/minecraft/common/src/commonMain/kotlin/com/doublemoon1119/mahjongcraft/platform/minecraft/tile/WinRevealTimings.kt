package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 胡牌倒牌各段開始的相對時刻，以遊戲 tick 計，從手牌理好的那一刻起算。
 *
 * @property reorder 手牌瞬間理好的時刻。
 * @property winningTileLaydown 自摸那張單獨倒下的時刻；不是自摸時為 null。
 * @property handLaydown 其餘手牌一起倒下的時刻。
 * @property end 全部倒完的時刻。
 */
data class WinRevealTimings(
    val reorder: Long,
    val winningTileLaydown: Long?,
    val handLaydown: Long,
    val end: Long,
)
