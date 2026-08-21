package com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyRegistryImpl
import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.ai.registerBuiltInAiStrategies
import com.doublemoon1119.mahjongcraft.flow.common.di.registerBuiltInRuleModules
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.buildBuiltInPersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.state.AuthoritativeStatePersistenceCodec
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AiTurnDriver
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionAuthorityResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimerManager
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerDecisionTimerFactory
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DrawTileUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.GetLegalActionsUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.RespondToChankanUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.RespondToDiscardUseCase
import com.doublemoon1119.mahjongcraft.flow.server.lifecycle.ServerSessionStateRestorer
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.JoinRoomUseCase
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.time.MonotonicClockImpl
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardEntry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.logic.table.PendingChankanReaction
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGameEventPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationPublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.room.service.FakeRoomEventPublisher
import kotlinx.coroutines.test.runTest
import net.minecraft.nbt.NbtCompound
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 由 Minecraft NBT 建立全新 server session 後繼續執行 Room／Game 流程的整合測試。 */
class AuthoritativeStateRecoveryIntegrationTest {
    /** 等待中的 Room 恢復後應重建 membership，並允許新玩家繼續加入。 */
    @Test
    fun `waiting room restores through NBT and accepts another player`() = runTest {
        val runtime = RuntimeFixture()
        val hostId = Uuid.random()
        val existingPlayerId = Uuid.random()
        val joiningPlayerId = Uuid.random()
        val room = Room(
            id = Uuid.random(),
            hostId = hostId,
            gameConfig = GameConfig(RiichiRuleConfig()),
            playerIds = listOf(hostId, existingPlayerId),
        )

        runtime.restore(AuthoritativeStateSnapshot(rooms = mapOf(room.id to room)))
        val result = JoinRoomUseCase(
            runtime.roomRepository,
            runtime.memberships,
            runtime.roomSnapshots,
            FakeRoomEventPublisher(),
        )(room.id, joiningPlayerId)

        assertIs<Outcome.Success<Unit>>(result)
        assertEquals(room.id, runtime.memberships.getTableId(hostId))
        assertEquals(room.id, runtime.memberships.getTableId(existingPlayerId))
        assertEquals(room.id, runtime.memberships.getTableId(joiningPlayerId))
        assertEquals(room.playerIds + joiningPlayerId, runtime.roomRepository.getRoom(room.id)?.playerIds)
    }

    /** 一般回合經 NBT 恢復後，當前玩家應能繼續摸牌。 */
    @Test
    fun `active turn restores through NBT and continues drawing`() = runTest {
        val runtime = RuntimeFixture()
        val state = runtime.createGame()
        runtime.restore(runtime.snapshotWith(state))

        val result = runtime.drawTile(state.id, state.currentPlayer.id)

        assertIs<Outcome.Success<Unit>>(result)
        assertNotNull(runtime.gameRepository.getTableState(state.id)?.currentPlayer?.hand?.lastDrawn)
    }

    /** 捨牌反應視窗經 NBT 恢復後，尚未回應的玩家應能提交 Pass。 */
    @Test
    fun `discard reaction restores through NBT and accepts response`() = runTest {
        val runtime = RuntimeFixture()
        val state = runtime.createGame()
        val discarder = state.players[0]
        val firstResponder = state.players[1]
        val secondResponder = state.players[2]
        val discardedTile = IdentifiedTile(Uuid.random(), Tile.Honor.East)
        val updatedFirstResponder = firstResponder.copy(
            hand = firstResponder.hand.copy(
                tiles = firstResponder.hand.tiles + List(2) { IdentifiedTile(Uuid.random(), Tile.Honor.East) },
            ),
        )
        val updatedDiscarder = discarder.copy(
            discardPile = (discarder.discardPile as RiichiDiscardPile).discard(RiichiDiscardEntry(discardedTile)),
        )
        val pendingState = state.copy(
            players = state.players.map { player ->
                when (player.id) {
                    discarder.id -> updatedDiscarder
                    firstResponder.id -> updatedFirstResponder
                    else -> player
                }
            },
            pendingReaction = PendingReaction(
                discarderId = discarder.id,
                tileId = discardedTile.id,
                eligiblePlayerIds = setOf(firstResponder.id, secondResponder.id),
            ),
        )
        runtime.restore(runtime.snapshotWith(pendingState))

        val result = runtime.respondToDiscard(pendingState.id, firstResponder.id)

        assertIs<Outcome.Success<Unit>>(result)
        assertEquals(
            GameAction.Pass,
            runtime.gameRepository.getTableState(pendingState.id)?.pendingReaction?.responses?.get(firstResponder.id),
        )
    }

