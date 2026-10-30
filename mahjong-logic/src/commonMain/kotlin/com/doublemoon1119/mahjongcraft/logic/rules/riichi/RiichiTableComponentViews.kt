package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewType

/** 日麻桌況可以明確交付給觀察者的資料。 */
object RiichiTableComponentViews {
    /** 已收下、尚未分配的供託點數。 */
    val POT_POINTS: ComponentViewType<Int> = ComponentViewType(
        id = "mahjongcraft:riichi/table_view/pot_points",
        valueClass = Int::class,
        copyValue = { it },
        validateValue = { require(it >= 0 && it % RIICHI_STICK_POINTS == 0) { "Invalid public riichi pot points" } },
    )
}
