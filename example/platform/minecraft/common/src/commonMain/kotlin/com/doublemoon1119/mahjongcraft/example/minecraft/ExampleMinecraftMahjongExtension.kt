package com.doublemoon1119.mahjongcraft.example.minecraft

import com.doublemoon1119.mahjongcraft.example.ExampleMahjongExtension
import com.doublemoon1119.mahjongcraft.example.ai.ExampleAiStrategyIds
import com.doublemoon1119.mahjongcraft.example.minecraft.presentation.ExamplePairingTableProps
import com.doublemoon1119.mahjongcraft.example.minecraft.presentation.examplePairingCatalogue
import com.doublemoon1119.mahjongcraft.example.minecraft.presentation.registerExamplePairingRoundInfo
import com.doublemoon1119.mahjongcraft.example.minecraft.presentation.registerExamplePairingSettlementTemplates
import com.doublemoon1119.mahjongcraft.example.minecraft.room.examplePairingGameConfigPresentation
import com.doublemoon1119.mahjongcraft.example.minecraft.text.ExampleMinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.example.minecraft.tile.registerExampleTileAssets
import com.doublemoon1119.mahjongcraft.example.minecraft.tile.registerExampleTileEmojis
import com.doublemoon1119.mahjongcraft.example.minecraft.tile.registerExampleTileLabels
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabulary
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.AiStrategyDisplayNameRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.ExhaustiveDrawReasonDisplayNameRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.GameActionVocabularyRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.GameConfigPresentationRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.MinecraftTileAssetRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.PublicPlayerIndicatorDisplayRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.RoundInfoLineDisplayRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.RuleCatalogueRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.RuleDecisionDisplayNameRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.RuleModuleDisplayNameRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.TablePropDescriberRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.TileDisplayNameRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.TileEmojiRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.TileLabelRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.WinSettlementPresentationTemplateRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.RuleDecisionDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropDescriberDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameDefinition

/** 結合共用範例與 Minecraft 呈現的入口，供各版本與 loader 的轉接層繼承。 */
open class ExampleMinecraftMahjongExtension :
    ExampleMahjongExtension(),
    MinecraftMahjongExtension {
    override fun registerGameConfigPresentations(registry: GameConfigPresentationRegistrar) {
        registry.register(examplePairingGameConfigPresentation())
    }

    override fun registerRuleCatalogues(registry: RuleCatalogueRegistrar) {
        registry.register(examplePairingCatalogue())
    }

    override fun registerWinSettlementPresentationTemplates(registry: WinSettlementPresentationTemplateRegistrar) {
        registerExamplePairingSettlementTemplates(registry)
    }

    override fun registerRoundInfoPresentations(registry: RoundInfoLineDisplayRegistrar) {
        registerExamplePairingRoundInfo(registry)
    }

    override fun registerTileAssets(registry: MinecraftTileAssetRegistrar) {
        registerExampleTileAssets(registry)
    }

    override fun registerTileLabels(registry: TileLabelRegistrar) {
        registerExampleTileLabels(registry)
    }

    override fun registerTileEmojis(registry: TileEmojiRegistrar) {
        registerExampleTileEmojis(registry)
    }

    override fun registerRuleModuleDisplayNames(registry: RuleModuleDisplayNameRegistrar) {
        registry.register(RuleModuleDisplayNameDefinition(ExamplePairingIds.RULE_MODULE, ExampleMinecraftMessageKeys.RULE_NAME))
    }

    override fun registerTileDisplayNames(registry: TileDisplayNameRegistrar) {
        registry.register(TileDisplayNameDefinition(ExamplePairingIds.INVERTED_ONE_MAN, ExampleMinecraftMessageKeys.TILE_INVERTED_ONE_MAN))
    }

    override fun registerAiStrategyDisplayNames(registry: AiStrategyDisplayNameRegistrar) {
        registry.register(AiStrategyDisplayNameDefinition(ExampleAiStrategyIds.TSUMOGIRI, ExampleMinecraftMessageKeys.AI_NAME))
    }

    override fun registerGameActionVocabulary(registry: GameActionVocabularyRegistrar) {
        registry.register(
            GameActionVocabularyDefinition(
                ruleModuleId = ExamplePairingIds.RULE_MODULE,
                actionId = ExamplePairingIds.REVEAL_HAND_ACTION,
                vocabulary = GameActionVocabulary(
                    labelKey = ExampleMinecraftMessageKeys.REVEAL_HAND,
                    order = 50,
                    descriptionKey = ExampleMinecraftMessageKeys.REVEAL_HAND_DESCRIPTION,
                    cardBodyKey = ExampleMinecraftMessageKeys.REVEAL_HAND_CARD_BODY,
                    cardHintKey = ExampleMinecraftMessageKeys.REVEAL_HAND_CARD_HINT,
                ),
            ),
        )
    }

    override fun registerPublicPlayerIndicatorDisplays(registry: PublicPlayerIndicatorDisplayRegistrar) {
        registry.register(
            PublicPlayerIndicatorDisplayDefinition(
                id = ExamplePairingIds.HAND_REVEALED_INDICATOR,
                display = PublicPlayerIndicatorDisplay(ExampleMinecraftMessageKeys.HAND_REVEALED),
            ),
        )
    }

    override fun registerRuleDecisionDisplayNames(registry: RuleDecisionDisplayNameRegistrar) {
        registry.register(RuleDecisionDisplayNameDefinition(ExamplePairingIds.EXCHANGE_STEP, ExampleMinecraftMessageKeys.EXCHANGE))
    }

    override fun registerExhaustiveDrawReasonDisplayNames(registry: ExhaustiveDrawReasonDisplayNameRegistrar) {
        registry.register(ExhaustiveDrawReasonDisplayNameDefinition(ExamplePairingIds.EXHAUSTIVE_DRAW, ExampleMinecraftMessageKeys.EXHAUSTIVE_DRAW))
    }

    override fun registerTablePropDescribers(registry: TablePropDescriberRegistrar) {
        registry.register(TablePropDescriberDefinition(ExamplePairingIds.RULE_MODULE, ExamplePairingTableProps))
    }
}
