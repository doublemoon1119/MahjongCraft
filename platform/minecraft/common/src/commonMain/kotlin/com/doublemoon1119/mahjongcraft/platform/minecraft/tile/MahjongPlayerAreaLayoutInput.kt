package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePoseState
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableFacing
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
 * @property standingTileIds 玩家立牌順序，不含摸牌位；只有完整公開平放時才沿用於共享布局。
 * @property drawnTileId 摸牌位的牌 UUID；沒有時為 null。
 * @property melds 依宣告順序排列的副露。
 * @property setAsideTileIds 依移出順序排列、接在副露之後的移出牌。
 * @property cornerWidth 桌角物件佔用的寬度。
 * @property tilePoseStates 逐張手牌姿態，決定每張牌占用的桌面範圍。
 * @property unsortedTileIds 直接補進立牌、還沒理過的牌，依補進的順序排列；共享布局把它們接在立牌最右邊。
 */
data class MahjongPlayerAreaLayoutInput(
    val controllerX: Int,
    val controllerY: Int,
    val controllerZ: Int,
    val tableFacing: MahjongTableFacing,
    val seatLayout: MahjongTableSeatLayout,
    val seatIndex: Int,
    val standingTileIds: List<Uuid>,
    val drawnTileId: Uuid?,
    val melds: List<MahjongMeldTileGroup>,
    val setAsideTileIds: List<Uuid>,
    val cornerWidth: Double,
    val tilePoseStates: List<TilePoseState> = emptyList(),
    val unsortedTileIds: List<Uuid> = emptyList(),
)
