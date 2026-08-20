package com.doublemoon1119.mahjongcraft.logic.base

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig

/**
 * [Meld] 的對稱快照，用於 Client 端渲染。
 *
 * 除暗槓（[MeldType.CLOSED_KAN]）外，副露本質上是公開宣告的牌組，[tiles]／[sourceTile] 恆為可見，
 * 不像 [HandSnapshot.standingTiles] 依 observer 權限決定是否隱藏牌面；暗槓則依規則
 * （[MahjongRuleConfig.revealsClosedKanTiles]）與本人視角決定，見 [Meld.toSnapshot] KDoc。
 */
data class MeldSnapshot(
    val type: MeldType,
    val tiles: List<IdentifiedTileSnapshot>,
    val sourceTile: IdentifiedTileSnapshot?,
    val sourceDirection: RelativeDirection,
)

/**
 * 產生一個 [Meld] 的快照。
 *
 * 除暗槓外，副露永遠公開，牌面固定可見；暗槓則依 [handIsVisible]（是否為手牌本人／被授權觀察者）
 * 與 [revealsClosedKanTiles]（該規則是否公開暗槓身份）決定——兩者任一成立即完整可見，都不成立時
 * 只保留牌張 ID。
 */
fun Meld.toSnapshot(handIsVisible: Boolean, revealsClosedKanTiles: Boolean): MeldSnapshot {
    val tilesVisible = handIsVisible || type != MeldType.CLOSED_KAN || revealsClosedKanTiles
    return MeldSnapshot(
        type = type,
        tiles = tiles.map { it.toSnapshot(isVisible = tilesVisible) },
        sourceTile = sourceTile?.toSnapshot(isVisible = tilesVisible),
        sourceDirection = sourceDirection,
    )
}
