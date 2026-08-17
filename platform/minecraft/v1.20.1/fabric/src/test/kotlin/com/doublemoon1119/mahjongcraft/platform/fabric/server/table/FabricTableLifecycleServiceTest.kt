package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.OrphanedTablePolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongDiscardPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongDiscardPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongDiscardPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongDrawnTilePresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongDrawnTilePresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongHandTilesPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongHandTilesPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongHandTilesPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresenter
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** [FabricTableLifecycleService] 的玩家破壞事件去重測試。 */
class FabricTableLifecycleServiceTest {
    /** 方塊替換已完成清理時，後續 Fabric AFTER event 不得再次執行清理。 */
    @Test
    fun `player break event skips cleanup after block replacement removed location`() = runTest {
        val fixture = Fixture()
        fixture.initialize()

        val replacementResult = fixture.cleanupService.cleanupMissing(fixture.tableId, fixture.entryRevision)
        val afterEventResult = fixture.lifecycleService.cleanupPlayerBroken(fixture.tableId)

        assertEquals(OrphanedTableCleanupResult.REMOVED_ROOM, replacementResult)
        assertNull(afterEventResult)
        assertNull(fixture.locations.get(fixture.tableId))
        assertNull(fixture.store.snapshot().rooms[fixture.tableId])
    }

    /** 沒有先發生方塊替換時，Fabric AFTER event 應完成玩家破壞清理。 */
    @Test
    fun `player break event cleans current table location`() = runTest {
        val fixture = Fixture()
        fixture.initialize()

        val result = fixture.lifecycleService.cleanupPlayerBroken(fixture.tableId)

        assertEquals(OrphanedTableCleanupResult.REMOVED_ROOM, result)
        assertNull(fixture.locations.get(fixture.tableId))
        assertNull(fixture.store.snapshot().rooms[fixture.tableId])
    }

    /** 建立玩家破壞測試使用的權威與衍生狀態。 */
    private class Fixture {
        /** 測試桌子 UUID。 */
        val tableId = Uuid.random()

        /** 測試玩家 UUID。 */
        private val playerId = Uuid.random()

        /** 權威狀態儲存。 */
        val store = AuthoritativeStateStore()

        /** 玩家 membership。 */
        private val memberships = PlayerMembershipRepositoryImpl()

        /** Room snapshot repository。 */
        private val roomSnapshots = RoomSnapshotRepositoryImpl()

        /** Game snapshot repository。 */
        private val gameSnapshots = GameSnapshotRepositoryImpl()

        /** 桌子位置索引。 */
        val locations = TableLocationRegistry()

        /** 初始位置 revision。 */
        val entryRevision = locations.put(
            tableId,
            TableLocation("minecraft:overworld", 0, 64, 0),
        ).revision

        /** 測試設定 state。 */
        private val configState = MinecraftServerConfigState(
            MinecraftServerConfig(orphanedTablePolicy = OrphanedTablePolicy.REMOVE_ALL),
        )

        /** 實際 orphan cleanup 服務。 */
        val cleanupService = OrphanedTableCleanupService(
            store,
            memberships,
            roomSnapshots,
            gameSnapshots,
            locations,
            configState,
        )

        /** 受測 Fabric lifecycle 服務。 */
        /** 記錄清理呼叫的骰子 presenter fake。 */
        private val diceRollPresenter = RecordingDiceRollPresenter()

        /** 記錄清理呼叫的牌牆 presenter fake。 */
        private val tileWallPresenter = RecordingTileWallPresenter()

        /** 記錄清理呼叫的手牌 presenter fake。 */
        private val handTilesPresenter = RecordingHandTilesPresenter()

        /** 記錄清理呼叫的牌河 presenter fake。 */
        private val discardPresenter = RecordingDiscardPresenter()

        /** 受測生命週期服務。 */
        val lifecycleService = FabricTableLifecycleService(
            store,
            locations,
            cleanupService,
            configState,
            diceRollPresenter,
            tileWallPresenter,
            handTilesPresenter,
            discardPresenter,
        )

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

        /** 記錄清理請求並回報沒有已載入牌牆用牌。 */
        override fun clear(tableId: Uuid, tableLocation: TableLocation): Int = 0
    }

    /** 只記錄正式手牌清理參數的測試 presenter。 */
    private class RecordingHandTilesPresenter : MahjongHandTilesPresenter {
        /** 此測試不使用正式手牌呈現。 */
        override fun present(presentation: MahjongHandTilesPresentation): MahjongHandTilesPresentationResult = MahjongHandTilesPresentationResult.PRESENTED

        /** 此測試不使用正式摸牌位呈現。 */
        override fun presentDrawnTile(presentation: MahjongDrawnTilePresentation): MahjongDrawnTilePresentationResult = MahjongDrawnTilePresentationResult.PRESENTED

        /** 記錄清理請求並回報沒有已載入手牌。 */
        override fun clear(tableId: Uuid, tableLocation: TableLocation): Int = 0
    }

    /** 只記錄正式牌河清理參數的測試 presenter。 */
    private class RecordingDiscardPresenter : MahjongDiscardPresenter {
        /** 此測試不使用正式牌河呈現。 */
        override fun present(presentation: MahjongDiscardPresentation): MahjongDiscardPresentationResult = MahjongDiscardPresentationResult.PRESENTED

        /** 記錄清理請求並回報沒有已載入牌河。 */
        override fun clear(tableId: Uuid, tableLocation: TableLocation): Int = 0
    }
}
