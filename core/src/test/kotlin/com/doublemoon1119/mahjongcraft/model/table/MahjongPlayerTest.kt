package com.doublemoon1119.mahjongcraft.model.table

import com.doublemoon1119.mahjongcraft.model.base.IdentifiedTile
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 針對 [MahjongPlayer] 的實體狀態與基礎邏輯進行測試。
 */
class MahjongPlayerTest {

    /**
     * 測試用的最簡捨牌紀錄實作。
     */
    private class TestEntry(tile: IdentifiedTile) : DiscardPile.DiscardEntry(tile)

    /**
     * 測試用的最簡牌河實作，用於驗證玩家實體對牌河的持有能力。
     */
    private class TestDiscardPile : DiscardPile<TestEntry> {
        private val _entries = mutableListOf<TestEntry>()
        override val entries: List<TestEntry> get() = _entries
        override fun discard(entry: TestEntry) { _entries.add(entry) }
        override fun takeLast() { _entries.lastOrNull()?.isTaken = true }
    }

    /**
     * 測試玩家實體的初始化與基本屬性。
     */
    @Test
    fun `test player initialization and basic properties`() {
        val uuid = UUID.randomUUID()
        val name = "TestPlayer"
        val seat = Wind.EAST
        val discardPile = TestDiscardPile()

        val player = MahjongPlayer(
            id = uuid,
            name = name,
            initialSeat = seat,
            discardPile = discardPile
        )

        // 驗證屬性賦值
        assertEquals(uuid, player.id)
        assertEquals(name, player.name)
        assertEquals(seat, player.initialSeat)
        assertEquals(seat, player.currentWind) // 初始方位應等於初始座位
        assertEquals(0, player.score) // 預設分數應為 0
        assertEquals(discardPile, player.discardPile)
    }

    /**
     * 測試玩家狀態的變更。
     */
    @Test
    fun `test player state updates`() {
        val player = MahjongPlayer(
            id = UUID.randomUUID(),
            name = "Player",
            initialSeat = Wind.EAST,
            discardPile = TestDiscardPile()
        )

        // 更新分數
        player.score = 25000
        assertEquals(25000, player.score)

        // 模擬過莊更新方位
        player.currentWind = Wind.NORTH
        assertEquals(Wind.NORTH, player.currentWind)
    }
}