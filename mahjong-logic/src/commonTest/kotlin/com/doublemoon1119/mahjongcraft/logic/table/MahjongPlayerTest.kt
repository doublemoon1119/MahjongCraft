package com.doublemoon1119.mahjongcraft.logic.table

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.GameAction.KanType
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * 針對 [MahjongPlayer] 的實體狀態與基礎邏輯進行單元測試。
 *
 * [MahjongPlayer] 為不可變值物件，所有操作皆回傳反映變更後狀態的新實例，
 * 因此測試中以 `player = player.xxx(...)` 的重新賦值方式驗證各項操作。
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
        val uuid = Uuid.random()
        val seat = Wind.EAST

        // 使用工廠建立玩家，工廠會自動注入 FakeDiscardPile
        val player = FakeMahjongPlayerFactory.create(
            id = uuid,
            initialSeat = seat,
        )

        // 驗證屬性賦值
        assertEquals(uuid, player.id, "Player ID should match the provided UUID.")
        assertEquals(seat.ordinal, player.initialSeatIndex, "Initial seat index should be correctly assigned.")
        assertEquals(seat, player.seatWind, "Current wind should be equal to initial seat upon initialization.")
        assertEquals(0, player.score, "Initial score should be 0 by default.")

        // 驗證牌河是否已正確持有（透過工廠注入）
        assert(player.discardPile is FakeDiscardPile)
    }

    /**
     * 驗證玩家狀態的可變動性。
     * * 測試當分數變更或方位轉換（過莊/連莊）時，透過 copy() 產生的新實例是否正確反映新值。
     */
    @Test
    fun `test player state updates`() {
        var player = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST)

        // 測試分數更新
        player = player.copy(score = 25000)
        assertEquals(25000, player.score, "Player score should be updatable.")

        // 測試方位變更 (例如過莊)
        player = player.copy(seatWind = Wind.SOUTH)
        assertEquals(Wind.SOUTH, player.seatWind, "Player's current wind should be updatable.")
    }

    /**
     * 驗證動作歷史的記錄功能。
     *
     * 確保玩家執行的動作能夠被正確記錄，且歷史記錄維持正確的順序。
     */
    @Test
    fun `test action history recording`() {
        var player = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST)

        // 驗證初始歷史為空
        assertTrue(player.actionHistory.isEmpty(), "Initial action history should be empty.")

        // 記錄幾個動作
        player = player.recordAction(GameAction.Draw)
        player = player.recordAction(GameAction.Discard(Uuid.random()))
        player = player.recordAction(
            GameAction.Kan(
                KanType.OPEN_KAN,
                Uuid.random(),
                listOf(Uuid.random(), Uuid.random(), Uuid.random()),
            ),
        )

        // 驗證歷史記錄數量與內容
        assertEquals(3, player.actionHistory.size, "Action history should contain 3 actions.")
        assertTrue(player.actionHistory[0] is GameAction.Draw, "First action should be Draw.")
        assertTrue(player.actionHistory[1] is GameAction.Discard, "Second action should be Discard.")
        assertTrue(player.actionHistory[2] is GameAction.Kan, "Third action should be Kan.")
    }

    /**
     * 驗證動作歷史的清除功能。
     *
     * 確保清除歷史記錄後，動作歷史會被正確重設。
     */
    @Test
    fun `test action history clearing`() {
        var player = FakeMahjongPlayerFactory.create(initialSeat = Wind.EAST)

        // 記錄動作
        player = player.recordAction(GameAction.Draw)
        player = player.recordAction(GameAction.Tsumo)

        // 驗證有記錄
        assertEquals(2, player.actionHistory.size, "Action history should contain 2 actions before clearing.")

        // 清除歷史
        player = player.clearActionHistory()

        // 驗證已清除
        assertTrue(player.actionHistory.isEmpty(), "Action history should be empty after clearing.")
    }
}
