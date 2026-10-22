package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.extension.MahjongExtension
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.runAsSingleWrite
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinPresentationHandoff
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionContext
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionSource
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.logic.module.ActionScoring
import com.doublemoon1119.mahjongcraft.logic.module.ActionScoringContext
import com.doublemoon1119.mahjongcraft.logic.module.ActionScoringPolicy
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleDefinition
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.NoOpActionScoringPolicy
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiFamilyRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.DrawFollowUpDecision
import com.doublemoon1119.mahjongcraft.logic.table.DrawFollowUpPolicy
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.NoOpWallRevealPolicy
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.PlayerRuleState
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawDecision
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawPolicy
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealPolicy
import com.doublemoon1119.mahjongcraft.logic.table.Wind
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
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 以登記的測試規則驗證摸牌後的自動移出與補牌，以及動作成立當下的點數收付。 */
class DrawFollowUpAndActionScoringIntegrationTest {
    /** 摸到一般牌時不追加任何動作。 */
    @Test
    fun `drawing an ordinary tile adds no follow up`() = runTest {
        val fixture = Fixture()
        val ordinary = tile(ORDINARY)
        fixture.repository.setTableState(fixture.table(wall = listOf(ordinary, tile(ORDINARY))))

        assertIs<Outcome.Success<Unit>>(fixture.draw(fixture.gameId, fixture.drawerId))

        val drawer = fixture.drawer()
        assertEquals(ordinary, drawer.hand.lastDrawn)
        assertEquals(listOf(GameAction.Draw), fixture.recordedActions())
        assertTrue(fixture.presentation.getPublishedPlayerTiles(fixture.gameId)!!.followUpSteps.isEmpty())
    }

    /** 連續摸到花時在同一筆寫入內依序移出並補牌，直到補到一般牌。 */
    @Test
    fun `consecutive flowers are replaced within a single write`() = runTest {
        val fixture = Fixture()
        val firstFlower = tile(FLOWER)
        val secondFlower = tile(FLOWER)
        val ordinary = tile(ORDINARY)
        fixture.repository.setTableState(fixture.table(wall = listOf(firstFlower, ordinary, secondFlower)))

        val result = fixture.repository.runAsSingleWrite(fixture.gameId) { fixture.draw(fixture.gameId, fixture.drawerId) }

        assertIs<Outcome.Success<Unit>>(result)
        val drawer = fixture.drawer()
        assertEquals(ordinary, drawer.hand.lastDrawn)
        assertEquals(listOf(firstFlower, secondFlower), (drawer.playerRuleState as FlowerState).flowers)
        assertEquals(listOf(GameAction.Draw, FLOWER_ACTION, GameAction.Draw, FLOWER_ACTION, GameAction.Draw), fixture.recordedActions())
        assertEquals(
            listOf(GameAction.Draw, FLOWER_ACTION, GameAction.Draw, FLOWER_ACTION, GameAction.Draw),
            fixture.events.getNotifiedActions(fixture.gameId, fixture.drawerId, fixture.drawerId),
        )
        val published = assertNotNull(fixture.presentation.getPublishedPlayerTiles(fixture.gameId))
        assertEquals(ordinary.id, published.drawnTileId)
        assertEquals(listOf(firstFlower.id to secondFlower.id, secondFlower.id to ordinary.id), published.followUpSteps.map { it.setAsideTileId to it.drawnTileId })
        assertEquals(listOf(firstFlower.id, secondFlower.id), published.setAsideTileIds)
        assertTrue(published.isNewlyDrawn)
    }

    /** 移出手牌時規則更新的動態狀態在補牌後仍然保留。 */
    @Test
    fun `dynamic state updated by a set aside survives the replacement draw`() = runTest {
        val fixture = Fixture(TestBehavior(countsFlowersInDynamicState = true))
        fixture.repository.setTableState(fixture.table(wall = listOf(tile(FLOWER), tile(ORDINARY), tile(FLOWER))))

        assertIs<Outcome.Success<Unit>>(fixture.draw(fixture.gameId, fixture.drawerId))

        assertEquals(TestPot(2), fixture.repository.getTableState(fixture.gameId)!!.dynamicRuleState)
    }

