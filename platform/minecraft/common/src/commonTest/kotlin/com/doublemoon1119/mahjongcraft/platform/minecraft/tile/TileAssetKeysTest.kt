package com.doublemoon1119.mahjongcraft.platform.minecraft.tile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BundledMinecraftMahjongExtensions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TileAssetKeysTest {

    /** 只登記內建 extension 映射並凍結的 registry；供本檔所有測試共用。 */
    private val registry: MinecraftTileAssetRegistry = MinecraftTileAssetRegistryImpl().apply {
        BundledMinecraftMahjongExtensions.all.forEach { it.registerTileAssets(this) }
        freeze()
    }

    @Test
    fun `test toAssetKey maps a plain numeric tile to suit letter plus value`() {
        assertEquals("m1", Tile.Numeric(Tile.Suit.Character, 1).toAssetKey(registry))
        assertEquals("p9", Tile.Numeric(Tile.Suit.Dot, 9).toAssetKey(registry))
        assertEquals("s5", Tile.Numeric(Tile.Suit.Bamboo, 5).toAssetKey(registry))
    }

    @Test
    fun `test toAssetKey appends a red suffix for red fives`() {
        assertEquals("m5_red", RiichiTileTypes.redFive(Tile.Suit.Character).toAssetKey(registry))
    }

    @Test
    fun `test toAssetKey maps honor tiles to their fixed English names`() {
        assertEquals("east", Tile.Honor.East.toAssetKey(registry))
        assertEquals("white_dragon", Tile.Honor.White.toAssetKey(registry))
    }

    @Test
    fun `test toAssetKey maps Taiwanese flower extensions`() {
        assertEquals("flower_spring", Tile.Extension(TaiwanTileTypes.SPRING).toAssetKey(registry))
        assertEquals("flower_chrysanthemum", Tile.Extension(TaiwanTileTypes.CHRYSANTHEMUM).toAssetKey(registry))
    }

    /** 基本牌在前、擴充牌種依登記順序接在後面，最後是 unknown；內建 extension 登記後共 46 個不重複的 key。 */
    @Test
    fun `all tile asset keys list standard tiles, registered tiles, then unknown`() {
        val keys = registry.allTileAssetKeys()

        assertEquals(46, keys.size)
        assertEquals(keys.size, keys.toSet().size, "Asset keys must be unique.")
        assertEquals(STANDARD_TILE_ASSET_KEYS, keys.take(STANDARD_TILE_ASSET_KEYS.size))
        assertEquals(registry.registeredAssetKeys.toList(), keys.drop(STANDARD_TILE_ASSET_KEYS.size).dropLast(1))
        assertEquals(UNKNOWN_TILE_ASSET_KEY, keys.last())
    }

    /** 第三方登記的牌種也列入清單，接在內建擴充牌種之後。 */
    @Test
    fun `all tile asset keys include third-party registrations`() {
        val thirdPartyRegistry = MinecraftTileAssetRegistryImpl().apply {
            BundledMinecraftMahjongExtensions.all.forEach { it.registerTileAssets(this) }
            register(TileTypeId.parse("example:animal/cat"), "animal_cat")
            freeze()
        }

        assertEquals(listOf("animal_cat", UNKNOWN_TILE_ASSET_KEY), thirdPartyRegistry.allTileAssetKeys().takeLast(2))
    }

    /** registry 凍結前擴充牌種尚未確定，不能取得清單。 */
    @Test
    fun `all tile asset keys require a frozen registry`() {
        assertFailsWith<IllegalStateException> { MinecraftTileAssetRegistryImpl().allTileAssetKeys() }
    }

    /** 驗證未知 Extension 安全回退至 unknown。 */
    @Test
    fun `unsupported extension tile types fall back to unknown`() {
        assertEquals(
            UNKNOWN_TILE_ASSET_KEY,
            Tile.Extension(TileTypeId.parse("example:missing")).toAssetKey(registry),
        )
    }

    /** 驗證已在 registry 註冊但不屬於固定內建清單的第三方 asset key 仍可正確解析。 */
    @Test
    fun `third-party registered extension resolves through the provided registry`() {
        val thirdPartyId = TileTypeId.parse("example:animal/cat")
        val thirdPartyRegistry = MinecraftTileAssetRegistryImpl().apply {
            BundledMinecraftMahjongExtensions.all.forEach { it.registerTileAssets(this) }
            register(thirdPartyId, "animal_cat")
        }

        assertEquals("animal_cat", Tile.Extension(thirdPartyId).toAssetKey(thirdPartyRegistry))
    }

    /** 驗證合法 key 保持不變，缺失及非法 key 回退至 unknown。 */
    @Test
    fun `normalization preserves valid keys and rejects unsupported values`() {
        assertEquals("m5_red", "m5_red".normalizedTileAssetKey(registry))
        assertEquals("flower_spring", "flower_spring".normalizedTileAssetKey(registry))
        assertEquals(UNKNOWN_TILE_ASSET_KEY, null.normalizedTileAssetKey(registry))
        assertEquals(UNKNOWN_TILE_ASSET_KEY, "example:missing".normalizedTileAssetKey(registry))
    }

    /** 驗證已在 registry 註冊的第三方 asset key 正規化時保持不變。 */
    @Test
    fun `normalization preserves third-party asset keys registered at runtime`() {
        val thirdPartyId = TileTypeId.parse("example:animal/cat")
        val thirdPartyRegistry = MinecraftTileAssetRegistryImpl().apply {
            BundledMinecraftMahjongExtensions.all.forEach { it.registerTileAssets(this) }
            register(thirdPartyId, "animal_cat")
        }

        assertEquals("animal_cat", "animal_cat".normalizedTileAssetKey(thirdPartyRegistry))
    }

    /** 驗證循環涵蓋完整清單，並讓缺失或非法 key 從第一張重新開始。 */
    @Test
    fun `next asset key cycles and invalid values restart at first tile`() {
        val keys = registry.allTileAssetKeys()

        assertEquals(keys[1], keys.first().nextTileAssetKey(registry))
        assertEquals(keys.first(), keys.last().nextTileAssetKey(registry))
        assertEquals(keys.first(), "invalid".nextTileAssetKey(registry))
        assertEquals(keys.first(), null.nextTileAssetKey(registry))
    }
}
