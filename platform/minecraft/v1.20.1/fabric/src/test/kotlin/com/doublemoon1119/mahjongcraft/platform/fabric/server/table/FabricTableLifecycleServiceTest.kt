package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateUpdate
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.entity.FabricEntitySpawnGateway
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.FabricWinCelebrationEffectScheduler
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.DebugWinRoundContinuationState
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.DebugWinShowcaseOverride
import com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence.FabricMahjongDataStatus
import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.FabricMahjongLobbyInfoPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.OrphanedTablePolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongPlayerInfoPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongPlayerInfoPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongPlayerInfoPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongRoundInfoPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongRoundInfoPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongRoundInfoPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTileSelectionConfirmPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTileSelectionConfirmPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTileSelectionConfirmPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableCornerWidthTracker
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongDiscardPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongDiscardPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongDiscardPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongInitialDealPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongPlayerAreaPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongPlayerAreaPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongPlayerAreaPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallOpeningPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallTransitionPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallTransitionResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongWinCelebrationPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongWinCelebrationResult
import com.doublemoon1119.mahjongcraft.testing.flow.common.concurrency.TestCoroutineDispatchers
import com.doublemoon1119.mahjongcraft.testing.flow.common.concurrency.createTestAppCoroutineScope
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** [FabricTableLifecycleService] 的玩家破壞事件去重與破壞判斷測試。 */
class FabricTableLifecycleServiceTest {
    /** 另一筆交易持有權威狀態的鎖時，破壞判斷仍依已發布狀態立即回答，不等待鎖。 */
    @Test
    fun `break decision does not wait for a transaction holding the lock`() = runTest {
        val fixture = Fixture()
        fixture.initialize()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val holder = launch {
            fixture.store.update { state ->
                entered.complete(Unit)
                release.await()
                AuthoritativeStateUpdate(state = state, result = Unit)
            }
        }
        entered.await()

        assertFalse(fixture.lifecycleService.isBreakAllowed(fixture.tableId))
        assertTrue(fixture.lifecycleService.isBreakAllowed(Uuid.random()))

        release.complete(Unit)
        holder.join()
    }

    /** 方塊替換已完成清理時，玩家破壞清理針對的位置項目已不存在，不得再次執行清理。 */
    @Test
    fun `player-broken cleanup is stale after block replacement removed location`() = runTest {
        val fixture = Fixture()
        fixture.initialize()

        val replacementResult = fixture.cleanupService.cleanupMissing(fixture.entry)
        val afterEventResult = fixture.lifecycleService.cleanupPlayerBroken(fixture.entry)

        assertEquals(OrphanedTableCleanupResult.REMOVED_ROOM, replacementResult)
        assertEquals(OrphanedTableCleanupResult.STALE_REQUEST, afterEventResult)
        assertNull(fixture.locations.get(fixture.tableId))
        assertNull(fixture.store.snapshot().rooms[fixture.tableId])
    }

    /** 沒有先發生方塊替換時，玩家破壞事件排入的清理應移除目前的桌子。 */
    @Test
    fun `player break event cleans current table location`() = runTest {
        val fixture = Fixture()
        fixture.initialize()

        fixture.lifecycleService.onPlayerBroken(fixture.tableId)
        fixture.drainCleanupQueue()

        assertNull(fixture.locations.get(fixture.tableId))
        assertNull(fixture.store.snapshot().rooms[fixture.tableId])
        assertNull(fixture.memberships.getVenueId(fixture.playerId))
    }