    /** 摸到花卻已經沒有牌可以補時，依規則以流局結束本局。 */
    @Test
    fun `a flower with nothing left to replace ends the round`() = runTest {
        val fixture = Fixture()
        fixture.repository.setTableState(fixture.table(wall = listOf(tile(FLOWER))))

        assertIs<Outcome.Success<Unit>>(fixture.draw(fixture.gameId, fixture.drawerId))

        assertEquals(GameAction.ExhaustiveDraw(NoReplacementReason), fixture.drawer().actionHistory.last())
        assertEquals(listOf(GameAction.Draw, GameAction.ExhaustiveDraw(NoReplacementReason)), fixture.recordedActions())
    }

    /** 規則要求移出卻補不到牌時視為規則錯誤，桌況與副作用都不改變。 */
    @Test
    fun `a follow up that cannot draw a replacement is a contract violation`() = runTest {
        val fixture = Fixture(TestBehavior(ignoresEmptyWall = true))
        val table = fixture.table(wall = listOf(tile(FLOWER)))
        fixture.repository.setTableState(table)

        assertFailsWith<RuleContractViolationException> { fixture.draw(fixture.gameId, fixture.drawerId) }

        assertEquals(table, fixture.repository.getTableState(fixture.gameId))
        assertTrue(fixture.repository.historyDrafts.isEmpty())
        assertTrue(fixture.events.getNotifiedActions(fixture.gameId, fixture.drawerId, fixture.drawerId).isEmpty())
    }

    /** 移出手牌的結果改動了其他玩家時視為規則錯誤。 */
    @Test
    fun `a set aside result that changes another player is rejected`() = runTest {
        val fixture = Fixture(TestBehavior(setAsideChangesOthers = true))
        val table = fixture.table(wall = listOf(tile(FLOWER), tile(ORDINARY)))
        fixture.repository.setTableState(table)

        assertFailsWith<RuleContractViolationException> { fixture.draw(fixture.gameId, fixture.drawerId) }

        assertEquals(table, fixture.repository.getTableState(fixture.gameId))
    }

    /** 其他玩家可以搶花時開啟搶和視窗並暫停；全員放過後補牌並繼續判斷。 */
    @Test
    fun `a robbable flower pauses the chain until everyone passes`() = runTest {
        val behavior = TestBehavior(robbingPlayerIds = mutableSetOf())
        val fixture = Fixture(behavior)
        behavior.robbingPlayerIds += fixture.southId
        val flower = tile(FLOWER)
        val ordinary = tile(ORDINARY)
        fixture.repository.setTableState(fixture.table(wall = listOf(flower, ordinary)))

        assertIs<Outcome.Success<Unit>>(fixture.draw(fixture.gameId, fixture.drawerId))

        val paused = assertNotNull(fixture.repository.getTableState(fixture.gameId))
        val pending = assertNotNull(paused.pendingRobbingReaction)
        assertEquals(flower, pending.robbedTile)
        assertEquals(setOf(fixture.southId), pending.eligiblePlayerIds)
        assertEquals(flower, paused.players.first { it.id == fixture.drawerId }.hand.lastDrawn)
        assertEquals(listOf(GameAction.Draw, FLOWER_ACTION), fixture.recordedActions())
        val pausedStep = fixture.presentation.getPublishedPlayerTiles(fixture.gameId)!!.followUpSteps.single()
        assertEquals(flower.id, pausedStep.setAsideTileId)
        assertNull(pausedStep.drawnTileId)

        assertIs<Outcome.Success<Unit>>(fixture.respondToRobbing(fixture.gameId, fixture.southId, GameAction.Pass))

        val resumed = fixture.drawer()
        assertNull(fixture.repository.getTableState(fixture.gameId)!!.pendingRobbingReaction)
        assertEquals(ordinary, resumed.hand.lastDrawn)
        assertEquals(listOf(flower), (resumed.playerRuleState as FlowerState).flowers)
    }

