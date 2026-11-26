package com.doublemoon1119.mahjongcraft.logic.tile.component

import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import kotlin.uuid.Uuid

/**
 * 規則依目前桌況算出的一張牌的組件，不回寫固定組件。
 *
 * @property tileId 目前位於任一合法牌區的牌 UUID。
 * @property components 已與來源值斷開別名的推導組件。
 */
data class TileComponentDescription(
    val tileId: Uuid,
    val components: ComponentSet,
)
