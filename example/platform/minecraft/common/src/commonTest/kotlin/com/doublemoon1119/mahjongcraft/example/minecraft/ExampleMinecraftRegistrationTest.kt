package com.doublemoon1119.mahjongcraft.example.minecraft

import com.doublemoon1119.mahjongcraft.example.ai.ExampleAiStrategyIds
import com.doublemoon1119.mahjongcraft.example.minecraft.presentation.ExampleMinecraftPresentationIds
import com.doublemoon1119.mahjongcraft.example.minecraft.text.ExampleMinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationConfig
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.preparation.RoundPreparationDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationValue
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomConfigSubmission
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 檢查第三方入口將既有範例功能接到平台文字登記，不依賴原生世界。 */
class ExampleMinecraftRegistrationTest {
    /** 實際設定 registry 隨換牌開關增減準備欄位，並接受範例畫面可提交的完整設定。 */
    @Test
    fun `room submission accepts pairing fields and optional preparation`() {
        val extension = ExampleMinecraftMahjongExtension()
        val modules = MahjongModuleRegistryImpl().apply(extension::registerRuleModules).apply { freeze() }
        val resolvers = RoundPreparationResolverRegistry().apply(extension::registerRoundPreparationResolvers).apply { freeze() }
        val presentations = GameConfigPresentationRegistryImpl(resolvers).apply(extension::registerGameConfigPresentations).apply { freeze() }
        val current = GameConfig(ExamplePairingRuleConfig())
        val before = assertNotNull(presentations.find(ExamplePairingIds.RULE_MODULE, current.ruleConfig))
        assertNull(current.flowConfig.roundPreparation)
        val toggle = before.fields.single { it.id == ExampleMinecraftPresentationIds.EXCHANGE_FIELD }
        val enabledDraft = assertNotNull(toggle.update)(current, GameConfigPresentationValue.BooleanValue(true))
        val enabled = enabledDraft.copy(
            flowConfig = presentations.normalizeFlowConfig(ExamplePairingIds.RULE_MODULE, enabledDraft.ruleConfig, enabledDraft.flowConfig),
        )
        val after = assertNotNull(presentations.find(ExamplePairingIds.RULE_MODULE, enabled.ruleConfig))
        assertEquals(before.fields.size + 1, after.fields.size)
        assertEquals(RoundPreparationConfig(30), enabled.flowConfig.roundPreparation)
        assertEquals(RoomConfigSubmission.Result.Accepted(enabled), RoomConfigSubmission.review(enabled, current, presentations, modules))
        val edited = enabled.copy(
            ruleConfig = (enabled.ruleConfig as ExamplePairingRuleConfig).copy(paymentPerPlayer = 75, roundCount = 8),
            flowConfig = enabled.flowConfig.copy(roundPreparation = RoundPreparationConfig(45)),
        )
        assertEquals(RoomConfigSubmission.Result.Accepted(edited), RoomConfigSubmission.review(edited, enabled, presentations, modules))
        val disabled = edited.copy(ruleConfig = (edited.ruleConfig as ExamplePairingRuleConfig).copy(exchangeEnabled = false))
        val normalized = disabled.copy(
            flowConfig = presentations.normalizeFlowConfig(ExamplePairingIds.RULE_MODULE, disabled.ruleConfig, disabled.flowConfig),
        )
        assertNull(normalized.flowConfig.roundPreparation)
        assertEquals(RoomConfigSubmission.Result.Accepted(normalized), RoomConfigSubmission.review(normalized, edited, presentations, modules))
        assertEquals(RoomConfigSubmission.Result.Invalid, RoomConfigSubmission.review(disabled, edited, presentations, modules))
    }

    /** 規則、牌種、AI、宣告、換牌與流局都使用範例自己的翻譯鍵。 */
    @Test
    fun `extension registers names and public interactions`() {
        val extension = ExampleMinecraftMahjongExtension()
        val rules = RuleModuleDisplayNameRegistryImpl().apply(extension::registerRuleModuleDisplayNames)
        val tiles = TileDisplayNameRegistryImpl().apply(extension::registerTileDisplayNames)
        val strategies = AiStrategyDisplayNameRegistryImpl().apply(extension::registerAiStrategyDisplayNames)
        val actions = GameActionVocabularyRegistryImpl().apply(extension::registerGameActionVocabulary)
        val indicators = PublicPlayerIndicatorDisplayRegistryImpl().apply(extension::registerPublicPlayerIndicatorDisplays)
        val preparation = RoundPreparationDisplayNameRegistryImpl().apply(extension::registerRoundPreparationDisplayNames)
        val draws = ExhaustiveDrawReasonDisplayNameRegistryImpl().apply(extension::registerExhaustiveDrawReasonDisplayNames)

        assertEquals(ExampleMinecraftMessageKeys.RULE_NAME, rules.find(ExamplePairingIds.RULE_MODULE))
        assertEquals(ExampleMinecraftMessageKeys.TILE_INVERTED_ONE_MAN, tiles.find(ExamplePairingIds.INVERTED_ONE_MAN))
        assertEquals(ExampleMinecraftMessageKeys.AI_NAME, strategies.find(ExampleAiStrategyIds.TSUMOGIRI))
        val declaration = assertNotNull(actions.find(ExamplePairingIds.RULE_MODULE, ExamplePairingIds.DECLARE_ACTION))
        assertEquals(ExampleMinecraftMessageKeys.DECLARE, declaration.labelKey)
        assertEquals(ExampleMinecraftMessageKeys.DECLARE_DESCRIPTION, declaration.descriptionKey)
        assertEquals(ExampleMinecraftMessageKeys.DECLARE_CARD_BODY, declaration.cardBodyKey)
        assertEquals(ExampleMinecraftMessageKeys.DECLARE_CARD_HINT, declaration.cardHintKey)
        assertEquals(ExampleMinecraftMessageKeys.DECLARED, indicators.find(ExamplePairingIds.DECLARATION_INDICATOR)?.translationKey)
        assertEquals(ExampleMinecraftMessageKeys.EXCHANGE, preparation.find(ExamplePairingIds.EXCHANGE_STEP))
        assertEquals(ExampleMinecraftMessageKeys.EXHAUSTIVE_DRAW, draws.find(ExamplePairingIds.EXHAUSTIVE_DRAW))
    }
}
