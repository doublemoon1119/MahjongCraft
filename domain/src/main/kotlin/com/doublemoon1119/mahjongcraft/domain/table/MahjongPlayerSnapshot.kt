package com.doublemoon1119.mahjongcraft.domain.table

import com.doublemoon1119.mahjongcraft.domain.base.HandSnapshot
import com.doublemoon1119.mahjongcraft.domain.base.toSnapshot
import java.util.*

/**
 * [MahjongPlayer] 的不可變快照，用於 Client 端渲染。
 *
 * @property id 玩家的唯一識別碼
 * @property initialSeat 初始座位方位
 * @property hand 手牌快照，其可見性由建立快照時傳入的 [isVisible] 參數決定
 * @property discardPile 牌河實體，始終對所有玩家可見
 * @property playerRuleState 規則特定的玩家狀態（如立直、振聽等）
 * @property score 當前分數
 */
data class MahjongPlayerSnapshot(
    val id: UUID,
    val initialSeat: Wind,
    val hand: HandSnapshot,
    val discardPile: DiscardPile<*>,
    val playerRuleState: PlayerRuleState?,
    val score: Int
)

/**
 * 產生一個 [MahjongPlayer] 的不可變快照。
 *
 * @param isVisible 控制手牌是否可見。當值為 `false` 時，手牌中的牌張資訊將被隱藏，僅保留識別碼
 * @return 依據 [isVisible] 決定手牌可見性的玩家快照
 */
fun MahjongPlayer.toSnapshot(isVisible: Boolean): MahjongPlayerSnapshot {
    return MahjongPlayerSnapshot(
        id = this.id,
        initialSeat = this.initialSeat,
        hand = this.hand.toSnapshot(isVisible),
        discardPile = this.discardPile,
        playerRuleState = this.playerRuleState,
        score = this.score
    )
}