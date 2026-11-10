package com.doublemoon1119.mahjongcraft.flow.server.lifecycle

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicy
import com.doublemoon1119.mahjongcraft.flow.server.game.policy.GameVisibilityPolicyImpl
import com.doublemoon1119.mahjongcraft.flow.server.game.service.DecisionTimerSynchronizationService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionAuthorityResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionAvailabilityService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionTimerManager
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerDecisionTimerFactory
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.time.MonotonicClockImpl
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentProjectionContext
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistry
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewSet
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleDefinition
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.logic.tile.component.BuiltInTileComponents
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileComponentDescription
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileFaceAccess
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledCoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.repository.componentValidatedGameRepository
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeDecisionTimerUpdatePublisher
import com.doublemoon1119.mahjongcraft.testing.flow.common.game.service.FakeGamePresentationBusyGate
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeMeldFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 對局投影失敗不能影響其他場次、改寫權威資料或公開額外資訊。 */
class ServerSessionProjectionRecoveryTest {
    /** 第二個觀察者失敗時，第一個算好的內容也不發布，整局改用保守快照；計時器照常排定，對局不會停住。 */
    @Test
    fun `one failed viewer replaces every view without losing other games`() = runTest {
        val broken = game()
        val healthy = game()
        val firstId = broken.tableState.players.first().id
        val lastId = broken.tableState.players.last().id
        val failure = IllegalStateException("Rule projection failed")
        val fixture = Fixture(decorate = { delegate ->
            object : GameVisibilityPolicy by delegate {
                override fun snapshotFor(game: Game, observerId: Uuid): TableStateSnapshot {
                    if (game.id == broken.id && observerId == lastId) throw failure
                    if (game.id == broken.id) return game.tableState.toSnapshot(game.tableState.players.mapTo(mutableSetOf()) { it.id })
                    return delegate.snapshotFor(game, observerId)
                }
            }
        })

        val result = fixture.restore(broken, healthy)

        assertEquals(listOf(broken.id), result.snapshotFailures.map { it.gameId })
        assertEquals("test:restoration", result.snapshotFailures.single().ruleModuleId)
        assertSame(failure, result.snapshotFailures.single().cause)
        broken.tableState.players.forEach { observer ->
            val snapshot = assertNotNull(fixture.snapshots.getSnapshot(broken.id, observer.id))
            assertTrue(snapshot.componentViews.isEmpty)
            snapshot.players.forEach { player ->
                assertTrue(player.componentViews.isEmpty)
                assertEquals(player.id == observer.id, player.hand.standingTiles.all { it.face != null })
            }
        }
        assertNull(fixture.snapshots.getSnapshot(broken.id, firstId)!!.players.last().hand.standingTiles.first().face)
        healthy.tableState.players.forEach { player ->
            assertEquals(fixture.policy.snapshotFor(healthy, player.id), fixture.snapshots.getSnapshot(healthy.id, player.id))
        }
        assertEquals(broken, fixture.store.snapshot().games[broken.id])
        assertEquals(healthy, fixture.store.snapshot().games[healthy.id])
        assertFalse(fixture.store.isDirty())
        assertEquals(
            fixture.timers.getStatuses(healthy.id).keys.map { id -> healthy.tableState.players.indexOfFirst { it.id == id } },
            fixture.timers.getStatuses(broken.id).keys.map { id -> broken.tableState.players.indexOfFirst { it.id == id } },
        )
        assertTrue(fixture.timers.getStatuses(broken.id).isNotEmpty())
    }

    /** 無效 UUID、重複描述與描述回呼失敗，都只影響該局的還原顯示。 */
    @Test
    fun `invalid tile descriptions and callback failures are isolated`() = runTest {
        val broken = game()
        val healthy = game()
        val tileId = broken.tableState.players.last().hand.tiles.first().id
        val scenarios: List<(TableState) -> List<TileComponentDescription>> = listOf(
            { listOf(description(Uuid.random())) },
            { List(2) { description(tileId) } },
            { throw IllegalArgumentException("Invalid description") },
        )
        scenarios.forEach { describe ->
            val fixture = Fixture(describe = { table -> if (table.id == broken.id) describe(table) else emptyList() })
            val result = fixture.restore(broken, healthy)
            assertEquals(listOf(broken.id), result.snapshotFailures.map { it.gameId })
            assertNotNull(fixture.snapshots.getSnapshot(healthy.id, healthy.tableState.players.first().id))
            val observer = broken.tableState.players.first()
            val restored = assertNotNull(fixture.snapshots.getSnapshot(broken.id, observer.id))
            assertTrue(restored.players.last().hand.standingTiles.all { it.face == null })
            assertTrue(restored.publicTileIds.isEmpty())
            assertEquals(broken, fixture.store.snapshot().games[broken.id])
        }
    }

