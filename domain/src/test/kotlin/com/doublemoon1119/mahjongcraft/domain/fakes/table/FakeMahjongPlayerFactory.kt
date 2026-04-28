package com.doublemoon1119.mahjongcraft.domain.fakes.table

import com.doublemoon1119.mahjongcraft.domain.base.Hand
import com.doublemoon1119.mahjongcraft.domain.table.DiscardPile
import com.doublemoon1119.mahjongcraft.domain.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.domain.table.Wind
import java.util.UUID

/**
 * 用於單元測試的玩家實體工廠。
 *
 * 提供便捷的方法來產生 [MahjongPlayer]，並預設注入 [FakeDiscardPile]。
 */
object FakeMahjongPlayerFactory {

    /**
     * 建立一個測試用的玩家。
     *
     * @param name 玩家名稱，預設為 "TestPlayer"。
     * @param initialSeat 初始座位方位，預設為 [Wind.EAST]。
     * @param id 玩家唯一識別碼，預設隨機產生。
     * @param hand 初始手牌，預設建立新的空 [Hand]。
     * @param discardPile 丟牌堆，預設為 [FakeDiscardPile]。
     * @return 具備模擬牌河的 [MahjongPlayer] 實體。
     */
    fun create(
        name: String = "TestPlayer",
        initialSeat: Wind = Wind.EAST,
        id: UUID = UUID.randomUUID(),
        hand: Hand = Hand(),
        discardPile: DiscardPile<*> = FakeDiscardPile()
    ): MahjongPlayer {
        return MahjongPlayer(
            id = id,
            name = name,
            initialSeat = initialSeat,
            hand = hand,
            discardPile = discardPile
        )
    }
}