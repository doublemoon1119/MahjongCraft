package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.component.ComponentPatch
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer

/**
 * 立直且仍有一發資格時，產生結束一發資格的變更。
 *
 * @return 結束一發的變更；沒有一發資格時不做任何變更。
 */
fun MahjongPlayer.ippatsuEndPatch(): ComponentPatch {
    val riichiState = riichiDeclaration?.takeIf { it.isIppatsu } ?: return ComponentPatch()
    return ComponentPatch.basedOn(components, values = ComponentSet.EMPTY.with(RiichiPlayerComponents.DECLARATION, riichiState.copy(isIppatsu = false)))
}

/**
 * 立直中的玩家放棄和牌時，產生本局永久振聽的變更。
 *
 * @return 永久振聽的變更；沒有立直時不做任何變更。
 */
fun MahjongPlayer.permanentFuritenPatch(): ComponentPatch {
    if (riichiDeclaration == null) return ComponentPatch()
    return ComponentPatch.basedOn(components, values = ComponentSet.EMPTY.with(RiichiPlayerComponents.PERMANENT_FURITEN, Unit))
}

/**
 * 碰或明槓湊成大三元、大四喜時，產生記錄包牌責任的變更。
 *
 * @param meldType 本次鳴牌的種類；吃不構成包牌。
 * @param calledTile 本次鳴取的他家捨牌。
 * @param sourceDirection 本次鳴取的來源相對方位。
 * @return 記錄包牌責任的變更；不構成包牌時不做任何變更。
 */
fun MahjongPlayer.paoLiabilityPatch(
    meldType: MeldType,
    calledTile: IdentifiedTile,
    sourceDirection: RelativeDirection,
): ComponentPatch {
    if (meldType != MeldType.PON && meldType != MeldType.OPEN_KAN) return ComponentPatch()
    val liability = PaoDetector.check(hand, calledTile.tile, sourceDirection) ?: return ComponentPatch()
    return ComponentPatch.basedOn(components, values = ComponentSet.EMPTY.with(RiichiPlayerComponents.PAO_LIABILITY, liability))
}