    /** 保守快照只使用既有公共證據，且不讓來源組件或外觀跟著輸出。 */
    @Test
    fun `fallback exposes only explicit public evidence and own faces`() = runTest {
        val original = game()
        val publicTile = original.tableState.players.last().hand.tiles.first()
        val broken = original.copy(tableState = original.tableState.copy(publicTileEvidenceIds = setOf(publicTile.id)))
        val fixture = Fixture(describe = { throw IllegalStateException("Projection failed") })

        fixture.restore(broken)

        val observerId = broken.tableState.players.first().id
        val restored = assertNotNull(fixture.snapshots.getSnapshot(broken.id, observerId))
        val otherHand = restored.players.last().hand
        assertEquals(publicTile.tile, otherHand.standingTiles.single { it.id == publicTile.id }.tile)
        assertNull(otherHand.lastDrawn?.face)
        assertEquals(setOf(publicTile.id), restored.publicTileIds)
        assertTrue(restored.tilePoseStates.isEmpty())
        assertNull(otherHand.standingTiles.single { it.id == publicTile.id }.face!!.appearanceId)
        assertTrue(otherHand.standingTiles.single { it.id == publicTile.id }.face!!.views.isEmpty)
    }

    /** 下一次還原重建快照與診斷，不沿用前一次的失敗狀態。 */
    @Test
    fun `a later restore can recover and finished games remain finished`() = runTest {
        var fails = true
        val fixture = Fixture(describe = { if (fails) error("Projection failed") else emptyList() })
        val finished = game().copy(isMatchOver = true)
        assertEquals(1, fixture.restore(finished).snapshotFailures.size)
        assertTrue(fixture.store.snapshot().games.getValue(finished.id).isMatchOver)
        fails = false
        assertTrue(fixture.restore(finished).snapshotFailures.isEmpty())
        assertEquals(fixture.policy.snapshotFor(finished, finished.hostId), fixture.snapshots.getSnapshot(finished.id, finished.hostId))
    }

    /** 沒有真人的對局不需建立真人快照；取消與致命錯誤不得被當成投影降級。 */
    @Test
    fun `all AI games need no observer projection and serious failures propagate`() = runTest {
        val initial = game()
        val allAi = initial.copy(aiPlayerStrategyKeys = initial.tableState.players.associate { it.id to "test:idle" })
        val fixture = Fixture(describe = { throw IllegalStateException("Must not project") })
        assertTrue(fixture.restore(allAi).snapshotFailures.isEmpty())
        listOf(CancellationException("Cancelled"), Error("Fatal")).forEach { failure ->
            val failing = Fixture(describe = { throw failure })
            val actual = assertFailsWith<Throwable> { failing.restore(game()) }
            assertSame(failure, actual)
        }
    }

