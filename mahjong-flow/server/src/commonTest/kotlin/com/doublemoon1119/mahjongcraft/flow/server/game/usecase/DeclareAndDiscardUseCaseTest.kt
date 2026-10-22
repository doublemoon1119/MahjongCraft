package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.PostActionExhaustiveDrawResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.runAsSingleWrite
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.GameAction.Extension
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionContext
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.logic.judgment.TileSelectionRequirement
import com.doublemoon1119.mahjongcraft.logic.module.DeclarationDiscardEffect
import com.doublemoon1119.mahjongcraft.logic.module.DeclarationDiscardPolicy
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleDefinition
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardEntry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.PlayerRuleState
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWallRevealable
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealCheckpoint
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealDecision
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealPolicy
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.FakeGameSnapshotRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
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

/** 驗證第三方宣告 policy 能使用共用流程，且未授權的玩家變更會被拒絕。 */
class DeclareAndDiscardUseCaseTest {
    /** 第三方宣告動作。 */
    private data object DeclarationAction : ExtensionGameAction {
        override val id: String = "test:declare"
    }

    /**
     * 建立可重複使用的第三方宣告測試環境。
     *
     * @param malicious 是否要求規則產生未授權的分數變更。
     * @param cost 本次宣告需支付的點數。
     * @param rejectWallReveal 是否讓宣告後的牌牆公開檢查拒絕。
     * @param mustDiscardAfterMeld 是否模擬剛完成自訂鳴牌、等待捨牌的玩家。
     * @param revealWallTile 是否讓宣告後的牌牆公開檢查公開一張牌山牌。
     * @param tsumoAvailable 宣告者是否同時可以自摸。
     * @param hookMode 放棄自摸與打牌後掛勾的測試行為；null 時不覆寫這兩個掛勾。
     */
    private class Fixture(
        malicious: Boolean = false,
        cost: Int = 0,
        rejectWallReveal: Boolean = false,
        mustDiscardAfterMeld: Boolean = false,
        revealWallTile: Boolean = false,
        tsumoAvailable: Boolean = false,
        hookMode: HookMode? = null,
    ) {
        /** 宣告者身分。 */
        val playerId = Uuid.random()

        /** 對局身分。 */
        val gameId = Uuid.random()

        /** 宣告時選取的捨牌。 */
        val tile = FakeIdentifiedTileFactory.create(Tile.Honor.White)

        /** 記錄共用宣告流程實際執行的牌牆公開 checkpoint。 */
        val wallRevealCheckpoints = mutableListOf<WallRevealCheckpoint>()

        /** 宣告前的玩家狀態。 */
        val player = FakeMahjongPlayerFactory.create(
            id = playerId,
            hand = Hand(lastDrawn = tile),
            discardPile = RiichiDiscardPile(),
        ).copy(score = 100, mustDiscardAfterMeld = mustDiscardAfterMeld)

        /** 保有規則動態狀態的宣告前桌況。 */
        val table: TableState = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(player),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            dynamicRuleState = RiichiDynamicState(riichiStickCount = 2),
        )

        /** 記錄權威寫入與歷史的測試倉庫。 */
        val repository = FakeGameRepository()

        /** 登記第三方宣告規則的正式模組註冊表。 */
        private val modules = MahjongModuleRegistryImpl().apply {
            register(
                MahjongModuleDefinition(RiichiRuleConfig::class, "test:declaration") { config, _ ->
                    DeclarationRule(
                        delegate = RiichiRuleModule("test:declaration", config),
                        malicious = malicious,
                        cost = cost,
                        wallRevealCheckpoints = wallRevealCheckpoints,
                        rejectWallReveal = rejectWallReveal,
                        revealWallTile = revealWallTile,
                        tsumoAvailable = tsumoAvailable,
                        hookMode = hookMode,
                    )
                },
            )
            freeze()
        }

        /** 接收正式流程事件的替身。 */
        val eventPublisher = FakeGameEventPublisher()

        /** 接收正式呈現要求的替身。 */
        val presentationPublisher = FakeGamePresentationPublisher()

        /** 保存同步快照的替身。 */
        val snapshots = FakeGameSnapshotRepository()

        /** 使用正式可見性投影的同步服務。 */
        private val snapshotSynchronizer = GameSnapshotSynchronizer(
            repository,
            snapshots,
            GameVisibilityPolicyImpl(modules),
        )

