package com.doublemoon1119.mahjongcraft.logic.table.layout

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import kotlin.uuid.Uuid

/**
 * [TileWallLayout.resolve] 的結果。
 *
 * @property drawOrder 依開門結果排列後的一般摸牌順序，不包含 [reservedWallTiles]；可直接用於建立只含
 * 活牌的 [TileWall]，再存入桌況的 `tileWall` 欄位。
 * @property reservedWallTiles 不由一般 [drawOrder] 摸牌流程取得、改由規則解讀用途與順序的開局保留牌；規則可依自身需求決定保留區的用途；通用布局結果不賦予
 * 日麻王牌等規則專屬語意。
 * @property structure 全部牌（含一般摸牌區與規則保留區）在實體牌牆的結構座標，鍵為 [IdentifiedTile.id]。
 */
data class TileWallLayoutResult(
    val drawOrder: List<IdentifiedTile>,
    val reservedWallTiles: List<IdentifiedTile>,
    val structure: Map<Uuid, TileWallPosition>,
)
