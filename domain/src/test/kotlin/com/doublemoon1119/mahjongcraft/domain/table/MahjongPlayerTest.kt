package com.doublemoon1119.mahjongcraft.domain.table

import com.doublemoon1119.mahjongcraft.testing.fakes.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.fakes.FakeMahjongPlayerFactory
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 針對 [MahjongPlayer] 的實體狀態與基礎邏輯進行單元測試。
 *
 * 驗證玩家物件的初始化屬性賦值，以及分數與方位等狀態的更新功能。
 */
class MahjongPlayerTest {

    /**
     * 驗證玩家實體的初始化與基礎屬性。
     *
     * 確保傳入的 ID、名稱與座位能正確反映在玩家物件中，且初始狀態（分數、方位）符合預設值。
     */
    @Test
    fun `test player initialization and basic properties`() {
        val uuid = UUID.randomUUID()
        val name = "TestPlayer"
        val seat = Wind.EAST

        // 使用工廠建立玩家，工廠會自動注入 FakeDiscardPile
        val player = FakeMahjongPlayerFactory.create(
            id = uuid,
            name = name,
            initialSeat = seat
        )

        // 驗證屬性賦值
        assertEquals(uuid, player.id, "Player ID should match the provided UUID.")
        assertEquals(name, player.name, "Player name should match the provided name.")
        assertEquals(seat, player.initialSeat, "Initial seat should be correctly assigned.")
        assertEquals(seat, player.currentWind, "Current wind should be equal to initial seat upon initialization.")
        assertEquals(0, player.score, "Initial score should be 0 by default.")

        // 驗證牌河是否已正確持有（透過工廠注入）
        assert(player.discardPile is FakeDiscardPile)
    }

    /**
     * 驗證玩家狀態的可變動性。
     * * 測試當分數變更或方位轉換（過莊/連莊）時，玩家物件是否能正確儲存新值。
     */
    @Test
    fun `test player state updates`() {
        val player = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST)

        // 測試分數更新
        player.score = 25000
        assertEquals(25000, player.score, "Player score should be updatable.")

        // 測試方位變更 (例如過莊)
        player.currentWind = Wind.SOUTH
        assertEquals(Wind.SOUTH, player.currentWind, "Player's current wind should be updatable.")
    }
}