    /** 槓後補到花時也會自動移出並再補牌；槓的收付可以把點數放進規則的點數池。 */
    @Test
    fun `a kan replacement flower is replaced and the kan can pay into a pot`() = runTest {
        val behavior = TestBehavior(
            scoring = { context ->
                (context.action as? GameAction.Kan)?.let {
                    ActionScoring(mapOf(context.actorPlayerId to -1000), potDeposit = 1000, dynamicRuleState = TestPot(1000))
                }
            },
        )
        val fixture = Fixture(behavior)
        val kanTile = tile(KAN_TILE)
        val flower = tile(FLOWER)
        val ordinary = tile(ORDINARY)
        val hand = Hand(tiles = List(3) { tile(KAN_TILE) } + fillers(9), lastDrawn = kanTile)
        fixture.repository.setTableState(fixture.table(drawerHand = hand, wall = listOf(ordinary, flower)))
        val kan = fixture.legalClosedKan()

        assertIs<Outcome.Success<Unit>>(fixture.kan(fixture.gameId, fixture.drawerId, kan.type, kan.tileId))

        val state = assertNotNull(fixture.repository.getTableState(fixture.gameId))
        val drawer = state.players.first { it.id == fixture.drawerId }
        assertEquals(ordinary, drawer.hand.lastDrawn)
        assertEquals(listOf(flower), (drawer.playerRuleState as FlowerState).flowers)
        assertEquals(INITIAL_SCORE - 1000, drawer.score)
        assertEquals(TestPot(1000), state.dynamicRuleState)
        assertEquals(listOf(kan, GameAction.Draw, FLOWER_ACTION, GameAction.Draw), fixture.recordedActions())
        val scored = fixture.repository.historyDrafts.mapNotNull { it.fact as? HistoryFact.ActionScored }.single()
        assertEquals(kan, scored.action)
        assertEquals(mapOf(fixture.drawerId to -1000), scored.scoreDeltas)
        assertEquals(1000, scored.potDeposit)
        assertEquals(1000, fixture.presentation.getPublishedActionScorings(fixture.gameId).single().potDeposit)
    }

    /** 碰成立當下由被碰的玩家付點數，收付寫入分數、牌譜並通知平台。 */
    @Test
    fun `a pon can make the discarder pay immediately`() = runTest {
        val behavior = TestBehavior(
            scoring = { context ->
                if (context.action is GameAction.Pon) {
                    ActionScoring(
                        mapOf(context.actorPlayerId to 2000, requireNotNull(context.sourcePlayerId) to -2000),
                        potDeposit = 0,
                        dynamicRuleState = context.tableStateAfterAction.dynamicRuleState,
                    )
                } else {
                    null
                }
            },
        )
        val fixture = Fixture(behavior)
        val discarded = tile(KAN_TILE)
        val matching = List(2) { tile(KAN_TILE) }
        fixture.repository.setTableState(fixture.ponTable(discarded, matching))

        assertIs<Outcome.Success<Unit>>(
            fixture.respondToDiscard(
                gameId = fixture.gameId,
                playerId = fixture.southId,
                action = GameAction.Pon(discarded.id, matching.map { it.id }),
                reactionId = fixture.repository.getTableState(fixture.gameId)!!.pendingReaction!!.id,
            ),
        )

        val state = assertNotNull(fixture.repository.getTableState(fixture.gameId))
        assertEquals(INITIAL_SCORE + 2000, state.players.first { it.id == fixture.southId }.score)
        assertEquals(INITIAL_SCORE - 2000, state.players.first { it.id == fixture.drawerId }.score)
        val scored = fixture.repository.historyDrafts.mapNotNull { it.fact as? HistoryFact.ActionScored }.single()
        assertEquals(mapOf(fixture.southId to 2000, fixture.drawerId to -2000), scored.scoreDeltas)
        assertEquals(fixture.southId, fixture.presentation.getPublishedActionScorings(fixture.gameId).single().actorPlayerId)
    }

