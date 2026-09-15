package com.doublemoon1119.mahjongcraft.extension

import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExtensionGameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistryImpl
import com.doublemoon1119.mahjongcraft.flow.network.dto.registry.DtoRegistry
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DiscardPileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DynamicRuleStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExhaustiveDrawReasonDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExtensionGameActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ExtensionGameCommandDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.GameLengthDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.MahjongRuleConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.PlayerRuleStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.ScoreConfigDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.buildBuiltInPersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameActionCommandFactoryRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostReactionRoundOutcomeResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.config.GameLength
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.config.ScoreConfig
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.PlayerRuleState
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeDefinition
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistry
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 驗證第三方 extension 的統一註冊順序、錯誤診斷與 registry freeze。 */
class MahjongExtensionRegistrarTest {
    /** 驗證三個註冊階段都會執行，完成後禁止新增規則模組。 */
    @Test
    fun `extension registers all integrations before registries freeze`() {
        val moduleRegistry = MahjongModuleRegistryImpl()
        val networkRegistries = TestNetworkDtoRegistries()
        val persistenceRegistries = buildBuiltInPersistenceRegistries()
        val tileTypeRegistry = TileTypeRegistryImpl()
        val calls = mutableListOf<String>()
        val extension = RecordingExtension(calls)

        val categories = MahjongExtensionRegistrar.registerAndFreeze(
            extensions = listOf(extension),
            registries = testCoreRegistries(
                moduleRegistry = moduleRegistry,
                tileTypeRegistry = tileTypeRegistry,
                networkRegistries = networkRegistries,
                persistenceRegistries = persistenceRegistries,
            ),
        )

        assertEquals(listOf("rule", "tile", "network", "persistence"), calls)
        assertEquals(
            setOf(
                "mahjongcraft:rule_module",
                "mahjongcraft:tile_type",
            ),
            categories.mapTo(mutableSetOf(), ExtensionRegistrationCategory::id),
        )
        assertTrue(moduleRegistry.getModule(RiichiRuleConfig()) is RiichiRuleModule)
        assertEquals(
            TileTypeId.parse("example:flower/spring"),
            tileTypeRegistry.require(TileTypeId.parse("example:flower/spring")).id,
        )
        assertFailsWith<IllegalStateException> {
            moduleRegistry.register(TaiwanRuleConfig::class, "example:taiwan") { config, id ->
                TaiwanRuleModule(id, config)
            }
        }
        assertFailsWith<IllegalStateException> {
            tileTypeRegistry.register(TileTypeDefinition(TileTypeId.parse("example:late")))
        }
    }

    /** 驗證註冊失敗時的例外會指出第三方 extension ID。 */
    @Test
    fun `registration failure identifies extension`() {
        val extension = object : MahjongExtension {
            override val id: String = "example:broken"

            override fun registerRuleModules(registry: MahjongModuleRegistry) {
                error("broken")
            }

            override fun registerNetworkDtos(registries: NetworkDtoRegistries) = Unit

            override fun registerPersistenceDtos(registries: PersistenceRegistries) = Unit
        }

        val error = assertFailsWith<MahjongExtensionRegistrationException> {
            MahjongExtensionRegistrar.registerAndFreeze(
                extensions = listOf(extension),
                registries = testCoreRegistries(),
            )
        }

        assertTrue(error.message.orEmpty().contains("example:broken"))
    }

    /** 驗證相同 extension ID 不會形成無法判斷來源的部分註冊結果。 */
    @Test
    fun `duplicate extension id fails registration`() {
        val extension = RecordingExtension(mutableListOf())

        val error = assertFailsWith<MahjongExtensionRegistrationException> {
            MahjongExtensionRegistrar.registerAndFreeze(
                extensions = listOf(extension, extension),
                registries = testCoreRegistries(),
            )
        }

        assertTrue(error.message.orEmpty().contains(extension.id))
        assertTrue(error.cause?.message.orEmpty().contains("Duplicate"))
    }
}

