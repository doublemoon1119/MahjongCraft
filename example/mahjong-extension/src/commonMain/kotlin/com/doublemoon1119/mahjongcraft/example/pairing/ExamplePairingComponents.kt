package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentType

/** 範例以獨立組件保存的規則資料。 */
object ExamplePairingComponents {
    /** 每局選定一次並保存的加分牌。 */
    val BONUS_TILES = ComponentType(
        id = ExamplePairingComponentIds.BONUS_TILES,
        valueClass = ExamplePairingBonusTiles::class,
        copyValue = { it.copy(values = it.values.toSet()) },
        target = BuiltInComponentOwners.TABLE,
    )

    /** 本局已攤牌；未附加此項即表示尚未攤牌。 */
    val HAND_REVEALED = ComponentType(
        id = ExamplePairingComponentIds.HAND_REVEALED,
        valueClass = Unit::class,
        copyValue = {},
        target = BuiltInComponentOwners.PLAYER,
    )

    /** 牌組建立時決定的公開牌面標記；帶有此項的牌所有人都看得到牌面。 */
    val PUBLIC_FACE = ComponentType(
        id = ExamplePairingComponentIds.PUBLIC_FACE,
        valueClass = Unit::class,
        copyValue = {},
        target = BuiltInComponentOwners.TILE,
    )
}
