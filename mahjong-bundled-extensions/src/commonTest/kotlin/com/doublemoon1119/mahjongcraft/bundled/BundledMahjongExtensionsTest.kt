package com.doublemoon1119.mahjongcraft.bundled

import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistryImpl
import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.ai.expectation.OpponentModelRegistry
import com.doublemoon1119.mahjongcraft.extension.CoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationSource
import com.doublemoon1119.mahjongcraft.extension.MahjongExtensionRegistrar
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistryImpl
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.HistoryReplayProjectionRegistry
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.emptyPersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameActionCommandFactoryRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 驗證內建規則 extension 經由正式 registrar 登記各自的平台無關整合。 */
class BundledMahjongExtensionsTest {
    /** 日麻與台麻依序登記，各自的規則模組與牌種歸在自己名下，空的 registry 不會預先帶有任何規則。 */
    @Test
    fun `bundled extensions register each rule under its own source`() {
        val registries = registries()

        val sources = MahjongExtensionRegistrar.registerAndFreeze(BundledMahjongExtensions.all, registries)

        assertEquals(listOf(null, BundledRiichiExtension.id, BundledTaiwanExtension.id), sources.map { it.extensionId })
        assertTrue(sources.first().categories.isEmpty(), "Empty registries must not carry any built-in rule")
        assertEquals(listOf(BuiltInRuleModuleIds.RIICHI, BuiltInRuleModuleIds.RIICHI_THREE_PLAYER), sources[1].ids("mahjongcraft:rule_module"))
        assertEquals(listOf(BuiltInRuleModuleIds.TAIWAN), sources[2].ids("mahjongcraft:rule_module"))
        assertEquals(RiichiTileTypes.ALL.map { it.toString() }.sorted(), sources[1].ids("mahjongcraft:tile_type"))
        assertEquals(TaiwanTileTypes.ALL.map { it.toString() }.sorted(), sources[2].ids("mahjongcraft:tile_type"))
        assertIs<RiichiRuleModule>(registries.moduleRegistry.getModule(RiichiRuleConfig()))
        assertIs<ThreePlayerRiichiRuleModule>(registries.moduleRegistry.getModule(ThreePlayerRiichiRuleConfig()))
        assertIs<TaiwanRuleModule>(registries.moduleRegistry.getModule(TaiwanRuleConfig()))
    }

    /** 日麻的規則狀態、擴充動作、對手模型與結算整合都登記在日麻名下，台麻只登記自己的規則設定與牌河。 */
    @Test
    fun `riichi integrations stay with riichi and taiwan registers only its own formats`() {
        val sources = MahjongExtensionRegistrar.registerAndFreeze(BundledMahjongExtensions.all, registries())
        val riichi = sources[1]
        val taiwan = sources[2]

        listOf(
            "mahjongcraft:opponent_model",
            "mahjongcraft:game_command",
            "mahjongcraft:post_action_exhaustive_draw_resolver",
            "mahjongcraft:win_settlement_detail_resolver",
            "mahjongcraft:win_celebration_cue_resolver",
        ).forEach { categoryId -> assertTrue(riichi.ids(categoryId).isNotEmpty(), "Riichi should register $categoryId") }
        assertTrue(riichi.ids("mahjongcraft:persistence_dto").any { "riichi/player_state" in it })
        assertTrue(taiwan.ids("mahjongcraft:persistence_dto").all { "taiwan" in it })
        assertTrue(taiwan.ids("mahjongcraft:network_dto").all { "taiwan" in it })
        assertEquals(listOf("mahjongcraft:taiwan/discard_pile"), taiwan.ids("mahjongcraft:history_replay_discard"))
    }

    /** 指定類別中這個來源登記的 ID；沒有登記時為空清單。 */
    private fun ExtensionRegistrationSource.ids(categoryId: String): List<String> = categories.firstOrNull { it.id == categoryId }?.registrationIds.orEmpty()

    /** 所有子 registry 皆為空的測試集合。 */
    private fun registries(): CoreExtensionRegistries {
        val moduleRegistry = MahjongModuleRegistryImpl()
        return CoreExtensionRegistries(
            moduleRegistry = moduleRegistry,
            tileTypeRegistry = TileTypeRegistryImpl(),
            networkRegistries = DefaultNetworkDtoRegistries(),
            persistenceRegistries = emptyPersistenceRegistries(),
            historyReplayProjectionRegistry = HistoryReplayProjectionRegistry(),
            winCelebrationCueResolverRegistry = WinCelebrationCueResolverRegistryImpl(),
            gameActionAiRegistry = ExtensionGameActionAiRegistry(moduleRegistry),
            aiStrategyRegistry = MahjongAiStrategyRegistryImpl(defaultKey = RandomAiStrategy.KEY),
            opponentModelRegistry = OpponentModelRegistry(),
            gameActionCommandFactoryRegistry = ExtensionGameActionCommandFactoryRegistry(),
            gameCommandRegistry = ExtensionGameCommandExecutorRegistry(),
            postReactionRoundOutcomeResolverRegistry = PostReactionRoundOutcomeResolverRegistry(),
            postActionExhaustiveDrawResolverRegistry = PostActionExhaustiveDrawResolverRegistry(),
            roundPreparationResolverRegistry = RoundPreparationResolverRegistry(),
            winRoundContinuationResolverRegistry = WinRoundContinuationResolverRegistry(),
            winSettlementDetailResolverRegistry = WinSettlementDetailResolverRegistry(),
        )
    }
}
