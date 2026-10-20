package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DiscardReactionResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.GetLegalActionsUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.RespondToDiscardUseCase
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionContext
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionSource
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleDefinition
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 驗證捨牌反應選項會經過玩家與 AI 共用的合法動作查詢與反應解析鏈。 */
class DiscardReactionOptionsTest {
    /** 擴充反應、暗槓與加槓不應穿過共用查詢成為可送出的捨牌反應。 */
    @Test
    fun `legal action query keeps only supported discard reactions`() {
        val fixture = createFixture()

        val result = fixture.useCase.resolve(fixture.table, fixture.respondent.id)

        assertEquals(
            listOf(
                GameAction.Pon(fixture.discarded.id, listOf(fixture.handTileA.id, fixture.handTileB.id)),
                GameAction.Pass,
            ),
            assertIs<Outcome.Success<List<GameAction>>>(result).value,
        )
    }

    /** 反應解析器只用過濾後的實際資格建立等待視窗，合法碰仍會保留。 */
    @Test
    fun `discard reaction resolver creates pending reaction for supported pon only`() {
        val fixture = createFixture()

        val result = DiscardReactionResolver.resolve(
            stateBeforeDiscard = fixture.table.copy(pendingReaction = null),
            stateAfterDiscard = fixture.table.copy(pendingReaction = null),
            module = fixture.module,
            discarderId = fixture.discarder.id,
            discardedTile = fixture.discarded,
        )

        assertTrue(result.abortiveDrawReason == null)
        assertEquals(
            PendingReaction(
                discarderId = fixture.discarder.id,
                tileId = fixture.discarded.id,
                eligiblePlayerIds = setOf(fixture.respondent.id),
            ),
            result.tableState.pendingReaction,
        )
    }

    /** 沒有任何受支援的鳴牌資格時，不應因規則回傳的擴充或錯誤槓選項開啟等待視窗。 */
    @Test
    fun `discard reaction resolver does not create pending reaction without supported pon`() {
        val fixture = createFixture(includePon = false)

        val result = DiscardReactionResolver.resolve(
            stateBeforeDiscard = fixture.table.copy(pendingReaction = null),
            stateAfterDiscard = fixture.table.copy(pendingReaction = null),
            module = fixture.module,
            discarderId = fixture.discarder.id,
            discardedTile = fixture.discarded,
        )

        assertTrue(result.abortiveDrawReason == null)
        assertTrue(result.tableState.pendingReaction == null)
    }

    /** 真正的回應用例也必須拒絕規則回傳但共用流程不支援的擴充反應，且不留下任何副作用。 */
    @Test
    fun `respond to discard rejects unsupported extension without changing state`() = runTest {
        val fixture = createFixture()
        val repository = FakeGameRepository().apply { setTableState(fixture.table) }
        val moduleRegistry = MahjongModuleRegistryImpl().apply {
            register(
                MahjongModuleDefinition(RiichiRuleConfig::class, TEST_RULE_ID) { config, _ ->
                    TestRuleModule(RiichiRuleModule(TEST_RULE_ID, config))
                },
            )
            freeze()
        }
        val snapshotSynchronizer = GameSnapshotSynchronizer(
            repository,
            FakeGameSnapshotRepository(),
            GameVisibilityPolicyImpl(moduleRegistry),
        )
        val winDetails = WinSettlementDetailResolverRegistry().apply { freeze() }
        val useCase = RespondToDiscardUseCase(
            gameRepository = repository,
            moduleRegistry = moduleRegistry,
            snapshotSynchronizer = snapshotSynchronizer,
            handSortPreferenceStore = HandSortPreferenceStore(),
            eventPublisher = FakeGameEventPublisher(),
            presentationPublisher = FakeGamePresentationPublisher(),
            winPresentationHandoff = WinPresentationHandoff(),
            winCelebrationCueResolverRegistry = bundledWinCelebrationCueResolverRegistry(),
            winSettlementDetailResolverRegistry = winDetails,
            postActionExhaustiveDrawResolverRegistry = PostActionExhaustiveDrawResolverRegistry().apply { freeze() },
        )

        val result = useCase(
            gameId = fixture.table.id,
            playerId = fixture.respondent.id,
            action = GameAction.Extension(TestReactionAction),
        )

        assertTrue(result is Outcome.Error)
        assertEquals(fixture.table, repository.getTableState(fixture.table.id))
        assertTrue(repository.historyDrafts.isEmpty())
    }