    /** 清理仍在排隊時同一 UUID 的桌子已在另一位置重新登記，排隊中的清理不得移除新來源。 */
    @Test
    fun `queued player-broken cleanup keeps a source re-registered while queued`() = runTest {
        val fixture = Fixture()
        fixture.initialize()
        val gate = CompletableDeferred<Unit>()
        fixture.cleanupQueue.submit(description = "test gate") { gate.await() }

        fixture.lifecycleService.onPlayerBroken(fixture.tableId)
        val newEntry = fixture.locations.put(fixture.tableId, TableLocation("minecraft:overworld", 32, 64, 0))
        gate.complete(Unit)
        fixture.drainCleanupQueue()

        assertSame(newEntry, fixture.locations.get(fixture.tableId))
        assertNotNull(fixture.store.snapshot().rooms[fixture.tableId])
        assertEquals(fixture.tableId, fixture.memberships.getVenueId(fixture.playerId))
    }

    /** 建立玩家破壞測試使用的權威與衍生狀態。 */
    private class Fixture {
        /** 測試桌子 UUID。 */
        val tableId = Uuid.random()

        /** 測試玩家 UUID。 */
        val playerId = Uuid.random()

        /** 權威狀態儲存。 */
        val store = AuthoritativeStateStore()

        /** 玩家 membership。 */
        val memberships = PlayerMembershipRepositoryImpl()

        /** Room snapshot repository。 */
        private val roomSnapshots = RoomSnapshotRepositoryImpl()

        /** Game snapshot repository。 */
        private val gameSnapshots = GameSnapshotRepositoryImpl()

        /** 桌子位置索引。 */
        val locations = TableLocationRegistry()

        /** 初始位置索引項目。 */
        val entry = locations.put(
            tableId,
            TableLocation("minecraft:overworld", 0, 64, 0),
        )

        /** 測試設定 state。 */
        private val configState = MinecraftServerConfigState(
            MinecraftServerConfig(orphanedTablePolicy = OrphanedTablePolicy.REMOVE_ALL),
        )

        /** 記錄桌子移除通知生命週期。 */
        private val removalNotifier = RecordingTableRemovalNotifier(locations)

        /** 實際 orphan cleanup 服務。 */
        val cleanupService = OrphanedTableCleanupService(
            store,
            memberships,
            roomSnapshots,
            gameSnapshots,
            locations,
            configState,
            removalNotifier,
        )

        /** 記錄清理呼叫的骰子 presenter fake。 */
        private val diceRollPresenter = RecordingDiceRollPresenter()

        /** 記錄清理呼叫的牌牆 presenter fake。 */
        private val tileWallPresenter = RecordingTileWallPresenter()

        /** 記錄清理呼叫的桌角區域 presenter fake。 */
        private val playerAreaPresenter = RecordingPlayerAreaPresenter()

        /** 記錄清理呼叫的規則桌面物件 presenter fake。 */
        private val tablePropPresenter = RecordingTablePropPresenter()

        /** 記錄清理呼叫的牌河 presenter fake。 */
        private val discardPresenter = RecordingDiscardPresenter()

        /** 記錄清理呼叫的桌面局況顯示 presenter fake。 */
        private val roundInfoPresenter = RecordingRoundInfoPresenter()

        /** 記錄清理呼叫的玩家資訊 presenter fake。 */
        private val playerInfoPresenter = RecordingPlayerInfoPresenter()

        /** 記錄清理呼叫的選牌確認面板 presenter fake。 */
        private val tileSelectionConfirmPresenter = RecordingTileSelectionConfirmPresenter()

        /** 依序執行權威清理的佇列。 */
        val cleanupQueue = TableCleanupQueue(scope = createTestAppCoroutineScope(), dispatchers = TestCoroutineDispatchers())

        /** 麻將資料的讀取狀態。 */
        val dataStatus = FabricMahjongDataStatus()

