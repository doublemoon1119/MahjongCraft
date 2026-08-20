package com.doublemoon1119.mahjongcraft.logic.base

/**
 * [Hand] 的對稱快照，用於 Client 端渲染。
 *
 * @property standingTiles 立牌快照，可見性由建立快照時傳入的 `isVisible` 決定。
 * @property lastDrawn 剛摸到但尚未整理的牌快照，可見性同 [standingTiles]。
 * @property melds 副露快照；除暗槓外恆為完整可見，不受 `isVisible` 影響，見 [MeldSnapshot] KDoc。
 */
data class HandSnapshot(
    val standingTiles: List<IdentifiedTileSnapshot>,
    val lastDrawn: IdentifiedTileSnapshot? = null,
    val melds: List<MeldSnapshot> = emptyList(),
)

/**
 * 產生一個 [Hand] 的快照。
 *
 * [isVisible] 控制立牌與剛摸到的牌，同時也是 [Meld.toSnapshot] 判斷暗槓可見性所需的「是否為手牌
 * 本人／被授權觀察者」條件；[revealsClosedKanTiles] 轉交給 [Meld.toSnapshot] 決定該規則是否公開
 * 暗槓身份，見其 KDoc。
 */
fun Hand.toSnapshot(isVisible: Boolean, revealsClosedKanTiles: Boolean): HandSnapshot = HandSnapshot(
    standingTiles = this.standingTiles.map { it.toSnapshot(isVisible) },
    lastDrawn = this.lastDrawn?.toSnapshot(isVisible),
    melds = this.melds.map { it.toSnapshot(handIsVisible = isVisible, revealsClosedKanTiles = revealsClosedKanTiles) },
)
