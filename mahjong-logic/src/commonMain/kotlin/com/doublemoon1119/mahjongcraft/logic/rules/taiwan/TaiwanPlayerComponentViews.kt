package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewType

/** 台灣麻將玩家允許交付給讀取者的規則資料。 */
object TaiwanPlayerComponentViews {
    /** 公開的宣告聽牌種類。 */
    val READY_DECLARATION: ComponentViewType<TaiwanReadyKind> = ComponentViewType(
        id = "mahjongcraft:taiwan/player_view/ready_declaration",
        valueClass = TaiwanReadyKind::class,
        copyValue = { it },
    )

    /** 只交付本人、尚未解除的過水標記。 */
    val PASSED_WIN: ComponentViewType<Unit> = ComponentViewType(
        id = "mahjongcraft:taiwan/player_view/passed_win",
        valueClass = Unit::class,
        copyValue = {},
    )
}