    /** 搶槓反應視窗經 NBT 恢復後，尚未回應的玩家應能提交 Pass。 */
    @Test
    fun `chankan reaction restores through NBT and accepts response`() = runTest {
        val runtime = RuntimeFixture()
        val state = runtime.createGame()
        val declarer = state.players[0]
        val firstResponder = state.players[1]
        val secondResponder = state.players[2]
        val robbedTile = IdentifiedTile(Uuid.random(), Tile.Honor.White)
        val readyHand = Hand(
            tiles = listOf(
                Tile.Honor.Green, Tile.Honor.Green, Tile.Honor.Green,
                Tile.Numeric(Tile.Suit.Character, 2), Tile.Numeric(Tile.Suit.Character, 3),
                Tile.Numeric(Tile.Suit.Character, 4),
                Tile.Numeric(Tile.Suit.Dot, 5), Tile.Numeric(Tile.Suit.Dot, 6), Tile.Numeric(Tile.Suit.Dot, 7),
                Tile.Numeric(Tile.Suit.Bamboo, 6), Tile.Numeric(Tile.Suit.Bamboo, 7),
                Tile.Numeric(Tile.Suit.Bamboo, 8),
                Tile.Honor.White,
            ).map { IdentifiedTile(Uuid.random(), it) },
        )
        val pendingState = state.copy(
            players = state.players.map { player ->
                if (player.id == firstResponder.id) player.copy(hand = readyHand) else player
            },
            pendingChankan = PendingChankanReaction(
                declarerId = declarer.id,
                kanAction = GameAction.Kan(
                    type = GameAction.KanType.ADDED_KAN,
                    tileId = robbedTile.id,
                    withTiles = List(3) { Uuid.random() },
                ),
                robbedTile = robbedTile,
                eligiblePlayerIds = setOf(firstResponder.id, secondResponder.id),
            ),
        )
        runtime.restore(runtime.snapshotWith(pendingState))

        val result = runtime.respondToChankan(pendingState.id, firstResponder.id)

        assertIs<Outcome.Success<Unit>>(result)
        assertEquals(
            GameAction.Pass,
            runtime.gameRepository.getTableState(pendingState.id)?.pendingChankan?.responses?.get(firstResponder.id),
        )
    }

    /** 輪到 AI 的牌局經 NBT 恢復後，應保留策略 key 並解析出單一機械摸牌動作。 */
    @Test
    fun `ai turn restores through NBT and resolves next action`() = runTest {
        val runtime = RuntimeFixture()
        val playerIds = List(4) { Uuid.random() }
        val state = runtime.createGame(
            playerIds = playerIds,
            aiPlayerStrategyKeys = playerIds.associateWith { RandomAiStrategy.KEY },
        )
        runtime.restore(runtime.snapshotWith(state))

        val action = runtime.aiTurnDriver.resolveNextAction(state.id)

        assertEquals(state.currentPlayer.id to GameCommand.Draw, action)
        assertNull(runtime.memberships.getTableId(state.currentPlayer.id))
        assertEquals(RandomAiStrategy.KEY, runtime.gameRepository.getTableState(state.id)?.currentPlayer?.aiStrategyKey)
    }

    /** 建立跨 persistence 與 server session 邊界的全新 runtime。 */
    private class RuntimeFixture {
        /** 內建 persistence codec。 */
        private val codec = AuthoritativeStatePersistenceCodec(buildBuiltInPersistenceRegistries())

        /** 日麻規則 registry。 */
        private val moduleRegistry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }

        /** 全新 server session 的權威 store。 */
        private val store = AuthoritativeStateStore()

        /** 恢復後使用的 Room repository。 */
        val roomRepository = RoomRepositoryImpl(store)