        /** 受測生命週期服務。 */
        val lifecycleService = FabricTableLifecycleService(
            store = store,
            locations = locations,
            debugWinRoundContinuationState = DebugWinRoundContinuationState(),
            debugWinShowcaseOverride = DebugWinShowcaseOverride(NonDevelopmentEnvironment),
            cleanupService = cleanupService,
            cleanupQueue = cleanupQueue,
            configState = configState,
            diceRollPresenter = diceRollPresenter,
            tileWallPresenter = tileWallPresenter,
            playerAreaPresenter = playerAreaPresenter,
            tablePropPresenter = tablePropPresenter,
            tableCornerWidths = TableCornerWidthTracker(),
            discardPresenter = discardPresenter,
            roundInfoPresenter = roundInfoPresenter,
            playerInfoPresenter = playerInfoPresenter,
            lobbyInfoPresenter = FabricMahjongLobbyInfoPresenter(FabricServerHolder(), locations, FabricEntitySpawnGateway(), MahjongModuleRegistryImpl()),
            tileSelectionConfirmPresenter = tileSelectionConfirmPresenter,
            presentationCleaner = FabricTablePresentationCleaner(FabricWinCelebrationEffectScheduler(FabricEntitySpawnGateway())),
            dataStatus = dataStatus,
        )

        /** 這個測試不驗證 debug 覆寫，一律回報非開發環境讓它保持 inert。 */
        object NonDevelopmentEnvironment : MinecraftEnvironment {
            override val isDevelopment: Boolean = false
        }

        /** 等待目前已排入的清理全部完成。 */
        suspend fun drainCleanupQueue() {
            cleanupQueue.submit(description = "test drain") {}.join()
        }

        /** 建立包含測試 Room 與 membership 的初始狀態。 */
        suspend fun initialize() {
            val room = Room(
                id = tableId,
                hostId = playerId,
                gameConfig = GameConfig(FakeMahjongRuleConfig()),
                playerIds = listOf(playerId),
            )
            store.load(AuthoritativeStateSnapshot(rooms = mapOf(tableId to room)))
            memberships.claim(playerId, tableId)
        }
    }

    /** 只記錄正式骰子清理參數的測試 presenter。 */
    private class RecordingDiceRollPresenter : MahjongDiceRollPresenter {
        /** 此測試不使用正式骰子呈現。 */
        override fun present(presentation: MahjongDiceRollPresentation): MahjongDiceRollPresentationResult = MahjongDiceRollPresentationResult.PRESENTED

        /** 記錄清理請求並回報沒有已載入骰子。 */
        override fun clear(tableId: Uuid, tableLocation: TableLocation): Int = 0
    }

    /** 只記錄正式牌牆清理參數的測試 presenter。 */
    private class RecordingTileWallPresenter : MahjongTileWallPresenter {
        /** 此測試不使用正式牌牆呈現。 */
        override fun present(presentation: MahjongTileWallPresentation): MahjongTileWallPresentationResult = MahjongTileWallPresentationResult.PRESENTED

        /** 此測試不使用正式開門動畫呈現。 */
        override fun presentOpening(presentation: MahjongTileWallOpeningPresentation): MahjongTileWallPresentationResult = MahjongTileWallPresentationResult.PRESENTED

        /** 此測試不使用正式牌牆 transition 呈現。 */
        override fun presentTransition(
            presentation: MahjongTileWallTransitionPresentation,
        ): MahjongTileWallTransitionResult = MahjongTileWallTransitionResult.PRESENTED

        /** 此測試不使用正式王牌追加翻面呈現。 */
        override fun revealDeadWallTiles(tableId: Uuid, tableLocation: TableLocation, revealedTileIds: Set<Uuid>): MahjongTileWallPresentationResult = MahjongTileWallPresentationResult.PRESENTED

        /** 記錄清理請求並回報沒有已載入牌牆用牌。 */
        override fun clear(tableId: Uuid, tableLocation: TableLocation): Int = 0
    }

    /** 只記錄正式桌角區域（手牌/摸牌位/副露）清理參數的測試 presenter。 */
    private class RecordingPlayerAreaPresenter : MahjongPlayerAreaPresenter {
        /** 此測試不使用正式桌角區域呈現。 */
        override fun present(presentation: MahjongPlayerAreaPresentation): MahjongPlayerAreaPresentationResult = MahjongPlayerAreaPresentationResult.PRESENTED

