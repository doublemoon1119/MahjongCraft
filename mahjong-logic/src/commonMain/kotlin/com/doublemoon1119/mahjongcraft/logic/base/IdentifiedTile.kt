package com.doublemoon1119.mahjongcraft.logic.base

import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import kotlin.uuid.Uuid

/**
 * 具有唯一身份標識的麻將牌。
 *
 * 以 [id] 區分同一種牌的不同實體，讓呼叫端可以穩定追蹤每一張牌。
 * 藉由組合 (Composition) 而非繼承的方式，保持了 Tile 屬性的純粹性。
 *
 * @property id 唯一識別碼，整副牌中不重複。
 * @property tile 該張牌的牌面種類，不含逐張特性。
 * @property components 這張牌的不可變權威組件；搬移時與 UUID、牌面一起保留。
 */
data class IdentifiedTile(
    val id: Uuid,
    val tile: Tile,
    val components: ComponentSet = ComponentSet.EMPTY,
) {
    init {
        components.requireTarget(BuiltInComponentOwners.TILE)
    }
}
