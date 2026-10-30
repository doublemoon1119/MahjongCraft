package com.doublemoon1119.mahjongcraft.logic.base

import com.doublemoon1119.mahjongcraft.logic.tile.TileInterpretationPolicy

/**
 * 由已知牌面重建分析用牌張，只保留規則允許解讀的特性。
 *
 * 不使用原始權威組件；結果只供分析，不能當成權威狀態回寫。
 *
 * @param interpretation 本局規則的牌值解讀方式。
 * @return 保留原 ID 的已知牌張；牌面未知時為 null。
 */
fun IdentifiedTileSnapshot.toKnownTile(interpretation: TileInterpretationPolicy): IdentifiedTile? = face?.let { known ->
    val value = interpretation.valueOf(known)
    IdentifiedTile(id = id, tile = value.tile, components = value.components)
}
