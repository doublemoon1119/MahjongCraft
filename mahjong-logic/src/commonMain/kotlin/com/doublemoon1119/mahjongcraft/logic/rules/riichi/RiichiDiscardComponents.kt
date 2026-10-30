package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.component.ComponentType
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile.DiscardEntry

/** 日麻捨牌紀錄使用的組件，不屬於被打出牌張的永久特性。 */
object RiichiDiscardComponents {
    /** 這次捨牌是立直宣告牌，並提供既有的公共牌譜標記；未附加此項即表示不是宣告牌。 */
    val RIICHI_DECLARED = ComponentType(
        id = "mahjongcraft:riichi/discard/riichi_declared",
        valueClass = Unit::class,
        copyValue = {},
        target = BuiltInComponentOwners.DISCARD_ENTRY,
        publicMarkers = { setOf(RiichiHistoryDiscardMarkerIds.RIICHI_DECLARED) },
    )

    /** 建立一筆立直宣告捨牌所使用的獨立組件集合。 */
    fun declaration(): ComponentSet = ComponentSet.EMPTY.with(RIICHI_DECLARED, Unit)
}

/** 這筆捨牌本身是否為立直宣告，不表示目前持有這張牌的玩家已立直。 */
val DiscardEntry.isRiichiDeclaration: Boolean get() = components[RiichiDiscardComponents.RIICHI_DECLARED] != null
