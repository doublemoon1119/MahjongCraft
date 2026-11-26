package com.doublemoon1119.mahjongcraft.logic.base

import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet

/**
 * 不帶局內身分的牌值，可用於牌組組成與假設計算。
 *
 * 這不是可操作的牌張。對外顯示仍須經授權投影，不能直接同步完整組件。
 *
 * @property tile 基本牌面，供規則判斷牌型等價關係。
 * @property components 此牌值的不可變特性，供規則計分或建立實際牌張。
 */
data class TileValue(
    val tile: Tile,
    val components: ComponentSet = ComponentSet.EMPTY,
) {
    init {
        components.requireTarget(BuiltInComponentOwners.TILE)
    }
}

/**
 * 取得牌張的牌值，不新增 UUID，也不另存需要同步的副本。
 *
 * @return 含相同基本牌面與不可變組件的牌值。
 */
fun IdentifiedTile.toValue(): TileValue = TileValue(tile, components)
