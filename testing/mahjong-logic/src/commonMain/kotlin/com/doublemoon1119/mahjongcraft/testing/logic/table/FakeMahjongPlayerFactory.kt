package com.doublemoon1119.mahjongcraft.testing.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.PlayerTileAreas
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlin.uuid.Uuid

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
     * @param initialSeatIndex 整場固定起家順位；預設沿用 [initialSeat] 的列舉順位。
     * @param seatWind 本局自風；預設沿用 [initialSeat]，方便既有測試描述座位。
     * @param id 玩家唯一識別碼，預設隨機產生。
     * @param hand 初始手牌，預設建立新的空 [Hand]。
     * @param discardPile 該玩家的牌河實體，預設為 [FakeDiscardPile]。
     * @param components 初始玩家組件。
     * @param tileAreas 初始移出牌區。
     * @return 使用指定牌河的 [MahjongPlayer] 實體；未指定時使用預設的 [FakeDiscardPile]。
     */
    fun create(
        initialSeat: Wind = Wind.EAST,
        initialSeatIndex: Int = initialSeat.ordinal,
        seatWind: Wind = initialSeat,
        id: Uuid = Uuid.random(),
        hand: Hand = Hand(),
        discardPile: DiscardPile<*> = FakeDiscardPile(),
        tileAreas: PlayerTileAreas = PlayerTileAreas.EMPTY,
        components: ComponentSet = ComponentSet.EMPTY,
    ): MahjongPlayer = MahjongPlayer(
        id = id,
        initialSeatIndex = initialSeatIndex,
        seatWind = seatWind,
        hand = hand,
        discardPile = discardPile,
        tileAreas = tileAreas,
        components = components,
    )
}
