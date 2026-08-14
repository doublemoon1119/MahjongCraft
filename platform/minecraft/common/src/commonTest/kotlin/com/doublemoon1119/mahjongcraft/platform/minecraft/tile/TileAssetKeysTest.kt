package com.doublemoon1119.mahjongcraft.platform.minecraft.tile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import kotlin.test.Test
import kotlin.test.assertEquals

class TileAssetKeysTest {

    @Test
    fun `test toAssetKey maps a plain numeric tile to suit letter plus value`() {
        assertEquals("m1", Tile.Numeric(Tile.Suit.Character, 1).toAssetKey())
        assertEquals("p9", Tile.Numeric(Tile.Suit.Dot, 9).toAssetKey())
        assertEquals("s5", Tile.Numeric(Tile.Suit.Bamboo, 5).toAssetKey())
    }

    @Test
    fun `test toAssetKey appends a red suffix for red fives`() {
        assertEquals("m5_red", RiichiTileTypes.redFive(Tile.Suit.Character).toAssetKey())
    }

    @Test
    fun `test toAssetKey maps honor tiles to their fixed English names`() {
        assertEquals("east", Tile.Honor.East.toAssetKey())
        assertEquals("white_dragon", Tile.Honor.White.toAssetKey())
    }

    @Test
    fun `test toAssetKey maps Taiwanese flower extensions`() {
        assertEquals("flower_spring", Tile.Extension(TaiwanTileTypes.SPRING).toAssetKey())
        assertEquals("flower_chrysanthemum", Tile.Extension(TaiwanTileTypes.CHRYSANTHEMUM).toAssetKey())
    }

    @Test
    fun `test all tile asset keys contain exactly 46 unique entries`() {
        assertEquals(46, ALL_TILE_ASSET_KEYS.size)
        assertEquals(
            ALL_TILE_ASSET_KEYS.size,
            ALL_TILE_ASSET_KEYS.toSet().size,
            "Asset keys must be unique, otherwise two tiles would collide onto the same predicate index.",
        )
        assertEquals(UNKNOWN_TILE_ASSET_KEY, ALL_TILE_ASSET_KEYS.last())
    }

    /** 驗證未知 Extension 安全回退至 unknown。 */
    @Test
    fun `unsupported extension tile types fall back to unknown`() {
        assertEquals(
            UNKNOWN_TILE_ASSET_KEY,
            Tile.Extension(TileTypeId.parse("example:missing")).toAssetKey(),
        )
    }

    /** 驗證合法 key 保持不變，缺失及非法 key 回退至 unknown。 */
    @Test
    fun `normalization preserves valid keys and rejects unsupported values`() {
        assertEquals("m5_red", "m5_red".normalizedTileAssetKey())
        assertEquals("flower_spring", "flower_spring".normalizedTileAssetKey())
        assertEquals(UNKNOWN_TILE_ASSET_KEY, null.normalizedTileAssetKey())
    }

    /** 驗證循環涵蓋完整清單，並讓缺失或非法 key 從第一張重新開始。 */
    @Test
    fun `next asset key cycles and invalid values restart at first tile`() {
        assertEquals(ALL_TILE_ASSET_KEYS[1], ALL_TILE_ASSET_KEYS.first().nextTileAssetKey())
        assertEquals(ALL_TILE_ASSET_KEYS.first(), ALL_TILE_ASSET_KEYS.last().nextTileAssetKey())
        assertEquals(ALL_TILE_ASSET_KEYS.first(), "invalid".nextTileAssetKey())
        assertEquals(ALL_TILE_ASSET_KEYS.first(), null.nextTileAssetKey())
    }
}
