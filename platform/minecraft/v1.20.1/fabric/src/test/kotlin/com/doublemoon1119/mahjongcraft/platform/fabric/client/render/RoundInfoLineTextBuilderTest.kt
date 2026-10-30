package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoArgument
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLine
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineCodec
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineProviderDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoMessageDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistryImpl
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import net.minecraft.text.LiteralTextContent
import net.minecraft.text.Text
import net.minecraft.text.TranslatableTextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** 驗證局況資料列及新參數能組成可本地化 Minecraft 文字。 */
class RoundInfoLineTextBuilderTest {
    /** 第三方 provider 的牌與規則文字經編碼往返後仍能正確組字。 */
    @Test
    fun `third party provider parameters survive codec round trip and display`() {
        val tileTypeId = TileTypeId.parse("thirdparty:flower")
        val tile = Tile.Extension(tileTypeId)
        val displayNames = TileDisplayNameRegistryImpl().apply {
            register(TileDisplayNameDefinition(tileTypeId, "thirdparty.tile.flower"))
        }
        val assets = MinecraftTileAssetRegistryImpl().apply {
            register(MinecraftTileAssetDefinition(tileTypeId, "thirdparty_flower"))
        }
        val emojis = TileEmojiRegistryImpl().apply {
            register(TileEmojiDefinition("thirdparty_flower", "🀫"))
        }
        val displays = RoundInfoLineDisplayRegistryImpl().apply {
            register(RoundInfoLineDisplayDefinition("thirdparty:round_info", RoundInfoLineDisplay("thirdparty.round_info.line")))
            register(RoundInfoMessageDefinition("thirdparty:exchange_enabled", "thirdparty.round_info.exchange_enabled"))
            register(
                RoundInfoLineProviderDefinition(
                    "thirdparty:rule",
                    RoundInfoLineProvider {
                        listOf(
                            RoundInfoLine(
                                "thirdparty:round_info",
                                listOf(RoundInfoArgument.TileValue(tile), RoundInfoArgument.Message("thirdparty:exchange_enabled")),
                            ),
                        )
                    },
                ),
            )
        }
        val warnings = mutableListOf<String>()
        val builder = RoundInfoLineTextBuilder(displays, displayNames, assets, emojis) { warnings.add(it) }
        val providerLines = displays.buildLines("thirdparty:rule", FakeTableStateFactory.create())
        val decodedLines = RoundInfoLineCodec.decode(RoundInfoLineCodec.encode(providerLines))

        val text = builder.buildLineText(decodedLines.single())
        val content = assertIs<TranslatableTextContent>(text.content)
        assertEquals("thirdparty.round_info.line", content.key)
        val tileText = assertIs<Text>(content.args[0])
        assertEquals("🀫 ", assertIs<LiteralTextContent>(tileText.content).string())
        assertEquals("thirdparty.tile.flower", assertIs<TranslatableTextContent>(tileText.siblings.single().content).key)
        val messageText = assertIs<Text>(content.args[1])
        assertEquals("thirdparty.round_info.exchange_enabled", assertIs<TranslatableTextContent>(messageText.content).key)
        assertEquals(emptyList(), warnings)
    }

    /** 擴充牌沒有登記名稱與 emoji 時，顯示完整牌種 ID。 */
    @Test
    fun `an unnamed extension tile displays its full type id`() {
        val registry = RoundInfoLineDisplayRegistryImpl().apply {
            register(RoundInfoLineDisplayDefinition("example:line", RoundInfoLineDisplay("example.line")))
        }
        val builder = RoundInfoLineTextBuilder(
            registry,
            TileDisplayNameRegistryImpl(),
            MinecraftTileAssetRegistryImpl(),
            TileEmojiRegistryImpl(),
        ) { }
        val line = RoundInfoLine(
            "example:line",
            listOf(RoundInfoArgument.TileValue(Tile.Extension(TileTypeId.parse("thirdparty:unnamed")))),
        )

        val content = assertIs<TranslatableTextContent>(builder.buildLineText(line).content)

        assertEquals("thirdparty:unnamed", assertIs<LiteralTextContent>(assertIs<Text>(content.args.single()).content).string())
    }

    /** 數字、風位與已登記的訊息參數依各自種類組成文字。 */
    @Test
    fun `built in argument types retain their text forms`() {
        val registry = RoundInfoLineDisplayRegistryImpl().apply {
            register(RoundInfoLineDisplayDefinition("example:line", RoundInfoLineDisplay("example.line")))
            register(RoundInfoMessageDefinition("example:enabled", "example.enabled"))
        }
        val builder = RoundInfoLineTextBuilder(
            registry,
            TileDisplayNameRegistryImpl(),
            MinecraftTileAssetRegistryImpl(),
            TileEmojiRegistryImpl(),
        ) { }

        val content = assertIs<TranslatableTextContent>(
            builder.buildLineText(
                RoundInfoLine(
                    "example:line",
                    listOf(
                        RoundInfoArgument.Number(3),
                        RoundInfoArgument.WindValue(Wind.SOUTH),
                        RoundInfoArgument.Message("example:enabled"),
                    ),
                ),
            ).content,
        )

        assertEquals(3, content.args[0])
        assertEquals("mahjongcraft.message.tile_honor_south", assertIs<TranslatableTextContent>(assertIs<Text>(content.args[1]).content).key)
        assertEquals("example.enabled", assertIs<TranslatableTextContent>(assertIs<Text>(content.args[2]).content).key)
    }

    /** 未登記訊息 ID 與無法辨識參數各依契約退回，並且只警告一次。 */
    @Test
    fun `missing messages and unknown arguments warn once`() {
        val registry = RoundInfoLineDisplayRegistryImpl().apply {
            register(RoundInfoLineDisplayDefinition("example:line", RoundInfoLineDisplay("example.line")))
        }
        val warnings = mutableListOf<String>()
        val builder = RoundInfoLineTextBuilder(
            registry,
            TileDisplayNameRegistryImpl(),
            MinecraftTileAssetRegistryImpl(),
            TileEmojiRegistryImpl(),
        ) { warnings += it }
        val line = RoundInfoLine(
            "example:line",
            listOf(
                RoundInfoArgument.Message("thirdparty:missing"),
                RoundInfoArgument.Message("thirdparty:missing"),
                RoundInfoArgument.Unrecognized,
                RoundInfoArgument.Unrecognized,
            ),
        )

        val content = assertIs<TranslatableTextContent>(builder.buildLineText(line).content)
        assertEquals("thirdparty:missing", assertIs<LiteralTextContent>(assertIs<Text>(content.args[0]).content).string())
        assertEquals("?", assertIs<LiteralTextContent>(assertIs<Text>(content.args[2]).content).string())
        builder.buildLineText(line)
        builder.buildLineText(RoundInfoLine("example:line", listOf(RoundInfoArgument.Message("thirdparty:another_missing"))))

        assertEquals(3, warnings.size)
        assertEquals(true, warnings.any { "thirdparty:missing" in it })
        assertEquals(true, warnings.any { "thirdparty:another_missing" in it })
        assertEquals(true, warnings.any { "unrecognized" in it })
    }
}
