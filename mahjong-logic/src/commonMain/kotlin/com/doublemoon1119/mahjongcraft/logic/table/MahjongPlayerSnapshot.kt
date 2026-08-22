package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.HandSnapshot
import com.doublemoon1119.mahjongcraft.logic.base.toSnapshot
import kotlin.uuid.Uuid

/**
 * [MahjongPlayer] 的不可變快照，用於 Client 端渲染。
 *
 * @property id 玩家的唯一識別碼
 * @property initialSeat 初始座位方位（起家順位，開局後終局前都不會再變）
 * @property currentWind 目前這一局的座位方位，隨連莊/過莊輪轉
 * @property hand 手牌快照，其可見性由建立快照時傳入的 [isVisible] 參數決定
 * @property discardPile 牌河實體，始終對所有玩家可見
 * @property playerRuleState 規則特定的玩家狀態（如立直、振聽等）
 * @property score 當前分數
 * @property isAi 該玩家是否由電腦（AI）操控，始終對所有玩家可見
 */
data class MahjongPlayerSnapshot(
    val id: Uuid,
    override val initialSeat: Wind,
    override val currentWind: Wind,
    val hand: HandSnapshot,
    val discardPile: DiscardPile<*>,
    val playerRuleState: PlayerRuleState?,
    override val score: Int,
    val isAi: Boolean,
) : RankablePlayer

/**
 * 產生一個 [MahjongPlayer] 的不可變快照。
 *
 * @param isVisible 控制手牌是否可見。當值為 `false` 時，手牌中的牌張資訊將被隱藏，僅保留識別碼
 * @param revealsClosedKanTiles 該規則是否公開暗槓身份，轉交給 [Hand.toSnapshot] 決定副露中暗槓的
 *   可見性
 * @return 依據 [isVisible] 決定手牌可見性的玩家快照
 */
fun MahjongPlayer.toSnapshot(isVisible: Boolean, revealsClosedKanTiles: Boolean): MahjongPlayerSnapshot = MahjongPlayerSnapshot(
    id = this.id,
    initialSeat = this.initialSeat,
    currentWind = this.currentWind,
    hand = this.hand.toSnapshot(isVisible, revealsClosedKanTiles),
    discardPile = this.discardPile,
    playerRuleState = this.playerRuleState,
    score = this.score,
    isAi = this.isAi,
)
