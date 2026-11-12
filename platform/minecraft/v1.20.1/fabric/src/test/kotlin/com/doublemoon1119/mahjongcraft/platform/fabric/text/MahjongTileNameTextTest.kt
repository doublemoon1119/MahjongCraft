package com.doublemoon1119.mahjongcraft.platform.fabric.text

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileValues
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BundledMinecraftMahjongExtensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.RiichiTranslationKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistryImpl
import net.minecraft.text.LiteralTextContent
import net.minecraft.text.TranslatableTextContent
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證不帶 emoji 的牌名文字。 */
class MahjongTileNameTextTest {
    /** 內建規則登記的牌名。 */
    private val displayNames = TileDisplayNameRegistryImpl().apply { BundledMinecraftMahjongExtensions.all.forEach { it.registerTileDisplayNames(this) } }

    /** 數牌與字牌使用共用牌名，數牌帶數字。 */
    @Test
    fun `numeric and honor tiles use the shared names`() {
        val numeric = Tile.Numeric(Tile.Suit.Character, 3).toNameText(displayNames).content as TranslatableTextContent
        assertEquals(MinecraftMessageKeys.TILE_SUIT_CHARACTER, numeric.key)
        assertEquals(listOf<Any>(3), numeric.args.toList())
        assertEquals(MinecraftMessageKeys.TILE_HONOR_EAST, (Tile.Honor.East.toNameText(displayNames).content as TranslatableTextContent).key)
    }

    /** 花牌使用規則登記的牌名，不加 emoji。 */
    @Test
    fun `flower tiles use the rule's names without emoji`() {
        val name = Tile.Extension(TaiwanTileTypes.SPRING).toNameText(displayNames)

        assertEquals(displayNames.find(TaiwanTileTypes.SPRING), (name.content as TranslatableTextContent).key)
        assertEquals(emptyList(), name.siblings)
    }

    /** 帶外觀樣式的牌面使用樣式名稱，例如赤五；樣式沒有名稱時使用基本牌面的名稱。 */
    @Test
    fun `appearance names take precedence over the base tile name`() {
        val redFive = TileFaceView(Tile.Numeric(Tile.Suit.Dot, 5), appearanceId = RiichiTileValues.RED_FIVE_APPEARANCE_ID).toNameText(displayNames)
        val unnamedStyle = TileFaceView(Tile.Numeric(Tile.Suit.Dot, 4), appearanceId = RiichiTileValues.RED_FIVE_APPEARANCE_ID).toNameText(displayNames)

        assertEquals(RiichiTranslationKeys.TILE_RED_FIVE_DOT, (redFive.content as TranslatableTextContent).key)
        assertEquals(MinecraftMessageKeys.TILE_SUIT_DOT, (unnamedStyle.content as TranslatableTextContent).key)
    }

    /** 沒有登記名稱的擴充牌顯示牌種識別碼。 */
    @Test
    fun `unregistered extension tiles show their type id`() {
        val name = Tile.Extension(TileTypeId.parse("example:unknown")).toNameText(displayNames)

        assertEquals("example:unknown", (name.content as LiteralTextContent).string())
    }
}
