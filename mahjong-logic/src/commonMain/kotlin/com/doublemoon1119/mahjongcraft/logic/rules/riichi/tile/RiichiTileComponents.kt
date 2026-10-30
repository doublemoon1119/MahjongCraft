package com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile

import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentType

/** 日麻逐張計分特性，與基本牌面及素材分開。 */
object RiichiTileComponents {
    /** 赤寶牌特性；沒有此組件表示不是赤寶牌。加算資格由日麻規則判斷。 */
    val RED_DORA = ComponentType(
        id = "mahjongcraft:riichi/tile/red_dora",
        valueClass = Unit::class,
        copyValue = {},
        target = BuiltInComponentOwners.TILE,
    )

    /** 目前已生效的表寶牌指示牌；只由規則按公開進度產生，不代表所有可見牌牆牌。 */
    val DORA_INDICATOR = ComponentType(
        id = "mahjongcraft:riichi/tile/dora_indicator",
        valueClass = Unit::class,
        copyValue = {},
        target = BuiltInComponentOwners.TILE,
    )
}
