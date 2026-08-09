package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TileAssetKeysTest {

    @Test
    fun `test toAssetKey maps a plain numeric tile to suit letter plus value`() {
        assertEquals("m1", Tile.Numeric(Tile.Suit.Character, 1).toAssetKey())
        assertEquals("p9", Tile.Numeric(Tile.Suit.Dot, 9).toAssetKey())
        assertEquals("s5", Tile.Numeric(Tile.Suit.Bamboo, 5).toAssetKey())
    }

    @Test
    fun `test toAssetKey appends a red suffix for red fives`() {
        assertEquals("m5_red", Tile.Numeric(Tile.Suit.Character, 5, isRed = true).toAssetKey())
    }

    @Test
    fun `test toAssetKey maps honor tiles to their fixed English names`() {
        assertEquals("east", Tile.Honor.East.toAssetKey())
        assertEquals("white_dragon", Tile.Honor.White.toAssetKey())
    }

    @Test
    fun `test toAssetKey throws for flower tiles since there is no asset for them yet`() {
        assertFailsWith<UnsupportedOperationException> { Tile.Flower.Spring.toAssetKey() }
    }

    @Test
    fun `test ALL_RIICHI_TILE_ASSET_KEYS has exactly 38 unique entries`() {
        assertEquals(38, ALL_RIICHI_TILE_ASSET_KEYS.size)
        assertEquals(
            ALL_RIICHI_TILE_ASSET_KEYS.size,
            ALL_RIICHI_TILE_ASSET_KEYS.toSet().size,
            "Asset keys must be unique, otherwise two tiles would collide onto the same predicate index.",
        )
        assertEquals(UNKNOWN_TILE_ASSET_KEY, ALL_RIICHI_TILE_ASSET_KEYS.last())
    }
}
