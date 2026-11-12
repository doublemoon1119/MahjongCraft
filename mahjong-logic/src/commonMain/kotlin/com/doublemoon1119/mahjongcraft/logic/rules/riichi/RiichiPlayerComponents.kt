package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentType

/** 日麻玩家攜帶的獨立規則資料。 */
object RiichiPlayerComponents {
    /** 已成立的立直宣告；沒有此項表示未立直。 */
    val DECLARATION: ComponentType<RiichiDeclaration> = ComponentType(
        id = "mahjongcraft:riichi/player/declaration",
        valueClass = RiichiDeclaration::class,
        copyValue = { it.copy() },
        target = BuiltInComponentOwners.PLAYER,
    )

    /** 本局已成立永久振聽；沒有此項表示未成立。 */
    val PERMANENT_FURITEN: ComponentType<Unit> = ComponentType(
        id = "mahjongcraft:riichi/player/permanent_furiten",
        valueClass = Unit::class,
        copyValue = {},
        target = BuiltInComponentOwners.PLAYER,
    )

    /** 本局成立的包牌責任；沒有此項表示尚未成立。 */
    val PAO_LIABILITY: ComponentType<RiichiPaoLiability> = ComponentType(
        id = "mahjongcraft:riichi/player/pao_liability",
        valueClass = RiichiPaoLiability::class,
        copyValue = { it.copy() },
        target = BuiltInComponentOwners.PLAYER,
    )
}
