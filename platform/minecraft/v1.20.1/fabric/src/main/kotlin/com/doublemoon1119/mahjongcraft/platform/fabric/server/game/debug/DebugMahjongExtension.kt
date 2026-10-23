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
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolverRegistry
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
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.decision.DebugRuleDecisionResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.DebugWinRoundContinuationResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.DebugWinRoundContinuationState
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule.DebugAutoNorthRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule.DebugAutoNorthRiichiRuleModule
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule.DebugWinnerDealerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule.DebugWinnerDealerRiichiRuleModule
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule.registerDebugAutoNorthRiichiNetworkDtos
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule.registerDebugAutoNorthRiichiPersistenceDtos
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule.registerDebugWinnerDealerRiichiNetworkDtos
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule.registerDebugWinnerDealerRiichiPersistenceDtos
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
 * 內建規則沒有自己的規則選擇流程時，補上 debug 用的規則選擇；另外登記可切換的胡牌後本局繼續流程、
 * debug 情境對手使用的腳本 AI 與決策文字卡的預覽用語。開發用的 [DebugAutoNorthRiichiRuleModule] 與
 * [DebugWinnerDealerRiichiRuleModule] 也在這裡登記，分別沿用三人與四人日麻的途中流局、結算與 Minecraft 呈現；
 * 它們沒有房間設定畫面，只能透過 debug 情境載入。
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
        registry.register(
            definition = MahjongModuleDefinition(DebugWinnerDealerRiichiRuleConfig::class, DebugWinnerDealerRiichiRuleModule.ID) { config, id ->
                DebugWinnerDealerRiichiRuleModule(id, config)
            },
        )
    }

    override fun registerNetworkDtos(registries: NetworkDtoRegistries) {
        registerDebugAutoNorthRiichiNetworkDtos(registries)
        registerDebugWinnerDealerRiichiNetworkDtos(registries)
    }

    override fun registerPersistenceDtos(registries: PersistenceRegistries) {
        registerDebugAutoNorthRiichiPersistenceDtos(registries)
        registerDebugWinnerDealerRiichiPersistenceDtos(registries)
    }

    override fun registerWinCelebrationCueResolvers(registry: WinCelebrationCueResolverRegistry) {
        DEBUG_VARIANT_RULE_MODULE_IDS.forEach { ruleModuleId ->
            registry.register(WinCelebrationCueResolverDefinition(ruleModuleId, RiichiWinCelebrationCueResolver))
        }
    }

    override fun registerOpponentModels(registry: OpponentModelRegistry) {
        DEBUG_VARIANT_RULE_MODULE_IDS.forEach { ruleModuleId ->
            registry.register(
                definition = OpponentModelDefinition(ruleModuleId) { module, depth ->
                    RiichiOpponentModel(rules = module.createPositionRules(), readingDepth = depth)
                },
            )
        }
    }

    override fun registerPostReactionRoundOutcomeResolvers(registry: PostReactionRoundOutcomeResolverRegistry) {
        DEBUG_VARIANT_RULE_MODULE_IDS.forEach { ruleModuleId -> registry.register(RiichiNagashiManganOutcomeResolver(ruleModuleId)) }
    }

    override fun registerPostActionExhaustiveDrawResolvers(registry: PostActionExhaustiveDrawResolverRegistry) {
        DEBUG_VARIANT_RULE_MODULE_IDS.forEach { ruleModuleId -> registry.register(RiichiSuukanNagareResolver(ruleModuleId)) }
    }

    override fun registerWinSettlementDetailResolvers(registry: WinSettlementDetailResolverRegistry) {
        DEBUG_VARIANT_RULE_MODULE_IDS.forEach { ruleModuleId ->
            registry.register(WinSettlementDetailResolverDefinition(ruleModuleId, RiichiWinSettlementDetailResolver))
        }
    }

    override fun registerRuleModuleDisplayNames(registry: RuleModuleDisplayNameRegistry) {
        registry.register(RuleModuleDisplayNameDefinition(DebugAutoNorthRiichiRuleModule.ID, MinecraftMessageKeys.DEBUG_RULE_MODULE_AUTO_NORTH_RIICHI))
        registry.register(RuleModuleDisplayNameDefinition(DebugWinnerDealerRiichiRuleModule.ID, MinecraftMessageKeys.DEBUG_RULE_MODULE_WINNER_DEALER_RIICHI))
    }

    override fun registerTileAssets(registry: MinecraftTileAssetRegistry) {
        DEBUG_VARIANT_RULE_MODULE_IDS.forEach { ruleModuleId -> RiichiFamilyMinecraftPresentation.registerTileAppearance(registry, ruleModuleId) }
    }

    override fun registerWinSettlementPresentationTemplates(registry: WinSettlementPresentationTemplateRegistry) {
        DEBUG_VARIANT_RULE_MODULE_IDS.forEach { ruleModuleId -> RiichiFamilyMinecraftPresentation.registerWinSettlementTemplate(registry, ruleModuleId) }
    }

    override fun registerGameActionSounds(registry: GameActionSoundPresentationRegistry) {
        DEBUG_VARIANT_RULE_MODULE_IDS.forEach { ruleModuleId -> RiichiFamilyMinecraftPresentation.registerGameActionSounds(registry, ruleModuleId) }
    }

    override fun registerRoundInfoPresentations(registry: RoundInfoLineDisplayRegistry) {
        DEBUG_VARIANT_RULE_MODULE_IDS.forEach { ruleModuleId -> RiichiFamilyMinecraftPresentation.registerRoundInfo(registry, ruleModuleId) }
    }

    override fun registerDecisionStatusDisplayNames(registry: DecisionStatusDisplayNameRegistry) {
        DEBUG_VARIANT_RULE_MODULE_IDS.forEach { ruleModuleId -> RiichiFamilyMinecraftPresentation.registerDecisionStatusDisplayNames(registry, ruleModuleId) }
    }

    override fun registerTablePropDescribers(registry: TablePropDescriberRegistry) {
        DEBUG_VARIANT_RULE_MODULE_IDS.forEach { ruleModuleId -> RiichiFamilyMinecraftPresentation.registerTableProps(registry, ruleModuleId) }
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
        RiichiFamilyMinecraftPresentation.registerGameActionVocabulary(registry, DebugWinnerDealerRiichiRuleModule.ID, includesPullNorth = false)
    }

    override fun registerAiStrategies(registry: MahjongAiStrategyRegistry) {
        registry.register(MahjongAiStrategyDefinition(DebugScriptedAiStrategy.TSUMOGIRI_KEY) { DebugScriptedAiStrategy(declaresKanFirst = false) })
        registry.register(MahjongAiStrategyDefinition(DebugScriptedAiStrategy.KAN_FIRST_KEY) { DebugScriptedAiStrategy(declaresKanFirst = true) })
        registry.register(
            definition = MahjongAiStrategyDefinition(DebugScriptedAiStrategy.PULL_NORTH_FIRST_KEY) {
                DebugScriptedAiStrategy(declaresKanFirst = false, pullsNorthFirst = true)
            },
        )
        registry.register(MahjongAiStrategyDefinition(DebugScriptedAiStrategy.WIN_FIRST_KEY) { DebugScriptedAiStrategy(declaresKanFirst = false, winsFirst = true) })
    }

    override fun registerRuleDecisionResolvers(registry: RuleDecisionResolverRegistry) {
        DEBUG_RULE_MODULE_IDS.forEach { ruleModuleId ->
            if (registry.find(ruleModuleId) == null) registry.register(DebugRuleDecisionResolver(ruleModuleId))
        }
    }

    override fun registerWinRoundContinuationResolvers(registry: WinRoundContinuationResolverRegistry) {
        DEBUG_RULE_MODULE_IDS.forEach { ruleModuleId ->
            registry.register(DebugWinRoundContinuationResolver(ruleModuleId = ruleModuleId, state = winRoundContinuationState))
        }
    }

    private companion object {
        /** 補上 debug 規則選擇與胡牌後本局繼續流程的規則模組。 */
        val DEBUG_RULE_MODULE_IDS: List<String> = listOf(
            BuiltInRuleModuleIds.RIICHI,
            BuiltInRuleModuleIds.RIICHI_THREE_PLAYER,
            BuiltInRuleModuleIds.TAIWAN,
            DebugAutoNorthRiichiRuleModule.ID,
            DebugWinnerDealerRiichiRuleModule.ID,
        )

        /** 沿用日麻結算、演出與 Minecraft 呈現的開發用規則模組。 */
        val DEBUG_VARIANT_RULE_MODULE_IDS: List<String> = listOf(DebugAutoNorthRiichiRuleModule.ID, DebugWinnerDealerRiichiRuleModule.ID)
    }
}
