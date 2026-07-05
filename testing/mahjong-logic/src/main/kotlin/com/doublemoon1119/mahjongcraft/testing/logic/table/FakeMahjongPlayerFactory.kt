package com.doublemoon1119.mahjongcraft.testing.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.PlayerRuleState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import java.util.*

/**
 * 用於單元測試的玩家實體工廠。
 *
 * 提供便捷的方法來產生 [MahjongPlayer]
 */
object FakeMahjongPlayerFactory {

    /**
     * 建立一個測試用的玩家。
     *
     * @param initialSeat 初始座位方位，預設為 [Wind.EAST]。
     * @param id 玩家唯一識別碼，預設隨機產生。
     * @param hand 初始手牌，預設建立新的空 [Hand]。
     * @param discardPile 該玩家的牌河實體，預設為 [FakeDiscardPile]。
     * @param playerRuleState 用於儲存規則特有的玩家狀態（如立直、振聽等）。
     * @return 具備模擬牌河的 [MahjongPlayer] 實體。
     */
    fun create(
        initialSeat: Wind = Wind.EAST,
        id: UUID = UUID.randomUUID(),
        hand: Hand = Hand(),
        discardPile: DiscardPile<*> = FakeDiscardPile(),
        playerRuleState: PlayerRuleState? = null
    ): MahjongPlayer {
        return MahjongPlayer(
            id = id,
            initialSeat = initialSeat,
            hand = hand,
            discardPile = discardPile,
            playerRuleState = playerRuleState
        )
    }
}