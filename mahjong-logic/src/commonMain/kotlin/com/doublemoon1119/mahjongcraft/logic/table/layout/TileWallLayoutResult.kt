package com.doublemoon1119.mahjongcraft.logic.table.layout

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import kotlin.uuid.Uuid

/**
 * [TileWallLayout.resolve] 的結果。
 *
 * @property drawOrder 依開門結果排列後的活牌摸牌順序；可直接用於建立 [TileWall]。
 * @property initialDeadWall 牌局開始當下的王牌快照，依規則定義的固定內部順序保存。這只是開局瞬間
 * 的初始狀態，不代表王牌整局固定不變——王牌在牌局進行中如何補牌（例如部分規則會在補牌後從活牌
 * 尾端移入王牌，維持王牌固定張數）屬於規則自身的 runtime 狀態，不是這個型別的責任範圍。
 * @property structure 全部牌（含活牌與王牌）在實體牌牆的結構座標，鍵為 [IdentifiedTile.id]，供未來
 * 3D 呈現使用。
 */
data class TileWallLayoutResult(
    val drawOrder: List<IdentifiedTile>,
    val initialDeadWall: List<IdentifiedTile>,
    val structure: Map<Uuid, TileWallPosition>,
)
