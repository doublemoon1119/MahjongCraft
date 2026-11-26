package com.doublemoon1119.mahjongcraft.logic.tile.component

import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 取得牌山區中已公開且要求正面朝上的牌，不把可讀取但朝下的牌當成翻牌。
 *
 * 此結果只描述共同姿態，不代表這些牌具有寶牌或其他計分用途。
 *
 * @param tableState 同一筆動作的權威桌況。
 * @param registry 已登記的組件與純效果。
 * @return 活牌山及保留區中正面朝上的公開牌 UUID。
 */
fun MahjongRuleModule<*>.publicFaceUpWallTileIds(tableState: TableState, registry: ComponentRegistry): Set<Uuid> = resolveTileComponents(tableState, registry).filter {
    (it.area.region == TileRegion.WALL || it.area.region == TileRegion.RESERVED) && it.isPublic && it.pose == TilePose.FACE_UP
}.mapTo(linkedSetOf()) { it.area.tile.id }
