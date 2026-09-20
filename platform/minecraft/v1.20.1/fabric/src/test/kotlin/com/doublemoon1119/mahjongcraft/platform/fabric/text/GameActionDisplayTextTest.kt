package com.doublemoon1119.mahjongcraft.platform.fabric.text

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.BuiltInGameActionIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabulary
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.registerBuiltInGameActionVocabulary
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.registerBuiltInRiichiReasons
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.registerRiichiGameActionVocabulary
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.registerBuiltInTileAssets
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.registerBuiltInTileDisplayNames
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.registerBuiltInTileEmojis
import net.minecraft.text.LiteralTextContent
import net.minecraft.text.Text
import net.minecraft.text.TranslatableTextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** [GameAction.toDisplayText] 的單元測試類別；純函式，不需要 [net.minecraft.server.network.ServerPlayerEntity]。 */
class GameActionDisplayTextTest {

    private val displayNameRegistry = TileDisplayNameRegistryImpl().apply { registerBuiltInTileDisplayNames() }
    private val ruleModuleId = BuiltInRuleModuleIds.RIICHI
    private val actionVocabulary = GameActionVocabularyRegistryImpl().apply {
        registerBuiltInGameActionVocabulary()
        registerRiichiGameActionVocabulary()
    }
    private val exhaustiveDrawReasonDisplayNameRegistry = ExhaustiveDrawReasonDisplayNameRegistryImpl().apply { registerBuiltInRiichiReasons() }
    private val assetRegistry = MinecraftTileAssetRegistryImpl().apply { registerBuiltInTileAssets() }
    private val emojiRegistry = TileEmojiRegistryImpl().apply { registerBuiltInTileEmojis() }
    private val fiveDot = Tile.Numeric(Tile.Suit.Dot, 5)

    /** 驗證帶牌面的動作（例如打出）組出「動作 + 牌面」文字，牌面本身也正確解析（含 emoji 前綴）。 */
    @Test
    fun `discard action includes resolved tile as argument`() {
        val content = GameAction.Discard(Uuid.random())
            .toDisplayText(fiveDot, ruleModuleId, actionVocabulary, displayNameRegistry, assetRegistry, emojiRegistry, exhaustiveDrawReasonDisplayNameRegistry).content as TranslatableTextContent
        assertEquals("mahjongcraft.message.game_action_discard", content.key)
        val tileArg = content.args.single() as Text
        assertEquals("🀝 ", (tileArg.content as LiteralTextContent).string())
        val tileArgContent = tileArg.siblings.single().content as TranslatableTextContent
        assertEquals("mahjongcraft.message.tile_suit_dot", tileArgContent.key)
    }

    /** 驗證不涉及特定牌面的動作（自摸）不需要 referenceTile 也能組出文字。 */
    @Test
    fun `tsumo action does not require a reference tile`() {
        val content = GameAction.Tsumo.toDisplayText(
            null,
            ruleModuleId,
            actionVocabulary,
            displayNameRegistry,
            assetRegistry,
            emojiRegistry,
            exhaustiveDrawReasonDisplayNameRegistry,
        ).content as TranslatableTextContent
        assertEquals("mahjongcraft.message.game_action_tsumo", content.key)
    }

    /** 驗證三種槓型各自對應不同的翻譯 key，不會被混淆成同一種槓。 */
    @Test
    fun `each kan type maps to its own key`() {
        val openKey = (
            GameAction.Kan(GameAction.KanType.OPEN_KAN, Uuid.random(), emptyList())
                .toDisplayText(fiveDot, ruleModuleId, actionVocabulary, displayNameRegistry, assetRegistry, emojiRegistry, exhaustiveDrawReasonDisplayNameRegistry).content as TranslatableTextContent
            ).key
        val closedKey = (
            GameAction.Kan(GameAction.KanType.CLOSED_KAN, Uuid.random(), emptyList())
                .toDisplayText(fiveDot, ruleModuleId, actionVocabulary, displayNameRegistry, assetRegistry, emojiRegistry, exhaustiveDrawReasonDisplayNameRegistry).content as TranslatableTextContent
            ).key
        val addedKey = (
            GameAction.Kan(GameAction.KanType.ADDED_KAN, Uuid.random(), emptyList())
                .toDisplayText(fiveDot, ruleModuleId, actionVocabulary, displayNameRegistry, assetRegistry, emojiRegistry, exhaustiveDrawReasonDisplayNameRegistry).content as TranslatableTextContent
            ).key

        assertEquals("mahjongcraft.message.game_action_kan_open", openKey)
        assertEquals("mahjongcraft.message.game_action_kan_closed", closedKey)
        assertEquals("mahjongcraft.message.game_action_kan_added", addedKey)
    }

    /** 驗證九種九牌顯示成專屬文字，不是通用流局 fallback。 */
    @Test
    fun `kyuushu kyuuhai resolves its dedicated key`() {
        val content = GameAction.ExhaustiveDraw(RiichiExhaustiveDrawReason.KyuushuKyuuhai)
            .toDisplayText(null, ruleModuleId, actionVocabulary, displayNameRegistry, assetRegistry, emojiRegistry, exhaustiveDrawReasonDisplayNameRegistry).content as TranslatableTextContent
        assertEquals("mahjongcraft.message.game_action_kyuushu_kyuuhai", content.key)
    }

    /** 驗證規則登記自己的用語時覆寫中立預設，沒有登記的規則沿用預設。 */
    @Test
    fun `a rule overrides the default wording of an action`() {
        actionVocabulary.register(
            BuiltInRuleModuleIds.TAIWAN,
            BuiltInGameActionIds.RON,
            GameActionVocabulary(labelKey = "example.hud.win", messageKey = "example.message.win"),
        )

        val taiwanKey = (
            GameAction.Ron(Uuid.random())
                .toDisplayText(fiveDot, BuiltInRuleModuleIds.TAIWAN, actionVocabulary, displayNameRegistry, assetRegistry, emojiRegistry, exhaustiveDrawReasonDisplayNameRegistry).content as TranslatableTextContent
            ).key
        val riichiKey = (
            GameAction.Ron(Uuid.random())
                .toDisplayText(fiveDot, ruleModuleId, actionVocabulary, displayNameRegistry, assetRegistry, emojiRegistry, exhaustiveDrawReasonDisplayNameRegistry).content as TranslatableTextContent
            ).key

        assertEquals("example.message.win", taiwanKey)
        assertEquals("mahjongcraft.message.game_action_ron", riichiKey)
    }

    /** 驗證非九種九牌的流局原因退回通用 fallback 文字。 */
    @Test
    fun `other exhaustive draw reasons fall back to the generic key`() {
        val otherReason = object : ExhaustiveDrawReason {
            override val id: String = "test:other_draw"
        }
        val content = GameAction.ExhaustiveDraw(otherReason)
            .toDisplayText(null, ruleModuleId, actionVocabulary, displayNameRegistry, assetRegistry, emojiRegistry, exhaustiveDrawReasonDisplayNameRegistry).content as TranslatableTextContent
        assertEquals("mahjongcraft.message.game_action_exhaustive_draw", content.key)
    }
}
