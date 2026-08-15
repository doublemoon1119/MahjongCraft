package com.doublemoon1119.mahjongcraft.platform.fabric.text

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.registerBuiltInTileAssets
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.registerBuiltInTileDisplayNames
import net.minecraft.text.LiteralTextContent
import net.minecraft.text.TranslatableTextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [Tile.toDisplayText] 的單元測試類別；純函式，不需要 [net.minecraft.server.network.ServerPlayerEntity]。 */
class MahjongTileDisplayTextTest {

    private val displayNameRegistry = TileDisplayNameRegistryImpl().apply { registerBuiltInTileDisplayNames() }
    private val assetRegistry = MinecraftTileAssetRegistryImpl().apply { registerBuiltInTileAssets() }

    /** 驗證數牌顯示成「牌面 emoji + 數值/花色」翻譯文字，帶正確的翻譯 key 與 emoji 前綴。 */
    @Test
    fun `numeric tile translates with suit key and emoji prefix`() {
        val text = Tile.Numeric(Tile.Suit.Dot, 3).toDisplayText(displayNameRegistry, assetRegistry)
        assertEquals("🀛 ", (text.content as LiteralTextContent).string())
        val translatable = text.siblings.single().content as TranslatableTextContent
        assertEquals("mahjongcraft.message.tile_suit_dot", translatable.key)
    }

    /** 驗證字牌顯示成對應的固定翻譯 key，並帶對應的 emoji 前綴。 */
    @Test
    fun `honor tile translates to its own key with emoji prefix`() {
        val text = Tile.Honor.Red.toDisplayText(displayNameRegistry, assetRegistry)
        assertEquals("🀄 ", (text.content as LiteralTextContent).string())
        val translatable = text.siblings.single().content as TranslatableTextContent
        assertEquals("mahjongcraft.message.tile_honor_red", translatable.key)
    }

    /** 驗證已登記的內建赤五能查到對應翻譯 key，並帶赤五專屬的 emoji 前綴。 */
    @Test
    fun `registered extension tile resolves translation key with emoji prefix`() {
        val text = Tile.Extension(RiichiTileTypes.RED_FIVE_CHARACTER).toDisplayText(displayNameRegistry, assetRegistry)
        assertEquals("🀬 ", (text.content as LiteralTextContent).string())
        val translatable = text.siblings.single().content as TranslatableTextContent
        assertEquals("mahjongcraft.message.tile_red_five_character", translatable.key)
    }

    /** 驗證未登記的擴充牌種 fallback 顯示原始 [TileTypeId] 字串，並退回未知牌 emoji 前綴。 */
    @Test
    fun `unregistered extension tile falls back to raw type id with unknown emoji prefix`() {
        val typeId = TileTypeId.parse("example:unregistered")
        val text = Tile.Extension(typeId).toDisplayText(displayNameRegistry, assetRegistry)
        assertEquals("🀯 ", (text.content as LiteralTextContent).string())
        val fallback = text.siblings.single().content as LiteralTextContent
        assertEquals("example:unregistered", fallback.string())
    }

    /** 驗證 asset key 沒有對應 emoji（例如未登記到素材 registry 的第三方牌種）時不強行湊前綴。 */
    @Test
    fun `tile with unmapped asset key has no emoji prefix`() {
        val typeId = TileTypeId.parse("example:custom")
        val registryWithCustomAsset = MinecraftTileAssetRegistryImpl().apply {
            registerBuiltInTileAssets()
            register(typeId, "custom_third_party_key")
        }

        val text = Tile.Extension(typeId).toDisplayText(displayNameRegistry, registryWithCustomAsset)
        assertTrue(text.siblings.isEmpty())
        assertEquals("example:custom", (text.content as LiteralTextContent).string())
    }
}