    /** 收付不符合規定時視為規則錯誤，桌況不改變。 */
    @Test
    fun `invalid action scoring is a contract violation`() = runTest {
        val invalidScorings: List<(ActionScoringContext) -> ActionScoring> = listOf(
            { ActionScoring(mapOf(it.actorPlayerId to 1000), potDeposit = 0, dynamicRuleState = null) },
            { ActionScoring(mapOf(Uuid.random() to 1000, it.actorPlayerId to -1000), potDeposit = 0, dynamicRuleState = null) },
            { ActionScoring(mapOf(it.actorPlayerId to 0), potDeposit = 0, dynamicRuleState = null) },
            { ActionScoring(emptyMap(), potDeposit = 0, dynamicRuleState = null) },
            { ActionScoring(mapOf(it.actorPlayerId to Int.MAX_VALUE), potDeposit = Int.MIN_VALUE + 1, dynamicRuleState = null) },
        )
        invalidScorings.forEach { invalid ->
            val fixture = Fixture(TestBehavior(scoring = { context -> invalid(context).takeIf { context.action == FLOWER_ACTION } }))
            val table = fixture.table(wall = listOf(tile(FLOWER), tile(ORDINARY)))
            fixture.repository.setTableState(table)

            assertFailsWith<RuleContractViolationException> { fixture.draw(fixture.gameId, fixture.drawerId) }

            assertEquals(table, fixture.repository.getTableState(fixture.gameId))
        }
    }

    /** 暫停在搶和視窗的移出還沒有成立，不收付點數。 */
    @Test
    fun `a set aside waiting for robbing responses is not scored`() = runTest {
        var scoringCalls = 0
        val behavior = TestBehavior(
            robbingPlayerIds = mutableSetOf(),
            scoring = {
                scoringCalls++
                null
            },
        )
        val fixture = Fixture(behavior)
        behavior.robbingPlayerIds += fixture.southId
        fixture.repository.setTableState(fixture.table(wall = listOf(tile(FLOWER), tile(ORDINARY))))

        assertIs<Outcome.Success<Unit>>(fixture.draw(fixture.gameId, fixture.drawerId))

        assertEquals(0, scoringCalls)
    }

    /** 摸牌後的掛勾只能修改規則狀態；改動分數時中止。 */
    @Test
    fun `a draw hook that changes the score is rejected`() = runTest {
        val fixture = Fixture(TestBehavior(hooksChangeScore = true))
        val table = fixture.table(wall = listOf(tile(ORDINARY)))
        fixture.repository.setTableState(table)

        assertFailsWith<RuleContractViolationException> { fixture.draw(fixture.gameId, fixture.drawerId) }

        assertEquals(table, fixture.repository.getTableState(fixture.gameId))
    }

    /** 鳴牌後的掛勾只能修改規則狀態；改動分數時中止。 */
    @Test
    fun `a meld hook that changes the score is rejected`() = runTest {
        val fixture = Fixture(TestBehavior(hooksChangeScore = true))
        val hand = Hand(tiles = List(3) { tile(KAN_TILE) } + fillers(9), lastDrawn = tile(KAN_TILE))
        val table = fixture.table(drawerHand = hand, wall = listOf(tile(ORDINARY), tile(ORDINARY)))
        fixture.repository.setTableState(table)
        val kan = fixture.legalClosedKan()

        assertFailsWith<RuleContractViolationException> { fixture.kan(fixture.gameId, fixture.drawerId, kan.type, kan.tileId) }

        assertEquals(table, fixture.repository.getTableState(fixture.gameId))
    }

    /**
     * 測試規則的可調整行為。
     *
     * @property scoring 動作即時收付；null 時不收付。
     * @property robbingPlayerIds 可以搶花的玩家。
     * @property ignoresEmptyWall 摸到花時是否不檢查牌山，直接要求移出。
     * @property setAsideChangesOthers 移出手牌的結果是否違規改動其他玩家。
     * @property hooksChangeScore 摸牌與鳴牌後的掛勾是否違規改動分數。
     * @property countsFlowersInDynamicState 移出花牌時是否把已移出的張數寫進規則動態狀態。
     */
    private class TestBehavior(
        val scoring: ((ActionScoringContext) -> ActionScoring?)? = null,
        val robbingPlayerIds: MutableSet<Uuid> = mutableSetOf(),
        val ignoresEmptyWall: Boolean = false,
        val setAsideChangesOthers: Boolean = false,
        val hooksChangeScore: Boolean = false,
        val countsFlowersInDynamicState: Boolean = false,
    )

    /**
     * 測試用的三人桌與正式用例。
     *
     * @param behavior 測試規則的行為。
     */
    private class Fixture(behavior: TestBehavior = TestBehavior()) {
        /** 對局身分。 */
        val gameId = Uuid.random()

        /** 目前輪到摸牌的玩家。 */
        val drawerId = Uuid.random()

        /** 下家。 */
        val southId = Uuid.random()

