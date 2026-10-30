package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.HandSnapshot
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTileSnapshot
import com.doublemoon1119.mahjongcraft.logic.base.toSnapshot
import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewSet
import kotlin.uuid.Uuid

/**
 * [MahjongPlayer] 對指定觀察者的不可變可見快照。
 *
 * @property componentViews 已按讀取者裁切的規則資料，不含原始權威組件。
 * @property id 玩家的唯一識別碼
 * @property initialSeatIndex 整場固定的起家座位順位。
 * @property seatWind 本局由規則指派的自風／門風。
 * @property hand 已按讀取者裁切的手牌快照。
 * @property discardPile 已按讀取者裁切的牌河快照。
 * @property tileAreas 依區域及區域內順序排列、已裁切牌面的移出牌區。
 * @property score 當前分數
 */
data class MahjongPlayerSnapshot(
    val id: Uuid,
    override val initialSeatIndex: Int,
    override val seatWind: Wind,
    val hand: HandSnapshot,
    val discardPile: DiscardPileSnapshot,
    val tileAreas: List<PlayerTileAreaSnapshot>,
    override val score: Int,
    val componentViews: ComponentViewSet = ComponentViewSet.EMPTY,
) : RankablePlayer {
    /** 所有已裁切的移出牌，依區域及區域內順序排列。 */
    val setAsideTiles: List<IdentifiedTileSnapshot> get() = tileAreas.flatMap { it.tiles }
}

/**
 * 產生一個 [MahjongPlayer] 的不可變快照。
 *
 * @param isVisible 控制手牌是否可見。當值為 `false` 時，手牌中的牌張資訊將被隱藏，僅保留識別碼
 * @param revealsClosedKanTiles 該規則是否公開暗槓身份，轉交給 [Hand.toSnapshot] 決定副露中暗槓的
 *   可見性
 * @return 依據 [isVisible] 決定手牌可見性的玩家快照
 */
fun MahjongPlayer.toSnapshot(
    isVisible: Boolean,
    revealsClosedKanTiles: Boolean,
): MahjongPlayerSnapshot = MahjongPlayerSnapshot(
    id = this.id,
    initialSeatIndex = this.initialSeatIndex,
    seatWind = this.seatWind,
    hand = this.hand.toSnapshot(isVisible, revealsClosedKanTiles),
    discardPile = this.discardPile.toSnapshot(),
    tileAreas = tileAreas.areas.map { area -> PlayerTileAreaSnapshot(area.id, area.tiles.map { it.toSnapshot(true) }) },
    score = this.score,
)
