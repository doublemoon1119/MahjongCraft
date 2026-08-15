package com.doublemoon1119.mahjongcraft.platform.fabric.text

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.registerBuiltInTileDisplayNames
import net.minecraft.text.LiteralTextContent
import net.minecraft.text.TranslatableTextContent
import kotlin.test.Test
import kotlin.test.assertEquals

/** [Tile.toDisplayText] 的單元測試類別；純函式，不需要 [net.minecraft.server.network.ServerPlayerEntity]。 */
class MahjongTileDisplayTextTest {

    private val registry = TileDisplayNameRegistryImpl().apply { registerBuiltInTileDisplayNames() }

    /** 驗證數牌顯示成「數值 + 花色」翻譯文字，帶正確的翻譯 key。 */
    @Test
    fun `numeric tile translates with suit key`() {
        val content = Tile.Numeric(Tile.Suit.Dot, 3).toDisplayText(registry).content as TranslatableTextContent
        assertEquals("mahjongcraft.message.tile_suit_dot", content.key)
    }

    /** 驗證字牌顯示成對應的固定翻譯 key。 */
    @Test
    fun `honor tile translates to its own key`() {
        val content = Tile.Honor.Red.toDisplayText(registry).content as TranslatableTextContent
        assertEquals("mahjongcraft.message.tile_honor_red", content.key)
    }

    /** 驗證已登記的內建赤五能查到對應翻譯 key。 */
    @Test
    fun `registered extension tile resolves translation key`() {
        val content = Tile.Extension(RiichiTileTypes.RED_FIVE_CHARACTER).toDisplayText(registry).content as TranslatableTextContent
        assertEquals("mahjongcraft.message.tile_red_five_character", content.key)
    }

    /** 驗證未登記的擴充牌種 fallback 顯示原始 [TileTypeId] 字串，不是丟例外或空字串。 */
    @Test
    fun `unregistered extension tile falls back to raw type id`() {
        val typeId = TileTypeId.parse("example:unregistered")
        val content = Tile.Extension(typeId).toDisplayText(registry).content as LiteralTextContent
        assertEquals("example:unregistered", content.string())
    }
}
