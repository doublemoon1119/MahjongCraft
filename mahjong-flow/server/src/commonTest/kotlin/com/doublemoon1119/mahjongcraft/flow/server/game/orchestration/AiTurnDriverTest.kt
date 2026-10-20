package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.AiDecisionPhase
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategy
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyDefinition
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistryImpl
import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRoundPreparation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationInputSpec
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerActionContextResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.GetLegalActionsUseCase
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.PendingRobbingReaction
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * [AiTurnDriver] 的單元測試類別。
 *
 * 搭配 [FakeMahjongAiStrategy] 讓斷言跟「AI 策略本身選得好不好」脫鉤（那是 `RandomAiStrategyTest`
 * 的範圍），只驗證 [AiTurnDriver] 是否正確找出該問誰、傳給策略的情境是否正確。
 */
class AiTurnDriverTest {

    /** 各測試使用的對局識別碼。 */
    private val gameId = Uuid.random()

    /**
     * 記錄決策情境並回傳固定命令的策略替身。
     *
     * @property command 正常完成時回傳的命令。
     * @property hangs 是否掛起直到取消，用於等待上限測試。
     * @property lastContext 最近一次收到的決策情境。
     * @property callCount 已接收的決策呼叫次數。
     */
    private class FakeMahjongAiStrategy(
        private val command: GameCommand = GameCommand.Draw,
        private val hangs: Boolean = false,
    ) : MahjongAiStrategy {
        var lastContext: AiDecisionContext? = null
            private set
        var callCount: Int = 0
            private set

        override suspend fun decideGameCommand(context: AiDecisionContext): GameCommand {
            lastContext = context
            callCount++
            if (hangs) awaitCancellation()
            return command
        }
    }

    /** 每局 AI 玩家測試共用的策略 key，對應到 [Fixtures] 裡註冊的假策略。 */
    private val strategyKey = STRATEGY_KEY

    /**
     * 共用假策略與正式規則解析器的受測環境。
     *
     * @param strategyCommand 策略正常完成時回傳的命令。
     * @param strategyHangs 是否讓策略掛起直到取消。
     * @property gameRepo 保存測試對局的資料倉庫。
     * @property moduleRegistry 登記內建規則的註冊中心。
     * @property strategy 記錄情境的固定策略。
     * @property strategyRegistry 將測試識別碼解析為固定策略的註冊中心。
     * @property actionContextResolver 解析玩家可操作情境的元件。
     * @property driver 受測的 AI 行動解析器。
     */
    private class Fixtures(strategyCommand: GameCommand = GameCommand.Draw, strategyHangs: Boolean = false) {
        val gameRepo = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val strategy = FakeMahjongAiStrategy(strategyCommand, strategyHangs)
        val strategyRegistry = MahjongAiStrategyRegistryImpl(defaultKey = STRATEGY_KEY).apply {
            register(MahjongAiStrategyDefinition(STRATEGY_KEY) { strategy })
        }
        val actionContextResolver = PlayerActionContextResolver()
        val driver = AiTurnDriver(
            gameRepo,
            GetLegalActionsUseCase(gameRepo, moduleRegistry, actionContextResolver),
            strategyRegistry,
            GameVisibilityPolicyImpl(moduleRegistry),
            moduleRegistry,
            AiDecisionExecutor.direct(failurePolicy = ExtensionFailurePolicy.Default),
            AutomaticGameCommandResolver(moduleRegistry, actionContextResolver),
            actionContextResolver,
        )
    }

    /** 測試策略的登記資訊。 */
    private companion object {
        /** 所有測試 AI 共用的命名空間策略識別碼。 */
        const val STRATEGY_KEY = "test:fake"
    }

    // ---- 搶槓反應 ----

