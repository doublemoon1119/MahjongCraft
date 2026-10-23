package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

/**
 * 一位玩家牌區每張牌的最終位置。
 *
 * @property cornerYieldShift 立牌與摸牌位為了避開桌角區一起往玩家方向平移的距離。
 * @property standing 依公開順序排列的立牌。
 * @property drawnSlot 摸牌位。
 * @property melds 依宣告順序排列的副露，每組依擺放順序列出每張牌；加槓補上的牌排在該組最後。
 * @property setAside 依移出順序排列的移出牌。
 */
data class MahjongPlayerAreaLayoutResult(
    val cornerYieldShift: Double,
    val standing: List<MahjongAreaTilePlacement>,
    val drawnSlot: MahjongTileWallPlacement,
    val melds: List<List<MahjongAreaTilePlacement>>,
    val setAside: List<MahjongAreaTilePlacement>,
)
