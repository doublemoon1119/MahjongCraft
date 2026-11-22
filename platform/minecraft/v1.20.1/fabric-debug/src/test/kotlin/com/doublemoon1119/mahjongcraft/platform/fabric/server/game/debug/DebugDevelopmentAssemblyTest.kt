package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistry
import com.doublemoon1119.mahjongcraft.bundled.BuiltInMahjongExtension
import com.doublemoon1119.mahjongcraft.bundled.BundledMahjongExtensions
import com.doublemoon1119.mahjongcraft.extension.CoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationSource
import com.doublemoon1119.mahjongcraft.extension.MahjongExtensionBootstrap
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundContinuationContext
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundDirective
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.WinRoundContinuationResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BundledMinecraftMahjongExtensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtensionBootstrap
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftPresentationRegistries
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.development.TableEndListeners
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.development.WinCelebrationRequestAdjusters
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.extension.LinkageIsolatingExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ServerEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.DebugCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.DebugDevelopmentEntrypoint
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.DebugMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.animation.DebugAnimationCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.decision.DebugDecisionCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.presentation.DebugPresentationCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.progression.DebugProgressionCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.progression.DebugRoundCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.scenario.DebugScenarioCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.support.DebugPlayerTableScope
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.support.DebugPreviewEntityLifecycle
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.support.DebugTilePreviewSupport
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.text.DebugTextCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.decision.DebugDecisionActionIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.history.HistoryGenerationRuntimeFactory
import com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.history.RegisteredHistoryGenerationRuntimeFactory
import com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.presentation.DebugWinRoundContinuationMode
import com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.presentation.DebugWinRoundContinuationState
import com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.presentation.DebugWinShowcaseOverride
import com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.scenario.DebugScriptedAiStrategy
import com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.support.DebugVirtualTableLayoutFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.lang.reflect.Proxy
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證 debug 服務接上本體的 Koin、擴充登記與開發用接口。 */
class DebugDevelopmentAssemblyTest {
    /** 每個測試結束後清除全域 Koin 容器。 */
    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    /** debug 擴充以獨立來源登記 debug 情境使用的腳本 AI 與文字卡用語，並依桌級設定切換胡牌後本局繼續。 */
    @Test
    fun `debug extension registers scripted ai and toggles win round continuation`() {
        val koin = startKoin(debugKoinApplication()).koin

        val sources = registerExtensions(koin, DebugMahjongExtension(winRoundContinuationState = koin.get()))

        assertTrue(DebugScriptedAiStrategy.TSUMOGIRI_KEY in koin.get<MahjongAiStrategyRegistry>().getAllStrategyKeys())
        assertNotNull(koin.get<GameActionVocabularyRegistry>().find(null, DebugDecisionActionIds.TEXT_CARD))
        val debugSource = sources.single { it.extensionId == "mahjongcraft:debug" }
        assertTrue(debugSource.categories.any { it.id == "mahjongcraft:ai_strategy" && DebugScriptedAiStrategy.TSUMOGIRI_KEY in it.registrationIds })
        assertTrue(sources.first().categories.none { category -> DebugScriptedAiStrategy.TSUMOGIRI_KEY in category.registrationIds })
        val table = FakeTableStateFactory.create(
            players = List(4) { FakeMahjongPlayerFactory.create() },
            config = RiichiRuleConfig(),
        )
        val context = WinRoundContinuationContext(
            previousTableState = table,
            settledTableState = table,
            winnerPlayerIds = setOf(table.players.first().id),
            ronDiscarderId = null,
            winningTileId = Uuid.random(),
        )
        val resolverRegistry = koin.get<WinRoundContinuationResolverRegistry>()
        val ruleModule = koin.get<MahjongModuleRegistry>().getModule(table.config)
        assertEquals(WinRoundDirective.EndRound, resolverRegistry.resolve(context, ruleModule))
        koin.get<DebugWinRoundContinuationState>().setMode(table.id, DebugWinRoundContinuationMode.FULL)
        assertIs<WinRoundDirective.ContinueRound>(resolverRegistry.resolve(context, ruleModule))
    }