    /**
     * 建立包含捨牌反應視窗的測試桌況。
     *
     * @param includePon 是否讓測試規則提供合法碰資格。
     */
    private fun createFixture(includePon: Boolean = true): Fixture {
        val discarded = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val handTileA = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val handTileB = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val discarder = FakeMahjongPlayerFactory.create(
            discardPile = FakeDiscardPile().discardTile(discarded),
        )
        val respondent = FakeMahjongPlayerFactory.create(
            hand = Hand(tiles = listOf(handTileA, handTileB)),
        )
        val table = FakeTableStateFactory.create(
            players = listOf(discarder, respondent),
            config = RiichiRuleConfig(),
            pendingReaction = PendingReaction(discarder.id, discarded.id, setOf(respondent.id)),
        )
        val module = TestRuleModule(RiichiRuleModule(TEST_RULE_ID, RiichiRuleConfig()), includePon)
        return Fixture(
            table = table,
            discarder = discarder,
            respondent = respondent,
            discarded = discarded,
            handTileA = handTileA,
            handTileB = handTileB,
            module = module,
            useCase = GetLegalActionsUseCase(
                gameRepository = FakeGameRepository(),
                moduleRegistry = MahjongModuleRegistryImpl().apply {
                    register(
                        MahjongModuleDefinition(RiichiRuleConfig::class, TEST_RULE_ID) { config, _ ->
                            TestRuleModule(RiichiRuleModule(TEST_RULE_ID, config), includePon)
                        },
                    )
                    freeze()
                },
            ),
        )
    }

    /**
     * 捨牌反應測試所需的權威桌況、規則模組與查詢用例。
     *
     * @property table 捨牌反應前的桌況。
     * @property discarder 打出牌的玩家。
     * @property respondent 等待反應的玩家。
     * @property discarded 其他玩家剛打出的牌。
     * @property handTileA 反應玩家用於碰牌的第一張手牌。
     * @property handTileB 反應玩家用於碰牌的第二張手牌。
     * @property module 直接交給反應解析器的測試規則模組。
     * @property useCase 透過 registry 查詢合法動作的用例。
     */
    private data class Fixture(
        val table: TableState,
        val discarder: MahjongPlayer,
        val respondent: MahjongPlayer,
        val discarded: IdentifiedTile,
        val handTileA: IdentifiedTile,
        val handTileB: IdentifiedTile,
        val module: TestRuleModule,
        val useCase: GetLegalActionsUseCase,
    )

    /**
     * 只替換反應資格查詢的測試規則模組，其餘行為委派給正式日麻規則。
     *
     * @param delegate 實際提供其他規則行為的日麻規則模組。
     * @param includePon 是否在捨牌反應資格中提供合法碰。
     */
    private class TestRuleModule(
        private val delegate: MahjongRuleModule<RiichiRuleConfig>,
        private val includePon: Boolean = true,
    ) : MahjongRuleModule<RiichiRuleConfig> by delegate {
        override fun createLegalActionValidator(): LegalActionValidator = object : LegalActionValidator {
            override fun getLegalActions(context: LegalActionContext): List<GameAction> {
                if (context.source is LegalActionSource.Discard) {
                    val incomingTile = context.incomingTile ?: return emptyList()
                    return buildList {
                        add(GameAction.Extension(TestReactionAction))
                        if (includePon) {
                            add(GameAction.Pon(incomingTile.id, context.player.hand.tiles.take(2).map { it.id }))
                        }
                        add(GameAction.Pass)
                        add(GameAction.Kan(GameAction.KanType.CLOSED_KAN, incomingTile.id, emptyList()))
                        add(GameAction.Kan(GameAction.KanType.ADDED_KAN, incomingTile.id, emptyList()))
                    }
                }
                return delegate.createLegalActionValidator().getLegalActions(context)
            }
        }
    }

    /** 代表規則可能回傳、但目前流程不支援的擴充反應。 */
    private data object TestReactionAction : ExtensionGameAction {
        override val id: String = "test:reaction"
    }

    private companion object {
        /** 測試規則模組的識別碼。 */
        const val TEST_RULE_ID = "test:reaction-rule"
    }
}
