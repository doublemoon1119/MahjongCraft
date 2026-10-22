package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.extension.MahjongExtension
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExtensionGameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameActionCommandFactoryDefinition
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameActionCommandFactoryRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameActionCommandMapper
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.runAsSingleWrite
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerActionContext
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinPresentationHandoff
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.service.isDrawWindowOpenFor
import com.doublemoon1119.mahjongcraft.flow.server.game.service.isOwnTurnWindowOpenFor
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.MeldTypeId
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.toSnapshot
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionContext
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.logic.judgment.TileSelectionRequirement
import com.doublemoon1119.mahjongcraft.logic.module.DiscardReactionCandidate
import com.doublemoon1119.mahjongcraft.logic.module.DiscardReactionPolicy
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleDefinition
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.MeldClaimEffect
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiFamilyRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledCoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledWinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 以登記的自訂規則驗證反應提交、選牌、優先序及逐牌公開的完整流程。 */
class CustomDiscardClaimIntegrationTest {
    /** 四張與五張的自訂副露都透過正式命令映射與單次寫入成立。 */
    @Test
    fun `custom four and five tile melds resolve with explicit selection and visibility`() = runTest {
        for (count in listOf(3, 4)) {
            val fixture = Fixture(count)
            fixture.repository.setTableState(fixture.table)
            val context = PlayerActionContext.DiscardReaction(fixture.first.id, fixture.pending)
            val command = assertIs<GameCommand.RespondToDiscard>(
                fixture.mapper.toCommand(context, CLAIM, fixture.selected(fixture.first.id)),
            )
            assertEquals(fixture.pending.id, command.reactionId)
            assertIs<Outcome.Success<Unit>>(
                fixture.repository.runAsSingleWrite(fixture.table.id) {
                    fixture.useCase(fixture.table.id, fixture.first.id, command.action, command.reactionId, command.selectedTileIds)
                },
            )
            assertTrue(fixture.events.getNotifiedActions(fixture.table.id, fixture.discarder.id, fixture.first.id).isEmpty())
            assertIs<Outcome.Success<Unit>>(
                fixture.useCase(fixture.table.id, fixture.second.id, GameAction.Pass, fixture.pending.id),
            )
            val state = assertNotNull(fixture.repository.getTableState(fixture.table.id))
            val meld = state.players.first { it.id == fixture.first.id }.hand.melds.single()
            assertEquals(count + 1, meld.tiles.size)
            assertEquals(MeldType.Extension(MeldTypeId.parse("test:wide_claim")), meld.type)
            assertTrue(state.players.first().discardPile.entries.last().isTaken)
            assertNull(state.pendingReaction)
            assertEquals(fixture.first.id, state.currentPlayer.id)
            assertFalse(state.isDrawWindowOpenFor(fixture.first.id))
            assertTrue(state.isOwnTurnWindowOpenFor(fixture.first.id))
            assertEquals(2, meld.toSnapshot(false, true).tiles.count { it.tile != null })
            assertEquals(count + 1, meld.toSnapshot(true, true).tiles.count { it.tile != null })
            assertEquals(listOf(CLAIM), fixture.events.getNotifiedActions(fixture.table.id, fixture.discarder.id, fixture.first.id))
            val remaining = state.currentPlayer.hand.tiles.single()
            assertIs<Outcome.Success<Unit>>(fixture.discard(fixture.table.id, fixture.first.id, remaining.id))
            assertFalse(assertNotNull(fixture.repository.getTableState(fixture.table.id)).players.first { it.id == fixture.first.id }.mustDiscardAfterMeld)
        }
    }

    /** 舊視窗、重複選牌與外人牌皆不能寫入反應或產生事件。 */
    @Test
    fun `invalid reaction window or tile selection leaves state and side effects unchanged`() = runTest {
        val fixture = Fixture(3)
        fixture.repository.setTableState(fixture.table)
        val selection = fixture.selected(fixture.first.id)
        val requests = listOf(
            Uuid.random() to selection,
            fixture.pending.id to listOf(selection.first(), selection.first(), selection.last()),
            fixture.pending.id to fixture.selected(fixture.second.id),
        )
        for ((window, selected) in requests) {
            assertIs<Outcome.Error<*>>(fixture.useCase(fixture.table.id, fixture.first.id, CLAIM, window, selected))
            assertEquals(fixture.table, fixture.repository.getTableState(fixture.table.id))
            assertTrue(fixture.repository.historyDrafts.isEmpty())
            assertTrue(fixture.events.getNotifiedActions(fixture.table.id, fixture.discarder.id, fixture.first.id).isEmpty())
        }
    }