    /**
     * 驗證搶槓反應視窗裡有資格且尚未回應的 AI 時，回傳該 AI 與策略決定的
     * [GameCommand.RespondToRobbing]，且傳給策略的情境為 [AiDecisionPhase.RespondingToRobbing]。
     */
    @Test
    fun `test pending chankan with eligible ai returns respond to chankan`() = runTest {
        val fixtures = Fixtures(strategyCommand = GameCommand.RespondToRobbing(GameAction.Pass))
        val declarerId = Uuid.random()
        val aiId = Uuid.random()
        val robbedTile = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val kanAction = GameAction.Kan(GameAction.KanType.ADDED_KAN, robbedTile.id, emptyList())
        val declarer = FakeMahjongPlayerFactory.create(
            id = declarerId,
            initialSeat = Wind.EAST,
            hand = Hand(tiles = listOf(FakeIdentifiedTileFactory.create(Tile.Honor.East))),
        )
        val ai = FakeMahjongPlayerFactory.create(
            id = aiId,
            initialSeat = Wind.SOUTH,
            hand = Hand(tiles = listOf(FakeIdentifiedTileFactory.create(Tile.Honor.South))),
            playerRuleState = RiichiPlayerState(),
        )
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer, ai),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingRobbingReaction = PendingRobbingReaction(declarerId, kanAction, robbedTile, setOf(aiId)),
        )
        fixtures.gameRepo.setTableState(table, mapOf(aiId to strategyKey))

        val result = fixtures.driver.resolveNextAction(gameId)

        assertEquals(aiId to GameCommand.RespondToRobbing(GameAction.Pass), result?.let { it.playerId to it.command })
        assertEquals(AiDecisionPhase.RespondingToRobbing, fixtures.strategy.lastContext?.phase)
        assertEquals(aiId, fixtures.strategy.lastContext?.selfId)
        val snapshot = assertNotNull(fixtures.strategy.lastContext?.snapshot)
        assertNotNull(snapshot.players.single { it.id == aiId }.hand.standingTiles.single().tile)
        assertNull(snapshot.players.single { it.id == declarerId }.hand.standingTiles.single().tile)
    }

    /**
     * 驗證搶槓反應視窗裡只有人類有資格時，不會誤觸發（回傳 null，策略未被呼叫）。
     */
    @Test
    fun `test pending chankan with only human eligible returns null`() = runTest {
        val fixtures = Fixtures()
        val declarerId = Uuid.random()
        val humanId = Uuid.random()
        val robbedTile = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val kanAction = GameAction.Kan(GameAction.KanType.ADDED_KAN, robbedTile.id, emptyList())
        val declarer = FakeMahjongPlayerFactory.create(id = declarerId, initialSeat = Wind.EAST)
        val human = FakeMahjongPlayerFactory.create(id = humanId, initialSeat = Wind.SOUTH)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer, human),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingRobbingReaction = PendingRobbingReaction(declarerId, kanAction, robbedTile, setOf(humanId)),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.driver.resolveNextAction(gameId)

        assertNull(result)
        assertEquals(0, fixtures.strategy.callCount)
    }

    /**
     * 驗證唯一有資格的 AI 已經回應過後，不會再次被問（回傳 null）。
     */
    @Test
    fun `test pending chankan where the only ai already responded returns null`() = runTest {
        val fixtures = Fixtures()
        val declarerId = Uuid.random()
        val aiId = Uuid.random()
        val robbedTile = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val kanAction = GameAction.Kan(GameAction.KanType.ADDED_KAN, robbedTile.id, emptyList())
        val declarer = FakeMahjongPlayerFactory.create(id = declarerId, initialSeat = Wind.EAST)
        val ai = FakeMahjongPlayerFactory.create(id = aiId, initialSeat = Wind.SOUTH)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer, ai),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingRobbingReaction = PendingRobbingReaction(
                declarerId,
                kanAction,
                robbedTile,
                setOf(aiId),
                responses = mapOf(aiId to GameAction.Pass),
            ),
        )
        fixtures.gameRepo.setTableState(table, mapOf(aiId to strategyKey))

        val result = fixtures.driver.resolveNextAction(gameId)

        assertNull(result)
    }

    // ---- 捨牌反應 ----

    /**
     * 驗證捨牌反應視窗裡有資格且尚未回應的 AI 時，回傳該 AI 與策略決定的
     * [GameCommand.RespondToDiscard]，且傳給策略的情境為 [AiDecisionPhase.RespondingToDiscard]。
     */
    @Test
    fun `test pending reaction with eligible ai returns respond to discard`() = runTest {
        val fixtures = Fixtures(strategyCommand = GameCommand.RespondToDiscard(GameAction.Pass))
        val discarderId = Uuid.random()
        val aiId = Uuid.random()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(discardedTile),
        )
        val ai = FakeMahjongPlayerFactory.create(id = aiId, initialSeat = Wind.SOUTH, playerRuleState = RiichiPlayerState())
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, ai),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(aiId)),
        )
        fixtures.gameRepo.setTableState(table, mapOf(aiId to strategyKey))

        val result = fixtures.driver.resolveNextAction(gameId)

        assertEquals(aiId to GameCommand.RespondToDiscard(GameAction.Pass), result?.let { it.playerId to it.command })
        assertEquals(AiDecisionPhase.RespondingToDiscard, fixtures.strategy.lastContext?.phase)
    }

    /**
     * 驗證捨牌反應視窗裡只有人類有資格時，不會誤觸發。
     */
    @Test
    fun `test pending reaction with only human eligible returns null`() = runTest {
        val fixtures = Fixtures()
        val discarderId = Uuid.random()
        val humanId = Uuid.random()
        val discardedTile = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val discarder = FakeMahjongPlayerFactory.create(
            id = discarderId,
            initialSeat = Wind.EAST,
            discardPile = FakeDiscardPile().discardTile(discardedTile),
        )
        val human = FakeMahjongPlayerFactory.create(id = humanId, initialSeat = Wind.SOUTH)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, human),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(humanId)),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.driver.resolveNextAction(gameId)

        assertNull(result)
    }

    // ---- 自己回合 ----

    /**
     * 驗證輪到 AI 回合、尚未摸牌時，回傳固定的 [GameCommand.Draw]，且完全不呼叫策略——
     * 摸牌不是一個需要策略的決定。
     */
    @Test
    fun `test own turn ai without last drawn returns draw and does not call strategy`() = runTest {
        val fixtures = Fixtures()
        val aiId = Uuid.random()
        val ai = FakeMahjongPlayerFactory.create(id = aiId, initialSeat = Wind.EAST)
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(ai), config = RiichiRuleConfig(), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table, mapOf(aiId to strategyKey))

        val result = fixtures.driver.resolveNextAction(gameId)

        assertEquals(aiId to GameCommand.Draw, result?.let { it.playerId to it.command })
        assertEquals(0, fixtures.strategy.callCount, "Drawing is not a strategic decision; the strategy should not be consulted.")
    }

    /** 驗證開局準備尚未完成時，不會把 AI 誤判成已進入摸打回合。 */
    @Test
    fun `test pending round preparation suppresses ordinary ai action`() = runTest {
        val fixtures = Fixtures()
        val aiId = Uuid.random()
        val ai = FakeMahjongPlayerFactory.create(id = aiId, initialSeat = Wind.EAST)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(ai),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
        )
        fixtures.gameRepo.setGame(
            Game(
                tableState = table,
                flowConfig = GameFlowConfig(),
                aiPlayerStrategyKeys = mapOf(aiId to strategyKey),
                pendingRoundPreparation = PendingRoundPreparation(
                    stepId = "test:confirmation",
                    stepIndex = 0,
                    inputSpecsByPlayerId = mapOf(aiId to RoundPreparationInputSpec.Confirmation),
                ),
            ),
        )

        assertNull(fixtures.driver.resolveNextAction(gameId))
        assertEquals(0, fixtures.strategy.callCount)
    }

    /**
     * 驗證輪到 AI 回合、已經摸牌時，會問策略該怎麼做，情境為 [AiDecisionPhase.OwnTurn]。
     */
    @Test
    fun `test own turn ai with last drawn asks strategy with own turn phase`() = runTest {
        val strategyCommand = GameCommand.Discard(Uuid.random())
        val fixtures = Fixtures(strategyCommand = strategyCommand)
        val aiId = Uuid.random()
        val lastDrawn = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val ai = FakeMahjongPlayerFactory.create(
            id = aiId,
            initialSeat = Wind.EAST,
            hand = Hand(lastDrawn = lastDrawn),
            playerRuleState = RiichiPlayerState(),
        )
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(ai), config = RiichiRuleConfig(), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table, mapOf(aiId to strategyKey))

        val result = fixtures.driver.resolveNextAction(gameId)

        assertEquals(aiId to strategyCommand, result?.let { it.playerId to it.command })
        assertEquals(AiDecisionPhase.OwnTurn, fixtures.strategy.lastContext?.phase)
        assertEquals(aiId, fixtures.strategy.lastContext?.selfId)
        assertNull(fixtures.strategy.lastContext?.forcedDiscardTileId)
    }

    /**
     * 驗證日麻 AI 立直後，決策情境會要求摸切剛摸入的牌。
     */
    @Test
    fun `test riichi ai receives last drawn as forced discard`() = runTest {
        val fixtures = Fixtures(strategyCommand = GameCommand.Discard(Uuid.random()))
        val aiId = Uuid.random()
        val riichiTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val lastDrawn = FakeIdentifiedTileFactory.create(Tile.Honor.South)
        val ai = FakeMahjongPlayerFactory.create(
            id = aiId,
            initialSeat = Wind.EAST,
            hand = Hand(lastDrawn = lastDrawn),
            playerRuleState = RiichiPlayerState(riichiTile = riichiTile),
        )
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(ai), config = RiichiRuleConfig(), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table, mapOf(aiId to strategyKey))

        fixtures.driver.resolveNextAction(gameId)

        assertEquals(lastDrawn.id, fixtures.strategy.lastContext?.forcedDiscardTileId)
    }

    /**
     * 驗證 AI 剛碰成立（`lastDrawn == null`，但 `actionHistory` 最後一筆是 [GameAction.Pon]）時，
     * 會直接問策略該捨哪張牌，而不是誤發 [GameCommand.Draw]——碰牌後應該直接捨牌，不能再摸一次牌。
     */
    @Test
    fun `test own turn ai that just ponned discards directly instead of drawing`() = runTest {
        val strategyCommand = GameCommand.Discard(Uuid.random())
        val fixtures = Fixtures(strategyCommand = strategyCommand)
        val aiId = Uuid.random()
        val remainingTile = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val ai = FakeMahjongPlayerFactory.create(id = aiId, initialSeat = Wind.EAST, hand = Hand(tiles = listOf(remainingTile)))
            .recordAction(GameAction.Pon(Uuid.random(), emptyList()))
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(ai), config = RiichiRuleConfig(), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table, mapOf(aiId to strategyKey))

        val result = fixtures.driver.resolveNextAction(gameId)

        assertEquals(aiId to strategyCommand, result?.let { it.playerId to it.command })
        assertEquals(AiDecisionPhase.OwnTurn, fixtures.strategy.lastContext?.phase)
    }

    /**
     * 驗證輪到人類回合、且沒有任何反應視窗時，回傳 null。
     */
    @Test
    fun `test own turn human returns null`() = runTest {
        val fixtures = Fixtures()
        val humanId = Uuid.random()
        val human = FakeMahjongPlayerFactory.create(id = humanId, initialSeat = Wind.EAST)
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(human), config = RiichiRuleConfig(), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.driver.resolveNextAction(gameId)

        assertNull(result)
    }

    /**
     * 驗證對局不存在時回傳 null（不拋錯）。
     */
    @Test
    fun `test game not found returns null`() = runTest {
        val fixtures = Fixtures()

        val result = fixtures.driver.resolveNextAction(gameId)

        assertNull(result)
    }

    /**
     * 驗證決策結果附上建立情境時讀到的遊戲，套用前可據此確認局面沒有改變。
     */
    @Test
    fun `test decision carries the game it was decided on`() = runTest {
        val strategyCommand = GameCommand.Discard(Uuid.random())
        val fixtures = Fixtures(strategyCommand = strategyCommand)
        val aiId = Uuid.random()
        val ai = FakeMahjongPlayerFactory.create(
            id = aiId,
            initialSeat = Wind.EAST,
            hand = Hand(lastDrawn = FakeIdentifiedTileFactory.create(Tile.Honor.East)),
        )
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(ai), config = RiichiRuleConfig(), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table, mapOf(aiId to strategyKey))

        val result = assertNotNull(fixtures.driver.resolveNextAction(gameId))

        assertEquals(fixtures.gameRepo.getGame(gameId), result.basis)
        assertEquals(table.id, fixtures.strategy.lastContext?.snapshot?.id)
    }

    /**
     * 驗證策略沒有在等待上限內給出結果時，改用與真人逾時相同的固定命令：自己回合打出剛摸入的牌。
     */
    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `test a strategy past the timeout falls back to the fixed command`() = runTest {
        val fixtures = Fixtures(strategyHangs = true)
        val aiId = Uuid.random()
        val lastDrawn = FakeIdentifiedTileFactory.create(Tile.Honor.East)
        val ai = FakeMahjongPlayerFactory.create(id = aiId, initialSeat = Wind.EAST, hand = Hand(lastDrawn = lastDrawn))
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(ai), config = RiichiRuleConfig(), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table, mapOf(aiId to strategyKey))

        val result = assertNotNull(fixtures.driver.resolveNextAction(gameId))

        assertEquals(GameCommand.Discard(lastDrawn.id), result.command)
        assertEquals(AiDecisionExecutor.DEFAULT_TIMEOUT.inWholeMilliseconds, testScheduler.currentTime)
    }
}