        /** 恢復後使用的 Game repository。 */
        val gameRepository = GameRepositoryImpl(store)

        /** 恢復後使用的 Room snapshot repository。 */
        val roomSnapshots = RoomSnapshotRepositoryImpl()

        /** 恢復後使用的 Game snapshot repository。 */
        private val gameSnapshots = GameSnapshotRepositoryImpl()

        /** 恢復後使用的 membership repository。 */
        val memberships = PlayerMembershipRepositoryImpl()

        /** 遊戲 snapshot 同步服務。 */
        private val snapshotSynchronizer = GameSnapshotSynchronizer(
            gameRepository,
            gameSnapshots,
            GameVisibilityPolicyImpl(),
        )

        /** 恢復後的 timer manager。 */
        private val timerManager = MonotonicClockImpl().let { clock ->
            GameDecisionTimerManager(
                gameRepository,
                GameDecisionAuthorityResolver(),
                PlayerDecisionTimerFactory(clock),
                clock,
            )
        }

        /** 衍生 session 狀態恢復服務。 */
        private val restorer = ServerSessionStateRestorer(
            roomSnapshots,
            gameSnapshots,
            memberships,
            GameVisibilityPolicyImpl(),
            timerManager,
        )

        /** AI 決策解析器。 */
        val aiTurnDriver = AiTurnDriver(
            gameRepository,
            GetLegalActionsUseCase(gameRepository, moduleRegistry),
            MahjongAiStrategyRegistryImpl(RandomAiStrategy.KEY).apply { registerBuiltInAiStrategies() },
            GameVisibilityPolicyImpl(),
        )

        /** 將 [source] 寫入 NBT，再由新 adapter 載入目前 runtime。 */
        suspend fun restore(source: AuthoritativeStateSnapshot) {
            val savedState = MahjongAuthoritativePersistentState.create(codec).apply { update(source) }
            val nbt = savedState.writeNbt(NbtCompound())
            val restored = MahjongAuthoritativePersistentState.fromNbt(nbt, codec).snapshot
            store.load(restored)
            restorer.restore(restored)
        }

        /** 建立使用正式日麻 module 初始化的牌局。 */
        fun createGame(
            playerIds: List<Uuid> = List(4) { Uuid.random() },
            aiPlayerStrategyKeys: Map<Uuid, String> = emptyMap(),
        ): TableState = GameInitializer.initialize(
            id = Uuid.random(),
            playerIds = playerIds,
            module = moduleRegistry.getModule(RiichiRuleConfig()),
            aiPlayerStrategyKeys = aiPlayerStrategyKeys,
        ).tableState

        /** 將單一 [state] 包裝成權威 snapshot。 */
        fun snapshotWith(state: TableState): AuthoritativeStateSnapshot = AuthoritativeStateSnapshot(
            games = mapOf(state.id to Game(state, GameFlowConfig())),
        )

        /** 使用恢復後 repository 執行摸牌。 */
        suspend fun drawTile(gameId: Uuid, playerId: Uuid): Outcome<Unit, *> = DrawTileUseCase(
            gameRepository,
            moduleRegistry,
            snapshotSynchronizer,
            FakeGameEventPublisher(),
            FakeGamePresentationPublisher(),
        )(gameId, playerId)

        /** 使用恢復後 repository 提交捨牌反應 Pass。 */
        suspend fun respondToDiscard(gameId: Uuid, playerId: Uuid): Outcome<Unit, *> = RespondToDiscardUseCase(
            gameRepository,
            moduleRegistry,
            snapshotSynchronizer,
            HandSortPreferenceStore(),
            FakeGameEventPublisher(),
            FakeGamePresentationPublisher(),
        )(gameId, playerId, GameAction.Pass)

        /** 使用恢復後 repository 提交搶槓反應 Pass。 */
        suspend fun respondToChankan(gameId: Uuid, playerId: Uuid): Outcome<Unit, *> = RespondToChankanUseCase(
            gameRepository,
            moduleRegistry,
            snapshotSynchronizer,
            FakeGameEventPublisher(),
            FakeGamePresentationPublisher(),
        )(gameId, playerId, GameAction.Pass)
    }
}
