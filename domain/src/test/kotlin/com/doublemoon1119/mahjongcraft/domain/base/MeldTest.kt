package com.doublemoon1119.mahjongcraft.domain.base

import com.doublemoon1119.mahjongcraft.testing.domain.base.FakeIdentifiedTileFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 針對 [Hand] 中的副露（鳴牌）相關邏輯進行單元測試。
 */
class MeldTest {

    /**
     * 測試吃牌 (CHI) 邏輯。
     * 驗證手牌正確減少兩張，且來自他人的牌 (source) 不應從手牌中扣除。
     */
    @Test
    fun `test call chi removes tiles from hand except source`() {
        // 手牌中有 1萬, 2萬
        val t1 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 1))
        val t2 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 2))
        // 上家打出 3萬 (source)
        val source = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 3))

        val hand = Hand(mutableListOf(t1, t2))

        // 執行吃牌 (1,2,3萬)
        hand.call(
            type = MeldType.CHI,
            tiles = listOf(t1, t2, source),
            source = source,
            direction = RelativeDirection.Left
        )

        // 驗證：立牌應該變空
        assertTrue(hand.standingTiles.isEmpty())
        // 驗證：副露列表應有一組吃
        assertEquals(1, hand.exposedMelds.size)
        assertEquals(MeldType.CHI, hand.exposedMelds[0].type)
        assertEquals(3, hand.exposedMelds[0].tiles.size)
        assertEquals(source, hand.exposedMelds[0].sourceTile)
    }

    /**
     * 測試暗槓 (CLOSED_KAN) 邏輯。
     * 驗證所有四張牌都從手牌中扣除（包含最後摸到的那張）。
     */
    @Test
    fun `test call closed kan removes four tiles from hand`() {
        val tileType = Tile.Numeric(Tile.Suit.Dot, 9)
        val t1 = FakeIdentifiedTileFactory.create(tileType)
        val t2 = FakeIdentifiedTileFactory.create(tileType)
        val t3 = FakeIdentifiedTileFactory.create(tileType)
        val t4 = FakeIdentifiedTileFactory.create(tileType)

        // 手牌三張，摸到第四張
        val hand = Hand(mutableListOf(t1, t2, t3))
        hand.lastDrawn = t4

        // 執行暗槓 (source 為 null)
        hand.call(
            type = MeldType.CLOSED_KAN,
            tiles = listOf(t1, t2, t3, t4),
            source = null,
            direction = RelativeDirection.Self
        )

        // 驗證：立牌與摸牌區都應清空
        assertTrue(hand.standingTiles.isEmpty())
        assertNull(hand.lastDrawn)
        assertEquals(1, hand.exposedMelds.size)
        assertEquals(MeldType.CLOSED_KAN, hand.exposedMelds[0].type)
    }

    /**
     * 測試加槓 (ADDED_KAN) 邏輯。
     * 驗證原本的 PON 被正確升級為 ADDED_KAN。
     */
    @Test
    fun `test upgrade to added kan`() {
        val tileType = Tile.Numeric(Tile.Suit.Bamboo, 5)
        val t1 = FakeIdentifiedTileFactory.create(tileType)
        val t2 = FakeIdentifiedTileFactory.create(tileType)
        val t3 = FakeIdentifiedTileFactory.create(tileType) // 來自他人的 source
        val t4 = FakeIdentifiedTileFactory.create(tileType) // 剛摸到的加槓牌

        val hand = Hand()
        // 先建立一個碰 (PON)
        hand.call(MeldType.PON, listOf(t1, t2, t3), t3, RelativeDirection.Across)

        // 摸到第四張
        hand.lastDrawn = t4

        // 執行加槓 (索引 0 的副露)
        hand.upgradeToAddedKan(t4, 0)

        // 驗證：摸牌區清空
        assertNull(hand.lastDrawn)
        // 驗證：副露升級
        val updatedMeld = hand.exposedMelds[0]
        assertEquals(MeldType.ADDED_KAN, updatedMeld.type)
        assertEquals(4, updatedMeld.tiles.size)
        assertTrue(updatedMeld.tiles.contains(t4))
    }
}