    /** debug 指令都掛在 `/mahjongcraft debug` 底下，各子指令保留原有的參數。 */
    @Test
    fun `debug command tree mounts every family under the debug root`() {
        val koin = startKoin(debugKoinApplication()).koin
        assertSame(koin.get<RegisteredHistoryGenerationRuntimeFactory>(), koin.get<HistoryGenerationRuntimeFactory>())
        val debugRoot = koin.get<DebugCommand>().build().build()
        assertEquals("mahjongcraft", debugRoot.name)
        assertEquals(setOf("debug"), debugRoot.children.map { it.name }.toSet())
        val debugNode = debugRoot.children.single()
        assertEquals(
            expected = setOf(
                "seated_participant",
                "scenario",
                "history",
                "stress",
                "win",
                "showcase",
                "dice",
                "deal",
                "draw",
                "discard",
                "exhaustive_draw_settlement",
                "hovered_text",
                "win_settlement",
                "match_settlement",
                "match_progression",
                "decision_hud",
                "meld",
                "continuing_win",
                "win_showcase_override",
                "rule_decision",
                "round",
            ),
            actual = debugNode.children.map { it.name }.toSet(),
            message = "every family subcommand is mounted under the debug root, and nothing else is",
        )
        assertEquals(
            expected = setOf("list", "load", "stress"),
            actual = koin.get<DebugScenarioCommand>().build().build().children.map { it.name }.toSet(),
        )
        assertEquals(
            expected = setOf("next", "combo"),
            actual = koin.get<DebugRoundCommand>().buildRoundCommand().build().children.map { it.name }.toSet(),
        )
        val presentationCommand = koin.get<DebugPresentationCommand>()
        assertEquals(
            expected = setOf("melds", "tsumo", "ron", "multi_ron", "phase"),
            actual = presentationCommand.buildShowcaseCommand().build().children.map { it.name }.toSet(),
        )
        val exhaustiveDrawSettlementNode = presentationCommand.buildExhaustiveDrawSettlementCommand().build()
        assertEquals(setOf("riichi", "abortive"), exhaustiveDrawSettlementNode.children.map { it.name }.toSet())
        assertEquals(
            expected = setOf("normal", "melds", "kyuushu", "score"),
            actual = exhaustiveDrawSettlementNode.getChild("riichi").children.map { it.name }.toSet(),
        )
        val winSettlementNode = presentationCommand.buildWinSettlementCommand().build()
        assertEquals(setOf("riichi", "taiwan"), winSettlementNode.children.map { it.name }.toSet())
        assertEquals(
            expected = setOf("tsumo", "ron", "yakuman", "pao", "nagashi"),
            actual = winSettlementNode.getChild("riichi").children.map { it.name }.toSet(),
        )
        assertEquals(
            expected = setOf("tsumo", "ron", "eight_flowers", "seven_flowers"),
            actual = winSettlementNode.getChild("taiwan").children.map { it.name }.toSet(),
        )
        assertEquals(
            expected = setOf("winner_count"),
            actual = winSettlementNode.getChild("riichi").getChild("ron").children.map { it.name }.toSet(),
            message = "ron keeps its optional winner count argument",
        )
        assertEquals(
            expected = setOf("clear", "cue"),
            actual = presentationCommand.buildWinShowcaseOverrideCommand().build().children.map { it.name }.toSet(),
        )
        koin.get<DebugTextCommand>()
        val decisionCommand = koin.get<DebugDecisionCommand>()
        val decisionHudNode = decisionCommand.buildDecisionHudCommand().build()
        assertEquals(
            expected = setOf("clear", "riichi", "riichi_three_player", "taiwan"),
            actual = decisionHudNode.children.map { it.name }.toSet(),
        )
        assertTrue(
            actual = decisionHudNode.getChild("riichi").children.map { it.name }
                .containsAll(listOf("timer", "chi", "pon", "kan", "ron", "text_card", "mixed", "transparent_calls")),
            message = "decision_hud riichi keeps its preview literals",
        )
        assertEquals(setOf("pull_north"), decisionHudNode.getChild("riichi_three_player").children.map { it.name }.toSet())
        assertEquals(
            expected = setOf(
                "discard_analysis",
                "discard_passed_win",
                "discard_tsumo_only",
                "discard_below_minimum",
                "discard_unavailable",
                "discard_mixed",
            ),
            actual = decisionHudNode.getChild("taiwan").children.map { it.name }.toSet(),
        )
        assertEquals(
            expected = setOf("start", "submit", "timeout", "cancel"),
            actual = decisionCommand.buildRuleDecisionCommand().build().children.map { it.name }.toSet(),
        )
        koin.get<DebugProgressionCommand>()
        koin.get<DebugAnimationCommand>()
        koin.get<DebugTilePreviewSupport>()
        koin.get<DebugVirtualTableLayoutFactory>()
        koin.get<DebugPreviewEntityLifecycle>()
        koin.get<DebugPlayerTableScope>()
    }

