package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyDefinition
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModelDefinition
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModelRegistry
import com.doublemoon1119.mahjongcraft.ai.riichi.RiichiOpponentModel
import com.doublemoon1119.mahjongcraft.extension.MahjongExtension
import com.doublemoon1119.mahjongcraft.flow.common.game.riichi.RiichiWinCelebrationCueResolver
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverDefinition
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.RiichiNagashiManganOutcomeResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.RiichiSuukanNagareResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.RiichiWinSettlementDetailResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverDefinition
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleDefinition
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.decision.DebugDecisionActionIds
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.decision.DebugRoundPreparationResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.DebugWinRoundContinuationResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.DebugWinRoundContinuationState
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule.DebugAutoNorthRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule.DebugAutoNorthRiichiRuleModule
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule.registerDebugAutoNorthRiichiNetworkDtos
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule.registerDebugAutoNorthRiichiPersistenceDtos
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario.DebugScriptedAiStrategy
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabulary
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyDefaultDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.RiichiFamilyMinecraftPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.GameActionSoundPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropDescriberRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry

/**
 * 只在開發環境登記的 debug 整合，讓 debug 指令與情境需要的流程能在遊戲內驗證。
 *
 * 內建規則沒有自己的開局準備流程時，補上 debug 用的開局準備；另外登記可切換的胡牌後本局繼續流程、
 * debug 情境對手使用的腳本 AI 與決策文字卡的預覽用語。開發用的 [DebugAutoNorthRiichiRuleModule] 也在這裡登記，
 * 並沿用三人日麻的途中流局、結算與 Minecraft 呈現；它沒有房間設定畫面，只能透過 debug 情境載入。
 * 正式環境不登記這個 extension。
 *
 * @property winRoundContinuationState debug 指令切換胡牌後本局繼續流程時共用的狀態。
 */
