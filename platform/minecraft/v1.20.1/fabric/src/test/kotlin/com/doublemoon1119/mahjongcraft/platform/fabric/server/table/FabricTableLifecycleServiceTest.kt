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

        /** 受測生命週期服務。 */
        val lifecycleService = FabricTableLifecycleService(
            store,
            locations,
            cleanupService,
            configState,
            diceRollPresenter,
        )

        /** 建立包含測試 Room 與 membership 的初始狀態。 */
        suspend fun initialize() {
            val room = Room(
                id = tableId,
                hostId = playerId,
                gameConfig = GameConfig(FakeMahjongRuleConfig()),
                playerIds = setOf(playerId),
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
}