/** 建立 registrar 測試使用的獨立 core registry 集合。 */
private fun testCoreRegistries(
    moduleRegistry: MahjongModuleRegistry = MahjongModuleRegistryImpl(),
    tileTypeRegistry: TileTypeRegistry = TileTypeRegistryImpl(),
    networkRegistries: NetworkDtoRegistries = TestNetworkDtoRegistries(),
    persistenceRegistries: PersistenceRegistries = buildBuiltInPersistenceRegistries(),
): CoreExtensionRegistries = CoreExtensionRegistries(
    moduleRegistry = moduleRegistry,
    tileTypeRegistry = tileTypeRegistry,
    networkRegistries = networkRegistries,
    persistenceRegistries = persistenceRegistries,
    winCelebrationCueResolverRegistry = WinCelebrationCueResolverRegistryImpl(),
    gameActionAiRegistry = ExtensionGameActionAiRegistry(),
    gameActionCommandFactoryRegistry = ExtensionGameActionCommandFactoryRegistry(),
    gameCommandRegistry = ExtensionGameCommandExecutorRegistry(),
    postReactionRoundOutcomeResolverRegistry = PostReactionRoundOutcomeResolverRegistry(),
    postActionExhaustiveDrawResolverRegistry = PostActionExhaustiveDrawResolverRegistry(),
    roundPreparationResolverRegistry = RoundPreparationResolverRegistry(),
    winRoundContinuationResolverRegistry = WinRoundContinuationResolverRegistry(),
    winSettlementDetailResolverRegistry = WinSettlementDetailResolverRegistry(),
)

/** 記錄 registrar 呼叫順序並登記一個可解析規則的測試 extension。 */
private class RecordingExtension(
    private val calls: MutableList<String>,
) : MahjongExtension {
    override val id: String = "example:recording"

    override fun registerRuleModules(registry: MahjongModuleRegistry) {
        calls += "rule"
        registry.register(RiichiRuleConfig::class, "example:riichi") { config, id -> RiichiRuleModule(id, config) }
    }

    override fun registerTileTypes(registry: TileTypeRegistry) {
        calls += "tile"
        registry.register(TileTypeDefinition(TileTypeId.parse("example:flower/spring")))
    }

    override fun registerNetworkDtos(registries: NetworkDtoRegistries) {
        calls += "network"
    }

    override fun registerPersistenceDtos(registries: PersistenceRegistries) {
        calls += "persistence"
    }
}

/** 提供 registrar 測試使用的獨立 network DTO registry 集合。 */
private class TestNetworkDtoRegistries : NetworkDtoRegistries {
    override val ruleConfig = DtoRegistry<MahjongRuleConfig, MahjongRuleConfigDto>()
    override val scoreConfig = DtoRegistry<ScoreConfig, ScoreConfigDto>()
    override val gameLength = DtoRegistry<GameLength, GameLengthDto>()
    override val dynamicRuleState = DtoRegistry<DynamicRuleState, DynamicRuleStateDto>()
    override val playerRuleState = DtoRegistry<PlayerRuleState, PlayerRuleStateDto>()
    override val discardPile = DtoRegistry<DiscardPile<*>, DiscardPileDto>()
    override val exhaustiveDrawReason = DtoRegistry<ExhaustiveDrawReason, ExhaustiveDrawReasonDto>()
    override val extensionGameAction = DtoRegistry<ExtensionGameAction, ExtensionGameActionDto>()
    override val extensionGameCommand = DtoRegistry<ExtensionGameCommand, ExtensionGameCommandDto>()

    override fun freeze() {
        ruleConfig.freeze()
        scoreConfig.freeze()
        gameLength.freeze()
        dynamicRuleState.freeze()
        playerRuleState.freeze()
        discardPile.freeze()
        exhaustiveDrawReason.freeze()
        extensionGameAction.freeze()
        extensionGameCommand.freeze()
    }
}
