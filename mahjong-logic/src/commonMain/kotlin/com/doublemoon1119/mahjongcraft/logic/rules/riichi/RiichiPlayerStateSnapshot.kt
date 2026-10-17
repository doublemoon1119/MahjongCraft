package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.table.PlayerRuleState

/**
 * 日麻玩家規則狀態的 observer 快照。
 *
 * 這個型別只可出現在可見快照與 AI 情境，不可拿來取代權威的 [RiichiPlayerState]。
 * nullable 欄位代表該資訊對目前讀取者隱藏，而不是代表其權威值為 false。
 *
 * @property riichiTile 公開的立直牌；未立直或未公開時為 null。
 * @property doubleRiichiTile 公開的雙立直牌；未雙立直或未公開時為 null。
 * @property isIppatsu 一發資格；此資訊可由公開的立直與鳴牌流程推導，因此維持公開。
 * @property paoLiability 公開的包牌責任；未成立或未公開時為 null。
 * @property isPermanentlyFuriten 永久振聽；對不應知道此資訊的讀取者為 null。
 * @property nukiDoraTiles 已公開移出的北牌。
 */
data class RiichiPlayerStateSnapshot(
    val riichiTile: IdentifiedTile? = null,
    val doubleRiichiTile: IdentifiedTile? = null,
    val isIppatsu: Boolean? = null,
    val paoLiability: PaoLiability? = null,
    val isPermanentlyFuriten: Boolean? = null,
    val nukiDoraTiles: List<IdentifiedTile> = emptyList(),
) : PlayerRuleState {
    /** 是否已公開宣告立直或雙立直。 */
    val isRiichi: Boolean get() = riichiTile != null || doubleRiichiTile != null

    /** 是否已公開宣告雙立直。 */
    val isDoubleRiichi: Boolean get() = doubleRiichiTile != null
}

/** 建立本人可用的完整日麻玩家狀態快照。 */
fun RiichiPlayerState.toSnapshot(): RiichiPlayerStateSnapshot = RiichiPlayerStateSnapshot(
    riichiTile = riichiTile,
    doubleRiichiTile = doubleRiichiTile,
    isIppatsu = isIppatsu,
    paoLiability = paoLiability,
    isPermanentlyFuriten = isPermanentlyFuriten,
    nukiDoraTiles = nukiDoraTiles,
)

/** 建立只含公開資訊的日麻玩家狀態快照。 */
fun RiichiPlayerState.toPublicSnapshot(): RiichiPlayerStateSnapshot = RiichiPlayerStateSnapshot(
    riichiTile = riichiTile,
    doubleRiichiTile = doubleRiichiTile,
    isIppatsu = isIppatsu,
    paoLiability = paoLiability,
    nukiDoraTiles = nukiDoraTiles,
)
