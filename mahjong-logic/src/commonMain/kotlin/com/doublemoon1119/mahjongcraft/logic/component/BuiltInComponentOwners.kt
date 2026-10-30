package com.doublemoon1119.mahjongcraft.logic.component

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile.DiscardEntry
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/** 共用遊戲模型使用的組件對象定義。 */
object BuiltInComponentOwners {
    /** 單張牌；牌值本身帶的特性屬於牌組，不一定登記生命週期。 */
    val TILE = ComponentOwnerType("mahjongcraft:component_owner/tile", IdentifiedTile::class, requiresLifecycle = false)

    /** 一次捨牌紀錄。 */
    val DISCARD_ENTRY = ComponentOwnerType("mahjongcraft:component_owner/discard_entry", DiscardEntry::class)

    /** 玩家。 */
    val PLAYER = ComponentOwnerType("mahjongcraft:component_owner/player", MahjongPlayer::class)

    /** 對局。 */
    val TABLE = ComponentOwnerType("mahjongcraft:component_owner/table", TableState::class)

    /** 一組副露。 */
    val MELD = ComponentOwnerType("mahjongcraft:component_owner/meld", Meld::class)

    /** 依固定順序排列的共用對象定義。 */
    val ALL: List<ComponentOwnerType<*>> = listOf(TILE, DISCARD_ENTRY, PLAYER, TABLE, MELD)
}