    /** 進入點接上和牌展示調整與牌桌結束通知：沒有覆寫時請求不變，牌桌結束後清掉這桌的 debug 設定。 */
    @Test
    fun `development entrypoint connects showcase override and table end cleanup`() {
        val koin = startKoin(debugKoinApplication()).koin
        // 測試沒有 Fabric 啟動器，指令登記改以非開發環境略過，不會用到伺服器事件；這個案例只驗證接口。
        val environment = object : MinecraftEnvironment {
            override val isDevelopment: Boolean = false
        }
        koin.loadModules(listOf(module { single<MinecraftEnvironment> { environment } }), allowOverride = true)
        DebugDevelopmentEntrypoint().initialize(koin, unusedServerEvents())
        val tableId = Uuid.random()
        val showcaseOverride = koin.get<DebugWinShowcaseOverride>()
        val continuationState = koin.get<DebugWinRoundContinuationState>()
        val request = WinCelebrationRequest(winningTileId = Uuid.random(), isTsumo = true, winners = emptyList(), revealsWinnerHands = false)

        assertSame(request, koin.get<WinCelebrationRequestAdjusters>().adjust(tableId, request))
        continuationState.setMode(tableId, DebugWinRoundContinuationMode.FULL)
        koin.get<TableEndListeners>().notifyTableEnded(tableId)

        assertEquals(DebugWinRoundContinuationMode.OFF, continuationState.modeFor(tableId))
        assertTrue(tableId !in showcaseOverride.armedTableIds())
    }

    /**
     * 以本體相同的順序登記內建、隨附與指定的擴充，並凍結核心與 Minecraft 呈現的登記表。
     *
     * @param koin 已載入 debug 服務的 Koin。
     * @param extension 額外登記的擴充。
     * @return 核心層依來源整理的登記。
     */
    private fun registerExtensions(koin: Koin, extension: DebugMahjongExtension): List<ExtensionRegistrationSource> {
        val core = koin.get<CoreExtensionRegistries>()
        val sources = MahjongExtensionBootstrap.registerAndFreeze(
            extensions = BundledMahjongExtensions.all + extension,
            registries = core,
            builtIn = BuiltInMahjongExtension(
                moduleRegistry = core.moduleRegistry,
                extensionActionRegistry = core.gameActionAiRegistry,
                opponentModelRegistry = core.opponentModelRegistry,
            ),
            failurePolicy = LinkageIsolatingExtensionFailurePolicy(),
        )
        MinecraftMahjongExtensionBootstrap.registerAndFreeze(
            extensions = BundledMinecraftMahjongExtensions.all + extension,
            registries = koin.get<MinecraftPresentationRegistries>(),
            failurePolicy = LinkageIsolatingExtensionFailurePolicy(),
        )
        return sources
    }

    /**
     * 建立不應被使用的伺服器事件來源；任何登記都會丟出例外。
     *
     * @return 呼叫任何方法都丟出例外的 [ServerEvents]。
     */
    private fun unusedServerEvents(): ServerEvents = Proxy.newProxyInstance(
        ServerEvents::class.java.classLoader,
        arrayOf(ServerEvents::class.java),
    ) { _, method, _ -> error("Unexpected server event registration: ${method.name}") } as ServerEvents
}
