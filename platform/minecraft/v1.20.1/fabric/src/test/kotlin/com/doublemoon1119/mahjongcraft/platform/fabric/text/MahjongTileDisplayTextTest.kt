package com.doublemoon1119.mahjongcraft.platform.fabric.text

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileValues
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BuiltInMinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BundledMinecraftMahjongExtensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistryImpl
import net.minecraft.text.LiteralTextContent
import net.minecraft.text.TranslatableTextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [Tile.toDisplayText] 的單元測試類別；純函式，不需要 [net.minecraft.server.network.ServerPlayerEntity]。 */
class MahjongTileDisplayTextTest {

    private val displayNameRegistry = TileDisplayNameRegistryImpl().apply { BundledMinecraftMahjongExtensions.all.forEach { it.registerTileDisplayNames(this) } }
    private val assetRegistry = MinecraftTileAssetRegistryImpl().apply { BundledMinecraftMahjongExtensions.all.forEach { it.registerTileAssets(this) } }
    private val emojiRegistry = TileEmojiRegistryImpl().apply {
        BuiltInMinecraftMahjongExtension.registerTileEmojis(this)
        BundledMinecraftMahjongExtensions.all.forEach { it.registerTileEmojis(this) }
    }

    /** 驗證數牌顯示成「牌面 emoji + 數值/花色」翻譯文字，帶正確的翻譯 key 與 emoji 前綴。 */
    @Test
    fun `numeric tile translates with suit key and emoji prefix`() {
        val text = Tile.Numeric(Tile.Suit.Dot, 3).toDisplayText(displayNameRegistry, assetRegistry, emojiRegistry)
        assertEquals("🀛 ", (text.content as LiteralTextContent).string())
        val translatable = text.siblings.single().content as TranslatableTextContent
        assertEquals("mahjongcraft.message.tile_suit_dot", translatable.key)
    }

    /** 驗證字牌顯示成對應的固定翻譯 key，並帶對應的 emoji 前綴。 */
    @Test
    fun `honor tile translates to its own key with emoji prefix`() {
        val text = Tile.Honor.Red.toDisplayText(displayNameRegistry, assetRegistry, emojiRegistry)
        assertEquals("🀄 ", (text.content as LiteralTextContent).string())
        val translatable = text.siblings.single().content as TranslatableTextContent
        assertEquals("mahjongcraft.message.tile_honor_red", translatable.key)
    }

    /** 驗證帶赤五外觀的五萬查到赤五的翻譯 key，並帶赤五專屬的 emoji 前綴。 */
    @Test
    fun `registered tile style resolves translation key with emoji prefix`() {
        val face = TileFaceView(Tile.Numeric(Tile.Suit.Character, 5), appearanceId = RiichiTileValues.RED_FIVE_APPEARANCE_ID)
        val text = face.toDisplayText(displayNameRegistry, assetRegistry, emojiRegistry)
        assertEquals("🀬 ", (text.content as LiteralTextContent).string())
        val translatable = text.siblings.single().content as TranslatableTextContent
        assertEquals("mahjongcraft.riichi.tile.red_five_character", translatable.key)
    }

    /** 驗證外觀樣式沒有登記名稱時，使用基本牌面的名稱與 emoji。 */
    @Test
    fun `unregistered tile style falls back to the base face`() {
        val face = TileFaceView(Tile.Numeric(Tile.Suit.Character, 4), appearanceId = RiichiTileValues.RED_FIVE_APPEARANCE_ID)
        val text = face.toDisplayText(displayNameRegistry, assetRegistry, emojiRegistry)
        val translatable = text.siblings.single().content as TranslatableTextContent
        assertEquals("mahjongcraft.message.tile_suit_character", translatable.key)
    }

    /** 驗證未登記的擴充牌種 fallback 顯示原始 [TileTypeId] 字串，並退回未知牌 emoji 前綴。 */
    @Test
    fun `unregistered extension tile falls back to raw type id with unknown emoji prefix`() {
        val typeId = TileTypeId.parse("example:unregistered")
        val text = Tile.Extension(typeId).toDisplayText(displayNameRegistry, assetRegistry, emojiRegistry)
        assertEquals("🀯 ", (text.content as LiteralTextContent).string())
        val fallback = text.siblings.single().content as LiteralTextContent
        assertEquals("example:unregistered", fallback.string())
    }

    /** 驗證 asset key 沒有對應 emoji（例如未登記到 emoji registry 的第三方牌種）時不強行湊前綴。 */
    @Test
    fun `tile with unmapped asset key has no emoji prefix`() {
        val typeId = TileTypeId.parse("example:custom")
        val registryWithCustomAsset = MinecraftTileAssetRegistryImpl().apply {
            BundledMinecraftMahjongExtensions.all.forEach { it.registerTileAssets(this) }
            register(MinecraftTileAssetDefinition(typeId, "custom_third_party_key"))
        }

        val text = Tile.Extension(typeId).toDisplayText(displayNameRegistry, registryWithCustomAsset, emojiRegistry)
        assertTrue(text.siblings.isEmpty())
        assertEquals("example:custom", (text.content as LiteralTextContent).string())
    }
}
