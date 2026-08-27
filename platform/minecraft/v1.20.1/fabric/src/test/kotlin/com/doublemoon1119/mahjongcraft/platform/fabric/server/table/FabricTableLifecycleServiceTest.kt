package com.doublemoon1119.mahjongcraft.platform.fabric.server.table

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.DebugWinRoundContinuationState
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.DebugWinShowcaseOverride
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.OrphanedTablePolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceRollPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.stick.MahjongScoringStickPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongRoundInfoPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongRoundInfoPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongRoundInfoPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongDiscardPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongDiscardPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongDiscardPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongInitialDealPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongPlayerAreaPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongPlayerAreaPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongPlayerAreaPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresentationResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongWinCelebrationPresentation
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongWinCelebrationResult
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

        /** 記錄清理呼叫的桌角區域 presenter fake。 */
        private val playerAreaPresenter = RecordingPlayerAreaPresenter()

        /** 記錄清理呼叫的積棒 presenter fake。 */
        private val scoringStickPresenter = RecordingScoringStickPresenter()

        /** 記錄清理呼叫的牌河 presenter fake。 */
        private val discardPresenter = RecordingDiscardPresenter()

        /** 記錄清理呼叫的桌面局況顯示 presenter fake。 */
        private val roundInfoPresenter = RecordingRoundInfoPresenter()

        /** 受測生命週期服務。 */
        val lifecycleService = FabricTableLifecycleService(
            store = store,
            locations = locations,
            debugWinRoundContinuationState = DebugWinRoundContinuationState(),
            debugWinShowcaseOverride = DebugWinShowcaseOverride(NonDevelopmentEnvironment),
            cleanupService = cleanupService,
            configState = configState,
            diceRollPresenter = diceRollPresenter,
            tileWallPresenter = tileWallPresenter,
            playerAreaPresenter = playerAreaPresenter,
            scoringStickPresenter = scoringStickPresenter,
            discardPresenter = discardPresenter,
            roundInfoPresenter = roundInfoPresenter,
        )

        /** 這個測試不驗證 debug 覆寫，一律回報非開發環境讓它保持 inert。 */
        object NonDevelopmentEnvironment : MinecraftEnvironment {
            override val isDevelopment: Boolean = false
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

    /** 只記錄正式積棒清理參數的測試 presenter。 */
    private class RecordingScoringStickPresenter : MahjongScoringStickPresenter {
        /** 此測試不使用正式積棒呈現。 */
        override fun present(presentation: MahjongScoringStickPresentation): MahjongScoringStickPresentationResult = MahjongScoringStickPresentationResult.PRESENTED

        /** 記錄清理請求並回報沒有已載入積棒。 */
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
}