internal class DebugMahjongExtension(
    private val winRoundContinuationState: DebugWinRoundContinuationState,
) : MahjongExtension,
    MinecraftMahjongExtension {
    override val id: String = MahjongCraftMetadata.id("debug")

    override fun registerRuleModules(registry: MahjongModuleRegistry) {
        registry.register(
            definition = MahjongModuleDefinition(DebugAutoNorthRiichiRuleConfig::class, DebugAutoNorthRiichiRuleModule.ID) { config, id ->
                DebugAutoNorthRiichiRuleModule(id, config)
            },
        )
    }

    override fun registerNetworkDtos(registries: NetworkDtoRegistries) = registerDebugAutoNorthRiichiNetworkDtos(registries)

    override fun registerPersistenceDtos(registries: PersistenceRegistries) = registerDebugAutoNorthRiichiPersistenceDtos(registries)

    override fun registerWinCelebrationCueResolvers(registry: WinCelebrationCueResolverRegistry) {
        registry.register(WinCelebrationCueResolverDefinition(DebugAutoNorthRiichiRuleModule.ID, RiichiWinCelebrationCueResolver))
    }

    override fun registerOpponentModels(registry: OpponentModelRegistry) {
        registry.register(
            definition = OpponentModelDefinition(DebugAutoNorthRiichiRuleModule.ID) { module, depth ->
                RiichiOpponentModel(rules = module.createPositionRules(), readingDepth = depth)
            },
        )
    }

    override fun registerPostReactionRoundOutcomeResolvers(registry: PostReactionRoundOutcomeResolverRegistry) {
        registry.register(RiichiNagashiManganOutcomeResolver(DebugAutoNorthRiichiRuleModule.ID))
    }

    override fun registerPostActionExhaustiveDrawResolvers(registry: PostActionExhaustiveDrawResolverRegistry) {
        registry.register(RiichiSuukanNagareResolver(DebugAutoNorthRiichiRuleModule.ID))
    }

    override fun registerWinSettlementDetailResolvers(registry: WinSettlementDetailResolverRegistry) {
        registry.register(WinSettlementDetailResolverDefinition(DebugAutoNorthRiichiRuleModule.ID, RiichiWinSettlementDetailResolver))
    }

    override fun registerRuleModuleDisplayNames(registry: RuleModuleDisplayNameRegistry) {
        registry.register(RuleModuleDisplayNameDefinition(DebugAutoNorthRiichiRuleModule.ID, MinecraftMessageKeys.DEBUG_RULE_MODULE_AUTO_NORTH_RIICHI))
    }

    override fun registerTileAssets(registry: MinecraftTileAssetRegistry) {
        RiichiFamilyMinecraftPresentation.registerTileAppearance(registry, DebugAutoNorthRiichiRuleModule.ID)
    }

    override fun registerWinSettlementPresentationTemplates(registry: WinSettlementPresentationTemplateRegistry) {
        RiichiFamilyMinecraftPresentation.registerWinSettlementTemplate(registry, DebugAutoNorthRiichiRuleModule.ID)
    }

    override fun registerGameActionSounds(registry: GameActionSoundPresentationRegistry) {
        RiichiFamilyMinecraftPresentation.registerGameActionSounds(registry, DebugAutoNorthRiichiRuleModule.ID)
    }

    override fun registerRoundInfoPresentations(registry: RoundInfoLineDisplayRegistry) {
        RiichiFamilyMinecraftPresentation.registerRoundInfo(registry, DebugAutoNorthRiichiRuleModule.ID)
    }

    override fun registerDecisionStatusDisplayNames(registry: DecisionStatusDisplayNameRegistry) {
        RiichiFamilyMinecraftPresentation.registerDecisionStatusDisplayNames(registry, DebugAutoNorthRiichiRuleModule.ID)
    }

    override fun registerTablePropDescribers(registry: TablePropDescriberRegistry) {
        RiichiFamilyMinecraftPresentation.registerTableProps(registry, DebugAutoNorthRiichiRuleModule.ID)
    }

    override fun registerGameActionVocabulary(registry: GameActionVocabularyRegistry) {
        registry.register(
            GameActionVocabularyDefaultDefinition(
                actionId = DebugDecisionActionIds.TEXT_CARD,
                vocabulary = GameActionVocabulary(
                    labelKey = MinecraftMessageKeys.DEBUG_DECISION_TEXT_CARD_LABEL,
                    descriptionKey = MinecraftMessageKeys.DEBUG_DECISION_TEXT_CARD_DESCRIPTION,
                    cardBodyKey = MinecraftMessageKeys.DEBUG_DECISION_TEXT_CARD_BODY,
                    cardHintKey = MinecraftMessageKeys.DEBUG_DECISION_TEXT_CARD_HINT,
                ),
            ),
        )
        RiichiFamilyMinecraftPresentation.registerGameActionVocabulary(registry, DebugAutoNorthRiichiRuleModule.ID, includesPullNorth = true)
    }

    override fun registerAiStrategies(registry: MahjongAiStrategyRegistry) {
        registry.register(MahjongAiStrategyDefinition(DebugScriptedAiStrategy.TSUMOGIRI_KEY) { DebugScriptedAiStrategy(declaresKanFirst = false) })
        registry.register(MahjongAiStrategyDefinition(DebugScriptedAiStrategy.KAN_FIRST_KEY) { DebugScriptedAiStrategy(declaresKanFirst = true) })
        registry.register(
            definition = MahjongAiStrategyDefinition(DebugScriptedAiStrategy.PULL_NORTH_FIRST_KEY) {
                DebugScriptedAiStrategy(declaresKanFirst = false, pullsNorthFirst = true)
            },
        )
    }

    override fun registerRoundPreparationResolvers(registry: RoundPreparationResolverRegistry) {
        DEBUG_RULE_MODULE_IDS.forEach { ruleModuleId ->
            if (registry.find(ruleModuleId) == null) registry.register(DebugRoundPreparationResolver(ruleModuleId))
        }
    }

    override fun registerWinRoundContinuationResolvers(registry: WinRoundContinuationResolverRegistry) {
        DEBUG_RULE_MODULE_IDS.forEach { ruleModuleId ->
            registry.register(DebugWinRoundContinuationResolver(ruleModuleId = ruleModuleId, state = winRoundContinuationState))
        }
    }

    private companion object {
        /** 補上 debug 開局準備與胡牌後本局繼續流程的規則模組。 */
        val DEBUG_RULE_MODULE_IDS: List<String> = listOf(
            BuiltInRuleModuleIds.RIICHI,
            BuiltInRuleModuleIds.RIICHI_THREE_PLAYER,
            BuiltInRuleModuleIds.TAIWAN,
            DebugAutoNorthRiichiRuleModule.ID,
        )
    }
}
