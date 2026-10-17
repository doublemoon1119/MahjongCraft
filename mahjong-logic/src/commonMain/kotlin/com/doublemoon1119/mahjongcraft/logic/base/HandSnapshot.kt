package com.doublemoon1119.mahjongcraft.logic.base

/**
 * [Hand] 對指定觀察者的可見快照。
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
 *
 * @param isVisible 觀察者是否可見手牌牌面。
 * @param revealsClosedKanTiles 規則是否公開暗槓牌面。
 * @return 依觀察者可見性建立的手牌快照。
 */
fun Hand.toSnapshot(isVisible: Boolean, revealsClosedKanTiles: Boolean): HandSnapshot = HandSnapshot(
    standingTiles = if (isVisible) {
        standingTiles.map { it.toSnapshot(true) }
    } else {
        val tilesById = standingTiles.associateBy { it.id }
        PublicHandOrder.arrange(tiles.map { it.id }).map { tilesById.getValue(it).toSnapshot(false) } +
            listOfNotNull(lastDrawn?.toSnapshot(false))
    },
    lastDrawn = this.lastDrawn?.toSnapshot(isVisible),
    melds = this.melds.map { it.toSnapshot(handIsVisible = isVisible, revealsClosedKanTiles = revealsClosedKanTiles) },
)