        /** 受測的共用宣告用例。 */
        val useCase = DeclareAndDiscardUseCase(
            gameRepository = repository,
            moduleRegistry = modules,
            snapshotSynchronizer = snapshotSynchronizer,
            handSortPreferenceStore = HandSortPreferenceStore(),
            postActionExhaustiveDrawResolverRegistry = PostActionExhaustiveDrawResolverRegistry().apply { freeze() },
            eventPublisher = eventPublisher,
            presentationPublisher = presentationPublisher,
        )
    }

    /** 第三方 policy 的合法效果可以透過共用流程完成宣告與捨牌。 */
    @Test
    fun `third party declaration succeeds through shared flow`() = runTest {
        val fixture = Fixture(cost = 15)
        fixture.repository.setTableState(fixture.table)
        fixture.snapshots.setSnapshot(
            fixture.playerId,
            fixture.table.toSnapshot(setOf(fixture.playerId), setAsideTiles = { emptyList() }),
        )

        val result = fixture.repository.runAsSingleWrite(fixture.gameId) {
            fixture.useCase(
                gameId = fixture.gameId,
                playerId = fixture.playerId,
                action = Extension(DeclarationAction),
                tileId = fixture.tile.id,
            )
        }

        assertIs<Outcome.Success<Unit>>(result)
        val updated = fixture.repository.getTableState(fixture.gameId)!!.players.single()
        assertEquals(listOf(GameAction.Extension(DeclarationAction), GameAction.Discard(fixture.tile.id)), updated.actionHistory)
        assertEquals(listOf(fixture.tile.id), updated.discardPile.entries.map { it.tile.id })
        assertEquals(85, updated.score)
        assertEquals(fixture.table.dynamicRuleState, fixture.repository.getTableState(fixture.gameId)?.dynamicRuleState)
        val actions = fixture.repository.historyDrafts.map { assertIs<HistoryFact.ActionAccepted>(it.fact).action }
        assertEquals(updated.actionHistory, actions)
        assertEquals(actions, fixture.eventPublisher.getNotifiedActions(fixture.gameId, fixture.playerId, fixture.playerId))
        assertNotNull(fixture.snapshots.getSnapshot(fixture.gameId, fixture.playerId))
        assertEquals(1, fixture.presentationPublisher.getRuleStateUpdateCount(fixture.gameId))
        assertNotNull(fixture.presentationPublisher.getPublishedPlayerTiles(fixture.gameId))
        assertNotNull(fixture.presentationPublisher.getPublishedRoundInfo(fixture.gameId))
        assertEquals(listOf(WallRevealCheckpoint.AFTER_DISCARD_REACTIONS), fixture.wallRevealCheckpoints)
    }

    /** 牌牆公開被規則拒絕時，宣告與捨牌不得留下半套寫入或副作用。 */
    @Test
    fun `rejected post declaration wall reveal is atomic`() = runTest {
        val fixture = Fixture(rejectWallReveal = true)
        fixture.repository.setTableState(fixture.table)

        val result = fixture.useCase(
            gameId = fixture.gameId,
            playerId = fixture.playerId,
            action = Extension(DeclarationAction),
            tileId = fixture.tile.id,
        )

        assertIs<Outcome.Error<*>>(result)
        assertEquals(fixture.table, fixture.repository.getTableState(fixture.gameId))
        assertEquals(listOf(WallRevealCheckpoint.AFTER_DISCARD_REACTIONS), fixture.wallRevealCheckpoints)
        assertTrue(fixture.repository.historyDrafts.isEmpty())
        assertTrue(fixture.eventPublisher.getNotifiedActions(fixture.gameId, fixture.playerId, fixture.playerId).isEmpty())
        assertNull(fixture.snapshots.getSnapshot(fixture.gameId, fixture.playerId))
        assertTrue(fixture.presentationPublisher.getDeclaredGameActions(fixture.gameId).isEmpty())
    }

    /** 宣告後的捨牌會清除自訂鳴牌留下的必須直接捨牌狀態。 */
    @Test
    fun `declaration discard clears must discard after meld`() = runTest {
        val fixture = Fixture(mustDiscardAfterMeld = true)
        fixture.repository.setTableState(fixture.table)

        val result = fixture.useCase(
            gameId = fixture.gameId,
            playerId = fixture.playerId,
            action = Extension(DeclarationAction),
            tileId = fixture.tile.id,
        )

        assertIs<Outcome.Success<Unit>>(result)
        assertEquals(false, fixture.repository.getTableState(fixture.gameId)!!.players.single().mustDiscardAfterMeld)
    }

    /** 宣告後的牌牆公開檢查公開新牌時，與一般捨牌一樣通知桌面呈現。 */
    @Test
    fun `wall tiles revealed after the declaration discard are published`() = runTest {
        val fixture = Fixture(revealWallTile = true)
        fixture.repository.setTableState(fixture.table)

        val result = fixture.useCase(
            gameId = fixture.gameId,
            playerId = fixture.playerId,
            action = Extension(DeclarationAction),
            tileId = fixture.tile.id,
        )

        assertIs<Outcome.Success<Unit>>(result)
        assertEquals(
            setOf(fixture.table.tileWall.getAllTiles().first().id),
            fixture.presentationPublisher.getPublishedDeadWallReveal(fixture.gameId),
        )
    }

    /** 宣告附帶的捨牌與一般捨牌相同：先套用放棄自摸與打牌後的規則掛勾，再套用宣告效果。 */
    @Test
    fun `declaration discard runs the discard hooks before the declaration effect`() = runTest {
        val fixture = Fixture(cost = 15, tsumoAvailable = true, hookMode = HookMode.RECORD_ORDER)
        fixture.repository.setTableState(fixture.table)

        val result = fixture.useCase(
            gameId = fixture.gameId,
            playerId = fixture.playerId,
            action = Extension(DeclarationAction),
            tileId = fixture.tile.id,
        )

        assertIs<Outcome.Success<Unit>>(result)
        val player = fixture.repository.getTableState(fixture.gameId)!!.players.single()
        assertEquals(HookTrail(listOf(DECLINED_WIN_MARK, DISCARDED_MARK, DECLARED_MARK)), player.playerRuleState)
        assertEquals(100 - 15, player.score)
    }

    /** 放棄自摸或打牌後的掛勾只能修改規則狀態；改動分數時中止，桌況保持不變。 */
    @Test
    fun `discard hook that changes the score is rejected atomically`() = runTest {
        val fixture = Fixture(tsumoAvailable = true, hookMode = HookMode.CHANGE_SCORE)
        fixture.repository.setTableState(fixture.table)

        assertFailsWith<RuleContractViolationException> {
            fixture.useCase(
                gameId = fixture.gameId,
                playerId = fixture.playerId,
                action = Extension(DeclarationAction),
                tileId = fixture.tile.id,
            )
        }
        assertEquals(fixture.table, fixture.repository.getTableState(fixture.gameId))
        assertTrue(fixture.repository.historyDrafts.isEmpty())
    }

    /** 第三方 policy 不能藉宣告效果修改未授權的玩家欄位，拒絕時桌況保持不變。 */
    @Test
    fun `unauthorized declaration effect is rejected atomically`() = runTest {
        val fixture = Fixture(malicious = true)
        fixture.repository.setTableState(fixture.table)

        assertFailsWith<RuleContractViolationException> {
            fixture.useCase(
                gameId = fixture.gameId,
                playerId = fixture.playerId,
                action = Extension(DeclarationAction),
                tileId = fixture.tile.id,
            )
        }
        assertEquals(fixture.table, fixture.repository.getTableState(fixture.gameId))
        assertTrue(fixture.repository.historyDrafts.isEmpty())
        assertTrue(fixture.eventPublisher.getNotifiedActions(fixture.gameId, fixture.playerId, fixture.playerId).isEmpty())
        assertNull(fixture.snapshots.getSnapshot(fixture.gameId, fixture.playerId))
        assertTrue(fixture.presentationPublisher.getDeclaredGameActions(fixture.gameId).isEmpty())
    }

    /**
     * 只記錄已公開牌山牌張的測試動態狀態。
     *
     * @property revealedTileIds 已公開的牌山牌張 ID。
     */
    private data class RevealedWallState(val revealedTileIds: Set<Uuid>) :
        DynamicRuleState,
        TileWallRevealable {
        override fun getVisibleTileIds(state: TableState): Set<Uuid> = revealedTileIds
    }

    /** 放棄自摸與打牌後掛勾的測試行為。 */
    private enum class HookMode {
        /** 在規則狀態依序記下執行過的掛勾。 */
        RECORD_ORDER,

        /** 違反契約，直接修改分數。 */
        CHANGE_SCORE,
    }

    /**
     * 依序記錄規則掛勾與宣告效果執行順序的測試規則狀態。
     *
     * @property marks 依執行順序排列的記號。
     */
    private data class HookTrail(val marks: List<String>) : PlayerRuleState

    /** [HookTrail] 使用的記號。 */
    private companion object {
        /** 放棄自摸掛勾的記號。 */
        const val DECLINED_WIN_MARK = "declined_win"

        /** 打牌後掛勾的記號。 */
        const val DISCARDED_MARK = "discarded"

        /** 宣告效果的記號。 */
        const val DECLARED_MARK = "declared"

        /**
         * 在玩家的 [HookTrail] 後面加上一個記號。
         *
         * @param mark 要加上的記號。
         * @return 規則狀態加上記號後的玩家。
         */
        fun MahjongPlayer.withMark(mark: String): MahjongPlayer = copy(playerRuleState = HookTrail((playerRuleState as? HookTrail)?.marks.orEmpty() + mark))
    }

    /**
     * 只覆寫第三方宣告接點，其餘規則行為委派給日麻模組。
     *
     * @property delegate 提供其他純規則能力的模組。
     * @property malicious 是否故意回傳錯誤分數。
     * @property cost 宣告的實際成本。
     * @property wallRevealCheckpoints 記錄共用流程實際執行的牌牆公開檢查點。
     * @property rejectWallReveal 是否由規則拒絕本次牌牆公開檢查。
     * @property revealWallTile 是否在牌牆公開檢查公開牌山第一張牌。
     * @property tsumoAvailable 合法動作是否包含自摸。
     * @property hookMode 放棄自摸與打牌後掛勾的測試行為；null 時沿用日麻掛勾。
     */
    private class DeclarationRule(
        private val delegate: MahjongRuleModule<RiichiRuleConfig>,
        private val malicious: Boolean,
        private val cost: Int,
        private val wallRevealCheckpoints: MutableList<WallRevealCheckpoint>,
        private val rejectWallReveal: Boolean,
        private val revealWallTile: Boolean,
        private val tsumoAvailable: Boolean,
        private val hookMode: HookMode?,
    ) : MahjongRuleModule<RiichiRuleConfig> by delegate {
        override fun createWallRevealPolicy(): WallRevealPolicy = WallRevealPolicy { context ->
            wallRevealCheckpoints += context.checkpoint
            when {
                rejectWallReveal -> WallRevealDecision.Rejected("test:wall_reveal_rejected")
                revealWallTile -> context.tableState.tileWall.getAllTiles().first().id.let { revealedId ->
                    WallRevealDecision.Updated(
                        dynamicRuleState = RevealedWallState(setOf(revealedId)),
                        newlyRevealedTileIds = setOf(revealedId),
                    )
                }
                else -> WallRevealDecision.NoChange
            }
        }

        override fun onPlayerDeclinedWin(player: MahjongPlayer): MahjongPlayer = when (hookMode) {
            HookMode.RECORD_ORDER -> player.withMark(DECLINED_WIN_MARK)
            HookMode.CHANGE_SCORE -> player.copy(score = player.score - 1)
            null -> delegate.onPlayerDeclinedWin(player)
        }

        override fun onPlayerDiscarded(player: MahjongPlayer): MahjongPlayer = when (hookMode) {
            HookMode.RECORD_ORDER -> player.withMark(DISCARDED_MARK)
            HookMode.CHANGE_SCORE -> player
            null -> delegate.onPlayerDiscarded(player)
        }

        override fun createLegalActionValidator(): LegalActionValidator = object : LegalActionValidator {
            override fun getLegalActions(context: LegalActionContext): List<GameAction> = listOfNotNull(Extension(DeclarationAction), GameAction.Tsumo.takeIf { tsumoAvailable })

            override fun tileSelectionRequirement(
                tableState: TableState,
                player: MahjongPlayer,
                action: GameAction,
            ): TileSelectionRequirement? = if (action is Extension && action.value == DeclarationAction) {
                TileSelectionRequirement(setOf(player.hand.lastDrawn!!.id))
            } else {
                null
            }
        }

        override fun declarationDiscardPolicy(): DeclarationDiscardPolicy = DeclarationDiscardPolicy { state, player, _, discard ->
            val discardPile = (player.discardPile as RiichiDiscardPile).discard(RiichiDiscardEntry(discard.tile))
            DeclarationDiscardEffect(
                dynamicRuleState = state.dynamicRuleState,
                cost = cost,
                player = player.copy(
                    hand = discard.hand,
                    discardPile = discardPile,
                    score = if (malicious) player.score + 1 else player.score - cost,
                ).let { updated -> if (updated.playerRuleState is HookTrail) updated.withMark(DECLARED_MARK) else updated },
            )
        }
    }
}
