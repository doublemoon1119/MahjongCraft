package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import kotlin.uuid.Uuid

/**
 * 計算一位玩家牌區需要的資料。
 *
 * @property controllerX 麻將桌 controller 的世界 X 座標。
 * @property controllerY 麻將桌 controller 的世界 Y 座標。
 * @property controllerZ 麻將桌 controller 的世界 Z 座標。
 * @property tableFacing 麻將桌的世界朝向。
 * @property seatLayout 本局玩家座位 index 對應的標準桌面布局。
 * @property seatIndex 玩家在固定座位順序中的 index。
 * @property standingTileIds 立牌，不含摸牌位的牌。
 * @property hasDrawnTile 摸牌位上是否有牌。
 * @property melds 依宣告順序排列的副露。
 * @property setAsideTileIds 依移出順序排列、接在副露之後的移出牌。
 * @property cornerWidth 桌角物件佔用的寬度。
 */
data class MahjongPlayerAreaLayoutInput(
    val controllerX: Int,
    val controllerY: Int,
    val controllerZ: Int,
    val tableFacing: MahjongTableFacing,
    val seatLayout: MahjongTableSeatLayout,
    val seatIndex: Int,
    val standingTileIds: List<Uuid>,
    val hasDrawnTile: Boolean,
    val melds: List<MahjongMeldTileGroup>,
    val setAsideTileIds: List<Uuid>,
    val cornerWidth: Double,
)
