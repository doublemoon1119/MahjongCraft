package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePoseState
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import kotlin.uuid.Uuid

/**
 * 計算整桌牌擺放所需的桌子位置與桌況。
 *
 * @property controllerX 控制方塊 X 座標。
 * @property controllerY 控制方塊 Y 座標。
 * @property controllerZ 控制方塊 Z 座標。
 * @property tableFacing 桌子在世界中的朝向。
 * @property seatLayout 座位與桌邊的對應。
 * @property tableState 要擺放的桌況。
 * @property tileLayout 該規則的桌面牌位配置；有實體牌牆時必須與 [wallProjection] 的配置相同。
 * @property cornerWidthBySeat 各座位副露角落被桌上物件占用的寬度；沒有列出的座位為 0。
 * @property handPoseStates 立牌與摸牌位的牌各自的姿態；沒有列出的牌直立。
 * @property wallProjection 牌牆格位投影到這張桌子的方式；沒有實體牌牆時為 null，牌牆的牌不擺放。
 * @property faceUpWallTileIds 牌牆中正面朝上的牌。
 * @property unsortedTileIds 直接補進立牌、還沒理過的牌，依補進的順序排列。
 */
data class TableTileArrangementInput(
    val controllerX: Int,
    val controllerY: Int,
    val controllerZ: Int,
    val tableFacing: MahjongTableFacing,
    val seatLayout: MahjongTableSeatLayout,
    val tableState: TableState,
    val tileLayout: TableTileLayoutProfile,
    val cornerWidthBySeat: Map<Int, Double>,
    val handPoseStates: List<TilePoseState>,
    val wallProjection: MahjongTileWallProjectionContext?,
    val faceUpWallTileIds: Set<Uuid>,
    val unsortedTileIds: List<Uuid> = emptyList(),
) {
    init {
        require(wallProjection == null || wallProjection.tileLayout == tileLayout) { "The wall and the player areas must use the same table tile layout" }
    }
}