        /** 第三位玩家。 */
        val westId = Uuid.random()

        /** 保存狀態與歷史的替身。 */
        val repository = FakeGameRepository()

        /** 記錄廣播動作的替身。 */
        val events = FakeGameEventPublisher()

        /** 記錄呈現要求的替身。 */
        val presentation = FakeGamePresentationPublisher()

        /** 透過公開 extension bootstrap 登記的規則。 */
        private val modules = bundledCoreExtensionRegistries(additionalExtensions = listOf(FlowerExtension(behavior))).moduleRegistry

        /** 共用的快照同步。 */
        private val snapshots = GameSnapshotSynchronizer(repository, FakeGameSnapshotRepository(), GameVisibilityPolicyImpl(modules))

        /** 正式摸牌用例。 */
        val draw = DrawTileUseCase(repository, modules, snapshots, events, presentation)

        /** 正式暗槓與加槓用例。 */
        val kan = DeclareKanUseCase(repository, modules, snapshots, events, presentation)

        /** 正式搶和回應用例。 */
        val respondToRobbing = RespondToRobbingUseCase(
            gameRepository = repository,
            moduleRegistry = modules,
            snapshotSynchronizer = snapshots,
            eventPublisher = events,
            presentationPublisher = presentation,
            winPresentationHandoff = WinPresentationHandoff(),
            winCelebrationCueResolverRegistry = bundledWinCelebrationCueResolverRegistry(),
            winSettlementDetailResolverRegistry = WinSettlementDetailResolverRegistry().apply { freeze() },
        )

        /** 正式捨牌反應用例。 */
        val respondToDiscard = RespondToDiscardUseCase(
            gameRepository = repository,
            moduleRegistry = modules,
            snapshotSynchronizer = snapshots,
            handSortPreferenceStore = HandSortPreferenceStore(),
            eventPublisher = events,
            presentationPublisher = presentation,
            winPresentationHandoff = WinPresentationHandoff(),
            winCelebrationCueResolverRegistry = bundledWinCelebrationCueResolverRegistry(),
            winSettlementDetailResolverRegistry = WinSettlementDetailResolverRegistry().apply { freeze() },
            postActionExhaustiveDrawResolverRegistry = PostActionExhaustiveDrawResolverRegistry().apply { freeze() },
        )

        /**
         * 建立輪到 [drawerId] 摸牌的桌況。
         *
         * @param drawerHand 摸牌玩家的手牌。
         * @param wall 活牌，依摸牌順序排列；補牌從尾端取。
         * @return 測試桌況。
         */
        fun table(
            drawerHand: Hand = Hand(tiles = fillers(13)),
            wall: List<IdentifiedTile> = listOf(tile(ORDINARY)),
        ): TableState = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(
                player(drawerId, Wind.EAST, drawerHand),
                player(southId, Wind.SOUTH, Hand(tiles = fillers(13))),
                player(westId, Wind.WEST, Hand(tiles = fillers(13))),
            ),
            config = FlowerRuleConfig(),
            tileWall = TileWall(wall),
            currentPlayerIndex = 0,
        )

