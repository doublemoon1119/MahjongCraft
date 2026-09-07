package com.doublemoon1119.mahjongcraft.logic.util

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證 [preferPlainTiles] 優先選一般牌、擴充牌型只在數量不足時才被選中的行為。 */
class IdentifiedTileExtensionsTest {
    private val extensionTypeId = TileTypeId.parse("example:red_five")

    /** 一般牌數量足夠時，擴充牌型完全不會被選中。 */
    @Test
    fun `prefers plain tiles when there are enough of them`() {
        val plain1 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5))
        val plain2 = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5))
        val extension = FakeIdentifiedTileFactory.create(Tile.Extension(extensionTypeId))

        val selected = listOf(extension, plain1, plain2).preferPlainTiles(2)

        assertEquals(listOf(plain1, plain2), selected)
    }

    /** 一般牌數量不足時，才會選到擴充牌型補足數量。 */
    @Test
    fun `falls back to extension tiles when not enough plain tiles are available`() {
        val plain = FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Character, 5))
        val extension = FakeIdentifiedTileFactory.create(Tile.Extension(extensionTypeId))

        val selected = listOf(extension, plain).preferPlainTiles(2)

        assertEquals(listOf(plain, extension), selected)
    }
}