    /** 所有組件投影都經過同一個逐局隔離邊界，不只保護逐張牌描述。 */
    @Test
    fun `tile player meld and table projection failures all restore conservatively`() = runTest {
        val original = game()
        val first = original.tableState.players.first()
        val meld = FakeMeldFactory.create(MeldType.Pon, List(3) { FakeIdentifiedTileFactory.create(Tile.Honor.Green) })
        val broken = original.copy(
            tableState = original.tableState.copy(
                players = original.tableState.players.map { player ->
                    if (player.id == first.id) player.copy(hand = player.hand.copy(melds = listOf(meld))) else player
                },
            ),
        )
        val healthy = game()
        listOf(BuiltInComponentOwners.TILE, BuiltInComponentOwners.PLAYER, BuiltInComponentOwners.MELD, BuiltInComponentOwners.TABLE).forEach { owner ->
            val failure = IllegalStateException("Projection failed for ${owner.id}")
            val delegate = bundledCoreExtensionRegistries().componentRegistry
            val failingRegistry = object : ComponentRegistry by delegate {
                override fun project(context: ComponentProjectionContext<*>, components: ComponentSet): ComponentViewSet {
                    if (context.tableState.id == broken.id && context.ownerType == owner) throw failure
                    return delegate.project(context, components)
                }
            }
            val fixture = Fixture(componentRegistry = failingRegistry)

            val result = fixture.restore(broken, healthy)

            assertSame(failure, result.snapshotFailures.single().cause)
            val restored = assertNotNull(fixture.snapshots.getSnapshot(broken.id, first.id))
            assertTrue(restored.componentViews.isEmpty)
            assertTrue(restored.players.all { it.componentViews.isEmpty && it.hand.melds.all { meldView -> meldView.componentViews.isEmpty } })
            assertTrue(restored.players.last().hand.standingTiles.all { it.face == null })
            assertNotNull(fixture.snapshots.getSnapshot(healthy.id, healthy.hostId))
            assertEquals(broken, fixture.store.snapshot().games[broken.id])
        }
    }

    /** 建立各有兩張手牌、尚未攤牌的兩人對局。 */
    private fun game(): Game = Game(
        tableState = FakeTableStateFactory.create(
            players = List(2) { FakeMahjongPlayerFactory.create(hand = FakeHandFactory.create(listOf(Tile.Honor.East), lastDrawn = Tile.Honor.South)) },
            config = RiichiRuleConfig(),
        ),
        flowConfig = GameFlowConfig(),
    )

    /** 建立要求公開牌面的描述，供無效 ID 與重複描述案例使用。 */
    private fun description(id: Uuid): TileComponentDescription = TileComponentDescription(id, ComponentSet.EMPTY.with(BuiltInTileComponents.FACE_ACCESS, TileFaceAccess.Public))

    /**
     * 使用正式倉庫、計時服務與可注入失敗的投影建立還原環境。
     *
     * @param describe 規則提供的逐張描述。
     * @param decorate 可在指定觀察者呼叫時失敗的政策包裝。
     * @param componentRegistry 使用正式登記或可在投影失敗的登記包裝。
     */
    private class Fixture(
        describe: (TableState) -> List<TileComponentDescription> = { emptyList() },
        decorate: (GameVisibilityPolicy) -> GameVisibilityPolicy = { it },
        componentRegistry: ComponentRegistry = bundledCoreExtensionRegistries().componentRegistry,
    ) {
        val snapshots = GameSnapshotRepositoryImpl()
        val store = AuthoritativeStateStore()
        private val registry = MahjongModuleRegistryImpl().apply {
            register(
                MahjongModuleDefinition(RiichiRuleConfig::class, "test:restoration") { config, id ->
                    object : MahjongRuleModule<RiichiRuleConfig> by RiichiRuleModule(id, config) {
                        override fun describeTileComponents(tableState: TableState): List<TileComponentDescription> = describe(tableState)
                    }
                },
            )
            freeze()
        }
        val policy = decorate(GameVisibilityPolicyImpl(registry, componentRegistry))
        private val repository = componentValidatedGameRepository(store)
        private val clock = MonotonicClockImpl()
        val timers = GameDecisionTimerManager(repository, GameDecisionAuthorityResolver(), PlayerDecisionTimerFactory(clock), clock)
        private val restorer = ServerSessionStateRestorer(
            roomSnapshots = RoomSnapshotRepositoryImpl(),
            gameSnapshots = snapshots,
            memberships = PlayerMembershipRepositoryImpl(),
            gameVisibilityPolicy = policy,
            decisionTimerManager = timers,
            decisionAvailabilityService = GameDecisionAvailabilityService(
                FakeGamePresentationBusyGate(),
                timers,
                DecisionTimerSynchronizationService(timers, repository, FakeDecisionTimerUpdatePublisher()),
            ),
            moduleRegistry = registry,
            failurePolicy = ExtensionFailurePolicy.Default,
        )

        /** 將指定場次視為已成功讀取的權威資料，再重建衍生狀態。 */
        suspend fun restore(vararg games: Game): ServerSessionStateRestoreResult {
            val state = AuthoritativeStateSnapshot(games = games.associateBy { it.id })
            store.load(state)
            return restorer.restore(state)
        }
    }
}