        /**
         * 建立 [southId] 可以碰 [drawerId] 捨牌的桌況。
         *
         * @param discarded 被碰的捨牌。
         * @param matching 下家手上兩張相同的牌。
         * @return 測試桌況。
         */
        fun ponTable(discarded: IdentifiedTile, matching: List<IdentifiedTile>): TableState = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(
                player(drawerId, Wind.EAST, Hand(tiles = fillers(12))).copy(discardPile = FakeDiscardPile().discardTile(discarded)),
                player(southId, Wind.SOUTH, Hand(tiles = matching + fillers(11))),
                player(westId, Wind.WEST, Hand(tiles = fillers(13))),
            ),
            config = FlowerRuleConfig(),
            tileWall = TileWall(listOf(tile(ORDINARY))),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(drawerId, discarded.id, setOf(southId), ronPlayerIds = emptySet(), id = Uuid.random()),
        )

        /** @return 目前保存的摸牌玩家。 */
        suspend fun drawer(): MahjongPlayer = repository.getTableState(gameId)!!.players.first { it.id == drawerId }

        /** @return 牌譜依序記錄的已接受動作。 */
        fun recordedActions(): List<GameAction> = repository.historyDrafts.mapNotNull { (it.fact as? HistoryFact.ActionAccepted)?.action }

        /** @return 摸牌玩家目前可以宣告的暗槓。 */
        suspend fun legalClosedKan(): GameAction.Kan {
            val state = repository.getTableState(gameId)!!
            return modules.getModule(state.config).createLegalActionValidator()
                .getLegalActions(LegalActionContext.ownTurn(state, drawerId))
                .filterIsInstance<GameAction.Kan>()
                .single { it.type == GameAction.KanType.CLOSED_KAN }
        }
    }

    /**
     * 記錄已移出的花牌的測試玩家規則狀態。
     *
     * @property flowers 依移出順序排列的花牌。
     */
    private data class FlowerState(val flowers: List<IdentifiedTile>) : PlayerRuleState

    /**
     * 只記錄點數池餘額的測試規則動態狀態。
     *
     * @property balance 點數池餘額。
     */
    private data class TestPot(val balance: Int) : DynamicRuleState

    /**
     * 測試規則的配置，委派日麻設定。
     *
     * @property delegate 提供與本測試無關的共用設定。
     */
    private data class FlowerRuleConfig(
        val delegate: RiichiRuleConfig = RiichiRuleConfig(),
    ) : RiichiFamilyRuleConfig by delegate

    /**
     * 透過公開 extension bootstrap 登記測試規則。
     *
     * @property behavior 測試規則的行為。
     */
    private class FlowerExtension(private val behavior: TestBehavior) : MahjongExtension {
        override val id: String = "test:flowers"

        override fun registerRuleModules(registry: MahjongModuleRegistry) {
            registry.register(
                MahjongModuleDefinition(FlowerRuleConfig::class, id) { config, moduleId ->
                    FlowerRule(RiichiRuleModule(moduleId, config.delegate), behavior)
                },
            )
        }

        override fun registerNetworkDtos(registries: NetworkDtoRegistries) = Unit

        override fun registerPersistenceDtos(registries: PersistenceRegistries) = Unit
    }

    /**
     * 摸到花自動移出、從牌山尾端補牌的測試規則，其他行為委派給日麻。
     *
     * @property delegate 提供測試未覆寫的行為；型別參數只用於已覆寫的 [config]。
     * @property behavior 測試規則的行為。
     */
    @Suppress("UNCHECKED_CAST")
    private class FlowerRule(
        private val delegate: MahjongRuleModule<RiichiRuleConfig>,
        private val behavior: TestBehavior,
    ) : MahjongRuleModule<FlowerRuleConfig> by (delegate as MahjongRuleModule<FlowerRuleConfig>) {
        override val config: FlowerRuleConfig = FlowerRuleConfig(delegate.config)

        override fun createWallRevealPolicy(): WallRevealPolicy = NoOpWallRevealPolicy

        override fun createDrawFollowUpPolicy(): DrawFollowUpPolicy = DrawFollowUpPolicy { context ->
            when {
                context.drawnTile.tile != FLOWER -> DrawFollowUpDecision.None
                context.tableState.tileWall.remainingCount == 0 && !behavior.ignoresEmptyWall -> DrawFollowUpDecision.EndRound(NoReplacementReason)
                else -> DrawFollowUpDecision.SetAside(FLOWER_ACTION)
            }
        }

        override fun createSupplementalDrawPolicy(): SupplementalDrawPolicy = SupplementalDrawPolicy { context ->
            val candidate = context.tableStateAfterAction
            val (replacement, wall) = candidate.tileWall.drawLast()
            if (replacement == null) {
                SupplementalDrawDecision.Rejected(SupplementalDrawReasonIds.WALL_EXHAUSTED)
            } else {
                SupplementalDrawDecision.Completed(listOf(replacement), wall, candidate.reservedWallTiles, candidate.dynamicRuleState)
            }
        }

        override fun createActionScoringPolicy(): ActionScoringPolicy = behavior.scoring?.let { scoring -> ActionScoringPolicy(scoring) } ?: NoOpActionScoringPolicy

        override fun tileSetAsideBy(player: MahjongPlayer, action: GameAction.Extension): IdentifiedTile? = player.hand.lastDrawn?.takeIf { action == FLOWER_ACTION && it.tile == FLOWER }

        override fun applyTileSetAsideAction(
            tableState: TableState,
            actorPlayerId: Uuid,
            action: GameAction.Extension,
        ): TableState? {
            val actor = tableState.players.first { it.id == actorPlayerId }
            val flower = tileSetAsideBy(actor, action) ?: return null
            val flowers = (actor.playerRuleState as? FlowerState)?.flowers.orEmpty() + flower
            val updated = actor.copy(
                hand = actor.hand.copy(lastDrawn = null),
                playerRuleState = FlowerState(flowers),
            ).recordAction(action)
            return tableState.copy(
                dynamicRuleState = if (behavior.countsFlowersInDynamicState) TestPot(flowers.size) else tableState.dynamicRuleState,
                players = tableState.players.map { player ->
                    when {
                        player.id == actorPlayerId -> updated
                        behavior.setAsideChangesOthers -> player.copy(score = player.score + 1)
                        else -> player
                    }
                },
            )
        }

        override fun setAsideTiles(player: MahjongPlayer): List<IdentifiedTile> = (player.playerRuleState as? FlowerState)?.flowers.orEmpty()

        override fun onPlayerDrew(player: MahjongPlayer): MahjongPlayer = if (behavior.hooksChangeScore) player.copy(score = player.score + 1) else delegate.onPlayerDrew(player)

        override fun onMeldClaimed(players: List<MahjongPlayer>): List<MahjongPlayer> = if (behavior.hooksChangeScore) players.map { it.copy(score = it.score + 1) } else delegate.onMeldClaimed(players)

        override fun createLegalActionValidator(): LegalActionValidator {
            val base = delegate.createLegalActionValidator()
            return object : LegalActionValidator by base {
                override fun getLegalActions(context: LegalActionContext): List<GameAction> {
                    val source = context.source
                    if (source is LegalActionSource.Declaration && source.action == FLOWER_ACTION) {
                        return if (context.player.id in behavior.robbingPlayerIds) {
                            listOf(GameAction.Ron(source.tile.id), GameAction.Pass)
                        } else {
                            listOf(GameAction.Pass)
                        }
                    }
                    return base.getLegalActions(context)
                }
            }
        }
    }

    /** 測試規則自動移出花牌的動作。 */
    private data object FlowerAction : ExtensionGameAction {
        override val id: String = "test:flower"
    }

    /** 摸到花卻沒有牌可以補時的流局原因。 */
    private data object NoReplacementReason : ExhaustiveDrawReason {
        override val id: String = "test:no_flower_replacement"
    }

    /** 測試共用的牌面與常數。 */
    private companion object {
        /** 測試規則當作花牌的牌面。 */
        val FLOWER: Tile = Tile.Honor.White

        /** 一般牌。 */
        val ORDINARY: Tile = Tile.Numeric(Tile.Suit.Character, 5)

        /** 暗槓與碰使用的牌面。 */
        val KAN_TILE: Tile = Tile.Numeric(Tile.Suit.Character, 1)

        /** 自動移出花牌的完整動作。 */
        val FLOWER_ACTION = GameAction.Extension(FlowerAction)

        /** 玩家的初始分數。 */
        const val INITIAL_SCORE: Int = 25000

        /**
         * 建立一張實體牌。
         *
         * @param face 牌面。
         * @return 新的實體牌。
         */
        fun tile(face: Tile): IdentifiedTile = FakeIdentifiedTileFactory.create(face)

        /**
         * 建立不會成組的填充牌。
         *
         * @param count 張數。
         * @return 依序排列的填充牌。
         */
        fun fillers(count: Int): List<IdentifiedTile> = List(count) { index ->
            tile(Tile.Numeric(Tile.Suit.entries[index % Tile.Suit.entries.size], 2 + index / Tile.Suit.entries.size % 4 * 2))
        }

        /**
         * 建立測試玩家。
         *
         * @param id 玩家身分。
         * @param wind 座位風。
         * @param hand 手牌。
         * @return 測試玩家。
         */
        fun player(id: Uuid, wind: Wind, hand: Hand): MahjongPlayer = FakeMahjongPlayerFactory.create(initialSeat = wind, id = id, hand = hand).copy(score = INITIAL_SCORE)
    }
}
