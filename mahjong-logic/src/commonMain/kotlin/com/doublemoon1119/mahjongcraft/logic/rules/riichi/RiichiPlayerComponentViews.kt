package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewType

/** 日麻玩家允許交付給讀取者的規則資料。 */
object RiichiPlayerComponentViews {
    /** 公開的立直宣告與一發資格。 */
    val DECLARATION: ComponentViewType<RiichiDeclaration> = ComponentViewType(
        id = "mahjongcraft:riichi/player_view/declaration",
        valueClass = RiichiDeclaration::class,
        copyValue = { it.copy() },
    )

    /** 只交付本人且已成立的永久振聽標記。 */
    val PERMANENT_FURITEN: ComponentViewType<Unit> = ComponentViewType(
        id = "mahjongcraft:riichi/player_view/permanent_furiten",
        valueClass = Unit::class,
        copyValue = {},
    )

    /** 已成立且公開的包牌責任。 */
    val PAO_LIABILITY: ComponentViewType<PaoLiability> = ComponentViewType(
        id = "mahjongcraft:riichi/player_view/pao_liability",
        valueClass = PaoLiability::class,
        copyValue = { it.copy() },
    )
}