        /** 此測試不使用正式開局發牌動畫呈現。 */
        override fun presentInitialDeal(presentation: MahjongInitialDealPresentation): MahjongPlayerAreaPresentationResult = MahjongPlayerAreaPresentationResult.PRESENTED

        /** 此測試不使用胡牌慶祝演出呈現。 */
        override fun presentWinCelebration(presentation: MahjongWinCelebrationPresentation): MahjongWinCelebrationResult = MahjongWinCelebrationResult(MahjongPlayerAreaPresentationResult.PRESENTED, null)

        /** 記錄清理請求並回報沒有已載入桌角區域用牌。 */
        override fun clear(tableId: Uuid, tableLocation: TableLocation): Int = 0
    }

    /** 只記錄規則桌面物件清理參數的測試 presenter。 */
    private class RecordingTablePropPresenter : TablePropPresenter {
        /** 此測試不使用規則桌面物件呈現。 */
        override fun present(presentation: TablePropPresentation): TablePropPresentationResult = TablePropPresentationResult.PRESENTED

        /** 記錄清理請求並回報沒有已載入的規則桌面物件。 */
        override fun clear(tableId: Uuid, tableLocation: TableLocation): Int = 0
    }

    /** 只記錄正式牌河清理參數的測試 presenter。 */
    private class RecordingDiscardPresenter : MahjongDiscardPresenter {
        /** 此測試不使用正式牌河呈現。 */
        override fun present(presentation: MahjongDiscardPresentation): MahjongDiscardPresentationResult = MahjongDiscardPresentationResult.PRESENTED

        /** 記錄清理請求並回報沒有已載入牌河。 */
        override fun clear(tableId: Uuid, tableLocation: TableLocation): Int = 0
    }

    /** 只記錄正式桌面局況顯示清理參數的測試 presenter。 */
    private class RecordingRoundInfoPresenter : MahjongRoundInfoPresenter {
        /** 此測試不使用正式局況顯示呈現。 */
        override fun present(presentation: MahjongRoundInfoPresentation): MahjongRoundInfoPresentationResult = MahjongRoundInfoPresentationResult.PRESENTED

        /** 記錄清理請求並回報沒有已載入局況顯示。 */
        override fun clear(tableId: Uuid, tableLocation: TableLocation): Int = 0
    }

    /** 此測試只需要滿足玩家公開資訊清理邊界。 */
    private class RecordingPlayerInfoPresenter : MahjongPlayerInfoPresenter {
        /** 此測試不使用正式玩家公開資訊呈現。 */
        override fun present(
            presentation: MahjongPlayerInfoPresentation,
            tableLocation: TableLocation,
            tableFacing: MahjongTableFacing,
        ): MahjongPlayerInfoPresentationResult = MahjongPlayerInfoPresentationResult.PRESENTED

        /** 此測試不使用正式玩家資訊延後顯示。 */
        override fun hideUntil(tableId: Uuid, tableLocation: TableLocation, gameTime: Long) = Unit

        /** 記錄清理請求並回報沒有已載入玩家資訊。 */
        override fun clear(tableId: Uuid, tableLocation: TableLocation): Int = 0
    }

    /** 此測試只需要滿足多選選牌確認面板清理邊界。 */
    private class RecordingTileSelectionConfirmPresenter : MahjongTileSelectionConfirmPresenter {
        /** 此測試不使用正式選牌確認面板呈現。 */
        override fun present(presentation: MahjongTileSelectionConfirmPresentation): MahjongTileSelectionConfirmPresentationResult = MahjongTileSelectionConfirmPresentationResult.PRESENTED

        /** 此測試不使用正式單一玩家面板清理。 */
        override fun clearForPlayer(tableId: Uuid, tableLocation: TableLocation, holderId: Uuid): Int = 0

        /** 記錄清理請求並回報沒有已載入選牌確認面板。 */
        override fun clear(tableId: Uuid, tableLocation: TableLocation): Int = 0
    }
}
