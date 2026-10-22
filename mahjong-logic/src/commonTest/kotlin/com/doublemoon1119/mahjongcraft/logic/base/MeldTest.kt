package com.doublemoon1119.mahjongcraft.logic.base

import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 針對 [Hand] 中的副露（鳴牌）相關邏輯進行單元測試。
 *
 * [Hand] 為不可變值物件，`call()`/`upgradeToAddedKan()` 皆回傳新的手牌實例，
 * 因此測試以重新賦值的方式取得操作後的手牌狀態。
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

        val hand = Hand(listOf(t1, t2))

        // 執行吃牌 (1,2,3萬)
        val updatedHand = hand.call(
            type = MeldType.CHI,
            tiles = listOf(t1, t2, source),
            source = source,
            direction = RelativeDirection.Left,
            publiclyRevealedTileIds = listOf(t1, t2, source).map { it.id }.toSet(),
            faceDownTileIds = emptySet(),
        )

        // 驗證：立牌應該變空
        assertTrue(updatedHand.standingTiles.isEmpty())
        // 驗證：副露列表應有一組吃
        assertEquals(1, updatedHand.exposedMelds.size)
        assertEquals(MeldType.CHI, updatedHand.exposedMelds[0].type)
        assertEquals(3, updatedHand.exposedMelds[0].tiles.size)
        assertEquals(source, updatedHand.exposedMelds[0].sourceTile)
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
        val hand = Hand(tiles = listOf(t1, t2, t3), lastDrawn = t4)

        // 執行暗槓 (source 為 null)
        val updatedHand = hand.call(
            type = MeldType.CLOSED_KAN,
            tiles = listOf(t1, t2, t3, t4),
            source = null,
            direction = RelativeDirection.Self,
            publiclyRevealedTileIds = closedKanPubliclyRevealedTileIds(listOf(t1, t2, t3, t4), revealsClosedKanTiles = true),
            faceDownTileIds = closedKanFaceDownTileIds(listOf(t1, t2, t3, t4)),
        )

        // 驗證：立牌與摸牌區都應清空
        assertTrue(updatedHand.standingTiles.isEmpty())
        assertNull(updatedHand.lastDrawn)
        assertEquals(1, updatedHand.exposedMelds.size)
        assertEquals(MeldType.CLOSED_KAN, updatedHand.exposedMelds[0].type)
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

        // 先建立一個碰 (PON)
        var hand = Hand(tiles = listOf(t1, t2)).call(
            type = MeldType.PON,
            tiles = listOf(t1, t2, t3),
            source = t3,
            direction = RelativeDirection.Across,
            publiclyRevealedTileIds = setOf(t1.id, t2.id, t3.id),
            faceDownTileIds = emptySet(),
        )

        // 摸到第四張
        hand = hand.copy(lastDrawn = t4)

        // 執行加槓 (索引 0 的副露)
        hand = hand.upgradeToAddedKan(t4, 0)

        // 驗證：摸牌區清空
        assertNull(hand.lastDrawn)
        // 驗證：副露升級
        val updatedMeld = hand.exposedMelds[0]
        assertEquals(MeldType.ADDED_KAN, updatedMeld.type)
        assertEquals(4, updatedMeld.tiles.size)
        assertTrue(updatedMeld.tiles.contains(t4))
    }

    /** 暗槓兩端朝下；規則公開暗槓時整組牌面公開，不公開時整組牌面都不公開。 */
    @Test
    fun `closed kan visibility follows the rule setting`() {
        val tiles = List(4) { FakeIdentifiedTileFactory.create(Tile.Honor.Green) }

        assertEquals(tiles.map { it.id }.toSet(), closedKanPubliclyRevealedTileIds(tiles, revealsClosedKanTiles = true))
        assertEquals(emptySet(), closedKanPubliclyRevealedTileIds(tiles, revealsClosedKanTiles = false))
        assertEquals(setOf(tiles.first().id, tiles.last().id), closedKanFaceDownTileIds(tiles))
    }
}
