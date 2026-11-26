package com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDeclaration
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDeclarationKind
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerComponents

/**
 * 建立測試玩家的立直宣告組件。
 *
 * @param declarationTile 宣告打出的牌；沒有時不建立宣告。
 * @param kind 宣告種類。
 * @param isIppatsu 是否仍在一發期限內，沒有宣告時必須為 false。
 * @return 可與其他玩家測試組件合併的集合。
 */
fun riichiDeclarationComponents(
    declarationTile: IdentifiedTile? = null,
    kind: RiichiDeclarationKind = RiichiDeclarationKind.RIICHI,
    isIppatsu: Boolean = false,
): ComponentSet {
    require(declarationTile != null || !isIppatsu) { "Ippatsu requires an existing declaration" }
    return declarationTile?.let {
        ComponentSet.EMPTY.with(
            type = RiichiPlayerComponents.DECLARATION,
            value = RiichiDeclaration(
                kind = kind,
                discardTileId = it.id,
                isIppatsu = isIppatsu,
            ),
        )
    } ?: ComponentSet.EMPTY
}
