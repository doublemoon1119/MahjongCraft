package com.doublemoon1119.mahjongcraft.testing.logic.table

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileAreaEntry
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileRegion
import com.doublemoon1119.mahjongcraft.logic.tile.component.resolveTileAreas
import kotlin.uuid.Uuid

/**
 * [tileId] 在這份桌況中所在的區域。
 *
 * @param tileId 要查詢的牌。
 * @return 牌所在的區域；牌不在任何目前區域時為 null。
 */
fun TableState.tileArea(tileId: Uuid): TileAreaEntry? = resolveTileAreas().firstOrNull { it.tile.id == tileId }

/**
 * [tileId] 在這份桌況中所在的區域種類。
 *
 * @param tileId 要查詢的牌。
 * @return 區域種類；牌不在任何目前區域時為 null。
 */
fun TableState.tileRegion(tileId: Uuid): TileRegion? = tileArea(tileId)?.region
