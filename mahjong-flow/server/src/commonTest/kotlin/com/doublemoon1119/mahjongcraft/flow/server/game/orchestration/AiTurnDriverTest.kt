package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.AiDecisionPhase
import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategy
import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.FakeGameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.GetLegalActionsUseCase
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.PendingChankanReaction
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/**
 * [AiTurnDriver] 的單元測試類別。
 *
 * 搭配 [FakeMahjongAiStrategy] 讓斷言跟「AI 策略本身選得好不好」脫鉤（那是 `RandomAiStrategyTest`
 * 的範圍），只驗證 [AiTurnDriver] 是否正確找出該問誰、傳給策略的情境是否正確。
 */
class AiTurnDriverTest {

    private val gameId = Uuid.random()

    private class FakeMahjongAiStrategy(private val command: GameCommand = GameCommand.Draw) : MahjongAiStrategy {
        var lastContext: AiDecisionContext? = null
            private set
        var callCount: Int = 0
            private set

        override suspend fun decide(context: AiDecisionContext): GameCommand {
            lastContext = context
            callCount++
            return command
        }
    }

    private class Fixtures(strategyCommand: GameCommand = GameCommand.Draw) {
        val gameRepo = FakeGameRepository()
        val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
        val strategy = FakeMahjongAiStrategy(strategyCommand)
        val driver = AiTurnDriver(gameRepo, GetLegalActionsUseCase(gameRepo, moduleRegistry), strategy)
    }

    // ---- 搶槓反應 ----

    /**
     * 驗證搶槓反應視窗裡有資格且尚未回應的 AI 時，回傳該 AI 與策略決定的
     * [GameCommand.RespondToChankan]，且傳給策略的情境為 [AiDecisionPhase.RespondingToChankan]。
     */
    @Test
    fun `test pending chankan with eligible ai returns respond to chankan`() = runTest {
        val fixtures = Fixtures(strategyCommand = GameCommand.RespondToChankan(GameAction.Pass))
        val declarerId = Uuid.random()
        val aiId = Uuid.random()
        val robbedTile = FakeIdentifiedTileFactory.create(Tile.Honor.White)
        val kanAction = GameAction.Kan(GameAction.KanType.ADDED_KAN, robbedTile.id, emptyList())
        val declarer = FakeMahjongPlayerFactory.create(id = declarerId, initialSeat = Wind.EAST)
        val ai = FakeMahjongPlayerFactory.create(id = aiId, initialSeat = Wind.SOUTH, isAi = true, playerRuleState = RiichiPlayerState())
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer, ai),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingChankan = PendingChankanReaction(declarerId, kanAction, robbedTile, setOf(aiId)),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.driver.resolveNextAction(gameId)

        assertEquals(aiId to GameCommand.RespondToChankan(GameAction.Pass), result)
        assertEquals(AiDecisionPhase.RespondingToChankan, fixtures.strategy.lastContext?.phase)
        assertEquals(aiId, fixtures.strategy.lastContext?.selfId)
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
            pendingChankan = PendingChankanReaction(declarerId, kanAction, robbedTile, setOf(humanId)),
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
        val ai = FakeMahjongPlayerFactory.create(id = aiId, initialSeat = Wind.SOUTH, isAi = true)
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(declarer, ai),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingChankan = PendingChankanReaction(
                declarerId,
                kanAction,
                robbedTile,
                setOf(aiId),
                responses = mapOf(aiId to GameAction.Pass),
            ),
        )
        fixtures.gameRepo.setTableState(table)

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
        val ai = FakeMahjongPlayerFactory.create(id = aiId, initialSeat = Wind.SOUTH, isAi = true, playerRuleState = RiichiPlayerState())
        val table = FakeTableStateFactory.create(
            id = gameId,
            players = listOf(discarder, ai),
            config = RiichiRuleConfig(),
            currentPlayerIndex = 0,
            pendingReaction = PendingReaction(discarderId, discardedTile.id, setOf(aiId)),
        )
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.driver.resolveNextAction(gameId)

        assertEquals(aiId to GameCommand.RespondToDiscard(GameAction.Pass), result)
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
        val ai = FakeMahjongPlayerFactory.create(id = aiId, initialSeat = Wind.EAST, isAi = true)
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(ai), config = RiichiRuleConfig(), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.driver.resolveNextAction(gameId)

        assertEquals(aiId to GameCommand.Draw, result)
        assertEquals(0, fixtures.strategy.callCount, "Drawing is not a strategic decision; the strategy should not be consulted.")
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
            isAi = true,
            playerRuleState = RiichiPlayerState(),
        )
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(ai), config = RiichiRuleConfig(), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.driver.resolveNextAction(gameId)

        assertEquals(aiId to strategyCommand, result)
        assertEquals(AiDecisionPhase.OwnTurn, fixtures.strategy.lastContext?.phase)
        assertEquals(aiId, fixtures.strategy.lastContext?.selfId)
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
        val ai = FakeMahjongPlayerFactory.create(id = aiId, initialSeat = Wind.EAST, hand = Hand(tiles = listOf(remainingTile)), isAi = true)
            .recordAction(GameAction.Pon(Uuid.random()))
        val table = FakeTableStateFactory.create(id = gameId, players = listOf(ai), config = RiichiRuleConfig(), currentPlayerIndex = 0)
        fixtures.gameRepo.setTableState(table)

        val result = fixtures.driver.resolveNextAction(gameId)

        assertEquals(aiId to strategyCommand, result)
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
}
