package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentType

/** 台灣麻將玩家攜帶的獨立規則資料。 */
object TaiwanPlayerComponents {
    /** 已成立的宣告聽牌；沒有此項表示未宣告。 */
    val READY_DECLARATION: ComponentType<TaiwanReadyKind> = ComponentType(
        id = "mahjongcraft:taiwan/player/ready_declaration",
        valueClass = TaiwanReadyKind::class,
        copyValue = { it },
        target = BuiltInComponentOwners.PLAYER,
    )

    /** 放過胡牌後尚未解除的過水；沒有此項表示可以胡牌。 */
    val PASSED_WIN: ComponentType<Unit> = ComponentType(
        id = "mahjongcraft:taiwan/player/passed_win",
        valueClass = Unit::class,
        copyValue = {},
        target = BuiltInComponentOwners.PLAYER,
    )
}
