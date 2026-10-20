package com.doublemoon1119.mahjongcraft.example.minecraft

import com.doublemoon1119.mahjongcraft.example.ExampleMahjongExtension
import com.doublemoon1119.mahjongcraft.example.ai.ExampleAiStrategyIds
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
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.preparation.RoundPreparationDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.preparation.RoundPreparationDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistry

/** 結合共用範例與 Minecraft 呈現的入口，供各版本與 loader 的轉接層繼承。 */
open class ExampleMinecraftMahjongExtension :
    ExampleMahjongExtension(),
    MinecraftMahjongExtension {
    override fun registerGameConfigPresentations(registry: GameConfigPresentationRegistry) {
        registry.register(examplePairingGameConfigPresentation())
    }

    override fun registerRuleCatalogues(registry: RuleCatalogueRegistry) {
        registry.register(examplePairingCatalogue())
    }

    override fun registerWinSettlementPresentationTemplates(registry: WinSettlementPresentationTemplateRegistry) {
        registerExamplePairingSettlementTemplates(registry)
    }

    override fun registerRoundInfoPresentations(registry: RoundInfoLineDisplayRegistry) {
        registerExamplePairingRoundInfo(registry)
    }

    override fun registerTileAssets(registry: MinecraftTileAssetRegistry) {
        registerExampleTileAssets(registry)
    }

    override fun registerTileLabels(registry: TileLabelRegistry) {
        registerExampleTileLabels(registry)
    }

    override fun registerTileEmojis(registry: TileEmojiRegistry) {
        registerExampleTileEmojis(registry)
    }

    override fun registerRuleModuleDisplayNames(registry: RuleModuleDisplayNameRegistry) {
        registry.register(RuleModuleDisplayNameDefinition(ExamplePairingIds.RULE_MODULE, ExampleMinecraftMessageKeys.RULE_NAME))
    }

    override fun registerTileDisplayNames(registry: TileDisplayNameRegistry) {
        registry.register(TileDisplayNameDefinition(ExamplePairingIds.INVERTED_ONE_MAN, ExampleMinecraftMessageKeys.TILE_INVERTED_ONE_MAN))
    }

    override fun registerAiStrategyDisplayNames(registry: AiStrategyDisplayNameRegistry) {
        registry.register(AiStrategyDisplayNameDefinition(ExampleAiStrategyIds.TSUMOGIRI, ExampleMinecraftMessageKeys.AI_NAME))
    }

    override fun registerGameActionVocabulary(registry: GameActionVocabularyRegistry) {
        registry.register(
            GameActionVocabularyDefinition(
                ruleModuleId = ExamplePairingIds.RULE_MODULE,
                actionId = ExamplePairingIds.DECLARE_ACTION,
                vocabulary = GameActionVocabulary(
                    labelKey = ExampleMinecraftMessageKeys.DECLARE,
                    order = 50,
                    descriptionKey = ExampleMinecraftMessageKeys.DECLARE_DESCRIPTION,
                    cardBodyKey = ExampleMinecraftMessageKeys.DECLARE_CARD_BODY,
                    cardHintKey = ExampleMinecraftMessageKeys.DECLARE_CARD_HINT,
                ),
            ),
        )
    }

    override fun registerPublicPlayerIndicatorDisplays(registry: PublicPlayerIndicatorDisplayRegistry) {
        registry.register(
            PublicPlayerIndicatorDisplayDefinition(
                id = ExamplePairingIds.DECLARATION_INDICATOR,
                display = PublicPlayerIndicatorDisplay(ExampleMinecraftMessageKeys.DECLARED),
            ),
        )
    }

    override fun registerRoundPreparationDisplayNames(registry: RoundPreparationDisplayNameRegistry) {
        registry.register(RoundPreparationDisplayNameDefinition(ExamplePairingIds.EXCHANGE_STEP, ExampleMinecraftMessageKeys.EXCHANGE))
    }

    override fun registerExhaustiveDrawReasonDisplayNames(registry: ExhaustiveDrawReasonDisplayNameRegistry) {
        registry.register(ExhaustiveDrawReasonDisplayNameDefinition(ExamplePairingIds.EXHAUSTIVE_DRAW, ExampleMinecraftMessageKeys.EXHAUSTIVE_DRAW))
    }
}