    /** 同級反應依座位順位處理，不受提交先後或整數極值影響。 */
    @Test
    fun `claim priority and nearest seat are independent of response arrival order`() = runTest {
        for (priorities in listOf(Int.MAX_VALUE to Int.MIN_VALUE, 100 to 100)) {
            val fixture = Fixture(3, priorities)
            fixture.repository.setTableState(fixture.table)
            assertIs<Outcome.Success<Unit>>(
                fixture.useCase(fixture.table.id, fixture.second.id, CLAIM, fixture.pending.id, fixture.selected(fixture.second.id)),
            )
            assertIs<Outcome.Success<Unit>>(
                fixture.useCase(fixture.table.id, fixture.first.id, CLAIM, fixture.pending.id, fixture.selected(fixture.first.id)),
            )
            val state = assertNotNull(fixture.repository.getTableState(fixture.table.id))
            assertEquals(fixture.first.id, state.currentPlayer.id)
            assertEquals(1, state.players.sumOf { it.hand.melds.size })
            assertEquals(4, state.players.first { it.id == fixture.second.id }.hand.tiles.size)
        }
    }

    /** 有自己回合 factory 的動作仍不能接管捨牌或搶和反應。 */
    @Test
    fun `reaction context does not invoke own turn extension command factory`() {
        val fixture = Fixture(3)
        var factoryCalls = 0
        val registry = ExtensionGameActionCommandFactoryRegistry().apply {
            register(
                ExtensionGameActionCommandFactoryDefinition(ClaimAction::class) { _, _ ->
                    factoryCalls++
                    OwnCommand
                },
            )
            freeze()
        }
        val mapper = GameActionCommandMapper(registry)
        assertIs<GameCommand.RespondToDiscard>(
            mapper.toCommand(PlayerActionContext.DiscardReaction(fixture.first.id, fixture.pending), CLAIM, fixture.selected(fixture.first.id)),
        )
        assertEquals(0, factoryCalls)
        assertIs<GameCommand.Extension>(mapper.toCommand(PlayerActionContext.OwnTurn(fixture.first.id), CLAIM))
        assertEquals(1, factoryCalls)
    }

    /**
     * 自訂鳴牌的記憶體環境，所有玩家的牌與候選都由同一份桌況建立。
     *
     * @param count 鳴牌需要的本人手牌數。
     * @param priorities 兩位回應者的反應優先值。
     */
    private class Fixture(private val count: Int, priorities: Pair<Int, Int> = 100 to 100) {
        /** 本次來源捨牌。 */
        val discarded = FakeIdentifiedTileFactory.create(Tile.Honor.White)

        /** 提供來源捨牌的玩家。 */
        val discarder = FakeMahjongPlayerFactory.create(discardPile = FakeDiscardPile().discardTile(discarded))

        /** 距離來源玩家最近的回應者。 */
        val first = FakeMahjongPlayerFactory.create(hand = Hand(tiles = List(count + 1) { FakeIdentifiedTileFactory.create(Tile.Honor.White) }))

        /** 第二位回應者。 */
        val second = FakeMahjongPlayerFactory.create(hand = Hand(tiles = List(count + 1) { FakeIdentifiedTileFactory.create(Tile.Honor.Green) }))

        /** 等待兩位玩家回應的固定視窗。 */
        val pending = PendingReaction(discarder.id, discarded.id, setOf(first.id, second.id), ronPlayerIds = setOf(first.id, second.id), id = Uuid.random())

        /** 反應提交前的權威桌況。 */
        val table = FakeTableStateFactory.create(players = listOf(discarder, first, second), config = ClaimRuleConfig(), pendingReaction = pending)

        /** 保存狀態與歷史的替身。 */
        val repository = FakeGameRepository()

        /** 記錄實際廣播的動作。 */
        val events = FakeGameEventPublisher()

        /** 提供自訂規則的公開登記表。 */
        val modules = bundledCoreExtensionRegistries(
            additionalExtensions = listOf(
                ClaimExtension(count, mapOf(first.id to priorities.first, second.id to priorities.second)),
            ),
        ).moduleRegistry

        /** 共用命令映射服務。 */
        val mapper = GameActionCommandMapper(ExtensionGameActionCommandFactoryRegistry().apply { freeze() })

        /** 正式捨牌反應用例。 */
        val useCase = RespondToDiscardUseCase(
            gameRepository = repository,
            moduleRegistry = modules,
            snapshotSynchronizer = GameSnapshotSynchronizer(repository, FakeGameSnapshotRepository(), GameVisibilityPolicyImpl(modules)),
            handSortPreferenceStore = HandSortPreferenceStore(),
            eventPublisher = events,
            presentationPublisher = FakeGamePresentationPublisher(),
            winPresentationHandoff = WinPresentationHandoff(),
            winCelebrationCueResolverRegistry = bundledWinCelebrationCueResolverRegistry(),
            winSettlementDetailResolverRegistry = WinSettlementDetailResolverRegistry().apply { freeze() },
            postActionExhaustiveDrawResolverRegistry = PostActionExhaustiveDrawResolverRegistry().apply { freeze() },
        )

