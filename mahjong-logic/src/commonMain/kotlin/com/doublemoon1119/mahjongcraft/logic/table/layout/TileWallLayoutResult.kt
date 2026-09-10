package com.doublemoon1119.mahjongcraft.logic.table.layout

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import kotlin.uuid.Uuid

/**
 * [TileWallLayout.resolve] 的結果。
 *
 * @property drawOrder 依開門結果排列後的一般摸牌順序，不包含 [reservedWallTiles]；可直接用於建立只含
 * 活牌的 [TileWall]，再存入桌況的 `tileWall` 欄位。
 * @property initialDeadWall 牌局開始當下的規則保留牌；舊名稱為既有 API 相容性而保留。保留牌不代表
 * 實體布局必須形成日麻式獨立王牌區，通用呼叫端應改讀 [reservedWallTiles]。
 * @property structure 全部牌（含活牌與王牌）在實體牌牆的結構座標，鍵為 [IdentifiedTile.id]，供未來
 * 3D 呈現使用。
 */
data class TileWallLayoutResult(
    val drawOrder: List<IdentifiedTile>,
    val initialDeadWall: List<IdentifiedTile>,
    val structure: Map<Uuid, TileWallPosition>,
) {
    /** 不由一般 [drawOrder] 摸牌流程取得、改由規則解讀用途與順序的開局保留牌。 */
    val reservedWallTiles: List<IdentifiedTile> get() = initialDeadWall
}