        /** 鳴牌後接續執行的正式捨牌用例。 */
        val discard = DiscardTileUseCase(
            gameRepository = repository,
            moduleRegistry = modules,
            snapshotSynchronizer = GameSnapshotSynchronizer(repository, FakeGameSnapshotRepository(), GameVisibilityPolicyImpl(modules)),
            handSortPreferenceStore = HandSortPreferenceStore(),
            postActionExhaustiveDrawResolverRegistry = PostActionExhaustiveDrawResolverRegistry().apply { freeze() },
            eventPublisher = events,
            presentationPublisher = FakeGamePresentationPublisher(),
        )

        /**
         * 取得指定玩家可交出的完整選牌。
         *
         * @param playerId 選牌的玩家。
         * @return 本人的手牌 ID，維持原本順序。
         */
        fun selected(playerId: Uuid): List<Uuid> = table.players.first { it.id == playerId }.hand.tiles.take(count).map { it.id }
    }

    /**
     * 測試用日麻家族配置，讓自訂規則可透過公開 extension registry 獨立登記。
     *
     * @property delegate 提供與本測試無關的共用設定。
     */
    private data class ClaimRuleConfig(
        val delegate: RiichiRuleConfig = RiichiRuleConfig(),
    ) : RiichiFamilyRuleConfig by delegate

    /**
     * 透過公開 extension bootstrap 登記測試用自訂規則模組。
     *
     * @property count 自訂副露取用的本人手牌張數。
     * @property priorities 各反應者的測試優先序。
     */
    private class ClaimExtension(
        private val count: Int,
        private val priorities: Map<Uuid, Int>,
    ) : MahjongExtension {
        override val id: String = "test:claims"

        override fun registerRuleModules(registry: MahjongModuleRegistry) {
            registry.register(
                MahjongModuleDefinition(ClaimRuleConfig::class, id) { config, moduleId ->
                    ClaimRule(RiichiRuleModule(moduleId, config.delegate), count, priorities)
                },
            )
        }

        override fun registerNetworkDtos(registries: NetworkDtoRegistries) = Unit

        override fun registerPersistenceDtos(registries: PersistenceRegistries) = Unit
    }

    /**
     * 只覆寫捨牌反應的測試規則，其他行為委派給日麻規則。
     *
     * @property delegate 提供測試未覆寫的行為；型別參數只用於已覆寫的 [config]，不接收配置參數。
     * @property count 自訂副露取用的本人手牌張數。
     * @property priorities 各反應者的測試優先序。
     */
    @Suppress("UNCHECKED_CAST")
    private class ClaimRule(
        private val delegate: MahjongRuleModule<RiichiRuleConfig>,
        private val count: Int,
        private val priorities: Map<Uuid, Int>,
    ) : MahjongRuleModule<ClaimRuleConfig> by (delegate as MahjongRuleModule<ClaimRuleConfig>) {
        override val config: ClaimRuleConfig = ClaimRuleConfig(delegate.config)
        override fun createLegalActionValidator(): LegalActionValidator = object : LegalActionValidator {
            override fun getLegalActions(context: LegalActionContext): List<GameAction> = if (context.player.hand.tiles.size >= count) listOf(CLAIM, GameAction.Pass) else listOf(GameAction.Pass)

            override fun tileSelectionRequirement(
                tableState: TableState,
                player: MahjongPlayer,
                action: GameAction,
            ): TileSelectionRequirement? = if (action == CLAIM) TileSelectionRequirement(player.hand.tiles.map { it.id }.toSet(), count, count) else null
        }

        override fun createDiscardReactionPolicy(): DiscardReactionPolicy = DiscardReactionPolicy { context ->
            val ids = context.tableState.players.first { it.id == context.responderId }.hand.tiles.take(count).map { it.id }
            if (CLAIM !in context.legalActions) return@DiscardReactionPolicy listOf(DiscardReactionCandidate(GameAction.Pass, 0))
            listOf(
                DiscardReactionCandidate(GameAction.Pass, 0),
                DiscardReactionCandidate(
                    action = CLAIM,
                    priority = priorities.getValue(context.responderId),
                    selectedTileIds = ids,
                    claimEffect = MeldClaimEffect(
                        meldType = MeldType.Extension(MeldTypeId.parse("test:wide_claim")),
                        handTileIds = ids,
                        publiclyRevealedTileIds = setOf(context.discardedTile.id, ids.first()),
                        faceDownTileIds = ids.drop(1).toSet(),
                        requiresSupplementalDraw = false,
                    ),
                ),
            )
        }
    }

    /** 自訂反應的完整動作；選牌獨立於此 payload。 */
    private data object ClaimAction : ExtensionGameAction {
        override val id: String = "test:claim"
    }

    /** 驗證 own-turn factory 不會被反應路徑呼叫的命令。 */
    private data object OwnCommand : ExtensionGameCommand

    /** 測試共用的動作常數。 */
    private companion object {
        /** 反應查詢與提交共用的動作值。 */
        val CLAIM = GameAction.Extension(ClaimAction)
    }
}
